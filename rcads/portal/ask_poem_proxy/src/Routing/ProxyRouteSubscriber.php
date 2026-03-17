<?php

namespace Drupal\ask_poem_proxy\Routing;

use Drupal\Core\Config\ConfigFactoryInterface;
use Drupal\Core\Routing\RouteSubscriberBase;
use Symfony\Component\Routing\RouteCollection;

/**
 * Alters ASK POEM proxy route paths from module configuration.
 */
class ProxyRouteSubscriber extends RouteSubscriberBase {

  /**
   * Default proxy base path.
   */
  private const DEFAULT_PROXY_BASE_PATH = '/ask-poem';

  /**
   * Proxy resource suffix.
   */
  private const RESOURCE_SUFFIX = '-resource';

  /**
   * Configuration factory.
   */
  protected ConfigFactoryInterface $configFactory;

  /**
   * ProxyRouteSubscriber constructor.
   */
  public function __construct(ConfigFactoryInterface $config_factory) {
    $this->configFactory = $config_factory;
  }

  /**
   * {@inheritdoc}
   */
  protected function alterRoutes(RouteCollection $collection): void {
    $config = $this->configFactory->get('ask_poem_proxy.settings');
    $proxy_base_path = $this->normalizeProxyBasePath((string) $config->get('proxy_base_path'));
    $resource_path = $this->buildResourcePath($proxy_base_path);

    if ($route = $collection->get('ask_poem_proxy.proxy')) {
      $route->setPath($proxy_base_path . '/{proxy_path}');
    }

    if ($route = $collection->get('ask_poem_proxy.proxy_resource')) {
      $route->setPath($resource_path);
    }
  }

  /**
   * Normalizes the configured base path.
   */
  protected function normalizeProxyBasePath(string $path): string {
    $path = trim($path);
    if ($path === '') {
      return self::DEFAULT_PROXY_BASE_PATH;
    }

    if (!str_starts_with($path, '/')) {
      $path = '/' . $path;
    }

    $path = preg_replace('#/+#', '/', $path) ?? self::DEFAULT_PROXY_BASE_PATH;
    $path = rtrim($path, '/');

    if ($path === '' || !preg_match('/^\/[a-z0-9\/_-]+$/i', $path) || $path === '/query') {
      return self::DEFAULT_PROXY_BASE_PATH;
    }

    return $path;
  }

  /**
   * Builds the resource route path from the proxy base path.
   */
  protected function buildResourcePath(string $proxy_base_path): string {
    return rtrim($proxy_base_path, '/') . self::RESOURCE_SUFFIX;
  }

}
