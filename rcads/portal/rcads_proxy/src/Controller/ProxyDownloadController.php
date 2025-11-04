<?php

namespace Drupal\rcads_proxy\Controller;

use Drupal\Core\Config\ConfigFactoryInterface;
use Drupal\Core\Controller\ControllerBase;
use Drupal\Core\Logger\LoggerChannelFactoryInterface;
use GuzzleHttp\ClientInterface;
use GuzzleHttp\Exception\GuzzleException;
use GuzzleHttp\Exception\RequestException;
use Psr\Log\LoggerInterface;
use Symfony\Component\DependencyInjection\ContainerInterface;
use Symfony\Component\HttpFoundation\HeaderUtils;
use Symfony\Component\HttpFoundation\Request;
use Symfony\Component\HttpFoundation\StreamedResponse;
use Symfony\Component\HttpKernel\Exception\HttpException;
use Symfony\Component\HttpKernel\Exception\NotFoundHttpException;

/**
 * Controller for proxying file downloads from an external service.
 */
class ProxyDownloadController extends ControllerBase {

  /**
   * Remote HTTP client.
   */
  protected ClientInterface $httpClient;

  /**
   * Configuration factory.
   *
   * @var \Drupal\Core\Config\ConfigFactoryInterface
   */
  protected $configFactory;

  /**
   * Logger channel.
   */
  protected LoggerInterface $logger;

  /**
   * ProxyDownloadController constructor.
   */
  public function __construct(ClientInterface $http_client, ConfigFactoryInterface $config_factory, LoggerChannelFactoryInterface $logger_factory) {
    $this->httpClient = $http_client;
    $this->configFactory = $config_factory;
    $this->logger = $logger_factory->get('rcads_proxy');
  }

  /**
   * {@inheritdoc}
   */
  public static function create(ContainerInterface $container): static {
    return new static(
      $container->get('http_client'),
      $container->get('config.factory'),
      $container->get('logger.factory')
    );
  }

  /**
   * Streams a file from the configured remote service.
   */
  public function download(Request $request): StreamedResponse {
    $config = $this->configFactory->get('rcads_proxy.settings');
    $base_url = (string) $config->get('base_url');

    $language = trim((string) $request->query->get('language'));
    $format = trim((string) $request->query->get('format'));
    $uri = trim((string) $request->query->get('uri'));

    if ($language === '' || $format === '') {
      throw new HttpException(400, (string) $this->t('Both language and format query parameters are required.'));
    }

    if ($uri === '') {
      throw new NotFoundHttpException((string) $this->t('A uri query parameter is required.'));
    }

    $is_absolute_uri = $this->isAbsoluteUri($uri);

    if (!$is_absolute_uri && $base_url === '') {
      throw new HttpException(500, (string) $this->t('Proxy base URL is not configured.'));
    }

    if ($is_absolute_uri) {
      $scheme = strtolower((string) parse_url($uri, PHP_URL_SCHEME));
      if (!in_array($scheme, ['http', 'https'], TRUE)) {
        throw new HttpException(400, (string) $this->t('Only HTTP and HTTPS URIs are allowed.'));
      }
    }
    elseif (strncmp($uri, '//', 2) === 0) {
      throw new HttpException(400, (string) $this->t('Protocol-relative URIs are not supported.'));
    }

    $filename = $this->extractFilename($uri);

    $forward_query = $request->query->all();
    unset($forward_query['uri']);

    $remote_url = $this->buildRemoteUrl($base_url, $uri, $forward_query);
    $remote_url = $base_url . '?uri=' . $uri . '&language=' . $language . '&format=' . $format;
    throw new HttpException(502, (string) $this->t('00 RCADS proxy requesting %url', ['%url' => $remote_url]));
    # create a string with the concatenation of base_url, uri and forward_query

    $headers = [
      'Accept' => '*/*',
    ];

    $auth_header = trim((string) $config->get('auth_header'));
    if ($auth_header !== '') {
      $headers['Authorization'] = $auth_header;
    }

    try {
      $remote_response = $this->httpClient->request('GET', $remote_url, [
        'stream' => true,
        'http_errors' => false,
        'headers' => $headers,
      ]);
    }
    catch (RequestException $exception) {
      $this->logger->error('Request to %url failed: @message', [
        '%url' => $remote_url,
        '@message' => $exception->getMessage(),
      ]);
      throw new HttpException(502, (string) $this->t('Unable to reach the remote service.'));
    }
    catch (GuzzleException $exception) {
      $this->logger->error('Unexpected error while requesting %url: @message', [
        '%url' => $remote_url,
        '@message' => $exception->getMessage(),
      ]);
      throw new HttpException(502, (string) $this->t('Unable to reach the remote service.'));
    }

    $status = $remote_response->getStatusCode();
    if ($status >= 400) {
      $this->logger->warning('Remote service returned @status for %url.', [
        '@status' => $status,
        '%url' => $remote_url,
      ]);

      if ($status === 404) {
        throw new NotFoundHttpException((string) $this->t('The requested file was not found.'));
      }

      throw new HttpException($status, (string) $this->t('The remote service reported an error.'));
    }

    $stream = $remote_response->getBody();
    $content_type = $remote_response->getHeaderLine('Content-Type') ?: 'application/octet-stream';
    $content_length = $remote_response->getHeaderLine('Content-Length');
    $disposition = HeaderUtils::makeDisposition(HeaderUtils::DISPOSITION_ATTACHMENT, $filename);

    $response_headers = [
      'Content-Type' => $content_type,
      'Content-Disposition' => $disposition,
      'Cache-Control' => 'no-store, private',
      'Pragma' => 'no-cache',
      'X-Content-Type-Options' => 'nosniff',
    ];

    if ($content_length !== '') {
      $response_headers['Content-Length'] = $content_length;
    }

    return new StreamedResponse(static function () use ($stream) {
      while (!$stream->eof()) {
        echo $stream->read(8192);
        flush();
      }
      $stream->close();
    }, $status, $response_headers);
  }

  /**
   * Builds a remote URL from the configured base URL and requested URI.
   */
  protected function buildRemoteUrl(string $base_url, string $uri, array $query): string {
    $parsed = parse_url($uri);
    if ($parsed === FALSE) {
      throw new NotFoundHttpException((string) $this->t('The requested file URI is invalid.'));
    }

    $is_absolute = isset($parsed['scheme']) && isset($parsed['host']);

    if ($is_absolute) {
      $url = $this->rebuildAbsoluteUri($parsed);
    }
    else {
      $relative_path = isset($parsed['path']) ? ltrim($parsed['path'], '/') : '';

      if ($relative_path === '') {
        throw new NotFoundHttpException((string) $this->t('The requested file URI is invalid.'));
      }

      $base_url = rtrim($base_url, '/');
      $url = $base_url . '/' . $relative_path;
    }

    $path_query = [];
    if (!empty($parsed['query'])) {
      parse_str($parsed['query'], $path_query);
    }

    if ($query) {
      $path_query = array_merge($path_query, $query);
    }

    if ($path_query) {
      $url .= '?' . http_build_query($path_query, '', '&', PHP_QUERY_RFC3986);
    }

    return $url;
  }

  /**
   * Determines if a URI includes a scheme and host.
   */
  protected function isAbsoluteUri(string $uri): bool {
    $parts = parse_url($uri);

    return $parts !== FALSE && !empty($parts['scheme']) && !empty($parts['host']);
  }

  /**
   * Rebuild an absolute URI string from parsed components.
   */
  protected function rebuildAbsoluteUri(array $parts): string {
    $scheme = isset($parts['scheme']) ? $parts['scheme'] . '://' : '';

    $authority = '';
    if (isset($parts['user'])) {
      $authority .= $parts['user'];
      if (isset($parts['pass'])) {
        $authority .= ':' . $parts['pass'];
      }
      $authority .= '@';
    }

    $authority .= $parts['host'] ?? '';
    if (isset($parts['port'])) {
      $authority .= ':' . $parts['port'];
    }

    $path = $parts['path'] ?? '';

    return $scheme . $authority . $path;
  }

  /**
   * Determines the filename to use for the download.
   */
  protected function extractFilename(string $uri): string {
    $parsed = parse_url($uri, PHP_URL_PATH) ?: 'download';
    $basename = basename((string) $parsed);

    return $basename !== '' ? $basename : 'download';
  }

}
