# ASK POEM Proxy

This module provides a reverse proxy endpoint that forwards requests to a
configured external website.

Configure settings at:

`/admin/config/services/ask-poem-proxy`

The proxy base path is configurable in module settings. Default: `/ask-poem`.

Routes:

- `/{proxy-base-path}` (default `/ask-poem`)
- `/{proxy-base-path}/{path}` (default `/ask-poem/{path}`)
- `/{proxy-base-path}-resource` (default `/ask-poem-resource`)
- `/query` (compatibility route for form submissions targeting root `/query`)

Examples:

- `/ask-poem`
- `/ask-poem/docs/api?lang=en`

The module forwards the incoming HTTP method, request path, query string, and
body to the configured upstream URL and streams the upstream response back to
the client.

For static assets that may be intercepted by web-server extension handling
(`.js`, `.css`, images, fonts), the module rewrites links to
`/{proxy-base-path}-resource?_ap_path=/original/path` so those requests are still
processed by Drupal and proxied upstream.
