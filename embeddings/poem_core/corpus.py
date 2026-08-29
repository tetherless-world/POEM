"""Corpus on disk: section discovery, the manifest, and the vector loader.

Merges what used to be split between ``generate_embeddings`` (manifest
read/write) and ``search_similarity`` (``discover_sections`` + the inline
manifest loader inside ``load_embeddings``). One implementation now both writes
and reads the content-hash manifest, so the two can never drift.
"""
from __future__ import annotations

import os
import sys
import glob
import json

import numpy as np

from . import config

MANIFEST_NAME = "manifest.json"


# ---------------------------------------------------------------------------
# Manifest
# ---------------------------------------------------------------------------

def read_manifest(out_dir: str) -> list[dict]:
    """Return the ordered ``[{"hash", "file"}, ...]`` manifest, or ``[]``."""
    path = os.path.join(out_dir, MANIFEST_NAME)
    if not os.path.exists(path):
        return []
    with open(path, encoding="utf-8") as f:
        return json.load(f)


def write_manifest(out_dir: str, entries: list[dict]) -> None:
    path = os.path.join(out_dir, MANIFEST_NAME)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(entries, f, ensure_ascii=False, indent=0)


# ---------------------------------------------------------------------------
# Sections
# ---------------------------------------------------------------------------

def discover_sections(embeddings_dir: str | None = None) -> list[str]:
    """Return section names on disk (subfolders containing ``texts.npy``).

    Falls back to the preferred known sections if none are found yet (e.g.
    before embeddings are generated) so callers always get a usable list.
    """
    base = embeddings_dir or config.EMBEDDINGS_DIR
    found: list[str] = []
    if os.path.isdir(base):
        for name in sorted(os.listdir(base)):
            d = os.path.join(base, name)
            if os.path.isdir(d) and os.path.exists(os.path.join(d, "texts.npy")):
                found.append(name)
    preferred = config.PREFERRED_SECTION_ORDER
    ordered = [s for s in preferred if s in found]
    ordered += [s for s in found if s not in preferred]
    return ordered or list(preferred)


# Discovered once at import (disk does not change between import and use), so
# ``from search_similarity import SECTIONS`` stays a stable module constant.
SECTIONS = discover_sections()


# ---------------------------------------------------------------------------
# Vector loading
# ---------------------------------------------------------------------------

def load_embeddings(
    filter_sections: list[str] | None = None,
    embeddings_dir: str | None = None,
) -> tuple[np.ndarray, np.ndarray, np.ndarray]:
    """Load paragraph embeddings and source texts from disk.

    Args:
        filter_sections: section names to load (e.g. ``["instruments"]``); None
            loads all discovered sections.
        embeddings_dir: base directory (defaults to ``config.EMBEDDINGS_DIR``).

    Returns ``(embeddings (N,dim) float32, texts (N,) object, sections (N,) object)``.

    Supports the content-hash ``manifest.json`` scheme (preferred) and the older
    ``NNNN_slug.npy`` / ``paragraph_N.npy`` filename schemes (fallbacks).
    """
    base = embeddings_dir or config.EMBEDDINGS_DIR
    all_embeddings: list[np.ndarray] = []
    all_texts: list[np.ndarray] = []
    all_sections: list[np.ndarray] = []

    sections_to_load = filter_sections if filter_sections is not None else SECTIONS
    for section in sections_to_load:
        section_dir = os.path.join(base, section)
        if not os.path.isdir(section_dir):
            print(f"  Warning: section folder not found: {section_dir}")
            print(f"  Run generate_embeddings.py first to create embeddings.")
            continue

        texts_path = os.path.join(section_dir, "texts.npy")
        if not os.path.exists(texts_path):
            print(f"  Warning: texts.npy missing in {section_dir}")
            continue

        texts = np.load(texts_path, allow_pickle=True)

        # Preferred: manifest.json maps each text row (in order) to its vector.
        manifest_path = os.path.join(section_dir, MANIFEST_NAME)
        if os.path.exists(manifest_path):
            manifest = read_manifest(section_dir)
            vec_paths = [os.path.join(section_dir, e["file"]) for e in manifest]
            section_embeddings = np.stack([np.load(p) for p in vec_paths])
            n = min(len(vec_paths), len(texts))
            all_embeddings.append(section_embeddings[:n])
            all_texts.append(texts[:n])
            all_sections.append(np.array([section] * n, dtype=object))
            print(f"  Loaded {n} paragraphs from '{section}' (manifest)")
            continue

        # Fallback A: NNNN_entity-slug.npy
        para_files = sorted(
            glob.glob(os.path.join(section_dir, "????_*.npy")),
            key=lambda p: int(os.path.splitext(os.path.basename(p))[0].split("_")[0]),
        )
        # Fallback B: legacy paragraph_N.npy
        if not para_files:
            para_files = sorted(
                glob.glob(os.path.join(section_dir, "paragraph_*.npy")),
                key=lambda p: int(os.path.splitext(os.path.basename(p))[0].split("_")[1]),
            )
        if not para_files:
            print(f"  Warning: no paragraph files found in {section_dir}")
            continue

        section_embeddings = np.stack([np.load(p) for p in para_files])
        all_embeddings.append(section_embeddings)
        all_texts.append(texts[: len(para_files)])
        all_sections.append(np.array([section] * len(para_files), dtype=object))
        print(f"  Loaded {len(para_files)} paragraphs from '{section}'")

    if not all_embeddings:
        print("No embeddings found. Run generate_embeddings.py first.")
        sys.exit(1)

    embeddings = np.concatenate(all_embeddings, axis=0)
    texts = np.concatenate(all_texts, axis=0)
    sections = np.concatenate(all_sections, axis=0)
    return embeddings, texts, sections
