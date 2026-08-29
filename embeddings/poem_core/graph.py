"""POEM RDF graph loader (shared infrastructure).

``load_graph`` and its file-discovery helpers used to live in
``generate_text_templates``; ``graph_lookup`` (the MCP server's graph access)
imported them from there via a cross-folder ``sys.path`` hack. They are pure
graph-loading infrastructure with no template-generation specifics, so they live
in the core and both the template generator and the MCP server import them here.

Loads the priority instance-data folder first (``poem-demo/dist/data``), then
merges every other data TTL in the repo (triple-level dedup), then layers the
ontology schema (``ontology/*.ttl`` and ``POEM.rdf``) on top.
"""
from __future__ import annotations

import os
import glob

from rdflib import Graph

from . import config

# Path fragments that must never be crawled for data TTLs (dependency/vcs dirs
# may ship unrelated .ttl fixtures).
_EXCLUDE_FRAGMENTS = (".venv", "site-packages", "node_modules", ".git", os.sep + "ontology" + os.sep)


def _load_data_dir(g: Graph, data_dir: str) -> None:
    """Load every TTL directly inside one input folder (the canonical data)."""
    for ttl_file in sorted(glob.glob(os.path.join(data_dir, "*.ttl"))):
        rel = os.path.relpath(ttl_file, config.PROJECT_ROOT)
        print(f"  Loading {rel}...")
        try:
            g.parse(ttl_file, format="turtle")
        except Exception as e:
            print(f"    Warning: could not load {rel}: {e}")


def _load_legacy(g: Graph, skip_dir: str | None = None) -> None:
    """Merge the rest of the repo's data TTLs so nothing is missing.

    Loads ``individualsFull.ttl`` plus every instrument/scale/collection TTL in
    the repo (excluding the ``rcads/`` mapping files and dependency dirs). RDF
    merges at the triple level, so files already loaded from the priority folder
    contribute no new triples. ``skip_dir`` (the priority folder) is skipped.
    """
    skip_abs = os.path.abspath(skip_dir) + os.sep if skip_dir else None

    full_path = os.path.join(config.PROJECT_ROOT, "individualsFull.ttl")
    if os.path.exists(full_path):
        print(f"  Loading {os.path.basename(full_path)}...")
        g.parse(full_path, format="turtle")

    KEYWORDS = ("collection", "instrument", "scale")
    for ttl_file in glob.glob(os.path.join(config.PROJECT_ROOT, "**", "*.ttl"), recursive=True):
        if any(frag in ttl_file for frag in _EXCLUDE_FRAGMENTS):
            continue
        if skip_abs and os.path.abspath(ttl_file).startswith(skip_abs):
            continue
        basename = os.path.basename(ttl_file).lower()
        in_rcads = os.sep + "rcads" + os.sep in ttl_file
        if any(kw in basename for kw in KEYWORDS) and not in_rcads:
            rel = os.path.relpath(ttl_file, config.PROJECT_ROOT)
            print(f"  Loading {rel}...")
            try:
                g.parse(ttl_file, format="turtle")
            except Exception as e:
                print(f"    Warning: could not load {rel}: {e}")


def load_graph(data_dir: str | None = None) -> Graph:
    """Load the POEM graph: priority folder first, then repo data, then schema.

    Args:
        data_dir: Priority folder of instance TTLs (default: ``config.DATA_DIR``,
            i.e. ``poem-demo/dist/data``).
    """
    g = Graph()

    data_dir = data_dir or config.DATA_DIR
    # 1. Priority folder, loaded first.
    if data_dir and os.path.isdir(data_dir):
        print(f"  Priority input folder: {os.path.relpath(data_dir, config.PROJECT_ROOT)}")
        _load_data_dir(g, data_dir)
    elif data_dir:
        print(f"  Priority input folder not found ({data_dir}); skipping to repo-wide load.")

    # 2. Everything else in the repo (triple-level dedup), skipping the priority folder.
    _load_legacy(g, skip_dir=data_dir if (data_dir and os.path.isdir(data_dir)) else None)

    # 3. Ontology files (OWL, PROV, RDF Schema) — schema, always layered on top.
    ontology_dir = os.path.join(config.PROJECT_ROOT, "ontology")
    for ttl_file in glob.glob(os.path.join(ontology_dir, "*.ttl")):
        print(f"  Loading ontology/{os.path.basename(ttl_file)}...")
        try:
            g.parse(ttl_file, format="turtle")
        except Exception as e:
            print(f"    Warning: could not load {ttl_file}: {e}")

    # 4. Main POEM ontology schema (class definitions).
    poem_rdf = os.path.join(config.PROJECT_ROOT, "POEM.rdf")
    if os.path.exists(poem_rdf):
        print(f"  Loading POEM.rdf...")
        try:
            g.parse(poem_rdf, format="xml")
        except Exception as e:
            print(f"    Warning: could not load POEM.rdf: {e}")

    print(f"  Total triples: {len(g)}\n")
    return g
