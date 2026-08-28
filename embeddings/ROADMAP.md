# POEM LLM Framework — Roadmap

Quick reference: see `embeddings/manuals/DOCS_SUMMARY.md` for a concise quick-start and dev notes.

How to build the conversational **POEM assistant** on top of the search engine that
already exists. The retrieval half is done; the "framework" is the **agent loop**
that puts an LLM in front of it.

## Where we are (retrieval half — done)

- **Engine:** `poem_core` — `qwen3-embedding` (4096-dim) vectors, a pluggable vector
  store (numpy / **Milvus**, exact FLAT), and RDF graph enrichment (`graph_lookup`).
- **Live vector DB:** Milvus verified end-to-end, local Standalone or Zilliz Cloud —
  see [docker/MILVUS.md](docker/MILVUS.md); re-check anytime with
  [docker/check_milvus.py](docker/check_milvus.py).
- **Three serving surfaces over the same engine:**
  CLI ([Pipeline/search_similarity.py](Pipeline/search_similarity.py)) ·
  MCP tools `search`/`get_statements` ([MCP/mcp_server.py](MCP/mcp_server.py)) ·
  REST API + Swagger ([API/api_server.py](API/api_server.py)).
- **LLM entry points:** an LM Studio host ([MCP/LM_STUDIO.md](MCP/LM_STUDIO.md)) and a
  starter terminal agent ([agent/chat_agent.py](agent/chat_agent.py)).

## The loop we're building

```
user msg ─► chat model (tool-calling, OpenAI-compatible)
         ─► search(query, top_k, section)      ─► ranked entities (JSON)
         ─► get_statements(id) on the best hit ─► graph relationships (JSON)
         ─► grounded answer citing entity ids  ─► [RCADS-25-CG-EN] …
```
RAG where **retrieval is a tool call** the model chooses — not context stuffing.

## Phases

### Phase 1 — Prove the loop  *(scaffolded)*
Fastest, zero code: **LM Studio** as the MCP host ([MCP/LM_STUDIO.md](MCP/LM_STUDIO.md)).
Confirm a tool-calling model (your `qwen2.5:7b`) calls `search` → `get_statements`
and grounds the answer; watch the tool-call cards for params/JSON.

### Phase 2 — A dedicated agent  *(starter shipped)*
[agent/chat_agent.py](agent/chat_agent.py) — a minimal loop: OpenAI SDK → a local
model (LM Studio `:1234` by default), tools = `search`/`get_statements` executed
against the REST API. Run:
```
# terminal 1: the search API
embeddings\MCP\.venv-mcp\Scripts\python.exe embeddings\API\api_server.py
# terminal 2: the agent
embeddings\MCP\.venv-mcp\Scripts\python.exe embeddings\agent\chat_agent.py
```
Next on this file: streaming output, multi-turn memory limits, richer citations,
automatic section routing, retry/observability around tool calls.

### Phase 3 — RAG quality
- **System prompt:** ground every answer in tool results; cite ids; expand with
  `get_statements`; say "not found" on empty results; no clinical/diagnostic advice
  beyond the catalogue. (Baseline prompt lives in `chat_agent.py`.)
- **Retrieval tuning:** `top_k`, `section` filters, metric (cosine default);
  consider a rerank or hybrid keyword+vector step.
- **Eval harness:** extend [Pipeline/evaluate_search.py](Pipeline/evaluate_search.py)
  with a `question → expected instrument/scale` set; track retrieval hit-rate as you
  tune prompts/params.

### Phase 4 — Serve it
- Add a **`/chat` endpoint** to [API/api_server.py](API/api_server.py) that runs the
  agent loop and streams tokens (SSE) — reuses the already-loaded store/graph, no new
  process.
- A thin web chat UI (or LM Studio for internal use); add auth + request logging.
- **Done in part:** the MCP server itself can now run as a network-reachable,
  containerized service (`MCP_TRANSPORT=http` + [MCP/Dockerfile](MCP/Dockerfile) +
  [docker/mcp-compose.yml](docker/mcp-compose.yml)) rather than only a
  locally-spawned stdio subprocess — see [MCP/MCP.md "Container deployment"](MCP/MCP.md#container-deployment).
  Auth/TLS on that transport is still open, folded into Phase 5 below.

### Phase 5 — Harden
- **Embeddings:** keep `search` on `qwen3-embedding` (4096-dim); pin a reliable serve
  (RPI or a local qwen3-embedding) with failover.
- **Milvus:** rotate the Zilliz `db_admin` password → a scoped API key; size the
  cluster. The reuse-guard fix means no per-startup rebuilds. Optionally add a
  `generate → Milvus` ingestion path (today `.npy` is canonical, rebuilt into Milvus).
- **Observability:** log tool calls, latency, and retrieval quality.

## Framework decision

| Option | What | When |
|---|---|---|
| **MCP host** (LM Studio / Claude Desktop) | zero code, UI-bound | internal demos |
| **Custom Python agent** *(recommended)* | `chat_agent.py` grown up: OpenAI SDK → local model, tools → REST API | a real product |
| LangChain / LlamaIndex / Haystack | framework wrappers around the same tools | fast prototype, heavier deps |

## The one hard constraint

The **chat model** and the **embedding model** are independent. Swap chat models
freely (any tool-caller — `qwen2.5:7b`, Llama-3.1-Instruct, …). The **embedding
model must stay `qwen3-embedding` (4096-dim)** or `search` breaks — unless you
regenerate the whole corpus with a new model (`generate_embeddings.py`).

## Test as you go

Every layer has a concrete check in **[TESTING.md](TESTING.md)** — suites, CLI, the
Milvus backend, the MCP server, the REST API, and this agent.
