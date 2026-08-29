# Milvus Integration

Quick reference: see `../manuals/DOCS_SUMMARY.md` for a single-page Milvus quick-start and common commands.

How the POEM embeddings stack uses **Milvus** (via the [`pymilvus`](https://github.com/milvus-io/pymilvus)
library) as its vector search engine — architecture, code map, collection schema,
configuration, the Docker stack, and how to verify the live path.

> **TL;DR** — Milvus is the **default** backend (`VECTOR_BACKEND=milvus`). It is
> reached over gRPC at `localhost:19530`, provisioned by
> [`milvus-compose.yml`](./milvus-compose.yml). Each metric is materialized as its
> own **FLAT** (exact) collection, so results are identical to the pure-numpy
> backend. If the server is unreachable or `pymilvus` is missing, the system
> **falls back to numpy automatically** — nothing breaks offline. For a local
> server, it also **self-heals first**: `docker_preflight.ensure_milvus_ready()`
> starts Docker Desktop / `docker compose up -d` if either is down before falling
> back (see §10).

---

## 1. Overview

- **Library:** `pymilvus>=3.0.0,<4.0.0` — used through the modern high-level
  **`MilvusClient`** API (`create_schema` / `add_field` / `prepare_index_params` /
  `insert` / `search`). The codebase does **not** use the older ORM-style
  `connections.connect` / `Collection` / `FieldSchema` / `CollectionSchema` API.
- **Server:** Milvus **v2.4.13 Standalone** in Docker (etcd + MinIO + standalone),
  gRPC on `19530`, health on `9091`.
- **Exactness:** each metric gets a dedicated **FLAT** index → exact nearest
  neighbors, so Milvus and numpy return the *same* ranking. This parity is
  deliberate — the test suite compares against numpy.
- **Resilience:** `get_store()` transparently degrades to numpy on any error
  (missing `pymilvus`, server down, wrong URI), logging one line to stderr. For a
  local server it tries to self-heal first (§10a) — start Docker Desktop / the
  compose stack — before falling back.

## 2. Architecture flow

```
generate_embeddings.py                 (embed via qwen3-embedding @ EMBED_BASE_URL)
        │  writes canonical vectors
        ▼
Pipeline/<section>/                     one folder per section
  ├─ *.npy            (one content-addressed vector per paragraph)
  ├─ texts.npy        (source strings, index-aligned)
  └─ manifest.json    ([{hash, file}, ...] in row order)
        │
        │  poem_core.corpus.load_embeddings()  ->  (emb (N,4096), texts (N,), sections (N,))
        ▼
poem_core.vector_store.get_store(emb, texts, sections)   [VECTOR_BACKEND]
        │
        ├─ "milvus"  ─► MilvusVectorStore ─► (re)build one FLAT collection per
        │                 metric in Milvus  ─► client.search(...)
        │                     │ on any error
        │                     ▼
        └─ "numpy" / fallback ─► NumpyVectorStore (in-process, exact)
        │
        ▼
   top_candidates(query_vec, metric, section, k) -> (scores, texts, sections)
        │
        ▼
   dedup-by-entity (poem_core.dedup)  ─►  [MCP] RDF enrich (graph_lookup)  ─►  results
```

**`.npy` files are the source of truth.** Milvus is a search *accelerator* rebuilt
from those arrays at startup — see [§7 In-memory-first](#7-in-memory-first-model).

## 3. Code map

| Concern | File | Anchor |
|---|---|---|
| Backends + factory | [`poem_core/vector_store.py`](../poem_core/vector_store.py) | `NumpyVectorStore` L42, `MilvusVectorStore` L81, `get_store` L187 |
| Milvus connect / warm-up | `poem_core/vector_store.py` | `__init__` L90–118 (lazy `from pymilvus import MilvusClient`, `list_collections()` probe, warm Cosine) |
| Build/reuse a collection | `poem_core/vector_store.py` | `_ensure` L123–159 (`from pymilvus import DataType`, schema, FLAT index, batched insert, `load_collection`) |
| Query | `poem_core/vector_store.py` | `top_candidates` L161–184 (`client.search`, section filter, L2 negation, L1→numpy) |
| Config / env knobs | [`poem_core/config.py`](../poem_core/config.py) | vector-store block L53–79 (`vector_backend`, `milvus_uri`, `milvus_collection`, `milvus_token`) |
| Metric → Milvus type | [`poem_core/metrics.py`](../poem_core/metrics.py) | `MILVUS_METRIC_TYPE` L49–54, `milvus_metric_for` L57 |
| Corpus loader (arrays) | [`poem_core/corpus.py`](../poem_core/corpus.py) | `load_embeddings` L73, `discover_sections` L45 |
| Consumer — CLI | [`Pipeline/search_similarity.py`](../Pipeline/search_similarity.py) | `get_store` L115, `top_candidates` L98 |
| Consumer — evaluation | [`Pipeline/evaluate_search.py`](../Pipeline/evaluate_search.py) | `get_store` L254, `top_candidates` L279 |
| Consumer — MCP server | [`MCP/mcp_server.py`](../MCP/mcp_server.py) | `get_store` L103, logs `Vector backend: ...` L104, `top_candidates` L179 |
| Back-compat shim | [`Pipeline/vector_store.py`](../Pipeline/vector_store.py) | re-exports from `poem_core` |

`pymilvus` is imported **only** in `poem_core/vector_store.py`, and only **lazily**
(inside `__init__` and `_ensure`), so importing the package costs nothing until the
Milvus path is actually taken.

## 4. Collections & schema

One **FLAT** collection **per metric**, named `{MILVUS_COLLECTION}_{metric}`:

| Metric name | Milvus metric type | Collection (base `poem`) |
|---|---|---|
| Cosine Similarity | `COSINE` | `poem_cosine` |
| Dot Product | `IP` | `poem_ip` |
| Euclidean (L2) | `L2` | `poem_l2` |
| Manhattan (L1) | *(none)* | *served by internal numpy fallback* |

Schema built in `_ensure` (`vector_store.py` L141–148):

| Field | Type | Notes |
|---|---|---|
| `id` | `INT64` | primary key, `auto_id=False` (row index) |
| `vector` | `FLOAT_VECTOR` | `dim = embeddings.shape[1]` (**4096**, derived — never hardcoded) |
| `text` | *dynamic* | source paragraph (`enable_dynamic_field=True`) |
| `section` | *dynamic* | `instruments` / `scales` / `collections` |

- Index: `add_index(field_name="vector", index_type="FLAT", metric_type=...)` → exact.
- Insert: rows in batches of **1000**, then `load_collection` into memory.
- **Reuse guard:** if the collection already exists and an exact **`count(*)`**
  query equals `len(texts)` (and `rebuild` is false), it is reused as-is; otherwise
  it is dropped and rebuilt. (`count(*)` is used instead of `get_collection_stats`
  row_count, which lags until a flush — see §12.)
- The full corpus (all sections, ≈**778** rows) lives in each collection; section
  restriction is applied at query time via the dynamic-field filter.

## 5. Metric handling & score convention

The pipeline's invariant is **higher = more similar**, for every metric:
- `COSINE` / `IP`: Milvus returns higher-is-closer → used directly.
- `L2`: Milvus returns distance (smaller-is-closer) → **negated** so higher = better
  (`vector_store.py` L180).
- `Manhattan (L1)`: Milvus has no native L1 metric, so `milvus_metric_for` returns
  `None` and `top_candidates` delegates to an internal `NumpyVectorStore`
  (`vector_store.py` L163–165). Results are still exact.

## 6. Query flow

`top_candidates(query_vec, metric, section, k)`:
1. Map the metric name → Milvus metric type; `None` → numpy fallback.
2. `_ensure` the per-metric collection (build once, cached in `self._built`).
3. `client.search(collection, data=[query_vec], limit=k, filter='section == "..."',
   output_fields=["text","section"], search_params={"metric_type": ...})`.
4. Return `(scores, texts, sections)` — `text`/`section` read from each hit's
   dynamic `entity` payload; L2 scores negated.

Callers then run the shared dedup-by-entity (`poem_core.dedup`); the MCP server
additionally enriches each hit from the RDF graph (`graph_lookup`).

## 7. "In-memory-first" model

The canonical data is the on-disk `.npy` corpus, **not** Milvus. At startup the
store (re)builds collections in Milvus from the loaded arrays; the server's
`./volumes` state is incidental and rebuildable. Consequence: **there is no
`generate → Milvus` ingestion path** today — regenerating embeddings updates the
`.npy` files, and the collections are rebuilt from them on next run (reused if the
collection already holds every row, via an exact `count(*)` check).

## 8. Fallback semantics

`get_store()` (`vector_store.py` L187–204):

```python
if backend == "milvus":
    try:
        return MilvusVectorStore(embeddings, texts, sections)
    except Exception as e:            # ImportError (no pymilvus) OR connection error
        sys.stderr.write("[vector_store] Milvus backend unavailable (...); falling back to numpy.\n")
        return NumpyVectorStore(embeddings, texts, sections)
return NumpyVectorStore(embeddings, texts, sections)
```

The constructor probes the server (`list_collections()`) and warms the Cosine
collection, so an unreachable server surfaces **immediately** and the fallback
triggers before any query runs.

**Tests pin numpy.** [`Pipeline/conftest.py`](../Pipeline/conftest.py) and
[`MCP/conftest.py`](../MCP/conftest.py) do
`os.environ.setdefault("VECTOR_BACKEND", "numpy")` so the suite is deterministic and
offline. Because it is `setdefault`, an **explicit** `VECTOR_BACKEND=milvus` in the
environment still wins for a deliberate parity run.

## 9. Configuration

All values are environment-overridable (read live), defined in `poem_core/config.py`:

| Env var | Default | Purpose |
|---|---|---|
| `VECTOR_BACKEND` | `milvus` | `milvus` (external server) or `numpy` (in-process) |
| `MILVUS_URI` | `http://localhost:19530` | Milvus gRPC endpoint (any OS). A bare path (e.g. `poem.db`) selects embedded **Milvus Lite**, which is **Linux/macOS only** |
| `MILVUS_COLLECTION` | `poem` | base name; per-metric collections derived from it |
| `MILVUS_TOKEN` | *(empty)* | auth token for a remote/cloud Milvus (e.g. Zilliz Cloud) |

Install the client: `pip install -e "embeddings[milvus]"` (the `milvus` extra in
[`pyproject.toml`](../pyproject.toml)) or `pip install "pymilvus>=3.0.0,<4.0.0"`.

## 10. Docker stack

Defined in [`milvus-compose.yml`](./milvus-compose.yml) — three services on a
`milvus` network:

| Service | Image | Ports | Role |
|---|---|---|---|
| `milvus-etcd` | `quay.io/coreos/etcd:v3.5.5` | *(internal)* | metadata / coordination |
| `milvus-minio` | `minio/minio:RELEASE.2023-03-20T…` | `9000`, `9001` | object storage |
| `milvus-standalone` | `milvusdb/milvus:v2.4.13` | `19530` (gRPC), `9091` (health) | the Milvus server (`MILVUS_URI`) |

```bash
# Start
docker compose -f embeddings/docker/milvus-compose.yml up -d
# Health (standalone has a 90s start_period — wait for it before querying)
docker compose -f embeddings/docker/milvus-compose.yml ps
curl http://localhost:9091/healthz
# Stop  /  Stop + wipe volumes
docker compose -f embeddings/docker/milvus-compose.yml down
docker compose -f embeddings/docker/milvus-compose.yml down -v
```

Data persists under `embeddings/docker/volumes/` via bind mounts, but is
rebuildable (see §7).

### 10a. Self-healing / surviving a reboot

Two layers cover "the machine got rebooted / Docker was never started" so you
(almost) never have to run the commands above by hand:

1. **`restart: unless-stopped`** on all three services in
   [`milvus-compose.yml`](./milvus-compose.yml) — once the Docker daemon is up,
   the containers come back on their own. For this to also happen automatically
   at boot (not just when you next open Docker Desktop), enable **Docker
   Desktop → Settings → General → "Start Docker Desktop when you sign in"**.
2. **[`poem_core/docker_preflight.py`](../poem_core/docker_preflight.py)**
   covers the remaining gap — Docker Desktop itself isn't running at all.
   `ensure_milvus_ready()`:
   - checks `docker info`; if unreachable, launches Docker Desktop (Windows/macOS;
     best-effort `systemctl start docker` on Linux) and waits for it,
   - checks the compose stack is running; if not, runs `docker compose up -d`,
   - waits for `/healthz` to report ready.

   It runs automatically from `vector_store.get_store()` (only for a *local*
   `MILVUS_URI` — a remote/cloud target never triggers a local Docker launch)
   and from `check_milvus.py`, `milvus_admin.py`, and `milvus_demo.py`. Run it
   standalone with `python embeddings/docker/ensure_docker.py`.

   - `MILVUS_SKIP_ENSURE=1` disables it entirely (CI, headless boxes).
   - `DOCKER_DESKTOP_EXE=<path>` overrides the Windows install-path lookup if
     Docker Desktop lives somewhere other than `Program Files`.

## 11. Verifying the live path

1. **Start & wait for healthy** — the standalone container's 90s `start_period`
   matters: a query issued before the server is ready trips the numpy fallback.
2. **Confirm the backend is Milvus (not the silent fallback):**
   ```bash
   VECTOR_BACKEND=milvus python -c "import sys; sys.path.insert(0,'embeddings'); \
   from poem_core.corpus import load_embeddings; from poem_core.vector_store import get_store; \
   e,t,s=load_embeddings(); print(type(get_store(e,t,s)).__name__)"
   # -> MilvusVectorStore   (and NO '[vector_store] Milvus backend unavailable' warning)
   ```
3. **Collections exist:** `MilvusClient("http://localhost:19530").list_collections()`
   shows `poem_cosine`, `poem_ip`, `poem_l2`, each with `row_count == len(texts)` (≈778).
4. **Exactness / parity:** with a stored vector as the query (`emb[0]`), Milvus and
   numpy return the same top-k for Cosine / Dot / L2; `emb[0]` ranks itself #1.
5. **MCP path:** `VECTOR_BACKEND=milvus` then start `MCP/mcp_server.py`; it logs
   `[mcp_server] Vector backend: MilvusVectorStore`.

> **Verified — server-independent** (2026-07-06, `pymilvus 2.4.15`, Python 3.8):
> the client imports; the corpus loads at **dim 4096** (778 vectors); a direct
> `MilvusVectorStore` with no server raises a **`MilvusException`** (not
> `ImportError`), so the library is fine and only the server is absent;
> `get_store()` falls back to numpy with the documented warning; the numpy parity
> ground-truth holds (`emb[0]` ranks #1 for all four metrics).
>
> **Verified — live against Zilliz Cloud** (2026-07-06, managed Milvus): with
> `VECTOR_BACKEND=milvus` and `MILVUS_URI`/`MILVUS_TOKEN` set, `get_store()` selected
> **`MilvusVectorStore`** (no fallback); `poem_cosine` / `poem_ip` / `poem_l2` were
> created with **778 entities each** (confirmed by a `count(*)` query — see the
> row_count note below); and Milvus top-k matched the numpy ground-truth for
> Cosine / Dot / L2 (`emb[0]` ranks #1), with Manhattan/L1 on the numpy fallback.

### TLS-intercepting networks (corporate proxy)

If your network intercepts TLS you'll see gRPC `CERTIFICATE_VERIFY_FAILED` on
connect and `get_store()` will **silently fall back to numpy** (with the warning).
Point gRPC at a CA bundle that includes the interceptor's root — simplest is the
Windows trust store exported to PEM:

```powershell
# Export the Windows trusted roots (includes the corporate/proxy root) to PEM:
$out = "$PWD\win_ca_bundle.pem"; $sb = [Text.StringBuilder]::new()
Get-ChildItem Cert:\LocalMachine\Root, Cert:\CurrentUser\Root | ForEach-Object {
  [void]$sb.AppendLine("-----BEGIN CERTIFICATE-----")
  [void]$sb.AppendLine([Convert]::ToBase64String($_.RawData,'InsertLineBreaks'))
  [void]$sb.AppendLine("-----END CERTIFICATE-----") }
Set-Content $out $sb.ToString() -Encoding ascii

# Then tell gRPC (pymilvus) to trust it, and connect as usual:
$env:GRPC_DEFAULT_SSL_ROOTS_FILE_PATH = "$PWD\win_ca_bundle.pem"
```

This is a client-side *network* workaround, not a code change — the same
`MILVUS_URI`/`MILVUS_TOKEN` then connect. (This is exactly how the live Zilliz
verification above was run.)

## 12. Known limitations / notes

- **No `generate → Milvus` store-of-record ingestion.** `.npy` remains canonical;
  Milvus is rebuilt from it (§7).
- **Minimal metadata in Milvus** (`text`, `section` only). Entity id/type/label are
  resolved from the RDF graph at query time (`MCP/graph_lookup.py`), not stored in
  Milvus.
- **FLAT is intentional** (exact, numpy-parity for tests). Switching to ANN
  indexes (IVF/HNSW) would trade exactness for speed and break the parity guarantee.
- **Dimension is derived** from `embeddings.shape[1]`, so the schema stays correct
  if the embedding model changes.
- **`get_collection_stats` row_count lags** (counts *sealed* segments only, so it
  reads **0 right after insert** even though data is queryable — use `count(*)` for
  the true number). `_ensure`'s reuse-guard therefore decides reuse with an exact
  **`count(*)` query**, not row_count, so existing collections are **reused, not
  rebuilt**, on every startup. *Verified live against Zilliz: a second `get_store()`
  did **0 drops / 0 inserts** with parity intact.*
- **Windows:** use the Docker Standalone server; embedded Milvus Lite (bare-path
  `MILVUS_URI`) is Linux/macOS only.

## 13. Ways to run Milvus — deployment modes & recommendation

`pymilvus` speaks the **same `MilvusClient` API** to every deployment — you only
change **where Milvus lives** (the `MILVUS_URI` / `MILVUS_TOKEN`). The four modes:

| Mode | How you point at it | Runs where | Best for | Fits POEM? |
|---|---|---|---|---|
| **Milvus Lite** (embedded) | `MILVUS_URI=./poem.db` (a bare file path) | In-process, no server | Quick local dev, notebooks, tiny corpora | ❌ **Linux/macOS only** — not on this Windows box |
| **Standalone** (Docker) | `http://localhost:19530` | One container set (etcd+MinIO+standalone) on your machine | Single-node, up to millions of vectors; the project default | ✅ **Recommended default** — this is [`milvus-compose.yml`](./milvus-compose.yml) |
| **Distributed** (Kubernetes) | `http://<cluster>:19530` | A K8s cluster (many pods, HA) | Very large scale, high availability, multi-tenant | ➖ Overkill for a ~778-vector corpus |
| **Zilliz Cloud** (managed) | `https://…zillizcloud.com` + `MILVUS_TOKEN` | Zilliz's cloud (fully managed) | No-ops, shared/remote access, teams | ✅ Good when a **hosted/shared** endpoint is wanted |

### "Milvus as an API from Python"

`pymilvus` is a **client**, not a server you embed in your app — you don't "run
Milvus in Python." To expose POEM search (which is backed by Milvus via
`get_store()`) as an **HTTP API from Python**, use the FastAPI service in
[`../API/api_server.py`](../API/api_server.py): set `VECTOR_BACKEND=milvus`
(+`MILVUS_URI`) and it serves `/search` over HTTP, with a Swagger UI at `/docs`.
See [../API/API.md](../API/API.md). (The MCP server is the equivalent surface for
LLM clients.)

### Recommendation for POEM

1. **Local / single developer:** **Standalone via Docker** (current default). Bring
   it up with `milvus-compose.yml`, keep **FLAT** indexes (exact, numpy-parity), and
   let the `.npy` files stay canonical (§7). Zero cost, exact results, matches tests.
2. **Shared / remote / no-ops:** **Zilliz Cloud** — point `MILVUS_URI` +
   `MILVUS_TOKEN` at the managed endpoint; no infra to run. Same code path.
3. **Serving to other apps:** run the **FastAPI service** (`VECTOR_BACKEND=milvus`)
   in front of whichever of the above you chose — that is the "Milvus as an API"
   deliverable.

Skip **Distributed** unless the corpus grows by orders of magnitude, and skip
**Milvus Lite** on Windows. In every case the schema, FLAT-exactness, and numpy
fallback described above are unchanged — only `MILVUS_URI`/`MILVUS_TOKEN` differ.

## 14. Managing accounts — upload, update, switch

[`check_milvus.py`](./check_milvus.py) *verifies* a backend; [`milvus_admin.py`](./milvus_admin.py)
*operates* on it. Target account = `MILVUS_URI` / `MILVUS_TOKEN` (env), overridable
per-run with `--uri` / `--token`.

| Task | Command |
|---|---|
| See what's on an account vs local data | `python embeddings/docker/milvus_admin.py status` |
| **Upload everything to a NEW account** | `python embeddings/docker/milvus_admin.py push --uri <B-uri> --token <B-token>` |
| **Update an account after the data changed** | `python embeddings/docker/milvus_admin.py push` |
| Reset / clean an account | `python embeddings/docker/milvus_admin.py drop` |

**Upload to a new account.** `push` (re)builds all three metric collections
(`poem_cosine` / `poem_ip` / `poem_l2`) from the local `.npy` corpus on whatever
`--uri` points at, creating them if absent — so populating a fresh Zilliz cluster is
a single `push`.

**Update after data changes.** Regenerate the corpus (`generate_embeddings.py`),
then `push` to make Milvus match the new `.npy`. `status` prints a **corpus
fingerprint** (a hash over the content manifests) so you can see whether the local
data changed. Note automatic startup reuse only compares the *row count*, so run
`push` explicitly after a **same-count content edit**. A full rebuild of 778 vectors
is a few seconds — no incremental sync is needed at this scale.

**Switch accounts.** Either export the env for the session (repoints the whole stack
— CLI, MCP, REST API, agent — at once):

```powershell
$env:MILVUS_URI   = "https://<account>.zillizcloud.com:19540"
$env:MILVUS_TOKEN = "<token>"      # db_admin:pw, or a revocable API key
$env:GRPC_DEFAULT_SSL_ROOTS_FILE_PATH = "C:\path\win_ca_bundle.pem"   # TLS-intercepted nets
```

…or pass `--uri` / `--token` per command to hit a different account without touching
the env. *Verified live against Zilliz: `status` → `push` → `status` round-trips with
all three collections at 778.*
