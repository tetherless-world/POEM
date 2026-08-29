# POEM Embeddings Pipeline — Documentation

Quick reference: see `../manuals/DOCS_SUMMARY.md` for a one-page pipeline quick-start and commands.

> **TL;DR** — from the repo root, with the RPI VPN connected:
> ```bash
> python embeddings/Pipeline/generate_text_templates.py   # RDF graph -> templates.txt
> python embeddings/Pipeline/generate_embeddings.py       # templates -> .npy vectors (778 today)
> python embeddings/Pipeline/search_similarity.py "instruments that measure anxiety in children"
> ```
> No VPN? `sample_embeddings.py` (Step 2 below) tells you immediately if the
> embedding endpoint is unreachable, before you run a full (re)build.

Complete reference for replicating the semantic search pipeline on top of the POEM ontology.

---

## Overview

The pipeline converts the POEM RDF knowledge graph into searchable vector embeddings in four stages:

```
individualsFull.ttl + instruments.ttl + scales.ttl + ...
          |
          v
 generate_text_templates.py   →   templates_official.txt
          |
          v
    sample_embeddings.py      (connectivity check — run once)
          |
          v
   generate_embeddings.py     →   instruments/  scales/  collections/
                                  ({slug}_{hash}.npy + texts.npy + manifest.json per section)
          |
          v
   search_similarity.py       ←   query sentence → ranked results
          |
          v
 test_search_similarity.py    →   test_results.txt
```

---

## Prerequisites

**Python:** 3.8 or later (matches `TESTING.md`'s `tutorial_env`). Scripts use
modern type-hint syntax (e.g. `tuple[...]`) but start with
`from __future__ import annotations`, which defers annotation evaluation to
strings — so they run fine on 3.8+, not just 3.10+.

**Install dependencies:**
```bash
pip install openai numpy rdflib pytest
```

| Package  | Used by                                                                  |
|----------|--------------------------------------------------------------------------|
| `openai` | sample_embeddings.py, generate_embeddings.py, search_similarity.py      |
| `numpy`  | generate_embeddings.py, search_similarity.py, test_search_similarity.py |
| `rdflib` | generate_text_templates.py                                               |
| `pytest` | test_search_similarity.py                                                |

**Network access:** The embedding endpoint defaults to RPI's IDEA cluster:
```
http://idea-llm-01.idea.rpi.edu:11435/v1   (model: qwen3-embedding, 4096-dim)
```
This server is HTTP-only (verified on VPN). Override with `EMBED_BASE_URL` / `EMBED_MODEL` (set `https://…` for a TLS-capable endpoint, or point at any OpenAI-compatible embeddings endpoint). The default endpoint is only reachable on the RPI campus network or VPN. No API key is required (`api_key="not-needed"` is set in all scripts). If a script raises `httpx.ConnectTimeout` or `openai.APITimeoutError`, connect to VPN and retry.

---

## Step-by-Step Replication

### Step 1 — Generate text templates from the RDF graph

**Script:** `embeddings/Pipeline/generate_text_templates.py`

```bash
# Generate all registered sections from the default input folder
# (poem-demo/dist/data)
python embeddings/Pipeline/generate_text_templates.py

# Read instance TTLs from a different folder
python embeddings/Pipeline/generate_text_templates.py --input path/to/data

# Write to a custom output file
python embeddings/Pipeline/generate_text_templates.py --output embeddings/Pipeline/templates_official.txt

# Generate only one or several sections (comma-separated)
python embeddings/Pipeline/generate_text_templates.py --only instruments
python embeddings/Pipeline/generate_text_templates.py --only scales,collections

# Add a NEW section generically — one paragraph per named individual of a class,
# no curated query needed (slug becomes the section/folder name):
python embeddings/Pipeline/generate_text_templates.py --only items --section items=vstoi:Item
```

**Expected output:** A text file whose sections are delimited by `=== NAME ===` headers (e.g. `=== INSTRUMENTS ===`, `=== SCALES ===`, `=== COLLECTIONS ===`, and any you add). Each paragraph describes one entity from the ontology.

**Generic sections.** Sections are driven by a `SECTION_REGISTRY`: the three curated sections (`instruments`, `scales`, `collections`) have tuned SPARQL; any other class can be turned into a section via `--section NAME=prefix:Class` (or a registry entry with just a `class`), which uses a generic runner (`run_generic`) that emits one paragraph per named individual with its outgoing properties. Blank nodes are skipped.

**Input folder.** `--input` (or the `POEM_DATA_DIR` env) sets the *priority* folder, loaded **first**; default `poem-demo/dist/data`. The rest of the repo's data TTLs (`individualsFull.ttl`, `individuals/`, `browser/backend/data/`, …) are then merged in so nothing is missing (e.g. `constructs.ttl`). RDF merges at the triple level, so the priority folder is never doubled; dependency/`.venv` dirs and the `rcads/` mapping files are excluded. The ontology schema (`ontology/*.ttl`, `POEM.rdf`) is layered on top.

---

### Step 2 — Verify the embedding endpoint

**Script:** `embeddings/Pipeline/sample_embeddings.py`

Run this before the full pipeline to confirm the embedding server is reachable. It embeds the first 3 template blocks and prints the vector length for each.

```bash
python embeddings/Pipeline/sample_embeddings.py
```

**Expected output:**
```
Text 0 embedding length: 4096
  Preview: GAD-7. Attributes include:...

Text 1 embedding length: 4096
  Preview: MTT-35-CG-EN-1. Attributes include:...
...
```

If this raises `httpx.ConnectTimeout`, you are not on the RPI network. Connect to VPN and retry.

---
  
### Step 3 — Generate embeddings and save as numpy files

**Script:** `embeddings/Pipeline/generate_embeddings.py`

```bash
# Full (re)build — embeds every block
python embeddings/Pipeline/generate_embeddings.py

# Incremental — re-embed ONLY new/changed blocks (uses each section's
# manifest.json); unchanged vectors are kept, removed entities' files deleted
python embeddings/Pipeline/generate_embeddings.py --incremental

# One or several sections only
python embeddings/Pipeline/generate_embeddings.py --only instruments
```

**Incremental updates.** Each section folder holds a `manifest.json` — an ordered
list of `{"hash", "file"}` (one per row of `texts.npy`) where `hash` is the
sha256 of the paragraph text and `file` is its content-addressed vector
(`{slug}_{hash12}.npy`). On `--incremental`, only blocks whose hash is new or
changed are embedded; identical blocks share a vector file; vector files no
longer referenced (removed/renamed entities, or leftovers from the old
`{idx:04d}_{slug}.npy` scheme) are deleted. This is the normal "the graph
evolved → update only the deltas" workflow.

**Expected output** (shape only — this example is from an early, smaller
demo-scale run; see the note below for the current live corpus size):
```
Section 'instruments': 176 paragraphs
Section 'scales': 18 paragraphs
Section 'collections': 5 paragraphs

Embedding backend: qwen3-embedding @ http://idea-llm-01.idea.rpi.edu:11435/v1
Mode: full rebuild

  [instruments] embedding batch 1 (50 new texts)...
  ...
[instruments] embedded 176, reused 0, removed 0  (total 176) -> .../instruments/

[scales] embedded 18, reused 0, removed 0  (total 18) -> .../scales/
[collections] embedded 5, reused 0, removed 0  (total 5) -> .../collections/

Done!
```

**Current live corpus** (as of this writing, and what every other doc in this
folder — `TESTING.md`, `docker/MILVUS.md`, `MCP/LM_STUDIO.md`, `agent/AGENT.md`
— refers to as "778"): **552 instruments + 217 scales + 9 collections = 778
vectors.** The counts above are shape-only sample output from an earlier,
smaller run of the same script against a reduced dataset; run
`generate_embeddings.py` yourself against the current `poem-demo/dist/data` to
see the real, current counts. A `--incremental` run shows non-zero
`reused`/`removed` instead of `embedded` for unchanged paragraphs.

---

### Step 4 — Search with similarity metrics

**Script:** `embeddings/Pipeline/search_similarity.py`

Requires VPN/campus network access (calls the embedding endpoint to vectorize the query).

```bash
# Single query
python embeddings/Pipeline/search_similarity.py "instruments that measure anxiety in children"

# Adjust number of results per metric (default: 5)
python embeddings/Pipeline/search_similarity.py "caregiver therapy attendance" --top-k 10

# Interactive mode — type multiple queries without restarting
python embeddings/Pipeline/search_similarity.py
```

**Expected output (per metric):**
```
======================================================================
  Cosine Similarity  —  Top 5 results
======================================================================
  # 1  [instruments  ]  score=+0.8912
       RCADS-25-Y-EN. Attributes include:   - instance of: psychometric questionnaire ...
  # 2  [instruments  ]  score=+0.8801
  ...
```

---

### Step 5 — Run the test suite

**Script:** `embeddings/Pipeline/test_search_similarity.py`

```bash
# Recommended: run directly — prints to console AND saves to test_results.txt
python embeddings/Pipeline/test_search_similarity.py

# Or run via pytest
python -m pytest embeddings/Pipeline/test_search_similarity.py -v

# Unit tests only — no VPN or .npy files needed
python -m pytest embeddings/Pipeline/test_search_similarity.py -v -k "TestSimilarityMetrics"

# Function-import integration tests only
python -m pytest embeddings/Pipeline/test_search_similarity.py -v -k "TestSearchQueries"

# CLI subprocess tests only
python -m pytest embeddings/Pipeline/test_search_similarity.py -v -k "TestCLISearch"
```

Results are saved to `embeddings/Pipeline/test_results.txt` when run directly with `python`.

`TestSearchQueries` and `TestCLISearch` are **automatically skipped** if the `embeddings/Pipeline/instruments/` folder is absent or empty — complete Step 3 first to enable them.

`TestCLISearch` also writes two result files to `embeddings/Pipeline/cli_query_results/` during the run.

---

## Running with Podman

Podman is the recommended container runtime — it is daemonless and rootless, which fits research/HPC environments like RPI's clusters.

### Prerequisites

```bash
# Install Podman (Fedora/RHEL)
sudo dnf install podman podman-compose

# Install Podman (Debian/Ubuntu)
sudo apt install podman

# Install podman-compose via pip (any OS)
pip install podman-compose
```

### Build the image

Run from the **project root**:

```bash
podman build -f embeddings/Pipeline/docker/Containerfile-embeddings -t poem-embeddings embeddings/Pipeline/
```

Or from inside the `embeddings/Pipeline/` directory:

```bash
podman build -f docker/Containerfile-embeddings -t poem-embeddings .
```

The image is built from `python:3.11-slim` and installs all dependencies from `embeddings/Pipeline/requirements.txt`.

### Volume layout

| Host path | Container path | Access |
|-----------|----------------|--------|
| Your RDF graph folder (TTL files) | `/data/graph` | read-only |
| Output folder (.npy files + templates) | `/data/embeddings` | read-write |

### Run the full pipeline (templates → embed)

```bash
podman run --rm \
  -v /path/to/your/graph:/data/graph:ro,Z \
  -v /path/to/output:/data/embeddings:Z \
  poem-embeddings pipeline
```

The `:Z` label relabels volumes for SELinux — it is a no-op on non-SELinux systems.

### Available commands

| Command | What it does |
|---------|-------------|
| `pipeline` | Generate templates then embeddings (default) |
| `templates` | Run `generate_text_templates.py` only |
| `sample` | Verify the embedding endpoint is reachable |
| `embed` | Run `generate_embeddings.py` only |
| `search "query"` | Run a similarity search query |
| `test` | Run the pytest test suite |

```bash
# Single search query
podman run --rm \
  -v /path/to/output:/data/embeddings:Z \
  poem-embeddings search "instruments that measure anxiety in children"

# Verify the endpoint before a full run
podman run --rm poem-embeddings sample
```

### Override environment variables

Any of the following can be set with `-e` to point the pipeline at a different graph or embedding server:

| Variable | Default | Purpose |
|----------|---------|---------|
| `POEM_PROJECT_ROOT` | `/data/graph` | Root of the mounted RDF graph |
| `TEMPLATES_OUTPUT` | `/data/embeddings/Pipeline/templates.txt` | Where templates are written |
| `TEMPLATES_PATH` | `/data/embeddings/Pipeline/templates.txt` | Templates file read by `embed` |
| `EMBEDDINGS_DIR` | `/data/embeddings` | Where `.npy` files are written/read |
| `EMBED_BASE_URL` | `http://idea-llm-01.idea.rpi.edu:11435/v1` | Embedding server URL |
| `EMBED_MODEL` | `qwen3-embedding` | Model name on the server |
| `BATCH_SIZE` | `50` | Texts per API call |

```bash
# Use a different embedding server
podman run --rm \
  -e EMBED_BASE_URL=http://my-server:8080/v1 \
  -e EMBED_MODEL=my-model:latest \
  -v /path/to/graph:/data/graph:ro,Z \
  -v /path/to/output:/data/embeddings:Z \
  poem-embeddings pipeline
```

### Using podman-compose

A compose file is provided at `docker/embeddings-compose.yml`. Copy it and the `docker/` folder to your working directory, then place your TTL files in a `graph/` subfolder:

```
myproject/
├── graph/          ← put your .ttl files here
├── embeddings_output/   ← created automatically
└── docker/
    ├── Containerfile-embeddings
    ├── embeddings-compose.yml
    └── entrypoint.sh
```

```bash
# Build
podman-compose -f embeddings/Pipeline/docker/embeddings-compose.yml build

# Run pipeline
podman-compose -f embeddings/Pipeline/docker/embeddings-compose.yml run --rm embeddings pipeline

# Search
podman-compose -f embeddings/Pipeline/docker/embeddings-compose.yml run --rm embeddings search "anxiety instruments"
```

### Browser stack (frontend + backend)

```bash
# Build both images
podman-compose -f embeddings/Pipeline/docker/browser-compose.yml build

# Start the full browser stack
podman-compose -f embeddings/Pipeline/docker/browser-compose.yml up

# Frontend: http://localhost:8080
# Backend API: internal only (http://poem-browser-backend:8000)

# Tear down
podman-compose -f embeddings/Pipeline/docker/browser-compose.yml down
```

---

## Script Reference

### `generate_text_templates.py`

| Property | Value |
|----------|-------|
| **Purpose** | Queries the POEM RDF knowledge graph via SPARQL and formats results as natural-language text blocks |
| **Input** | `individualsFull.ttl`, `individuals/*.ttl`, `ontology/*.ttl`, `POEM.rdf` |
| **Output** | A `.txt` file with `=== INSTRUMENTS ===`, `=== SCALES ===`, `=== COLLECTIONS ===` sections |
| **Default output path** | `embeddings/Pipeline/templates.txt` |
| **Requires network** | No |

**Key configuration:**
- `PROJECT_ROOT` — auto-detected from script location (one level up from `embeddings/Pipeline/`)
- `KEYWORDS` — TTL files matching `("collection", "instrument", "scale")` are loaded automatically
- SPARQL queries: `INSTRUMENT_QUERY`, `SCALE_QUERY`, `COLLECTION_QUERY` — edit these to change what fields are extracted

**CLI flags:**

| Flag | Default | Description |
|------|---------|-------------|
| `--output FILE` | `embeddings/Pipeline/templates.txt` | Path to write the output text file |
| `--only {instruments,scales,collections}` | *(all three)* | Generate only one section |

---

### `sample_embeddings.py`

| Property | Value |
|----------|-------|
| **Purpose** | Minimal connectivity check — embeds 3 blocks and prints vector lengths |
| **Input** | `embeddings/Pipeline/templates.txt` (first 3 non-header blocks) |
| **Output** | Printed embedding lengths and vector previews to stdout |
| **Endpoint** | `http://idea-llm-01.idea.rpi.edu:11435/v1` |
| **Model** | `qwen3-embedding` |
| **Requires network** | Yes — RPI VPN or campus |

Run this before `generate_embeddings.py` whenever the endpoint may have changed or after a period of inactivity.

---

### `generate_embeddings.py`

| Property | Value |
|----------|-------|
| **Purpose** | Embeds all template blocks and saves one `.npy` file per paragraph |
| **Input** | `embeddings/Pipeline/templates_official.txt` |
| **Output** | `embeddings/Pipeline/instruments/`, `embeddings/Pipeline/scales/`, `embeddings/Pipeline/collections/` |
| **Endpoint** | `http://idea-llm-01.idea.rpi.edu:11435/v1` |
| **Model** | `qwen3-embedding` |
| **Requires network** | Yes — RPI VPN or campus |

**Key configuration constants** (edit at top of file):

| Constant | Default | Description |
|----------|---------|-------------|
| `TEMPLATES_PATH` | `embeddings/Pipeline/templates_official.txt` | Input file |
| `BATCH_SIZE` | `50` | Number of texts per API call |

**Output structure per section:**

| File | Shape | dtype | Description |
|------|-------|-------|-------------|
| `texts.npy` | `(N,)` | object | Source text strings, index-aligned with the manifest |
| `manifest.json` | — | — | Ordered `[{"hash","file"}]`, one per text row → its vector file |
| `{slug}_{hash12}.npy` | `(dim,)` | float32 | Content-addressed embedding vector for one paragraph |
| ... | | | |

`load_embeddings()` prefers `manifest.json` (aligning vectors to `texts.npy`),
and falls back to the legacy `{idx:04d}_{slug}.npy` / `paragraph_{idx}.npy`
schemes for older corpora that have no manifest.

---

### `search_similarity.py`

| Property | Value |
|----------|-------|
| **Purpose** | Embeds a query and ranks all stored paragraphs by four similarity metrics |
| **Input** | Query string + `embeddings/Pipeline/instruments/`, `embeddings/Pipeline/scales/`, `embeddings/Pipeline/collections/` |
| **Output** | Ranked results printed to stdout per metric |
| **Endpoint** | `http://idea-llm-01.idea.rpi.edu:11435/v1` |
| **Model** | `qwen3-embedding` |
| **Requires network** | Yes — RPI VPN or campus (to embed the query) |

**Key configuration constants:**

| Constant | Default | Description |
|----------|---------|-------------|
| `SECTIONS` | auto-detected via `discover_sections()` | Section subfolders (any folder under `EMBEDDINGS_DIR` with a `texts.npy`) — a new section becomes searchable with no code change |
| `DEFAULT_TOP_K` | `5` | Results shown per metric |

**CLI flags:**

| Argument | Default | Description |
|----------|---------|-------------|
| `query` (positional) | *(none — interactive mode)* | Query sentence |
| `--top-k N` | `5` | Number of results per metric |

**Importable functions** (used by the test suite):

| Function | Signature | Description |
|----------|-----------|-------------|
| `load_embeddings()` | `→ (embeddings, texts, sections)` | Load all .npy files from disk |
| `embed_query(query)` | `str → np.ndarray` | Embed one string via API |
| `cosine_similarity(q, M)` | `→ np.ndarray` | Scores in `[-1, 1]`, higher = more similar |
| `dot_product(q, M)` | `→ np.ndarray` | Raw dot product, higher = more similar |
| `euclidean_distance(q, M)` | `→ np.ndarray` | Negated L2 distance, higher = more similar |
| `manhattan_distance(q, M)` | `→ np.ndarray` | Negated L1 distance, higher = more similar |

---

### `test_search_similarity.py`

| Property | Value |
|----------|-------|
| **Purpose** | Test suite — verifies metric math (unit tests) and search correctness (integration tests) |
| **Input** | Stored `.npy` embeddings + embedding endpoint (integration tests only) |
| **Output** | Console output + `embeddings/Pipeline/test_results.txt` |
| **Requires network** | Only for `TestSearchQueries` (integration tests) |

**Test classes:**

| Class | Count | Requires endpoint | Requires .npy files | How it tests |
|-------|-------|-------------------|---------------------|--------------|
| `TestSimilarityMetrics` | 15 | No | No | Imports functions directly, uses synthetic numpy vectors |
| `TestSearchQueries` | 29 | Yes | Yes | Imports functions directly, runs real queries via API |
| `TestCLISearch` | 48 | Yes | Yes | Calls `search_similarity.py` as a subprocess, exactly like the terminal |

`TestSearchQueries` and `TestCLISearch` are auto-skipped if `embeddings/Pipeline/instruments/` is absent or empty.

Five `TestCLISearch` tests additionally write their output to `embeddings/Pipeline/cli_query_results/`:
- `instruments_anxiety.txt` — instruments measuring anxiety in children
- `scales_ocd_sp.txt` — Social Phobia / OCD scale query
- `symptom_fear_speaking.txt` — fear of public speaking (SNOMED symptom)
- `teacher_informant.txt` — teacher informant school anxiety query (top-10)
- `rcads47_full.txt` — RCADS-47 full-scale query (top-10)

`TestCLISearch` query categories:
- Instrument queries by code/name (5)
- `--top-k` flag variants (3)
- Scale queries (3)
- Collection queries (2)
- Verbatim item text (3)
- Edge cases (4)
- SNOMED symptom-grounded queries from `constructs.ttl` (7)
- New informant types from `informants.ttl`: Teacher, Therapist, Adult (3)
- Scale notation queries from `scales.ttl`: SP, SAD, MDD, Clarity, Relationship (5)
- Clinical use-case queries (6)
- Multilingual queries inspired by `itemStems.ttl` (2)
- Write-to-file (5)

---

## Output File Layout

After running all steps the `embeddings/Pipeline/` folder looks like this:

```
embeddings/Pipeline/
├── generate_text_templates.py
├── sample_embeddings.py
├── generate_embeddings.py
├── search_similarity.py
├── test_search_similarity.py
├── PIPELINE_DOCS.md
├── templates.txt                  (default output of generate_text_templates.py)
├── templates_official.txt         (curated input used by generate_embeddings.py)
├── test_results.txt               (saved output of test_search_similarity.py)
├── cli_query_results/
│   ├── instruments_anxiety.txt    (instruments measuring anxiety in children)
│   ├── scales_ocd_sp.txt          (Social Phobia / OCD scale query)
│   ├── symptom_fear_speaking.txt  (fear of public speaking — SNOMED symptom)
│   ├── teacher_informant.txt      (teacher informant school anxiety, top-10)
│   └── rcads47_full.txt           (RCADS-47 full-scale query, top-10)
│
├── instruments/
│   ├── texts.npy                  shape (N_inst,)        dtype=object
│   ├── manifest.json              ordered [{"hash","file"}] → each text row's vector
│   ├── GAD-7_25bd4c3c7ac1.npy     shape (4096,)          dtype=float32  ({slug}_{hash12}.npy)
│   ├── MTT-35-CG-EN-1_4727bb1c5e9d.npy
│   └── ...
│
├── scales/
│   ├── texts.npy                  shape (N_scl,)         dtype=object
│   ├── manifest.json
│   ├── Social-Phobia-9-1_75f6d420ac4a.npy
│   └── ...
│
└── collections/
    ├── texts.npy                  shape (N_col,)         dtype=object
    ├── manifest.json
    ├── RCADS_ec8aa468913e.npy
    └── ...
```

**To load a stored embedding manually:**
```python
import json, numpy as np

# Resolve the first paragraph's vector via the manifest (row-aligned to texts.npy)
manifest = json.load(open("embeddings/Pipeline/instruments/manifest.json", encoding="utf-8"))
vec = np.load(f"embeddings/Pipeline/instruments/{manifest[0]['file']}")
print(vec.shape)   # (4096,)

# Look up its source text (index-aligned with the manifest)
texts = np.load("embeddings/Pipeline/instruments/texts.npy", allow_pickle=True)
print(texts[0])
```

---

## Vector store backend

Search goes through a small store abstraction (`poem_core.vector_store`, re-exported
as `vector_store.py`) so the same code runs against either backend, chosen by the
`VECTOR_BACKEND` env var:

> **Deep-dive:** see [`embeddings/docker/MILVUS.md`](../docker/MILVUS.md) for the full
> Milvus integration — code map, collection schema, metric handling, and how to
> verify the live path.

| Backend | How | Notes |
|---------|-----|-------|
| `milvus` (default) | An **external** Milvus server (Standalone, separate process) via `MilvusClient(MILVUS_URI)` | Each supported metric is a **FLAT** collection → results are *exact* (not approximate), matching numpy. Manhattan (L1), which Milvus has no native metric for, is served by an exact numpy fallback. If the server is unreachable or `pymilvus` is missing, the store **falls back to numpy** with a warning. |
| `numpy` | All vectors in one array, scored by `METRICS` | Zero extra deps; brute-force exact search. Cosine reuses a once-computed normalized matrix. |

```bash
# 1. Start the external Milvus server (separate process; any OS, incl. Windows):
docker compose -f embeddings/docker/milvus-compose.yml up -d

# 2. Install the package + Milvus client (blessed setup):
pip install -e embeddings        # exposes the shared `poem_core` package
pip install pymilvus             # or: pip install -e "embeddings[milvus]"

# 3. Search (milvus is the default; MILVUS_URI defaults to http://localhost:19530):
python embeddings/Pipeline/search_similarity.py "anxiety in children"

# Force the pure-numpy backend (no server needed):
VECTOR_BACKEND=numpy python embeddings/Pipeline/search_similarity.py "anxiety in children"
```

The collection is built into memory from the canonical `.npy` vectors at startup
("in memory first"); the server's on-disk volumes are incidental and rebuildable.
`MILVUS_TOKEN` authenticates a remote/cloud server (e.g. Zilliz Cloud).

---

## Similarity Metrics Reference

| Metric | Formula | Range | Interpretation |
|--------|---------|-------|----------------|
| **Cosine Similarity** | `(q · m) / (‖q‖ ‖m‖)` | `[-1, 1]` | 1 = identical direction, 0 = orthogonal, -1 = opposite. Best metric for semantic meaning regardless of vector magnitude. |
| **Dot Product** | `q · m` | `(-∞, +∞)` | Unnormalized similarity. Sensitive to vector magnitude — larger vectors score higher even at the same angle. |
| **Euclidean (L2)** | `−‖q − m‖₂` | `(-∞, 0]` | Geometric distance, negated so higher = closer. Accounts for both direction and magnitude. |
| **Manhattan (L1)** | `−Σ|qᵢ − mᵢ|` | `(-∞, 0]` | Sum of absolute coordinate differences, negated. More robust to outlier dimensions than L2. |

All metrics are oriented so that **higher score = more similar**, enabling consistent ranking across all four.

---

## Common Errors

| Error | Cause | Fix |
|-------|-------|-----|
| `httpx.ConnectTimeout` | Not on RPI network | Connect to RPI VPN or campus Wi-Fi |
| `openai.APITimeoutError` | Same as above | Connect to RPI VPN or campus Wi-Fi |
| `No embeddings found` | Step 3 not yet run | Run `generate_embeddings.py` first |
| `texts.npy missing` | Partial run of Step 3 | Re-run `generate_embeddings.py` |
| `ModuleNotFoundError: numpy` | Dependency missing | `pip install numpy` |
| `ModuleNotFoundError: openai` | Dependency missing | `pip install openai` |
