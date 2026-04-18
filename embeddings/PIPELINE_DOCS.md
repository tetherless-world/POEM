# POEM Embeddings Pipeline — Documentation

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
                                  (paragraph_N.npy + texts.npy per section)
          |
          v
   search_similarity.py       ←   query sentence → ranked results
          |
          v
 test_search_similarity.py    →   test_results.txt
```

---

## Prerequisites

**Python:** 3.10 or later (uses `tuple[...]` type hints)

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

**Network access:** The embedding endpoint is hosted at RPI's IDEA cluster:
```
http://idea-llm-02.idea.rpi.edu:1234/v1
```
This endpoint is only reachable when on the RPI campus network or connected via VPN. No API key is required (`api_key="not-needed"` is set in all scripts). If a script raises `httpx.ConnectTimeout` or `openai.APITimeoutError`, connect to VPN and retry.

---

## Step-by-Step Replication

### Step 1 — Generate text templates from the RDF graph

**Script:** `embeddings/generate_text_templates.py`

```bash
# Generate all three sections (instruments, scales, collections)
python embeddings/generate_text_templates.py

# Write to a custom output file
python embeddings/generate_text_templates.py --output embeddings/templates_official.txt

# Generate only one section
python embeddings/generate_text_templates.py --only instruments
python embeddings/generate_text_templates.py --only scales
python embeddings/generate_text_templates.py --only collections
```

**Expected output:** A text file with three sections delimited by `=== INSTRUMENTS ===`, `=== SCALES ===`, and `=== COLLECTIONS ===`. Each paragraph describes one entity from the ontology.

---

### Step 2 — Verify the embedding endpoint

**Script:** `embeddings/sample_embeddings.py`

Run this before the full pipeline to confirm the embedding server is reachable. It embeds the first 3 template blocks and prints the vector length for each.

```bash
python embeddings/sample_embeddings.py
```

**Expected output:**
```
Text 0 embedding length: 2048
  Preview: GAD-7. Attributes include:...

Text 1 embedding length: 2048
  Preview: MTT-35-CG-EN-1. Attributes include:...
...
```

If this raises `httpx.ConnectTimeout`, you are not on the RPI network. Connect to VPN and retry.

---

### Step 3 — Generate embeddings and save as numpy files

**Script:** `embeddings/generate_embeddings.py`

```bash
python embeddings/generate_embeddings.py
```

**Expected output:**
```
Section 'instruments': 5732 paragraphs
Section 'scales': 408 paragraphs
Section 'collections': 54 paragraphs

[instruments] Saved texts index (5732 entries)
  Embedding batch 1 (50 texts)...
  ...
  Saved 5732 paragraph files to embeddings/instruments/

[scales] Saved texts index (408 entries)
  ...

[collections] Saved texts index (54 entries)
  ...

Done!
```

---

### Step 4 — Search with similarity metrics

**Script:** `embeddings/search_similarity.py`

Requires VPN/campus network access (calls the embedding endpoint to vectorize the query).

```bash
# Single query
python embeddings/search_similarity.py "instruments that measure anxiety in children"

# Adjust number of results per metric (default: 5)
python embeddings/search_similarity.py "caregiver therapy attendance" --top-k 10

# Interactive mode — type multiple queries without restarting
python embeddings/search_similarity.py
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

**Script:** `embeddings/test_search_similarity.py`

```bash
# Recommended: run directly — prints output to console AND saves to test_results.txt
python embeddings/test_search_similarity.py

# Or run via pytest directly
python -m pytest embeddings/test_search_similarity.py -v

# Unit tests only (no VPN or .npy files needed)
python -m pytest embeddings/test_search_similarity.py -v -k "TestSimilarityMetrics"

# Integration tests only (requires Steps 3 and VPN access)
python -m pytest embeddings/test_search_similarity.py -v -k "TestSearchQueries"
```

Results are saved to `embeddings/test_results.txt` when run directly with `python`.

**Integration tests are automatically skipped** if the `embeddings/instruments/` folder is absent or empty — complete Step 3 first to enable them.

---

## Script Reference

### `generate_text_templates.py`

| Property | Value |
|----------|-------|
| **Purpose** | Queries the POEM RDF knowledge graph via SPARQL and formats results as natural-language text blocks |
| **Input** | `individualsFull.ttl`, `individuals/*.ttl`, `ontology/*.ttl`, `POEM.rdf` |
| **Output** | A `.txt` file with `=== INSTRUMENTS ===`, `=== SCALES ===`, `=== COLLECTIONS ===` sections |
| **Default output path** | `embeddings/templates.txt` |
| **Requires network** | No |

**Key configuration:**
- `PROJECT_ROOT` — auto-detected from script location (one level up from `embeddings/`)
- `KEYWORDS` — TTL files matching `("collection", "instrument", "scale")` are loaded automatically
- SPARQL queries: `INSTRUMENT_QUERY`, `SCALE_QUERY`, `COLLECTION_QUERY` — edit these to change what fields are extracted

**CLI flags:**

| Flag | Default | Description |
|------|---------|-------------|
| `--output FILE` | `embeddings/templates.txt` | Path to write the output text file |
| `--only {instruments,scales,collections}` | *(all three)* | Generate only one section |

---

### `sample_embeddings.py`

| Property | Value |
|----------|-------|
| **Purpose** | Minimal connectivity check — embeds 3 blocks and prints vector lengths |
| **Input** | `embeddings/templates.txt` (first 3 non-header blocks) |
| **Output** | Printed embedding lengths and vector previews to stdout |
| **Endpoint** | `http://idea-llm-02.idea.rpi.edu:1234/v1` |
| **Model** | `qwen3-embedding:latest` |
| **Requires network** | Yes — RPI VPN or campus |

Run this before `generate_embeddings.py` whenever the endpoint may have changed or after a period of inactivity.

---

### `generate_embeddings.py`

| Property | Value |
|----------|-------|
| **Purpose** | Embeds all template blocks and saves one `.npy` file per paragraph |
| **Input** | `embeddings/templates_official.txt` |
| **Output** | `embeddings/instruments/`, `embeddings/scales/`, `embeddings/collections/` |
| **Endpoint** | `http://idea-llm-02.idea.rpi.edu:1234/v1` |
| **Model** | `qwen3-embedding:latest` |
| **Requires network** | Yes — RPI VPN or campus |

**Key configuration constants** (edit at top of file):

| Constant | Default | Description |
|----------|---------|-------------|
| `TEMPLATES_PATH` | `embeddings/templates_official.txt` | Input file |
| `BATCH_SIZE` | `50` | Number of texts per API call |

**Output structure per section:**

| File | Shape | dtype | Description |
|------|-------|-------|-------------|
| `texts.npy` | `(N,)` | object | Source text strings, index-aligned with paragraph files |
| `paragraph_0.npy` | `(dim,)` | float32 | Embedding vector for paragraph 0 |
| `paragraph_1.npy` | `(dim,)` | float32 | Embedding vector for paragraph 1 |
| ... | | | |

---

### `search_similarity.py`

| Property | Value |
|----------|-------|
| **Purpose** | Embeds a query and ranks all stored paragraphs by four similarity metrics |
| **Input** | Query string + `embeddings/instruments/`, `embeddings/scales/`, `embeddings/collections/` |
| **Output** | Ranked results printed to stdout per metric |
| **Endpoint** | `http://idea-llm-02.idea.rpi.edu:1234/v1` |
| **Model** | `qwen3-embedding:latest` |
| **Requires network** | Yes — RPI VPN or campus (to embed the query) |

**Key configuration constants:**

| Constant | Default | Description |
|----------|---------|-------------|
| `SECTIONS` | `["instruments", "scales", "collections"]` | Subfolders to load |
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
| **Output** | Console output + `embeddings/test_results.txt` |
| **Requires network** | Only for `TestSearchQueries` (integration tests) |

**Test classes:**

| Class | Count | Requires endpoint | Requires .npy files |
|-------|-------|-------------------|---------------------|
| `TestSimilarityMetrics` | 15 | No | No |
| `TestSearchQueries` | 29 | Yes | Yes |

Integration tests are auto-skipped if `embeddings/instruments/` is absent or empty.

---

## Output File Layout

After running all steps the `embeddings/` folder looks like this:

```
embeddings/
├── generate_text_templates.py
├── sample_embeddings.py
├── generate_embeddings.py
├── search_similarity.py
├── test_search_similarity.py
├── PIPELINE_DOCS.md
├── templates.txt                  (default output of generate_text_templates.py)
├── templates_official.txt         (curated input used by generate_embeddings.py)
├── test_results.txt               (saved output of test_search_similarity.py)
│
├── instruments/
│   ├── texts.npy                  shape (N_inst,)        dtype=object
│   ├── paragraph_0.npy            shape (embedding_dim,) dtype=float32
│   ├── paragraph_1.npy
│   └── ...
│
├── scales/
│   ├── texts.npy                  shape (N_scl,)         dtype=object
│   ├── paragraph_0.npy
│   └── ...
│
└── collections/
    ├── texts.npy                  shape (N_col,)         dtype=object
    ├── paragraph_0.npy
    └── ...
```

**To load a stored embedding manually:**
```python
import numpy as np

# Load one paragraph vector
vec = np.load("embeddings/instruments/paragraph_0.npy")
print(vec.shape)   # e.g. (2048,)

# Look up its source text
texts = np.load("embeddings/instruments/texts.npy", allow_pickle=True)
print(texts[0])
```

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
