# Testing the POEM Embeddings Stack — active runbook

Quick reference: see `embeddings/manuals/DOCS_SUMMARY.md` for a compact quick-start and troubleshooting cheatsheet.

Concrete commands to **actively test every layer**: automated suites, the CLI, the
vector backend (numpy *and* local Milvus), the MCP server, and the REST API.
Windows/PowerShell shown; adapt paths as needed.

## Environments & knobs

| Interpreter | Python | Has | Use for |
|---|---|---|---|
| `tutorial_env` (`python`) | 3.8 | numpy, pymilvus 2.4 | CLI, Milvus checks |
| `MCP/.venv-mcp` | 3.12 | fastmcp, fastapi, uvicorn, pymilvus 3.0, rdflib, openai | MCP server, REST API, pytest |

Key env vars (all optional; sensible defaults):

```powershell
$env:VECTOR_BACKEND = "numpy"   # or "milvus"
$env:EMBED_BASE_URL = "http://idea-llm-01.idea.rpi.edu:11435/v1"   # needs RPI VPN
$env:EMBED_MODEL    = "qwen3-embedding"                            # must stay 4096-dim
# Local Milvus:
$env:MILVUS_URI     = "http://localhost:19530"
$env:MILVUS_TOKEN   = ""
```

---

## 0. Embedding endpoint reachable? (needs RPI VPN)

```powershell
python embeddings/Pipeline/sample_embeddings.py
# -> "Text 0 embedding length: 4096" for the first few templates
```
`httpx.ConnectTimeout` → you're off the RPI network; the tools that embed a
**query** (`/search`, CLI search, MCP `search`) won't work until you're on VPN or
point `EMBED_*` at a local `qwen3-embedding`.

## 1. Automated tests (offline, numpy — no network)

```powershell
embeddings\MCP\.venv-mcp\Scripts\python.exe -m pytest `
  embeddings\MCP\test_mcp.py embeddings\Pipeline\test_search_similarity.py -q
# expect: all passed (network-only cases are skipped)
```
The suites pin `VECTOR_BACKEND=numpy` via `conftest.py`, so they need no server.

### Intensive edge-case suites

Complementing the suite above with deliberately adversarial cases — Docker
not running, nonexistent/malformed entity ids, argument edge cases, and the
container-deployment failure paths:

| File | Covers |
|---|---|
| `poem_core/test_docker_preflight.py` | Every branch of the "Docker isn't running" self-heal state machine (daemon down, stack down, never-becomes-healthy, every OS branch, `MILVUS_SKIP_ENSURE`) — fully mocked, never touches real Docker. |
| `poem_core/test_vector_store.py` | `get_store()`'s Milvus-unreachable → numpy fallback, and the local-vs-remote `MILVUS_URI` gating of the Docker self-heal. |
| `MCP/test_mcp_intensive.py` | A wide matrix of nonexistent/malformed ids into `get_statements`; `search` argument edge cases; `MCP_TRANSPORT` resolution; the `/health` route; the 0-triples and generic-exception startup guards. |
| `agent/test_lmstudio_preflight.py` | The chat agent's own "chat server isn't running" self-heal (mirrors `test_docker_preflight.py` for LM Studio). |
| `agent/test_chat_agent.py` | The tool-call loop (including the 6-round cap on a looping model), `repl()`'s error handling, both `call_tool` transports (including a real spawned-server nonexistent-id check), and `main()`'s dispatch. |

```powershell
# Fast subset (~30s) -- excludes the two tests that spawn a real mcp_server.py
# subprocess (each redoes the full corpus + graph load):
embeddings\MCP\.venv-mcp\Scripts\python.exe -m pytest `
  embeddings\poem_core embeddings\MCP embeddings\agent -m "not slow" -q

# Full suite, including the slow real-subprocess integration tests (~10-15 min
# depending on machine load -- run this before pushing, not on every save):
embeddings\MCP\.venv-mcp\Scripts\python.exe -m pytest `
  embeddings\poem_core embeddings\MCP embeddings\agent -q
```

## 2. CLI search (needs embedding endpoint)

```powershell
python embeddings/Pipeline/search_similarity.py "instruments that measure anxiety in children"
python embeddings/Pipeline/search_similarity.py --section instruments --top-k 5 "caregiver depression report"
```
Prints top-k per metric. `VECTOR_BACKEND=numpy` uses the in-process store; set it to
`milvus` to route through Milvus (results are identical — FLAT is exact).

## 3. Vector backend — numpy default & local Milvus

- **numpy** (zero setup): it's the default when no Milvus is reachable, and forced by
  `VECTOR_BACKEND=numpy`.
- **Local Milvus** — one command verifies connection, collections, exact parity vs
  numpy, and reuse:

```powershell
$env:VECTOR_BACKEND="milvus"
$env:MILVUS_URI="http://localhost:19530"
$env:MILVUS_TOKEN=""
python embeddings/docker/check_milvus.py
# -> backend selected: MilvusVectorStore ; parity match=True ×3 ;
#    count(*)=778 ×3 ; reuse OK ; RESULT: PASS
```
`FAIL: fell back to numpy` means the server was unreachable. **Before** reaching
for a raw `docker compose up -d`, make sure Docker itself is actually running —
`docker compose up -d` just errors out ("cannot connect to the Docker daemon")
if Docker Desktop isn't started yet. The one-liner that checks *and* self-heals
(launches Docker Desktop if needed, brings up the stack, waits for health) is:
```powershell
python embeddings/docker/ensure_docker.py
```
This is the same check `check_milvus.py`/`vector_store.get_store()` already run
automatically for any local `MILVUS_URI` — running it by hand just lets you see
the result before running something else. Only fall back to the manual command
below if you specifically want the stack up without touching anything else:
`docker compose -f embeddings/docker/milvus-compose.yml up -d`.

## 4. MCP server (the LLM tool surface)

```powershell
# a) No-protocol sanity check (search needs VPN; get_statements is offline)
embeddings\MCP\.venv-mcp\Scripts\python.exe embeddings\MCP\try_search.py
embeddings\MCP\.venv-mcp\Scripts\python.exe embeddings\MCP\try_search.py RCADS-25-CG-EN

# b) Interactive web inspector (call tools by hand, see params + JSON)
embeddings\MCP\.venv-mcp\Scripts\fastmcp.exe dev inspector embeddings\MCP\mcp_server.py
#   open the printed http://localhost:6274/?MCP_PROXY_AUTH_TOKEN=... URL

# c) From a local LLM that CALLS the tools -> see MCP/LM_STUDIO.md
```
On startup the server logs `Vector backend: NumpyVectorStore|MilvusVectorStore` —
that's how you confirm which backend it picked.

## 5. REST API (HTTP + Swagger)

```powershell
# Run it
embeddings\MCP\.venv-mcp\Scripts\python.exe embeddings\API\api_server.py
# Web UI: open http://localhost:8000/docs  ("Try it out" on each endpoint)

# Or by curl:
curl http://localhost:8000/health
curl http://localhost:8000/statements/RCADS-25-CG-EN            # offline
curl "http://localhost:8000/search?query=anxiety%20in%20children&top_k=3"   # needs embedding
```
Full reference + an offline `TestClient` smoke test are in [API/API.md](API/API.md).

---

## One-shot smoke sequence

```powershell
# 1. offline suites
embeddings\MCP\.venv-mcp\Scripts\python.exe -m pytest embeddings\MCP\test_mcp.py -q
# 2. graph lookup offline
embeddings\MCP\.venv-mcp\Scripts\python.exe embeddings\MCP\try_search.py RCADS-25-CG-EN
# 3. milvus backend (if configured)
python embeddings\docker\check_milvus.py
# 4. REST API health (in another shell after starting api_server.py)
curl http://localhost:8000/health
```

## End-to-end acceptance test (whole process)

Proves the *entire* chain in one ordered, gated sequence (stop at first failure):
a question → embedding → vector DB → dedup + graph enrichment → serving surfaces →
a grounded LLM answer. Three gates are automated by one harness; two are manual.

**1. Point the whole stack at the target once** (local Milvus shown):
```powershell
$env:VECTOR_BACKEND="milvus"
$env:MILVUS_URI="http://localhost:19530"
$env:MILVUS_TOKEN=""
```

**2. Run the automated gates (1, 2, 4):**
```powershell
embeddings\MCP\.venv-mcp\Scripts\python.exe embeddings\e2e_check.py
# exits 0 iff all hard gates pass; then prints the manual steps for Gates 3 & 5.
```

| Gate | Proves | How | Auto? |
|---|---|---|---|
| 1 | core logic intact | pytest suites (offline, numpy) | ✅ |
| 2 | live backend exact & synced | `check_milvus.py` → MilvusVectorStore, parity ×3, count 778 ×3, reuse | ✅ |
| 3 | real query embeds → vector DB → results | `search_similarity.py "<q>"` (needs embedding endpoint) | manual |
| 4 | serving surfaces use the same backend | REST `/health` backend, `/statements`, `/search` | ✅ |
| 5 | grounded LLM answer (the top) | `api_server` + `chat_agent`: ask a question → cited answer | manual |

**3. Manual gates (need the embedding endpoint / Ollama):**
```powershell
# Gate 3
embeddings\MCP\.venv-mcp\Scripts\python.exe `
  embeddings\Pipeline\search_similarity.py "instruments that measure anxiety in children"
# Gate 5 (two terminals)
embeddings\MCP\.venv-mcp\Scripts\python.exe embeddings\API\api_server.py        # A
embeddings\MCP\.venv-mcp\Scripts\python.exe embeddings\agent\chat_agent.py       # B
#   ask: "Which instruments measure anxiety in children? Describe the top one."
#   PASS if it calls search -> get_statements and cites entity ids.
```

**Pass = whole process green:** Gates 1–5 pass, **no `[vector_store] Milvus backend
unavailable`** warning (that = a silent numpy fallback, usually the CA bundle), and the
agent's answer cites ids that `search` returned. If `/search` returns **503**, you're
off the embedding endpoint (get on RPI VPN, or point `EMBED_*` at a local
`qwen3-embedding`) — Gates 3 & 5 can't complete until that's reachable.

## Troubleshooting

| Symptom | Cause → fix |
|---|---|
| `httpx.ConnectTimeout` / `/search` 503 | Embedding endpoint unreachable → RPI VPN, or point `EMBED_*` at a local `qwen3-embedding`. |
| `check_milvus.py` prints `fell back to numpy` | Server down / wrong URI / missing local Docker container → check each; the stderr warning names the exception. |
| Dimension / shape error in search | Query embedded by a non-4096 model → use `qwen3-embedding`, or regenerate the corpus. |
| LM Studio model never calls the tool | Use a tool-calling model; enable `poem-search`; approve tool calls ([MCP/LM_STUDIO.md](MCP/LM_STUDIO.md)). |
| `fastmcp dev … mcp_server.py` "Unknown command" | FastMCP 3.x → use `fastmcp dev inspector <file>` ([MCP.md §3](MCP/MCP.md)). |
