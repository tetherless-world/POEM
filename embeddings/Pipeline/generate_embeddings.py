#!/usr/bin/env python3
"""Generate (or incrementally update) embeddings for all template blocks.

Each paragraph block is embedded and stored as one ``.npy`` vector in a section
subfolder, alongside:

  * ``texts.npy``      — the source text strings, index-aligned with the vectors.
  * ``manifest.json``  — an ordered list of ``{"hash", "file"}`` (one per text
                         row) mapping each paragraph to its vector file and the
                         sha256 of its text. This is what makes incremental
                         updates possible: on a later run, only blocks whose text
                         hash changed (or are new) are re-embedded; vectors for
                         unchanged blocks are kept, and files for removed blocks
                         are deleted.

Vector filenames are content-addressed: ``{slug}_{hash12}.npy``. Identical blocks
share a file and are embedded once.

Usage:
    # Full (re)build — embeds every block
    python generate_embeddings.py

    # Incremental — re-embed only changed/new blocks (needs a prior manifest)
    python generate_embeddings.py --incremental

    # One section only
    python generate_embeddings.py --only instruments
"""
from __future__ import annotations

import os
import sys

_EMB_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
if _EMB_ROOT not in sys.path:
    sys.path.insert(0, _EMB_ROOT)

import re  # noqa: E402
import glob  # noqa: E402
import hashlib  # noqa: E402
import argparse  # noqa: E402

import numpy as np  # noqa: E402

from poem_core import config  # noqa: E402
from poem_core.entities import entity_slug_from_text  # noqa: E402
from poem_core.embedding_client import embed_texts  # noqa: E402
from poem_core.corpus import read_manifest, write_manifest  # noqa: E402

TEMPLATES_PATH = config.TEMPLATES_PATH
EMBEDDINGS_DIR = config.EMBEDDINGS_DIR
BATCH_SIZE = config.BATCH_SIZE


# ---------------------------------------------------------------------------
# Template parsing
# ---------------------------------------------------------------------------

def parse_sections(text: str) -> dict:
    """Split the template file into named sections.

    Returns a dict mapping a slugified section name to a list of text blocks,
    e.g. {"instruments": [...], "scales": [...], "item_stems": [...]}.
    """
    header_pattern = re.compile(r"=== ([A-Z0-9][A-Z0-9 _-]*?) ===")
    headers = list(header_pattern.finditer(text))

    sections = {}
    for i, match in enumerate(headers):
        name = match.group(1).strip().lower().replace(" ", "_")
        start = match.end()
        end = headers[i + 1].start() if i + 1 < len(headers) else len(text)
        section_text = text[start:end]

        blocks = [b.strip() for b in re.split(r"\n\n+", section_text)]
        blocks = [b for b in blocks if b]
        sections[name] = blocks

    return sections


# ---------------------------------------------------------------------------
# Content addressing
# ---------------------------------------------------------------------------

def _text_hash(text: str) -> str:
    return hashlib.sha256(text.encode("utf-8")).hexdigest()


def _vector_filename(text: str, h: str) -> str:
    return f"{entity_slug_from_text(text)}_{h[:12]}.npy"


# ---------------------------------------------------------------------------
# Embedding
# ---------------------------------------------------------------------------

def update_section(
    section_name: str,
    texts: list[str],
    out_dir: str,
    incremental: bool,
) -> dict:
    """Embed (or reuse) every block in one section and persist vectors + manifest.

    Returns a stats dict: {embedded, reused, removed, total}.
    """
    os.makedirs(out_dir, exist_ok=True)

    prior = {e["hash"]: e["file"] for e in read_manifest(out_dir)} if incremental else {}

    # Build the index-aligned manifest and figure out which unique hashes still
    # need an embedding written to disk.
    entries: list[dict] = []
    to_embed: dict[str, str] = {}   # hash -> representative text (dedup identical blocks)
    reused = 0
    for text in texts:
        h = _text_hash(text)
        reuse_file = prior.get(h)
        if reuse_file and os.path.exists(os.path.join(out_dir, reuse_file)):
            entries.append({"hash": h, "file": reuse_file})
            reused += 1
            continue
        fname = _vector_filename(text, h)
        entries.append({"hash": h, "file": fname})
        # Only embed a given hash once; skip if its file already exists on disk.
        if h not in to_embed and not os.path.exists(os.path.join(out_dir, fname)):
            to_embed[h] = text

    # Embed the missing unique blocks in batches.
    pending = list(to_embed.items())
    file_by_hash = {e["hash"]: e["file"] for e in entries}
    embedded = 0
    for i in range(0, len(pending), BATCH_SIZE):
        chunk = pending[i:i + BATCH_SIZE]
        print(f"  [{section_name}] embedding batch {i // BATCH_SIZE + 1} ({len(chunk)} new texts)...")
        vecs = embed_texts([t for _, t in chunk])
        for (h, _), vec in zip(chunk, vecs):
            np.save(os.path.join(out_dir, file_by_hash[h]), vec)
            embedded += 1

    # Persist the texts index and manifest (index-aligned).
    np.save(os.path.join(out_dir, "texts.npy"), np.array(texts, dtype=object))
    write_manifest(out_dir, entries)

    # Delete stale vector files no longer referenced.
    keep = {e["file"] for e in entries} | {"texts.npy"}
    removed = 0
    for path in glob.glob(os.path.join(out_dir, "*.npy")):
        if os.path.basename(path) not in keep:
            os.remove(path)
            removed += 1

    return {"embedded": embedded, "reused": reused, "removed": removed, "total": len(texts)}


# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------

def main() -> None:
    parser = argparse.ArgumentParser(description="Generate or update POEM embeddings.")
    parser.add_argument("--templates", default=TEMPLATES_PATH,
                        help=f"Templates file to embed (default: {TEMPLATES_PATH})")
    parser.add_argument("--incremental", action="store_true",
                        help="Re-embed only new/changed blocks (uses each section's manifest.json)")
    parser.add_argument("--only", default=None,
                        help="Comma-separated section name(s) to embed (default: all in the file)")
    args = parser.parse_args()

    with open(args.templates, encoding="utf-8") as f:
        raw = f.read()
    sections = parse_sections(raw)

    if args.only:
        wanted = {s.strip().lower() for s in args.only.split(",")}
        sections = {k: v for k, v in sections.items() if k in wanted}

    for name, blocks in sections.items():
        print(f"Section '{name}': {len(blocks)} paragraphs")

    print(f"\nEmbedding backend: {config.EMBED_MODEL} @ {config.EMBED_BASE_URL}")
    print(f"Mode: {'incremental' if args.incremental else 'full rebuild'}\n")

    for section_name, texts in sections.items():
        out_dir = os.path.join(EMBEDDINGS_DIR, section_name)
        stats = update_section(section_name, texts, out_dir, args.incremental)
        print(f"[{section_name}] embedded {stats['embedded']}, reused {stats['reused']}, "
              f"removed {stats['removed']}  (total {stats['total']}) -> {out_dir}/")

    print("\nDone!")


if __name__ == "__main__":
    main()
