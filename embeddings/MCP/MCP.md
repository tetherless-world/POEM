# POEM Search — MCP Server

Quick reference: see `../manuals/DOCS_SUMMARY.md` for a one‑page quick-start and common commands.

> **TL;DR** — Two tools over MCP: `search` (semantic search, needs the RPI
> VPN/embedding endpoint) and `get_statements` (graph lookup, fully offline).
> Sanity-check both with no protocol involved:
> ```powershell
> embeddings\MCP\.venv-mcp\Scripts\python.exe embeddings\MCP\try_search.py RCADS-25-CG-EN
> ```
> Then start the real server (stdio, for LM Studio / `agent/chat_agent.py`):
> ```powershell
> embeddings\MCP\.venv-mcp\Scripts\python.exe embeddings\MCP\mcp_server.py
> ```
> Need it reachable over a network instead (containers, remote clients)? See
> [Container deployment](#container-deployment) below.

This folder exposes the POEM semantic search (built in [`../Pipeline/`](../Pipeline/PIPELINE_DOCS.md)) as a **Model Context Protocol (MCP)** tool, so that *any* LLM — fine-tuned or not — running in an MCP-capable client can call it. It is the backend for a future POEM chatbot.

The server does not reimplement search: it imports the shared **`poem_core`** package (the embedding client, similarity metrics, the corpus loader, result dedup, the RDF graph loader, and the vector store) and serves it. It exposes **two tools**, modeled on the [Wikidata MCP](https://www.wikidata.org/wiki/Wikidata:MCP#Tools) server:

- **`search`** — semantic search that returns matching entities as `id` (skos:notation) + `label`.
- **`get_statements`** — a direct POEM-graph lookup that returns an entity's immediate relationships, given an `id` from a prior `search`.

This is the two-call flow a chatbot uses: find entities by topic, then read one entity's relationships from the knowledge graph (and chain further via the ids those relationships expose).

**Feature — bring your own embedding LLM:** the embedding model behind the `search` tool is configurable directly in `mcp_server.py`. Point it at your own OpenAI-compatible endpoint, or leave it on the default POEM server (`idea-llm-01:11435`). See [Configuration](#configuration).

---

## What's in this folder

| File | Purpose |
|------|---------|
| `mcp_server.py` | The MCP server. Loads the embedding corpus **and** the POEM RDF graph once at startup, and exposes the `search` and `get_statements` tools over stdio. |
| `graph_lookup.py` | RDF access for the tools: resolves an entity's `(id, label)` and returns its immediate relationships. Uses `poem_core.graph.load_graph()` / `poem_core.entities.readable_local_name()` so it queries the *same* graph the embeddings were built from. |
| `conftest.py` | Pins `VECTOR_BACKEND=numpy` for the test suite so it runs offline/deterministically (no Milvus server needed). |
| `try_search.py` | Quick standalone check — calls `search` / `get_statements` directly, no MCP protocol. |
| `test_mcp.py` | Offline pytest suite (graph + `get_statements` + monkeypatched `search`); one live test gated on `POEM_TEST_NETWORK=1`. |
| `test_mcp_intensive.py` | Intensive edge cases: nonexistent/malformed entity ids, `search` argument edge cases, `MCP_TRANSPORT` resolution, the `/health` route, and the startup failure guards. See [TESTING.md "Intensive edge-case suites"](../TESTING.md#intensive-edge-case-suites). |
| `requirements-mcp.txt` | Dependencies for the server (`fastmcp`, `numpy`, `openai`, `rdflib`, `pymilvus`). |
| `setup_lmstudio.ps1` | One-command LM Studio setup for **any** Windows device: builds a device-local venv, installs deps, registers `poem-search` in `%USERPROFILE%\.lmstudio\mcp.json`, and smoke-tests offline. See [LM_STUDIO.md §0](LM_STUDIO.md). |
| `.venv-mcp/` | Dedicated Python 3.12 virtual environment (git-ignored). FastMCP requires Python ≥ 3.10, while the Pipeline runs on 3.8 — hence a separate env. |
| `Dockerfile` | Containerizes the server for network/remote deployment. See [Container deployment](#container-deployment). |
| `http_smoke_test.py` | Offline-safe check of the HTTP transport (`get_statements` over a running container/server) — no VPN needed. |
| `../docker/mcp-compose.yml` | Compose service wiring the containerized server to the local Milvus stack. Merge with `milvus-compose.yml` via `-f` (see [Container deployment](#container-deployment)). |

---

## Background

**MCP** is an open, JSON-RPC–based standard that lets an LLM application (the *client/host* — an agent framework, a custom chatbot, etc.) call external capabilities (a *server*) through a uniform interface. We publish search once as an MCP server; any MCP-aware client can then discover and invoke it. Communication uses a **transport** — here **stdio** (the client launches the server as a subprocess and talks over stdin/stdout).

**FastMCP** is the high-level Python framework for building MCP servers: you write a normal function, decorate it with `@mcp.tool`, and it auto-generates the tool schema from your type hints and uses the docstring as the description the LLM sees.

---

## Prerequisites

- **Python ≥ 3.10** (the bundled `.venv-mcp` uses 3.12).
- The Pipeline embeddings must already exist in `../Pipeline/instruments/`, `../Pipeline/scales/`, `../Pipeline/collections/` (see [PIPELINE_DOCS.md](../Pipeline/PIPELINE_DOCS.md), Step 3). The server loads these at startup.
- The **POEM RDF graph** must be present at the repo root — the same TTLs the Pipeline reads (`individualsFull.ttl`, `individuals/`, `ontology/`, `POEM.rdf`). `get_statements` and the `id`/`label` resolution in `search` query this graph (loaded once at startup via `graph_lookup`).
- **Network:** only the `search` tool needs the network — it embeds the query via the RPI embedding server (`idea-llm-01.idea.rpi.edu:11435`), reachable only on the RPI network/VPN. Off-network, the server still starts and loads the corpus + graph, `get_statements` works fully (pure graph lookup), but a `search` call times out.
- **Docker (only if `VECTOR_BACKEND=milvus`, the default):** you don't need to start anything by hand — on startup, `get_store()` calls `docker_preflight.ensure_milvus_ready()`, which launches Docker Desktop and brings up `milvus-compose.yml` for you if they aren't already running (adds a delay of up to a few minutes the first time; see [`../docker/MILVUS.md` §10a](../docker/MILVUS.md#10a-self-healing--surviving-a-reboot)). To skip this entirely (e.g. in a container, or to force numpy), set `VECTOR_BACKEND=numpy` or `MILVUS_SKIP_ENSURE=1`.

### One-time environment setup

The `.venv-mcp` env is already built. To recreate it from scratch:

```powershell
py -3.12 -m venv embeddings\MCP\.venv-mcp
embeddings\MCP\.venv-mcp\Scripts\python.exe -m pip install -r embeddings\MCP\requirements-mcp.txt
# Recommended: also install the shared core package (editable) so `import poem_core` resolves:
embeddings\MCP\.venv-mcp\Scripts\python.exe -m pip install -e embeddings
```

> If pip fails with `CERTIFICATE_VERIFY_FAILED` on this network, add:
> `--trusted-host pypi.org --trusted-host files.pythonhosted.org --trusted-host pypi.python.org`

Throughout this doc, `PYTHON` means the venv interpreter:
`embeddings\MCP\.venv-mcp\Scripts\python.exe`

---

## The tools

### `search`

```
search(query: str,
       top_k: int = 5,
       section: str | None = None,           # 'instruments' | 'scales' | 'collections'
       metric: str = "Cosine Similarity")    # also: 'Dot Product', 'Euclidean (L2)', 'Manhattan (L1)'
    -> list[dict]
```

Embeds `query`, ranks all stored paragraphs, and returns up to `top_k` entities, **deduplicated by entity** (so one instrument doesn't fill every slot). The return type is a `SearchResult` TypedDict, so FastMCP also emits an **`outputSchema`** and **structured content** for the tool (plus the serialized-JSON text block for back-compat). Each result:

| key | meaning |
|-----|---------|
| `id` | the entity's `skos:notation` (e.g. `"RCADS-25-CG-EN"`, `"SP"`). Pass this to `get_statements`. |
| `label` | human-readable `rdfs:label` (e.g. `"Social Phobia (9.1)"`). |
| `section` | the section the hit came from (commonly `instruments`/`scales`/`collections`; others may exist — sections are auto-detected, see below). |
| `score` | similarity score (float; higher = more similar). |
| `type` | the entity's readable `rdf:type` (e.g. `"Psychometric Questionnaire"`), or `null`. |
| `description` | short ontology description (`rdfs:comment`/`dc:description`/`skos:definition`, truncated), or `null`. |
| `aliases` | up to 3 `skos:altLabel` alternatives (may be `[]`). |
| `snippet` | preview of the matched paragraph text. |

`type`/`description`/`aliases` are **graph-internal enrichment** — pulled from the same POEM graph at no extra network cost — to give the LLM more to reason over without a follow-up `get_statements` call.

Invalid `metric`/`section`, or `top_k < 1`, raise a `ValueError` surfaced to the client.

```jsonc
// search("anxiety in children", top_k=2)
[
  {"id": "RCADS-25-CG-EN", "label": "RCADS-25-CG-EN", "section": "instruments",
   "score": 0.71, "type": "Psychometric Questionnaire",
   "description": null, "aliases": [], "snippet": "RCADS-25-CG-EN. Attributes include: ..."},
  {"id": "SP", "label": "Social Phobia (9.1)", "section": "scales",
   "score": 0.66, "type": "Questionnaire Scale",
   "description": null, "aliases": [], "snippet": "Social Phobia (9.1). Attributes include: ..."}
]
```

### `get_statements`

```
get_statements(entity_id: str,
               lang: str = "en")   # accepted for forward-compatibility; labels returned as stored
    -> list[dict]
```

Resolves `entity_id` (an `id` from a prior `search` — a `skos:notation`; an `fhir:code` or `rdfs:label` also works) to a graph node and returns its **immediate outgoing relationships**. Each item is `{property, value, value_id}`, where `value_id` is the object's own id when the object is itself a graph entity — so it can be fed straight back into `get_statements` to traverse the graph — and `null` for plain literal values. An id matching no entity raises `ValueError`.

```jsonc
// get_statements("RCADS-25-CG-EN")  -> (excerpt)
[
  {"property": "instance of",  "value": "psychometric questionnaire", "value_id": null},
  {"property": "has attribute","value": "Caregiver",                  "value_id": null},
  {"property": "has attribute","value": "Major Depressive Disorder (10.1)", "value_id": "MDD"},
  {"property": "has member",   "value": "My child feels sad or empty","value_id": null},
  {"property": "notation",     "value": "RCADS-25-CG-EN",             "value_id": null}
]
```

Resolution by section: instruments resolve by notation (notation == code == label); scales resolve their label to recover the notation (`"Social Phobia (9.1)"` → id `"SP"`); collections have no notation, so their `id` is the label (e.g. `"RCADS"`).

---

## How to run

> **See also:** [LM_STUDIO.md](LM_STUDIO.md) — run these tools from a local LLM (LM Studio) and watch the call parameters/JSON. · [../API/API.md](../API/API.md) — the same search as a plain HTTP/JSON REST API (Swagger UI at `/docs`).

### 1. Quick sanity check (no MCP)

Full two-call flow (`search` needs the RPI network; it then describes the top hit via `get_statements`):

```powershell
embeddings\MCP\.venv-mcp\Scripts\python.exe embeddings\MCP\try_search.py
```

Offline `get_statements` only — pass an id, no network needed:

```powershell
embeddings\MCP\.venv-mcp\Scripts\python.exe embeddings\MCP\try_search.py RCADS-25-CG-EN
```

Run the offline test suite the same way:

```powershell
embeddings\MCP\.venv-mcp\Scripts\python.exe -m pytest embeddings\MCP\test_mcp.py -v
```

### 2. Start the server (stdio)

```powershell
embeddings\MCP\.venv-mcp\Scripts\python.exe embeddings\MCP\mcp_server.py
```

### 3. Inspect interactively (dev UI)

Launches the **MCP Inspector** web UI (needs Node/`npx` — installed here). On FastMCP **3.x** the inspector is a `dev` **subcommand**:

```powershell
embeddings\MCP\.venv-mcp\Scripts\fastmcp.exe dev inspector embeddings\MCP\mcp_server.py
```

> On FastMCP **2.x** this was `fastmcp dev <file>` (no `inspector`). That older form errors on 3.x with *"Unknown command … Available commands: inspector, apps."* — add `inspector` as above.

Open the URL it prints — e.g. `http://localhost:6274/?MCP_PROXY_AUTH_TOKEN=…` — **including the token** (recent Inspector builds reject a bare `localhost:6274`). Then **Tools → `search` / `get_statements` → fill arguments → Run**. Override ports with `--ui-port` / `--server-port`.

**Dependency gotcha.** `fastmcp dev inspector` runs the server in an isolated `uv` environment, which may not see this project's deps (`numpy`, `rdflib`, `openai`, `pymilvus`, `poem_core`). If the terminal shows a `ModuleNotFoundError`, either inject them:

```powershell
embeddings\MCP\.venv-mcp\Scripts\fastmcp.exe dev inspector embeddings\MCP\mcp_server.py `
  --with-requirements embeddings\MCP\requirements-mcp.txt --with-editable embeddings
```

…or bypass fastmcp's environment entirely and point the Inspector at the ready-made venv (most reliable):

> **Windows path gotcha — use forward slashes.** The Inspector CLI/UI re-tokenizes the command and args with the npm `shell-quote` package, which applies **POSIX** escaping rules (`\P` → `P`) *inside the Inspector's own Node code* — this happens regardless of which shell you run `npx` from (PowerShell, cmd, or Git Bash all trigger it identically, since the shell itself isn't the one doing the mangling). Backslash-separated paths get their backslashes silently stripped: `embeddings\MCP\mcp_server.py` becomes `o:POEMembeddingsMCPmcp_server.py`, a legal-but-obscure Windows "drive-relative" path that Windows then resolves against drive O:'s current directory — producing a garbled, nonexistent path (you'll see an error like `can't open file 'O:\\POEM\\POEMembeddingsMCPmcp_server.py'`). The interpreter path suffers the same fate, so `spawn-rx`'s executable lookup falls back to whatever `python.exe` is first on `PATH` (often the wrong install). **Use forward slashes** for both paths to sidestep it entirely — Windows accepts `/` in paths everywhere that matters here, and `shell-quote` leaves them alone:

```powershell
npx @modelcontextprotocol/inspector <path-to-your-checkout>/embeddings/MCP/.venv-mcp/Scripts/python.exe <path-to-your-checkout>/embeddings/MCP/mcp_server.py
```

As with every run mode, `search` needs the RPI network (to embed the query); `get_statements` works offline.

### 4. Connect from a Python MCP client

```python
import asyncio
from fastmcp import Client

client = Client(r"embeddings\MCP\mcp_server.py")  # launched over stdio

async def main():
    async with client:
        print([t.name for t in await client.list_tools()])
        res = await client.call_tool("search", {
            "query": "caregiver report of depression",
            "top_k": 3,
            "section": "instruments",
        })
        for r in res.data:
            print(f"[{r['section']}] {r['id']}  ({r['label']})  {r['score']:+.4f}")

        # Follow up: read the top hit's relationships from the graph.
        top_id = res.data[0]["id"]
        stmts = await client.call_tool("get_statements", {"entity_id": top_id})
        for s in stmts.data:
            chain = f"  -> {s['value_id']}" if s["value_id"] else ""
            print(f"  {s['property']}: {s['value']}{chain}")

asyncio.run(main())
```

Run it with the venv python, from the repo root, so the same interpreter is
used to spawn the server and the relative path above resolves.

### 5. Register with an MCP client

> **LM Studio:** don't do this by hand — run [`setup_lmstudio.ps1`](./setup_lmstudio.ps1) once per device (see [LM_STUDIO.md §0](LM_STUDIO.md)). It builds a device-local venv (the shared `.venv-mcp` only works on the machine that created it) and writes the config entry with escape-proof forward-slash paths.

Most MCP clients accept a JSON config that lists servers to launch. Unlike the
commands above, this needs an **absolute** path — the client isn't necessarily
launched from your repo checkout. Add an entry like the following, substituting
`<path-to-your-checkout>` for your own repo's location (key/field names vary by
client), and restart the client:

```json
{
  "mcpServers": {
    "poem-search": {
      "command": "<path-to-your-checkout>\\embeddings\\MCP\\.venv-mcp\\Scripts\\python.exe",
      "args": ["<path-to-your-checkout>\\embeddings\\MCP\\mcp_server.py"]
    }
  }
}
```

---

## Configuration

### Choosing the embedding LLM (bring your own, or use the default)

The `search` tool turns each query into a vector using an embedding model. You can **attach your own LLM / embedding endpoint** or fall back to the default POEM server — configured **in code**, at the top of `mcp_server.py`:

```python
# --- Embedding LLM / backend configuration (in mcp_server.py) ---
EMBED_LLM_BASE_URL: str | None = None   # e.g. "http://localhost:1234/v1"
EMBED_LLM_MODEL:    str | None = None   # e.g. "nomic-embed-text"

DEFAULT_EMBED_BASE_URL = "http://idea-llm-01.idea.rpi.edu:11435/v1"
DEFAULT_EMBED_MODEL    = "qwen3-embedding"
```

- **Use your own:** set `EMBED_LLM_BASE_URL` (and `EMBED_LLM_MODEL` if the model name differs) to any **OpenAI-compatible** embeddings endpoint — a local server, vLLM, Ollama, etc. No API key is required (`api_key="not-needed"`).
- **Use the default:** leave both as `None` and the server uses `idea-llm-01.idea.rpi.edu:11435` with model `qwen3-embedding`.

On startup the server logs which backend it chose, e.g.:
`[mcp_server] Embedding backend: qwen3-embedding @ http://idea-llm-01.idea.rpi.edu:11435/v1`

> Precedence: the in-code value wins; if left `None`, an existing `EMBED_BASE_URL` / `EMBED_MODEL` environment variable is honored; otherwise the default is used. Whatever you pick must produce embeddings **compatible with the stored `.npy` vectors** — those were generated with `qwen3-embedding`, so a different model only makes sense if you also regenerate embeddings via the [Pipeline](../Pipeline/PIPELINE_DOCS.md).

### Other settings (environment variables, read by `../Pipeline/search_similarity.py`)

| Variable | Default | Purpose |
|----------|---------|---------|
| `EMBED_BASE_URL` | `http://idea-llm-01.idea.rpi.edu:11435/v1` | Embedding server endpoint (set in code via `EMBED_LLM_BASE_URL`). The RPI server is HTTP-only; set `https://…` for a TLS-capable endpoint. |
| `EMBED_MODEL` | `qwen3-embedding` | Embedding model (set in code via `EMBED_LLM_MODEL`) |
| `EMBEDDINGS_DIR` | the `Pipeline/` folder | Where `.npy` embeddings are loaded from |
| `VECTOR_BACKEND` | `milvus` | Vector store: `milvus` (external server, default) or `numpy` (in-process). |
| `MILVUS_URI` | `http://localhost:19530` | External Milvus server URL (any OS). Only used when `VECTOR_BACKEND=milvus`. |
| `MILVUS_TOKEN` | *(empty)* | Auth token for a remote/cloud Milvus server (e.g. Zilliz Cloud). |
| `MILVUS_COLLECTION` | `poem` | Base collection name (one FLAT collection per metric is derived from it). |
| `MCP_TRANSPORT` | `stdio` | `stdio` (default, unchanged local-subprocess behavior) or `http` (network transport — see [Container deployment](#container-deployment)). |
| `MCP_HOST` | `127.0.0.1` | Bind address, `http` transport only. The Dockerfile sets `0.0.0.0`. |
| `MCP_PORT` | `8100` | Bind port, `http` transport only. Deliberately not `8000` — that's `API/api_server.py`'s port, and both surfaces may run at once. |
| `MILVUS_SKIP_ENSURE` | *(unset)* | Set to `1` to skip the automatic Docker/Milvus self-heal (`docker_preflight.ensure_milvus_ready()`) entirely — the container image sets this, since it never manages a host Docker daemon. |

> **Sections are auto-detected.** The server loads whatever section subfolders exist under `EMBEDDINGS_DIR` (any folder with a `texts.npy`). Adding a new section — e.g. `items` — is just a matter of generating its embeddings; no code change here.

> **Milvus is the default backend** and runs as a separate process (`docker compose -f embeddings/docker/milvus-compose.yml up -d`). It uses **FLAT** indexes, so results are exact (identical to numpy); Manhattan/L1 is served by an internal numpy fallback. If the server is unreachable or `pymilvus` is not installed, the store **falls back to numpy automatically** (logged to stderr) — so the server still works offline. Install `pymilvus` (see `requirements-mcp.txt`) to enable the Milvus path.

---

## Container deployment

Everything above assumes a client (LM Studio, `agent/chat_agent.py`, `try_search.py`)
launches `mcp_server.py` itself and talks over **stdio**. To run the server as a
standalone, network-reachable service instead — e.g. in Docker, for a remote or
shared deployment — switch it to the **HTTP transport** and containerize it.

### Why the RDF graph isn't baked into the image

The POEM RDF graph (`individualsFull.ttl`, `ontology/`, `POEM.rdf`,
`poem-demo/dist/data/`) lives **one level above `embeddings/`** in the repo, and
`poem_core/graph.py` additionally globs the *entire* repo tree for more TTLs by
filename keyword. Docker can't `COPY` across the build-context boundary, and
that open-ended glob makes a curated file list unmaintainable — so the image
ships the **code and the corpus** (`.npy` vectors, already inside `embeddings/`)
but expects the **graph** to be supplied via a **read-only bind mount of the
repo root** at runtime, with `POEM_PROJECT_ROOT` pointed at the mount (an
override `poem_core/config.py` already supports). This mirrors how
`milvus-compose.yml` already bind-mounts Milvus's own data rather than baking
it into an image — and it means graph updates take effect on container
restart, with no rebuild. Trade-off: the image alone isn't fully self-contained
for `get_statements` — a deployment host needs a mounted repo checkout.

### Build and run standalone

```powershell
# Build context is embeddings/, not this MCP/ folder or the repo root:
docker build -f embeddings/MCP/Dockerfile -t poem-mcp:latest embeddings

# Run with the numpy backend (no Milvus needed) and the repo root mounted
# read-only for the graph:
docker run --rm -d --name poem-mcp `
  -e VECTOR_BACKEND=numpy `
  -e POEM_PROJECT_ROOT=/data/repo `
  -v <repo-root>:/data/repo:ro `
  -p 8100:8100 `
  poem-mcp:latest

# Confirm it's healthy, then exercise it (offline-safe: get_statements only):
docker inspect -f "{{.State.Health.Status}}" poem-mcp
embeddings\MCP\.venv-mcp\Scripts\python.exe embeddings\MCP\http_smoke_test.py
```

The image defaults to `MCP_TRANSPORT=http`, `MCP_HOST=0.0.0.0`, `MCP_PORT=8100`,
and `MILVUS_SKIP_ENSURE=1` (a container never manages a host Docker daemon —
that's a bare-metal/dev-machine feature; an unreachable Milvus just falls back
to numpy, same as always). The `/health` route (used by the image's
`HEALTHCHECK`) reports paragraph count, graph triple count, and the active
vector backend — a 200 only confirms the process finished loading at startup,
not that the embedding endpoint `search` needs is reachable.

### Run alongside Milvus (compose)

`embeddings/docker/mcp-compose.yml` defines an `mcp` service meant to be merged
with `milvus-compose.yml` (it alone won't validate — its `depends_on` only
resolves once both files are merged):

```powershell
docker compose -f embeddings/docker/milvus-compose.yml `
                -f embeddings/docker/mcp-compose.yml up -d --build
```

This wires `MILVUS_URI=http://milvus-standalone:19530` over the compose
network Milvus's own file already names `milvus`, waits for Milvus's health
check before starting, and bind-mounts the repo root for the graph. Off the
RPI network? Uncomment and set `EMBED_BASE_URL`/`EMBED_MODEL` in the compose
file to point at any other reachable OpenAI-compatible embedding endpoint.

Tear down: `docker compose -f embeddings/docker/milvus-compose.yml -f embeddings/docker/mcp-compose.yml down`.

### Two gotchas

- If you export `MCP_TRANSPORT=http` in a shell to test the container locally,
  **unset it** before running `agent/chat_agent.py` or LM Studio in that *same*
  shell — it'll leak into the spawned subprocess and break their stdio flow.
- **Milvus over a compose hostname can hang on first connect.** Reproduced and
  fixed during development: `pymilvus`'s gRPC channel construction against a
  Docker Compose service hostname (`MILVUS_URI=http://milvus-standalone:19530`)
  can hang indefinitely, even though plain TCP/HTTP to that same hostname
  succeeds instantly and connecting by the container's raw IP works fine — a
  gRPC happy-eyeballs resolver quirk against Docker's embedded DNS, not a
  wiring problem in the compose file. The image sets
  `GRPC_EXPERIMENTAL_ENABLE_HAPPY_EYEBALLS=false` by default to work around it
  (harmless if `MILVUS_URI` targets a real DNS name instead, e.g. Zilliz
  Cloud). If a from-scratch build ever hangs at "Loaded graph: N triples" with
  the container stuck at `unhealthy`, this env var is the first thing to check.

### What's still out of scope

No auth or TLS termination on the HTTP transport (see the closing "Out of
scope" note above) — put a reverse proxy in front for anything beyond a
trusted local network. That, plus observability, is Phase 5 territory per
[`../ROADMAP.md`](../ROADMAP.md).

---

## Notes & gotchas

- **stdio keeps stdout clean.** Under stdio, stdout is reserved for the JSON-RPC protocol. Both the corpus loader and the graph loader print progress, so `mcp_server.py` runs them under `redirect_stdout(sys.stderr)`. Don't add bare `print()` calls that go to stdout.
- **Imports resolve via the shared core package.** `mcp_server.py` and `graph_lookup.py` add the `embeddings/` root to `sys.path` and import from `poem_core` (config, metrics, corpus, embedding client, dedup, vector store, graph) — no more reaching into the sibling `Pipeline/` folder. The blessed setup is `pip install -e embeddings`, which makes `poem_core` importable without the path shim. `graph_lookup` still sets `POEM_PROJECT_ROOT` to the repo root (belt-and-suspenders) so `poem_core.graph.load_graph()` finds the repo's TTLs.
- **Corpus + graph load once.** Both load at server startup; each `search` call is then one embedding network call + a numpy matmul, and each `get_statements` call is an in-memory graph lookup (no network).
- **Graph is the merged repo snapshots.** `load_graph()` merges several overlapping TTL copies (`individuals/`, `browser/…`, `poem-demo/…`) — the same union the embeddings were built from. `get_statements` cleans the merge artifacts: it folds equivalent predicates to one readable name, renders member items via their stem text, drops `owl:NamedIndividual`, and suppresses unresolved bare-id references.
- **Out of scope (future):** auth on the HTTP transport, multilingual labels (the `lang` arg is a placeholder), and extra tools (e.g. `list_sections`, `get_full_text`). The chatbot/LLM client itself now ships as [`../agent/chat_agent.py`](../agent/AGENT.md), and a network transport is covered in [Container deployment](#container-deployment) below — both were previously listed here as future work.
