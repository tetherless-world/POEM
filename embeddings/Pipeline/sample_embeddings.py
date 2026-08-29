#!/usr/bin/env python3
"""Sample: verify the embedding endpoint is reachable and working.

Reads a few blocks from templates.txt and prints the embedding size for each.
Run this first to confirm connectivity before the full pipeline.

Usage:
    python sample_embeddings.py
"""
from __future__ import annotations

import os
import sys
import re

_EMB_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
if _EMB_ROOT not in sys.path:
    sys.path.insert(0, _EMB_ROOT)

from poem_core import config  # noqa: E402
from poem_core.embedding_client import embed_texts  # noqa: E402

# Default to templates.txt (TEMPLATES_OUTPUT); honor a TEMPLATES_PATH override.
TEMPLATES_FILE = os.environ.get("TEMPLATES_PATH", config.TEMPLATES_OUTPUT)

with open(TEMPLATES_FILE, encoding="utf-8") as f:
    raw = f.read()

all_blocks = [b.strip() for b in re.split(r"\n\n+", raw)]
all_blocks = [b for b in all_blocks if b and not b.startswith("===")]

# Take just the first 3 blocks as a sample.
texts = all_blocks[:3]

vectors = embed_texts(texts)
for i, vec in enumerate(vectors):
    print(f"Text {i} embedding length:", len(vec))
    print(f"  Preview: {texts[i][:80]}...")
    print()
    print(vec.tolist())
