<?php

namespace Drupal\rcads_proxy\Form;

use Drupal\Core\Form\ConfigFormBase;
use Drupal\Core\Form\FormStateInterface;

/**
 * Configure settings for the RCADS proxy module.
 */
class ProxySettingsForm extends ConfigFormBase {

  /**
   * {@inheritdoc}
   */
  public function getFormId(): string {
    return 'rcads_proxy_settings_form';
  }

  /**
   * {@inheritdoc}
   */
  protected function getEditableConfigNames(): array {
    return ['rcads_proxy.settings'];
  }

  /**
   * {@inheritdoc}
   */
  public function buildForm(array $form, FormStateInterface $form_state): array {
    $config = $this->config('rcads_proxy.settings');

    $form['base_url'] = [
      '#type' => 'url',
      '#title' => $this->t('Base URL'),
      '#description' => $this->t('The base URL of the remote service used for proxy downloads. Include the trailing slash if the service requires one.'),
      '#default_value' => $config->get('base_url'),
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
  public function submitForm(array &$form, FormStateInterface $form_state): void {
    parent::submitForm($form, $form_state);

    $this->config('rcads_proxy.settings')
      ->set('base_url', $form_state->getValue('base_url'))
      ->set('auth_header', $form_state->getValue('auth_header'))
      ->save();
  }

}
