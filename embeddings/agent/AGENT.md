# POEM Chat Agent (`chat_agent.py`)

Quick reference: see `../manuals/DOCS_SUMMARY.md` for a single-page quick-start and common commands.

A minimal terminal chatbot that puts a **local, tool-calling chat model** in front
of the POEM search tools. The model decides when to call `search` /
`get_statements`; the agent executes those calls and feeds the JSON back, so every
answer is grounded in the ontology and cites entity ids (e.g. `[RCADS-25-CG-EN]`).

> **TL;DR** — one terminal:
> ```powershell
> embeddings\MCP\.venv-mcp\Scripts\python.exe embeddings\agent\chat_agent.py
> ```
> Preconditions: a tool-calling chat server is up (LM Studio on `:1234` by default,
> or Ollama on `:11434` — see [§3](#3-managing-which-chat-model-you-use)), and —
> for the `search` tool only — the embedding endpoint is reachable (RPI VPN, or a
> local `qwen3-embedding`). `get_statements` works fully offline.

---

## 1. Architecture

```
you --> chat model (OpenAI-compatible: Ollama / LM Studio / vLLM)
            --tool calls--> MCP client --stdio--> ../MCP/mcp_server.py
            --> grounded answer with [entity-id] citations
```

Two separate LLM roles are involved — don't conflate them:

| Role | What it does | Which model | Where it's configured |
|---|---|---|---|
| **Chat model** | Conversation + decides tool calls | **Your choice** — any tool-calling model (Qwen2.5-7B, Llama-3.1-8B, …) | `CHAT_BASE_URL` / `CHAT_MODEL` (this agent) |
| **Embedding model** | Embeds the `search` query | **Fixed:** `qwen3-embedding` (4096-dim) — must match the stored corpus | `EMBED_BASE_URL` / `EMBED_MODEL` (the MCP server it spawns) |

The chat model is fully swappable ([§3](#3-managing-which-chat-model-you-use)).
The embedding model is **not** — the corpus vectors were built with
`qwen3-embedding`, so queries must be embedded by the same model or `search`
errors/returns nonsense (see [../MCP/LM_STUDIO.md](../MCP/LM_STUDIO.md) "the
embedding-model must match").

## 2. Running it

Use a Python ≥ 3.10 env with the deps installed. Both MCP venvs already have
everything:

```powershell
# Dev venv (on the machine that created it):
embeddings\MCP\.venv-mcp\Scripts\python.exe embeddings\agent\chat_agent.py

# Per-device venv (created by MCP/setup_lmstudio.ps1 on any machine):
%LOCALAPPDATA%\POEM\mcp-venv\Scripts\python.exe embeddings\agent\chat_agent.py
```

Fresh env instead: `pip install -r embeddings/agent/requirements-agent.txt`
(`openai`, `fastmcp`, `httpx`) — see [requirements-agent.txt](./requirements-agent.txt).
The default (MCP) transport also spawns `../MCP/mcp_server.py`, whose deps are
`requirements-mcp.txt` (covered automatically by the venvs above).

On start (MCP mode) the agent spawns the MCP server as a subprocess — the corpus
(~778 vectors) and RDF graph load, which takes a moment — then discovers the tool
list from the server (`list_tools`), so schemas can never drift from the
implementation. Then chat at the `you>` prompt; tool calls are echoed to stderr as
`· search({...})`. Type `exit` or Ctrl-C to quit. Each user turn allows at most
**6 tool-call rounds** before the agent stops a looping model.

## 3. Managing which chat model you use

The agent talks to **any OpenAI-compatible chat server** via three env vars:

| Env var | Default | Meaning |
|---|---|---|
| `CHAT_BASE_URL` | `http://localhost:1234/v1` | The chat server (LM Studio default; Ollama is `:11434/v1`) |
| `CHAT_MODEL` | `google/gemma-4-e4b` | Model name **as the server knows it** |
| `CHAT_API_KEY` | `lm-studio` | Ignored by local servers; set for a real cloud endpoint |

**The model must support tool/function calling** — that's how it invokes `search`
/ `get_statements`. Good local picks: *Qwen2.5-7B-Instruct*, *Llama-3.1-8B-Instruct*
(the current default, `gemma-4-e4b`, is what happened to be downloaded locally;
swap in a verified tool-caller if it doesn't reliably invoke tools). A
non-tool-calling model will just answer from its own head, ungrounded.

Switching is just env vars — no code changes:

```powershell
# LM Studio (default) — start its server (Developer tab) and pick a loaded model:
$env:CHAT_MODEL = "qwen2.5-7b-instruct"   # the id LM Studio shows for the loaded model

# Ollama as the chat server:
$env:CHAT_BASE_URL = "http://localhost:11434/v1"
$env:CHAT_MODEL    = "qwen2.5:7b"            # or llama3.1:8b, etc. (see `ollama list`)

# vLLM / any other OpenAI-compatible server:
$env:CHAT_BASE_URL = "http://<host>:<port>/v1"
$env:CHAT_MODEL    = "<served model name>"

# then run the agent as usual
embeddings\MCP\.venv-mcp\Scripts\python.exe embeddings\agent\chat_agent.py
```

The agent prints `Chat model: <model> @ <url>` at startup so you can confirm what
it's actually using. If the model name doesn't match one the server has
loaded/pulled, the first turn fails with a chat error naming `CHAT_BASE_URL`.

### 3a. Self-healing on startup

For the default LM Studio backend, `main()` calls
[`lmstudio_preflight.ensure_lmstudio_ready()`](./lmstudio_preflight.py) before
opening the tool loop — you don't have to remember to open LM Studio, start its
server, and load the model yourself every time:

1. Checks `<CHAT_BASE_URL>/models`; if unreachable, tries `lms server start`.
2. If that fails (the LM Studio *application* isn't running at all — `lms
   server start` alone can't wake a fully-closed app), launches LM Studio and
   retries `lms server start` until it comes up.
3. Checks `CHAT_MODEL` is loaded; if not, `lms load <model>`.

This mirrors [`poem_core/docker_preflight.py`](../poem_core/docker_preflight.py)'s
role for Milvus. It only ever acts on a **local** `CHAT_BASE_URL`
(localhost/127.0.0.1) — pointing at Ollama, vLLM, or a remote server never
triggers a local LM Studio launch. `CHAT_SKIP_ENSURE=1` disables it entirely;
`LM_STUDIO_EXE` / `LMS_CLI_EXE` override the app / `lms` CLI paths if they're
not in the standard locations.

## 4. How it utilizes LM Studio

LM Studio can play **three different parts** in this stack — pick per role:

### 4a. LM Studio as the agent's *chat* backend

Run LM Studio purely as a local OpenAI-compatible server that `chat_agent.py`
talks to:

1. In LM Studio, download a **tool-calling** chat model (Discover tab) and load it.
2. Start the local server (Developer tab — default `http://localhost:1234/v1`).
3. Point the agent at it (see [§3](#3-managing-which-chat-model-you-use)):
   `CHAT_BASE_URL=http://localhost:1234/v1`, `CHAT_MODEL=<loaded model id>`.

Here the agent still owns the tool loop and spawns the MCP server itself; LM
Studio only generates the chat turns.

### 4b. LM Studio as the *MCP host* (replaces this agent)

LM Studio ≥ 0.3.17 is itself an MCP host: it runs the chat model **and** calls the
POEM tools directly, with expandable tool-call cards showing parameters + JSON. In
that setup you don't run `chat_agent.py` at all — register the server with the
one-command setup:

```powershell
powershell -ExecutionPolicy Bypass -File O:\POEM\embeddings\MCP\setup_lmstudio.ps1
```
(`O:\POEM` is this team's mapped share; adjust for wherever your own checkout lives.)

Full walkthrough: [../MCP/LM_STUDIO.md](../MCP/LM_STUDIO.md). Rule of thumb:
LM Studio-as-host for interactive/inspection use; `chat_agent.py` when you want a
scriptable terminal loop or a base to grow the Phase 3 assistant from
([../ROADMAP.md](../ROADMAP.md)).

### 4c. LM Studio as the *embedding* provider (for `search`)

Independent of the chat model, the spawned MCP server needs a `qwen3-embedding`
endpoint to embed queries. Default is the RPI server (VPN required). To go fully
local, load a **`qwen3-embedding`** GGUF in LM Studio and point the MCP server at
it before starting the agent:

```powershell
$env:EMBED_BASE_URL = "http://localhost:1234/v1"
$env:EMBED_MODEL    = "qwen3-embedding"
```

⚠️ Only `qwen3-embedding` (4096-dim) works — a different embedder (e.g.
`nomic-embed-text`, 768-dim) mismatches the stored corpus unless you regenerate
all embeddings with it (`generate_embeddings.py`).

## 5. Tool transports (`POEM_TOOLS`)

| Mode | How tools run | When to use |
|---|---|---|
| `mcp` *(default)* | Agent spawns `../MCP/mcp_server.py` over stdio; tool schemas discovered live | One terminal, no extra process — the normal path |
| `rest` | Calls the FastAPI service (`../API/api_server.py`) over HTTP | The REST API is already running / shared |

```powershell
# rest mode: start the API first, then
$env:POEM_TOOLS  = "rest"
$env:POEM_API_URL = "http://localhost:8000"   # default
```

## 6. Full configuration reference

| Env var | Default | Purpose |
|---|---|---|
| `CHAT_BASE_URL` | `http://localhost:1234/v1` | OpenAI-compatible chat server (LM Studio / Ollama / vLLM) |
| `CHAT_MODEL` | `google/gemma-4-e4b` | Tool-calling chat model name |
| `CHAT_API_KEY` | `lm-studio` | API key (ignored by local servers) |
| `CHAT_SKIP_ENSURE` | unset | Set to skip the LM Studio self-heal preflight (§3a) entirely |
| `LM_STUDIO_EXE` / `LMS_CLI_EXE` | standard install paths | Override where the preflight looks for the app / `lms` CLI |
| `POEM_TOOLS` | `mcp` | Tool transport: `mcp` or `rest` |
| `POEM_MCP_PYTHON` | this interpreter | Python used to spawn the MCP server (mcp mode) |
| `POEM_MCP_SERVER` | `../MCP/mcp_server.py` | Path to the MCP server script (mcp mode) |
| `POEM_API_URL` | `http://localhost:8000` | REST API base URL (rest mode) |
| `EMBED_BASE_URL` / `EMBED_MODEL` | RPI / `qwen3-embedding` | Inherited by the spawned MCP server — where `search` queries get embedded |
| `VECTOR_BACKEND` | `milvus` (auto-falls back to numpy) | Inherited by the spawned MCP server — see [../docker/MILVUS.md](../docker/MILVUS.md) |

## 7. Troubleshooting

| Symptom | Fix |
|---|---|
| `!! chat error ... Is the chat server up at <url>?` | For the LM Studio default this should self-heal (§3a) — if it still fails, `lms server status` / check `LM_STUDIO_EXE`; for Ollama, start it manually; check `CHAT_MODEL` matches a loaded/pulled model. |
| Model answers but never calls tools | Use a **tool-calling** model (§3); phrase the question so it needs the catalogue. |
| `search` returns an error / nonsense | Embedding endpoint down or wrong model — `EMBED_MODEL` must be `qwen3-embedding` (4096-dim); RPI endpoint needs VPN (§4c). |
| `get_statements` works but `search` doesn't | Expected offline — `get_statements` is a pure graph lookup; `search` needs the embedding endpoint. |
| `!! MCP server script not found` | Set `POEM_MCP_SERVER` to the real path of `mcp_server.py`. |
| `ModuleNotFoundError: fastmcp` (or on server spawn) | Run the agent with an MCP venv (§2), or set `POEM_MCP_PYTHON` to one. |
| `(stopped after too many tool-call rounds)` | The model looped; ask again more specifically or use a stronger chat model. |
| rest mode: `POEM REST API not reachable` | Start `embeddings/API/api_server.py` first, or unset `POEM_TOOLS` to use MCP mode. |

## 8. Related docs

- [../MCP/MCP.md](../MCP/MCP.md) — the MCP server the agent spawns (tools, schemas).
- [../MCP/LM_STUDIO.md](../MCP/LM_STUDIO.md) — LM Studio as MCP host + embedding caveat.
- [../API/API.md](../API/API.md) — the REST surface used by `POEM_TOOLS=rest`.
- [../ROADMAP.md](../ROADMAP.md) — how this Phase 2 agent grows into the full assistant.
- [../TESTING.md](../TESTING.md) — gate 5: manual grounded-answer check via this agent; automated coverage lives in [test_chat_agent.py](./test_chat_agent.py) (tool-call loop, `repl()` error handling, both transports, `main()` dispatch) and [test_lmstudio_preflight.py](./test_lmstudio_preflight.py) (the self-heal in §3a) — see [TESTING.md "Intensive edge-case suites"](../TESTING.md#intensive-edge-case-suites).
