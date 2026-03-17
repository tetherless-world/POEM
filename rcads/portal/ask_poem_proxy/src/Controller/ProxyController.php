<?php

namespace Drupal\ask_poem_proxy\Controller;

use Drupal\Core\Controller\ControllerBase;
use Drupal\Core\Logger\LoggerChannelFactoryInterface;
use GuzzleHttp\ClientInterface;
use GuzzleHttp\Exception\GuzzleException;
use GuzzleHttp\Exception\RequestException;
use Psr\Log\LoggerInterface;
use Symfony\Component\DependencyInjection\ContainerInterface;
use Symfony\Component\HttpFoundation\Cookie;
use Symfony\Component\HttpFoundation\Request;
use Symfony\Component\HttpFoundation\Response;
use Symfony\Component\HttpFoundation\StreamedResponse;
use Symfony\Component\HttpKernel\Exception\HttpException;

/**
 * Reverse proxy controller for ASK POEM.
 */
class ProxyController extends ControllerBase {

  /**
   * Default proxy route base.
   */
  private const DEFAULT_PROXY_ROUTE_BASE = '/ask-poem';

  /**
   * Suffix used by the internal resource proxy route.
   */
  private const PROXY_RESOURCE_SUFFIX = '-resource';

  /**
   * Internal query key used to carry proxied path values.
   */
  private const INTERNAL_PATH_QUERY_KEY = '_ap_path';

  /**
   * Remote HTTP client.
   */
  protected ClientInterface $httpClient;

  /**
   * Logger channel.
   */
  protected LoggerInterface $logger;

  /**
   * ProxyController constructor.
   */
  public function __construct(ClientInterface $http_client, LoggerChannelFactoryInterface $logger_factory) {
    $this->httpClient = $http_client;
    $this->logger = $logger_factory->get('ask_poem_proxy');
  }

  /**
   * {@inheritdoc}
   */
  public static function create(ContainerInterface $container): static {
    return new static(
      $container->get('http_client'),
      $container->get('logger.factory')
    );
  }

  /**
   * Proxies the incoming request to the configured upstream website.
   */
  public function proxy(Request $request, string $proxy_path = ''): Response {
    $config = $this->config('ask_poem_proxy.settings');
    $upstream_base_url = trim((string) $config->get('upstream_base_url'));
    $proxy_path = $this->resolveProxyPath($request, $proxy_path);
    $forward_query_string = $this->buildForwardQueryString($request);

    if ($upstream_base_url === '') {
      throw new HttpException(500, (string) $this->t('ASK POEM proxy upstream URL is not configured.'));
    }

    $method = strtoupper($request->getMethod());
    $headers = $this->buildForwardHeaders(
      $request->headers->all(),
      trim((string) $config->get('auth_header')),
      $request
    );

    $options = [
      'stream' => TRUE,
      'http_errors' => FALSE,
      'allow_redirects' => FALSE,
      'headers' => $headers,
    ];

    if (!in_array($method, ['GET', 'HEAD'], TRUE)) {
      $options['body'] = $request->getContent();
    }

    $path_candidates = $this->buildUpstreamPathCandidates($upstream_base_url, $proxy_path);
    $effective_proxy_path = $path_candidates[0];
    $upstream_url = '';
    $upstream_response = NULL;

    foreach ($path_candidates as $index => $candidate_path) {
      $candidate_url = $this->buildUpstreamUrl(
        $upstream_base_url,
        $candidate_path,
        $forward_query_string
      );

      try {
        $candidate_response = $this->httpClient->request($method, $candidate_url, $options);
      }
      catch (RequestException $exception) {
        $this->logger->error('Request to %url failed: @message', [
          '%url' => $candidate_url,
          '@message' => $exception->getMessage(),
        ]);
        throw new HttpException(502, (string) $this->t('Unable to reach the upstream service.'));
      }
      catch (GuzzleException $exception) {
        $this->logger->error('Unexpected proxy error for %url: @message', [
          '%url' => $candidate_url,
          '@message' => $exception->getMessage(),
        ]);
        throw new HttpException(502, (string) $this->t('Unable to reach the upstream service.'));
      }

      $is_last_candidate = $index === array_key_last($path_candidates);
      $is_retryable_status = in_array($method, ['GET', 'HEAD'], TRUE) && $candidate_response->getStatusCode() === 404;

      if (!$is_last_candidate && $is_retryable_status) {
        $candidate_response->getBody()->close();
        continue;
      }

      $upstream_response = $candidate_response;
      $upstream_url = $candidate_url;
      $effective_proxy_path = $candidate_path;
      break;
    }

    if ($upstream_response === NULL) {
      throw new HttpException(502, (string) $this->t('Unable to reach the upstream service.'));
    }

    $content_type = $upstream_response->getHeaderLine('Content-Type');
    $rewrite_body = $method !== 'HEAD' && $this->shouldRewriteBody($content_type);

    if ($rewrite_body) {
      $body = (string) $upstream_response->getBody();
      try {
        $body = $this->rewriteResponseBody($body, $content_type, $request, $effective_proxy_path);
      }
      catch (\Throwable $exception) {
        $this->logger->error('Body rewrite failed for %url: @message', [
          '%url' => $upstream_url,
          '@message' => $exception->getMessage(),
        ]);
      }
      $response = new Response($body, $upstream_response->getStatusCode());
    }
    else {
      $stream = $upstream_response->getBody();
      $response = new StreamedResponse(static function () use ($stream, $method) {
        if ($method === 'HEAD') {
          $stream->close();
          return;
        }

        while (!$stream->eof()) {
          echo $stream->read(8192);
          flush();
        }
        $stream->close();
      }, $upstream_response->getStatusCode());
    }

    foreach ($upstream_response->getHeaders() as $name => $values) {
      if ($this->isHopByHopHeader($name)) {
        continue;
      }

      if ($rewrite_body && in_array(strtolower($name), ['content-length', 'content-encoding', 'etag', 'last-modified'], TRUE)) {
        continue;
      }

      foreach ($values as $value) {
        if (strtolower($name) === 'location') {
          $value = $this->rewriteLocationHeader($value, $upstream_base_url, $request);
        }

        if (strtolower($name) === 'set-cookie') {
          try {
            $response->headers->setCookie(Cookie::fromString($value));
          }
          catch (\Throwable) {
            $response->headers->set($name, $value, FALSE);
          }
          continue;
        }

        $response->headers->set($name, $value, FALSE);
      }
    }

    return $response;
  }

  /**
   * Builds the upstream URL from settings + incoming path + query string.
   */
  protected function buildUpstreamUrl(string $base_url, string $effective_proxy_path, string $incoming_query): string {
    $base_parts = parse_url($base_url);
    if ($base_parts === FALSE || empty($base_parts['scheme']) || empty($base_parts['host'])) {
      throw new HttpException(500, (string) $this->t('ASK POEM proxy upstream URL is invalid.'));
    }

    $scheme = strtolower((string) $base_parts['scheme']);
    if (!in_array($scheme, ['http', 'https'], TRUE)) {
      throw new HttpException(500, (string) $this->t('Only HTTP and HTTPS upstream URLs are allowed.'));
    }

    $origin = $this->buildOrigin($base_parts);
    $full_path = $effective_proxy_path !== '' ? $effective_proxy_path : '/';
    if (!str_starts_with($full_path, '/')) {
      $full_path = '/' . $full_path;
    }

    $query_parts = [];
    if (!empty($base_parts['query'])) {
      $query_parts[] = (string) $base_parts['query'];
    }
    if ($incoming_query !== '') {
      $query_parts[] = $incoming_query;
    }

    $url = $origin . $full_path;
    if ($query_parts !== []) {
      $url .= '?' . implode('&', $query_parts);
    }

    return $url;
  }

  /**
   * Determines the upstream path represented by the current proxy request.
   */
  protected function buildUpstreamPathCandidates(string $base_url, string $proxy_path): array {
    $request_path = trim($proxy_path, '/');
    $base_path = $this->getConfiguredBasePath($base_url);
    $candidates = [];

    if ($request_path === '') {
      $candidates[] = $base_path !== '' ? $base_path : '/';
      if ($base_path !== '') {
        $candidates[] = '/';
      }
      return array_values(array_unique($candidates));
    }

    $request_candidate = '/' . $request_path;
    $candidates[] = $request_candidate;

    if ($base_path !== '') {
      $candidates[] = rtrim($base_path, '/') . $request_candidate;
    }

    return array_values(array_unique($candidates));
  }

  /**
   * Returns the configured upstream base path as a normalized absolute path.
   */
  protected function getConfiguredBasePath(string $base_url): string {
    $base_parts = parse_url($base_url);
    if ($base_parts === FALSE) {
      return '';
    }

    $path = trim((string) ($base_parts['path'] ?? ''), '/');
    if ($path === '') {
      return '';
    }

    return '/' . $path;
  }

  /**
   * Builds proxy request headers to send upstream.
   */
  protected function buildForwardHeaders(array $incoming_headers, string $auth_header, Request $request): array {
    $headers = [];

    foreach ($incoming_headers as $name => $values) {
      if ($this->isHopByHopHeader($name) || in_array(strtolower($name), ['host', 'content-length'], TRUE)) {
        continue;
      }
      $headers[$name] = implode(', ', $values);
    }

    $headers['X-Forwarded-Proto'] = $request->getScheme();
    $headers['X-Forwarded-Host'] = $request->getHttpHost();
    $headers['X-Forwarded-Uri'] = $request->getRequestUri();

    $remote_addr = $request->server->get('REMOTE_ADDR');
    if (is_string($remote_addr) && $remote_addr !== '') {
      $headers['X-Forwarded-For'] = $remote_addr;
    }

    if ($auth_header !== '') {
      $headers['Authorization'] = $auth_header;
    }

    // Disable upstream compression so HTML/CSS can be rewritten safely.
    $headers['Accept-Encoding'] = 'identity';

    return $headers;
  }

  /**
   * Rewrites redirect location headers to keep clients on the proxy route.
   */
  protected function rewriteLocationHeader(string $location, string $upstream_base_url, Request $request): string {
    $base_parts = parse_url($upstream_base_url);
    if ($base_parts === FALSE || empty($base_parts['scheme']) || empty($base_parts['host'])) {
      return $location;
    }

    $proxy_prefix = rtrim($request->getBasePath(), '/') . $this->getConfiguredProxyRouteBase();
    $upstream_origin = $this->buildOrigin($base_parts);

    $upstream_base_path = '/' . trim((string) ($base_parts['path'] ?? ''), '/');
    if ($upstream_base_path === '/') {
      $upstream_base_path = '';
    }

    if (str_starts_with($location, $upstream_origin)) {
      $tail = substr($location, strlen($upstream_origin));
      if ($upstream_base_path !== '' && ($tail === $upstream_base_path || str_starts_with($tail, $upstream_base_path . '/'))) {
        $tail = substr($tail, strlen($upstream_base_path));
      }
      if ($tail === '' || !str_starts_with($tail, '/')) {
        $tail = '/' . ltrim($tail, '/');
      }
      return $proxy_prefix . $tail;
    }

    if (str_starts_with($location, '/')) {
      $tail = $location;
      if ($upstream_base_path !== '' && ($tail === $upstream_base_path || str_starts_with($tail, $upstream_base_path . '/'))) {
        $tail = substr($tail, strlen($upstream_base_path));
        if ($tail === '') {
          $tail = '/';
        }
      }
      return $proxy_prefix . $tail;
    }

    return $location;
  }

  /**
   * Returns TRUE when a response body should be rewritten for proxy paths.
   */
  protected function shouldRewriteBody(string $content_type): bool {
    $content_type = strtolower(trim(explode(';', $content_type)[0] ?? ''));
    return in_array($content_type, ['text/html', 'application/xhtml+xml', 'text/css'], TRUE);
  }

  /**
   * Rewrites root-relative links so they stay under the proxy route.
   */
  protected function rewriteResponseBody(string $body, string $content_type, Request $request, string $effective_proxy_path): string {
    $mime = strtolower(trim(explode(';', $content_type)[0] ?? ''));
    $proxy_route_base = $this->getConfiguredProxyRouteBase();
    $proxy_prefix = rtrim($request->getBasePath(), '/') . $proxy_route_base;
    $resource_prefix = rtrim($request->getBasePath(), '/') . $this->buildResourceRouteBase($proxy_route_base);

    if (in_array($mime, ['text/html', 'application/xhtml+xml'], TRUE)) {
      $body = $this->ensureHtmlBaseHref($body, $request, $effective_proxy_path);
      $body = $this->rewriteHtmlAttributes($body, $proxy_prefix, $resource_prefix);
      $body = $this->rewriteSrcsetAttributes($body, $proxy_prefix, $resource_prefix);
      $body = $this->rewriteCssUrls($body, $proxy_prefix, $resource_prefix);
    }
    elseif ($mime === 'text/css') {
      $body = $this->rewriteCssUrls($body, $proxy_prefix, $resource_prefix);
    }

    return $body;
  }

  /**
   * Rewrites href/src/action/poster attributes containing root-relative paths.
   */
  protected function rewriteHtmlAttributes(string $body, string $proxy_prefix, string $resource_prefix): string {
    $result = preg_replace_callback(
      '/\b(href|src|action|poster)\s*=\s*(?:"([^"]*)"|\'([^\']*)\'|([^\s"\'>]+))/i',
      function (array $matches) use ($proxy_prefix, $resource_prefix): string {
        $quote = '';
        $value = '';

        if (isset($matches[2]) && $matches[2] !== '') {
          $quote = '"';
          $value = $matches[2];
        }
        elseif (isset($matches[3]) && $matches[3] !== '') {
          $quote = "'";
          $value = $matches[3];
        }
        elseif (isset($matches[4])) {
          $value = $matches[4];
        }

        $value = $this->rewriteRootRelativeUrl($value, $proxy_prefix, $resource_prefix);
        if ($quote !== '') {
          return $matches[1] . '=' . $quote . $value . $quote;
        }

        return $matches[1] . '=' . $value;
      },
      $body
    );
    return is_string($result) ? $result : $body;
  }

  /**
   * Rewrites root-relative URLs within srcset attributes.
   */
  protected function rewriteSrcsetAttributes(string $body, string $proxy_prefix, string $resource_prefix): string {
    $result = preg_replace_callback(
      '/\bsrcset\s*=\s*(?:"([^"]*)"|\'([^\']*)\'|([^\s"\'>]+))/i',
      function (array $matches) use ($proxy_prefix, $resource_prefix): string {
        $quote = '';
        $raw = '';
        if (isset($matches[1]) && $matches[1] !== '') {
          $quote = '"';
          $raw = $matches[1];
        }
        elseif (isset($matches[2]) && $matches[2] !== '') {
          $quote = "'";
          $raw = $matches[2];
        }
        elseif (isset($matches[3])) {
          $raw = $matches[3];
        }

        $entries = array_map('trim', explode(',', $raw));
        foreach ($entries as $index => $entry) {
          if ($entry === '') {
            continue;
          }

          $parts = preg_split('/\s+/', $entry, 2);
          $url = $parts[0] ?? '';
          $descriptor = $parts[1] ?? '';
          $rewritten = $this->rewriteRootRelativeUrl($url, $proxy_prefix, $resource_prefix);
          $entries[$index] = trim($rewritten . ' ' . $descriptor);
        }

        $value = implode(', ', $entries);
        if ($quote !== '') {
          return 'srcset=' . $quote . $value . $quote;
        }

        return 'srcset=' . $value;
      },
      $body
    );
    return is_string($result) ? $result : $body;
  }

  /**
   * Ensures HTML has a base href pointing to the current proxy directory.
   */
  protected function ensureHtmlBaseHref(string $body, Request $request, string $effective_proxy_path): string {
    $base_href = $this->buildHtmlBaseHref($request, $effective_proxy_path);

    if (preg_match('/<base\b[^>]*>/i', $body) === 1) {
      $result = preg_replace('/<base\b[^>]*>/i', '<base href="' . htmlspecialchars($base_href, ENT_QUOTES | ENT_SUBSTITUTE, 'UTF-8') . '">', $body, 1);
      return is_string($result) ? $result : $body;
    }

    $head_tag = preg_match('/<head\b[^>]*>/i', $body);
    if ($head_tag === 1) {
      $result = preg_replace(
        '/<head\b[^>]*>/i',
        '$0' . "\n" . '<base href="' . htmlspecialchars($base_href, ENT_QUOTES | ENT_SUBSTITUTE, 'UTF-8') . '">',
        $body,
        1
      );
      return is_string($result) ? $result : $body;
    }

    return $body;
  }

  /**
   * Builds a base href that keeps relative URLs under the proxy route.
   */
  protected function buildHtmlBaseHref(Request $request, string $effective_proxy_path): string {
    $proxy_prefix = rtrim($request->getBasePath(), '/') . $this->getConfiguredProxyRouteBase();

    if ($effective_proxy_path === '' || $effective_proxy_path === '/') {
      return $proxy_prefix . '/';
    }

    if (str_ends_with($effective_proxy_path, '/')) {
      return $proxy_prefix . $effective_proxy_path;
    }

    $directory = dirname($effective_proxy_path);
    if ($directory === '.' || $directory === DIRECTORY_SEPARATOR) {
      return $proxy_prefix . '/';
    }

    return $proxy_prefix . rtrim(str_replace('\\', '/', $directory), '/') . '/';
  }

  /**
   * Rewrites root-relative URLs used inside CSS url(...) tokens.
   */
  protected function rewriteCssUrls(string $body, string $proxy_prefix, string $resource_prefix): string {
    $result = preg_replace_callback(
      '/url\(\s*(["\']?)([^)"\']+)\1\s*\)/i',
      function (array $matches) use ($proxy_prefix, $resource_prefix): string {
        $url = $this->rewriteRootRelativeUrl($matches[2], $proxy_prefix, $resource_prefix);
        return 'url(' . $matches[1] . $url . $matches[1] . ')';
      },
      $body
    );
    return is_string($result) ? $result : $body;
  }

  /**
   * Rewrites a URL if it starts at root and is not already proxied.
   */
  protected function rewriteRootRelativeUrl(string $url, string $proxy_prefix, string $resource_prefix): string {
    $parts = parse_url($url);
    if ($parts === FALSE) {
      return $url;
    }

    $path = (string) ($parts['path'] ?? '');
    if (!str_starts_with($path, '/')) {
      return $url;
    }

    if (str_starts_with($path, '//')) {
      return $url;
    }

    if ($path === $proxy_prefix || str_starts_with($path, $proxy_prefix . '/')) {
      return $url;
    }

    if ($path === $resource_prefix || str_starts_with($path, $resource_prefix . '/')) {
      return $url;
    }

    if ($this->shouldRouteViaQueryProxy($path)) {
      $rewritten = $resource_prefix . '?' . self::INTERNAL_PATH_QUERY_KEY . '=' . rawurlencode($path);
    }
    else {
      $rewritten = $proxy_prefix . $path;
    }

    $query = (string) ($parts['query'] ?? '');
    if ($query !== '') {
      $separator = str_contains($rewritten, '?') ? '&' : '?';
      $rewritten .= $separator . $query;
    }

    $fragment = (string) ($parts['fragment'] ?? '');
    if ($fragment !== '') {
      $rewritten .= '#' . $fragment;
    }

    return $rewritten;
  }

  /**
   * Detects URLs that should be routed through query-based proxy endpoint.
   */
  protected function shouldRouteViaQueryProxy(string $path): bool {
    $extension = pathinfo($path, PATHINFO_EXTENSION);
    if ($extension === '') {
      return FALSE;
    }

    return preg_match('/^[a-z0-9]{1,8}$/i', $extension) === 1;
  }

  /**
   * Resolves proxy path from route parameter or internal query fallback.
   */
  protected function resolveProxyPath(Request $request, string $proxy_path): string {
    $proxy_path = trim($proxy_path);
    if ($proxy_path !== '') {
      return $proxy_path;
    }

    $raw = trim((string) $request->query->get(self::INTERNAL_PATH_QUERY_KEY, ''));
    if ($raw === '') {
      return '';
    }

    $decoded = rawurldecode($raw);
    return ltrim($decoded, '/');
  }

  /**
   * Builds forwarded query string excluding internal proxy parameters.
   */
  protected function buildForwardQueryString(Request $request): string {
    $query = $request->query->all();
    unset($query[self::INTERNAL_PATH_QUERY_KEY]);

    if ($query === []) {
      return '';
    }

    return http_build_query($query, '', '&', PHP_QUERY_RFC3986);
  }

  /**
   * Gets the configured proxy route base path.
   */
  protected function getConfiguredProxyRouteBase(): string {
    $configured_path = (string) $this->config('ask_poem_proxy.settings')->get('proxy_base_path');
    return $this->normalizeProxyBasePath($configured_path);
  }

  /**
   * Normalizes a proxy base path from configuration.
   */
  protected function normalizeProxyBasePath(string $path): string {
    $path = trim($path);
    if ($path === '') {
      return self::DEFAULT_PROXY_ROUTE_BASE;
    }

    if (!str_starts_with($path, '/')) {
      $path = '/' . $path;
    }

    $path = preg_replace('#/+#', '/', $path) ?? self::DEFAULT_PROXY_ROUTE_BASE;
    $path = rtrim($path, '/');

    if ($path === '' || !preg_match('/^\/[a-z0-9\/_-]+$/i', $path) || $path === '/query') {
      return self::DEFAULT_PROXY_ROUTE_BASE;
    }

    return $path;
  }

  /**
   * Builds the proxy resource route base from the proxy base path.
   */
  protected function buildResourceRouteBase(string $proxy_route_base): string {
    return rtrim($proxy_route_base, '/') . self::PROXY_RESOURCE_SUFFIX;
  }

  /**
   * Returns whether the header is hop-by-hop and should not be forwarded.
   */
  protected function isHopByHopHeader(string $name): bool {
    return in_array(strtolower($name), [
      'connection',
      'keep-alive',
      'proxy-authenticate',
      'proxy-authorization',
      'te',
      'trailer',
      'transfer-encoding',
      'upgrade',
    ], TRUE);
  }

  /**
   * Builds URI origin (scheme + authority) from parsed URL parts.
   */
  protected function buildOrigin(array $parts): string {
    $origin = $parts['scheme'] . '://';

    if (isset($parts['user'])) {
      $origin .= $parts['user'];
      if (isset($parts['pass'])) {
        $origin .= ':' . $parts['pass'];
      }
      $origin .= '@';
    }

    $origin .= $parts['host'];
    if (isset($parts['port'])) {
      $origin .= ':' . $parts['port'];
    }

    return $origin;
  }

}
