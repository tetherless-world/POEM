# POEM Embeddings — Quick Reference

Purpose
- Concise entrypoint for the embeddings subsystem: what it does, how to run it, and key operational gotchas.

Quick start (dev machine)
1. Install editable package and extras:

```powershell
pip install -e embeddings
pip install -e "embeddings[milvus]"   # if you need Milvus
```

2. Run the pipeline (generate templates → embeddings):

```powershell
python embeddings/Pipeline/generate_text_templates.py
python embeddings/Pipeline/generate_embeddings.py
```

3. Run the REST API (uses same core):

```powershell
# use the MCP venv recommended for the API server
.\embeddings\MCP\.venv-mcp\Scripts\python.exe .\embeddings\API\api_server.py
# Open http://localhost:8000/docs
```

4. Optional: start local Milvus (Standalone) for `VECTOR_BACKEND=milvus`:

```powershell
docker compose -f embeddings/docker/milvus-compose.yml up -d
$env:VECTOR_BACKEND = "milvus"
$env:MILVUS_URI = "http://localhost:19530"
```

Key components (one-paragraph each)
- `poem_core/` — shared core: config, embedding client, metrics, corpus loader, RDF graph loader, dedup, and vector-store factory (`get_store`). All serving surfaces import this package.
- `Pipeline/` — authoring and embedding: `generate_text_templates.py` → `generate_embeddings.py` → `.npy` arrays per section (the source of truth). `search_similarity.py` is the CLI consumer.
- `MCP/` — MCP server exposing two tools (`search`, `get_statements`) so LLM hosts can call POEM as a tool. Loads corpus + RDF graph at startup. Runs over stdio (local subprocess) by default, or containerized over HTTP (`MCP_TRANSPORT=http`, `MCP/Dockerfile`, `docker/mcp-compose.yml`) for network/remote deployment — see `MCP/MCP.md` "Container deployment".
- `API/` — FastAPI wrapper exposing `/search`, `/statements`, `/health` and the Swagger UI.
- `docker/` — Milvus compose and admin/check scripts to run or verify a local Milvus Standalone.

Important operational notes
- Embedding model compatibility: the stored corpus is 4096-dim (`qwen3-embedding`). `EMBED_MODEL` must match the vectors or search results will break.
- Milvus behavior: FLAT indexes are used for exact parity with numpy. The pipeline currently rebuilds Milvus collections from the `.npy` corpus at startup or via admin push; consider adding an incremental ingestion path for large corpora.
- Resilience: `get_store()` falls back to the `NumpyVectorStore` if Milvus is unreachable or `pymilvus` is missing.
- Startup: Milvus Standalone has a long `start_period` (90s). Wait for `http://localhost:9091/healthz` before running parity checks.

Where to look next (files)
- Overview: `embeddings/MAIN.md`
- Pipeline docs: `embeddings/Pipeline/PIPELINE_DOCS.md`
- Milvus integration: `embeddings/docker/MILVUS.md`
- API: `embeddings/API/API.md`
- MCP: `embeddings/MCP/MCP.md`
- Tests & runbook: `embeddings/TESTING.md`

Troubleshooting cheatsheet
- `/search` returns 503: embedding endpoint unreachable (VPN or point `EMBED_BASE_URL` to a local embedder).
- `check_milvus.py` prints "fell back to numpy": Milvus not reachable; check `MILVUS_URI` and Docker containers. Run `python embeddings/docker/ensure_docker.py` first — it starts Docker Desktop and the Milvus stack for you if either isn't up.
- Dimension errors: query embedder returned non-4096 vectors — ensure `EMBED_MODEL` is `qwen3-embedding` unless you regenerate the corpus.
