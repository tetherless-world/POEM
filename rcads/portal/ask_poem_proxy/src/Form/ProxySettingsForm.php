<?php

namespace Drupal\ask_poem_proxy\Form;

use Drupal\Core\Form\ConfigFormBase;
use Drupal\Core\Form\FormStateInterface;

/**
 * Configure settings for the ASK POEM proxy module.
 */
class ProxySettingsForm extends ConfigFormBase {

  /**
   * Default proxy base path.
   */
  private const DEFAULT_PROXY_BASE_PATH = '/ask-poem';

  /**
   * {@inheritdoc}
   */
  public function getFormId(): string {
    return 'ask_poem_proxy_settings_form';
  }

  /**
   * {@inheritdoc}
   */
  protected function getEditableConfigNames(): array {
    return ['ask_poem_proxy.settings'];
  }

  /**
   * {@inheritdoc}
   */
  public function buildForm(array $form, FormStateInterface $form_state): array {
    $config = $this->config('ask_poem_proxy.settings');

    $form['proxy_base_path'] = [
      '#type' => 'textfield',
      '#title' => $this->t('Proxy base path'),
      '#description' => $this->t('Drupal path prefix used by the proxy endpoint. Example: /ask-poem'),
      '#default_value' => $config->get('proxy_base_path') ?: self::DEFAULT_PROXY_BASE_PATH,
      '#required' => TRUE,
    ];

    $form['upstream_base_url'] = [
      '#type' => 'url',
      '#title' => $this->t('Upstream base URL'),
      '#description' => $this->t('Base URL of the external website to proxy, for example https://external.example.org or https://external.example.org/subpath.'),
      '#default_value' => $config->get('upstream_base_url'),
      '#required' => TRUE,
    ];

    $form['auth_header'] = [
      '#type' => 'textfield',
      '#title' => $this->t('Authorization header'),
      '#description' => $this->t('Optional Authorization header value to send with each proxied request (for example, "Bearer XYZ"). Leave empty if not needed.'),
      '#default_value' => $config->get('auth_header'),
    ];

    return parent::buildForm($form, $form_state);
  }

  /**
   * {@inheritdoc}
   */
  public function validateForm(array &$form, FormStateInterface $form_state): void {
    parent::validateForm($form, $form_state);

    $proxy_base_path = $this->normalizeProxyBasePath((string) $form_state->getValue('proxy_base_path'));

    if (!preg_match('/^\/[a-z0-9\/_-]+$/i', $proxy_base_path)) {
      $form_state->setErrorByName('proxy_base_path', (string) $this->t('Proxy base path may contain only letters, numbers, dashes, underscores, and slashes.'));
      return;
    }

    if ($proxy_base_path === '/query') {
      $form_state->setErrorByName('proxy_base_path', (string) $this->t('The path /query is reserved by the compatibility route.'));
      return;
    }

    $form_state->setValue('proxy_base_path', $proxy_base_path);
  }

  /**
   * {@inheritdoc}
   */
  public function submitForm(array &$form, FormStateInterface $form_state): void {
    parent::submitForm($form, $form_state);

    $this->config('ask_poem_proxy.settings')
      ->set('proxy_base_path', $form_state->getValue('proxy_base_path'))
      ->set('upstream_base_url', $form_state->getValue('upstream_base_url'))
      ->set('auth_header', $form_state->getValue('auth_header'))
      ->save();

    // Route paths are config-driven via a route subscriber, so rebuild router.
    \Drupal::service('router.builder')->rebuild();
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

    return $path === '' ? self::DEFAULT_PROXY_BASE_PATH : $path;
  }

}
