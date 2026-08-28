# POEM MCP — Work Log

Quick reference: see `../manuals/DOCS_SUMMARY.md` for a single-page quick-start and common commands.

A short log of the MCP work, from first learning the protocol to a two-tool
server (`search` + `get_statements`) backed by the embedding pipeline and the
POEM RDF graph. Dates are 2026.

---

## Thu May 28 – Mon Jun 1 — Learning MCP

- Read the **Model Context Protocol** spec: the host/client/server split, the
  JSON-RPC message model, and **transports** (settled on **stdio** — the client
  launches the server as a subprocess and talks over stdin/stdout).
- Picked **FastMCP** as the framework: decorate a plain function with
  `@mcp.tool`; the schema is generated from type hints and the docstring becomes
  the description the LLM sees.
- Studied the **Wikidata MCP** server as the design template
  (`wikidata.org/wiki/Wikidata:MCP`, `wd-mcp.wmcloud.org/docs`): a `search`
  returning entity **id + label**, and a **`get_statements`** returning an
  entity's property/value relationships. Adopted this two-call shape as the
  target for POEM.
- Confirmed the data we'd expose: the existing embedding search in
  `../Pipeline/` (instruments / scales / collections) and the POEM RDF graph
  (`skos:notation` ids, `rdfs:label` labels).

## Tue Jun 2 — MCP server built

- `mcp_server.py`: wraps the existing pipeline (no search reimplemented) and
  serves a `search` tool over stdio. Imports `search_similarity` /
  `evaluate_search` from the sibling `../Pipeline/` via `sys.path`.
- Corpus loaded **once** at startup; loader output redirected to **stderr** so
  stdout stays clean for the JSON-RPC stream.
- "Bring your own embedding LLM" config block at the top of the server
  (`EMBED_LLM_BASE_URL` / `EMBED_LLM_MODEL`), defaulting to the POEM server.
- Dedicated `.venv-mcp` (Python 3.12, FastMCP needs ≥ 3.10) + `requirements-mcp.txt`;
  `try_search.py` for a no-protocol sanity check.

## Wed–Thu Jun 3–4 — Hardening & docs

- Wired dedup-by-entity (`get_unique_top_results`) into the tool and added
  argument validation (metric / section / `top_k`) surfaced as `ValueError`.
- Wrote `MCP.md`: background, prerequisites, the tool reference, how to run
  (sanity check, stdio, `fastmcp dev inspector`, Python client, client registration), and
  the configuration / gotchas.

## Fri Jun 5 — Tests

- `test_mcp.py` (pytest): `search` result shape + argument-validation tests,
  using a monkeypatched embedding call so the suite runs **offline**; live
  embedding-server test gated behind `POEM_TEST_NETWORK=1`. Graph-tool tests
  added once that tool landed (next day).

## Sat Jun 6 (today) — Graph-backed results

- `search` now returns the **slim** shape `{id, label, section, score}` — `id`
  is the `skos:notation` a follow-up call can use.
- New **`get_statements(entity_id)`** tool + `graph_lookup.py`: a direct query
  against the POEM graph returning an entity's **immediate relationships** as
  `{property, value, value_id}` (chainable `value_id` for entity objects),
  mirroring the Wikidata MCP. Reuses the Pipeline's `load_graph()` so it's the
  same graph the embeddings were built from.
- Resolution cascade (notation → code → label) covers all three sections;
  output is cleaned (readable predicates, item stem text, noise dropped).
- `rdflib` added to requirements; `try_search.py` updated to the two-call flow;
  `test_mcp.py` extended with offline graph / `get_statements` tests.
  **Suite: 13 passed, 1 skipped (live).**

---

### Status

`search` + `get_statements` work end-to-end. `get_statements` is fully offline;
`search` needs the RPI embedding server (VPN). Out of scope for now: the chatbot
client itself, HTTP transport/auth, multilingual labels, and further tools
(`list_sections`, `get_full_text`).
