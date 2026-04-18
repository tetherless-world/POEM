#!/usr/bin/env python3
"""Generate embeddings for all template blocks and store them as numpy files.

One .npy file is saved per paragraph, organized into section subfolders:
    embeddings/instruments/paragraph_0.npy
    embeddings/instruments/paragraph_1.npy
    ...
    embeddings/scales/paragraph_0.npy
    ...
    embeddings/collections/paragraph_0.npy
    ...

A companion texts.npy is saved in each subfolder to map paragraph indices
back to their source text.

Install dependencies first:
    pip install openai numpy

Usage:
    python embeddings/generate_embeddings.py
"""

import os
import re

import numpy as np
from openai import OpenAI

# ---------------------------------------------------------------------------
# Load and parse templates_official.txt into sections
# ---------------------------------------------------------------------------
_HERE = os.path.dirname(os.path.abspath(__file__))
TEMPLATES_PATH = os.path.join(_HERE, "templates_official.txt")

with open(TEMPLATES_PATH, encoding="utf-8") as f:
    raw = f.read()


def parse_sections(text: str) -> dict:
    """Split the template file into named sections.

    Returns a dict mapping lowercase section name to a list of text blocks,
    e.g. {"instruments": [...], "scales": [...], "collections": [...]}.
    """
    # Find each === SECTION NAME === header and its position
    header_pattern = re.compile(r"=== ([A-Z]+) ===")
    headers = list(header_pattern.finditer(text))

    sections = {}
    for i, match in enumerate(headers):
        name = match.group(1).lower()
        start = match.end()
        end = headers[i + 1].start() if i + 1 < len(headers) else len(text)
        section_text = text[start:end]

        # Split into blocks and filter empty ones
        blocks = [b.strip() for b in re.split(r"\n\n+", section_text)]
        blocks = [b for b in blocks if b]
        sections[name] = blocks

    return sections


sections = parse_sections(raw)
for name, blocks in sections.items():
    print(f"Section '{name}': {len(blocks)} paragraphs")

# ---------------------------------------------------------------------------
# OpenAI-compatible embeddings client
# ---------------------------------------------------------------------------
client = OpenAI(
    base_url="http://idea-llm-02.idea.rpi.edu:1234/v1",
    api_key="not-needed"
)

BATCH_SIZE = 50  # number of texts per API call — adjust if needed

# ---------------------------------------------------------------------------
# Embed each section and save one .npy per paragraph
# ---------------------------------------------------------------------------
for section_name, texts in sections.items():
    out_dir = os.path.join(_HERE, section_name)
    os.makedirs(out_dir, exist_ok=True)

    # Save texts index so paragraph_N.npy can be mapped back to source text
    np.save(os.path.join(out_dir, "texts.npy"), np.array(texts, dtype=object))
    print(f"\n[{section_name}] Saved texts index ({len(texts)} entries)")

    idx = 0
    for i in range(0, len(texts), BATCH_SIZE):
        batch = texts[i:i + BATCH_SIZE]
        print(f"  Embedding batch {i // BATCH_SIZE + 1} ({len(batch)} texts)...")

        response = client.embeddings.create(
            model="qwen3-embedding:latest",
            input=batch
        )

        for emb in response.data:
            vec = np.array(emb.embedding, dtype=np.float32)
            out_path = os.path.join(out_dir, f"paragraph_{idx}.npy")
            np.save(out_path, vec)
            idx += 1

    print(f"  Saved {idx} paragraph files to {out_dir}/")

print("\nDone! To verify:")
print("  python -c \"import numpy as np; v = np.load('embeddings/instruments/paragraph_0.npy'); print(v.shape)\"")
