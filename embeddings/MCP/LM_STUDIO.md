# Trying the POEM MCP Server with LM Studio

Quick reference: see `../manuals/DOCS_SUMMARY.md` for a compact quick-start and troubleshooting tips.

Run the POEM `search` / `get_statements` tools from a **local** LLM and watch the
tool calls (parameters in, JSON out) — no cloud, no API keys.

**Why LM Studio:** it is both a local model runner *and* an **MCP host** (v0.3.17+),
so one app runs your chat model **and** lets it call this server's tools. (Ollama,
by contrast, runs models but is not an MCP host — see [§ Ollama](#ollama-as-the-embedding-provider).)

---

## ⚠️ Read first: the embedding-model must match

The `search` tool embeds your **query** and compares it to the **stored corpus
vectors**, which were built with **`qwen3-embedding` (4096-dim)**. So the query
**must be embedded by the same model**. Consequences:

- ✅ **Keep `search`'s embedding on `qwen3-embedding`** — either the RPI endpoint
  (`http://idea-llm-01.idea.rpi.edu:11435/v1`, needs VPN) or a **local**
  `qwen3-embedding` served by LM Studio/Ollama at 4096-dim.
- ❌ **Do not** substitute a different embedder (e.g. `nomic-embed-text`, 768-dim).
  The dimension mismatch makes `search` error or return nonsense — unless you
  **regenerate the whole corpus** with that model (`generate_embeddings.py`).

**LM Studio's role is the chat model + MCP host.** Its *embedding* side only helps
`search` if the model it serves is `qwen3-embedding`.

---

## 0. One-command setup (any device)

The whole registration below is automated by
[`setup_lmstudio.ps1`](./setup_lmstudio.ps1). On **any** Windows machine with
LM Studio installed and the POEM share mapped, run:

```powershell
powershell -ExecutionPolicy Bypass -File O:\POEM\embeddings\MCP\setup_lmstudio.ps1
```

(`O:\POEM` is this team's mapped share; substitute wherever your own checkout lives if it's not on a mapped `O:` drive.)

Prerequisites: LM Studio ≥ 0.3.17, your repo checkout accessible (this team maps it to `O:`), **Python ≥ 3.10** on
the machine, and (for `search` only) the RPI network/VPN.

What it does:

1. Finds a Python ≥ 3.10 (`py -3.12/-3.11/-3.10`, then `python` on PATH).
2. Creates a **device-local venv** at `%LOCALAPPDATA%\POEM\mcp-venv`. This is
   deliberate: a venv hard-codes the absolute path of its base interpreter in
   `pyvenv.cfg`, so the shared `.venv-mcp` on `O:` only works on the machine
   that created it — **venvs are not portable across machines**.
3. Installs `requirements-mcp.txt` + `pip install -e ..\..` (the `poem_core`
   package), retrying with `--trusted-host` flags on this network's known
   certificate failures.
4. Merges a `poem-search` entry into `%USERPROFILE%\.lmstudio\mcp.json`
   (preserving any other servers), written with **forward slashes** — single
   backslashes in JSON are invalid escapes and get silently dropped, which
   mangles the path (`<drive>:\<checkout>\embeddings\...` → `<drive>:<checkout>embeddings...`).
5. Runs an offline smoke test (`try_search.py RCADS-25-CG-EN` — pure graph
   lookup, no network).

Then restart LM Studio → **Program** tab (chip icon) → enable **poem-search**.
First load is slow (~860 embedding files + the RDF graph). Re-running the
script is safe — it reuses the venv and updates the config entry in place.

## 1. Install & load models

1. Install **LM Studio ≥ 0.3.17** (MCP support) from lmstudio.ai. *(Not currently
   installed on this machine — `lms` CLI / port 1234 were absent when this was written.)*
2. Download a **tool-calling chat model** (Discover tab) — e.g. *Qwen2.5-7B-Instruct*
   or *Llama-3.1-8B-Instruct*. Tool/function calling is required for the model to
   invoke `search`.
3. For embeddings, pick one:
   - **RPI (simplest, reliable):** nothing to download; requires VPN.
   - **Local `qwen3-embedding`:** load a `qwen3-embedding` GGUF in LM Studio (or
     `ollama pull qwen3-embedding`) and start its local server (default
     `http://localhost:1234/v1`).

## 2. Point the MCP server at your embedding endpoint

Configured with env vars — no code edit needed (`mcp_server.py` honors
`EMBED_BASE_URL`/`EMBED_MODEL`). You'll set these in `mcp.json` below. Examples:

| Embedding source | `EMBED_BASE_URL` | `EMBED_MODEL` |
|---|---|---|
| RPI server (default) | `http://idea-llm-01.idea.rpi.edu:11435/v1` | `qwen3-embedding` |
| Local (LM Studio) | `http://localhost:1234/v1` | `qwen3-embedding` |

## 3. Register the POEM server in LM Studio

**Preferred: run [`setup_lmstudio.ps1`](./setup_lmstudio.ps1) (§0) — it writes
this config for you.** The manual steps below are the equivalent by hand.

LM Studio launches MCP servers over **stdio** from an `mcp.json` (same schema as
Claude Desktop). In LM Studio: the **Program** panel (right sidebar) → **Install →
Edit `mcp.json`** (file lives at `%USERPROFILE%\.lmstudio\mcp.json`). Add:

```json
{
  "mcpServers": {
    "poem-search": {
      "command": "C:/Users/<you>/AppData/Local/POEM/mcp-venv/Scripts/python.exe",
      "args": ["O:/POEM/embeddings/MCP/mcp_server.py"],
      "env": {
        "VECTOR_BACKEND": "numpy",
        "EMBED_BASE_URL": "http://idea-llm-01.idea.rpi.edu:11435/v1",
        "EMBED_MODEL": "qwen3-embedding"
      }
    }
  }
}
```

Notes:
- `command` must be a Python env **on this machine** with the deps installed —
  the per-device venv the setup script creates (shown above), or `.venv-mcp` on
  the machine that built it. A wrong interpreter (e.g. bare miniconda) fails
  with `ModuleNotFoundError: fastmcp`.
- **Use forward slashes** (valid on Windows). If you use backslashes they must
  be doubled (`\\`) — single `\` are invalid JSON escapes and get silently
  dropped, mangling the path.
- The `env` block is optional: without it the server uses its defaults
  (`milvus` backend with automatic numpy fallback, RPI embedding endpoint).
  `VECTOR_BACKEND=numpy` skips the Milvus connection attempt on machines that
  don't run it — see [../docker/MILVUS.md](../docker/MILVUS.md).
- Save, then enable **poem-search** in LM Studio.
- First launch loads ~778 vectors + the RDF graph (a few seconds); LM Studio shows
  the server as running when ready.

## 4. Chat, and watch the parameters + JSON

In a chat with your tool-calling model, ask something that needs the tools:

> *"Use poem-search to find instruments that measure anxiety in children, then
> describe the top result."*

LM Studio will show a **tool-call card** you can expand to see exactly what you
asked for:

- **Call parameters** (the arguments the model chose):
  ```json
  { "query": "instruments that measure anxiety in children",
    "top_k": 5, "section": "instruments", "metric": "Cosine Similarity" }
  ```
- **Result** (the JSON the tool returned — approve the call to run it):
  ```json
  [
    { "id": "RCADS-47-CG-EN", "label": "…", "section": "instruments",
      "score": 0.71, "type": "…", "description": "…", "aliases": ["…"],
      "snippet": "…" }
  ]
  ```

The model then typically calls **`get_statements`** with a result's `id` (e.g.
`RCADS-25-CG-EN`) to read that entity's relationships, and writes a summary.

## Ollama as the embedding provider

Ollama is running here on `:11434` but is **not** an MCP host, so it can't call the
tools itself. It *can* serve embeddings — but only usefully if it serves
**`qwen3-embedding` at 4096-dim**. Its current `nomic-embed-text` is **768-dim** and
**incompatible** with the stored corpus (see the caveat above). If you obtain a
`qwen3-embedding` model for Ollama, point the server at it:

```
EMBED_BASE_URL = http://localhost:11434/v1
EMBED_MODEL    = qwen3-embedding
```

`qwen2.5:7b` (also installed in Ollama) is a fine **tool-calling chat** model — use
it as LM Studio's model, or with any other MCP client.

## Prefer to inspect without an LLM?

Two ways to see the same parameters/JSON directly:

- **FastMCP dev inspector** — a web UI to call the tools by hand:
  `fastmcp dev inspector …` (see [MCP.md §3](./MCP.md)).
- **REST API `/docs`** — Swagger UI over HTTP: run
  [`API/api_server.py`](../API/api_server.py) and open `http://localhost:8000/docs`
  (see [../API/API.md](../API/API.md)).

## Troubleshooting

| Symptom | Fix |
|---|---|
| Model never calls the tool | Use a **tool-calling** model; phrase the ask to require search; enable **poem-search** in LM Studio and approve tool calls. |
| `search` errors / empty | Embedding endpoint unreachable or wrong model. Confirm `EMBED_MODEL=qwen3-embedding` and `EMBED_BASE_URL` is reachable (VPN for RPI). |
| Dimension / shape error | You embedded the query with a non-4096 model. Use `qwen3-embedding`, or regenerate the corpus with your model. |
| Server won't start | Run the `command`+`args` in a terminal to see the real error; ensure `command` points at this machine's venv (re-run `setup_lmstudio.ps1` to rebuild it). |
| `ModuleNotFoundError` on start | `command` points at the wrong Python (not the venv), or the venv came from another machine — re-run `setup_lmstudio.ps1`. |
| Path in error looks mangled (`POEMembeddings...`) | Single backslashes in `mcp.json` were dropped by the JSON parser — use forward slashes or `\\`. |
| `get_statements` 404 | Pass an `id` from a `search` result (a skos:notation like `RCADS-25-CG-EN` or `SP`). |
