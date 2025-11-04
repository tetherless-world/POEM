# RCADS Proxy

This module provides an endpoint that proxies file downloads from a remote web service without persisting files on the Drupal server. Configure the base URL and optional authorization header at `/admin/config/services/rcads-proxy`.

Usage example:

```
/rcads-proxy/download?language=en&format=pdf&uri=https://remote.example.com/reports/latest.pdf
```

The request streams the remote file back to the user directly while passing the
language and format parameters to the upstream service. Relative URIs are still
supported and will be resolved against the configured base URL.
