"""Centralized configuration for the POEM embeddings stack.

Single source of truth for filesystem paths, the embedding endpoint, batching,
and vector-store backend selection. Every value honors an environment-variable
override so deployments can retarget without code edits; the defaults reproduce
the original per-module behavior exactly.

Previously these settings were copy-pasted as ``os.environ.get(...)`` calls
across ``search_similarity``, ``generate_embeddings``, ``evaluate_search``,
``generate_text_templates``, ``graph_lookup`` and ``mcp_server``.
"""
from __future__ import annotations

import os

# --- Repo layout -----------------------------------------------------------
# This file lives at <root>/embeddings/poem_core/config.py, so two parents up is
# the embeddings/ root and three is the project root. POEM_PROJECT_ROOT
# overrides the project root (the MCP graph layer sets it explicitly).
_THIS = os.path.dirname(os.path.abspath(__file__))
EMBEDDINGS_ROOT = os.path.dirname(_THIS)                     # <root>/embeddings
_DEFAULT_PROJECT_ROOT = os.path.dirname(EMBEDDINGS_ROOT)     # <root>

PROJECT_ROOT = os.environ.get("POEM_PROJECT_ROOT", _DEFAULT_PROJECT_ROOT)
PIPELINE_DIR = os.path.join(EMBEDDINGS_ROOT, "Pipeline")

# Where the stored ``.npy`` vectors live (one subfolder per section).
EMBEDDINGS_DIR = os.environ.get("EMBEDDINGS_DIR", PIPELINE_DIR)

# Canonical instance-data folder for the RDF graph; ontology schema is layered
# on top by graph.load_graph().
DATA_DIR = os.environ.get("POEM_DATA_DIR", os.path.join(PROJECT_ROOT, "poem-demo", "dist", "data"))

# Template files (generated artifacts). TEMPLATES_PATH is the input that
# generate_embeddings reads; TEMPLATES_OUTPUT is where generate_text_templates
# writes.
TEMPLATES_PATH = os.environ.get("TEMPLATES_PATH", os.path.join(PIPELINE_DIR, "templates_official.txt"))
TEMPLATES_OUTPUT = os.environ.get("TEMPLATES_OUTPUT", os.path.join(PIPELINE_DIR, "templates.txt"))

# --- Embedding endpoint (OpenAI-compatible) --------------------------------
EMBED_BASE_URL = os.environ.get("EMBED_BASE_URL", "http://idea-llm-01.idea.rpi.edu:11435/v1")
EMBED_MODEL = os.environ.get("EMBED_MODEL", "qwen3-embedding")
EMBED_API_KEY = os.environ.get("EMBED_API_KEY", "not-needed")
EMBED_TIMEOUT = float(os.environ.get("EMBED_TIMEOUT", "60"))
EMBED_MAX_RETRIES = int(os.environ.get("EMBED_MAX_RETRIES", "2"))
BATCH_SIZE = int(os.environ.get("BATCH_SIZE", "50"))

# --- Sections --------------------------------------------------------------
# Known sections are listed first for stable output order; any other section
# folder found on disk is appended (auto-discovery).
PREFERRED_SECTION_ORDER = ["instruments", "scales", "collections"]

# --- Vector store ----------------------------------------------------------
# Milvus is the default engine (external Standalone server, separate process).
# It is exact (FLAT index) so results match the numpy backend; if the server is
# unreachable, get_store() transparently falls back to numpy.
DEFAULT_VECTOR_BACKEND = "milvus"


def vector_backend() -> str:
    """Active backend, read live so tests/CLI can override via VECTOR_BACKEND."""
    return os.environ.get("VECTOR_BACKEND", DEFAULT_VECTOR_BACKEND).lower()


def milvus_uri() -> str:
    """Milvus endpoint. Default targets a local external Standalone server.

    A ``http://host:19530`` URL works on any OS (incl. Windows). A bare path
    (e.g. ``poem_milvus.db``) would select embedded Milvus Lite (Linux/macOS).
    """
    return os.environ.get("MILVUS_URI", "http://localhost:19530")


def milvus_collection() -> str:
    return os.environ.get("MILVUS_COLLECTION", "poem")


def milvus_token() -> str:
    return os.environ.get("MILVUS_TOKEN", "")
