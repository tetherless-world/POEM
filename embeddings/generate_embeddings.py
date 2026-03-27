#!/usr/bin/env python3
"""Generate embeddings for all template blocks and store them in ChromaDB.

Install dependencies first:
    pip install openai chromadb

Usage:
    python embeddings/generate_embeddings.py
"""

import os
import re

from openai import OpenAI
import chromadb

# ---------------------------------------------------------------------------
# Load all template blocks from templates.txt
# ---------------------------------------------------------------------------
_HERE = os.path.dirname(os.path.abspath(__file__))
TEMPLATES_PATH = os.path.join(_HERE, "templates_official.txt")

with open(TEMPLATES_PATH, encoding="utf-8") as f:
    raw = f.read()

all_blocks = [b.strip() for b in re.split(r"\n\n+", raw)]
texts = [b for b in all_blocks if b and not b.startswith("===")]

print(f"Loaded {len(texts)} template blocks from templates.txt")

# ---------------------------------------------------------------------------
# Call the embeddings endpoint
# ---------------------------------------------------------------------------
client = OpenAI(
    base_url="http://idea-llm-02.idea.rpi.edu:1234/v1",
    api_key="not-needed"
)

BATCH_SIZE = 50  # number of texts per API call — adjust if needed

all_embeddings = []
for i in range(0, len(texts), BATCH_SIZE):
    batch = texts[i : i + BATCH_SIZE]
    print(f"  Embedding batch {i // BATCH_SIZE + 1} ({len(batch)} texts)...")

    response = client.embeddings.create(
        model="qwen3-embedding:latest",
        input=batch
    )

    # Print embedding size for each item (same as sample script)
    for j, emb in enumerate(response.data):
        print(f"    Text {i + j} embedding length:", len(emb.embedding))
        all_embeddings.append(emb.embedding)

print(f"\nGenerated {len(all_embeddings)} embeddings total")

# ---------------------------------------------------------------------------
# Save embeddings to ChromaDB (local vector database)
# ---------------------------------------------------------------------------
DB_PATH = os.path.join(_HERE, "chroma_db")
COLLECTION_NAME = "poem_templates"

print(f"\nSaving to ChromaDB at: {DB_PATH}")

chroma = chromadb.PersistentClient(path=DB_PATH)
collection = chroma.get_or_create_collection(name=COLLECTION_NAME)

# upsert so re-running this script doesn't create duplicates
ids = [str(i) for i in range(len(texts))]
collection.upsert(
    ids=ids,
    documents=texts,
    embeddings=all_embeddings,
)

print(f"Saved {collection.count()} vectors to collection '{COLLECTION_NAME}'")
print(f"\nDone! To verify:")
print(f"  python -c \"import chromadb; c = chromadb.PersistentClient('{DB_PATH}'); col = c.get_collection('{COLLECTION_NAME}'); print(col.count(), 'vectors stored')\"")
