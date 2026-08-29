#!/usr/bin/env python3
r"""Actively verify the Milvus vector backend end-to-end (no embedding endpoint needed).

Reads MILVUS_URI / MILVUS_TOKEN from the environment and forces VECTOR_BACKEND=milvus.
Confirms, in one run:
  1. the real ``MilvusVectorStore`` is selected (not the silent numpy fallback);
  2. the per-metric collections exist with the full row count (via ``count(*)``);
  3. Milvus top-k matches the numpy ground truth for every native metric
     (a stored vector is used as the query, so no RPI / embedding server is needed);
  4. a second ``get_store()`` **reuses** the collections (0 drops / 0 inserts).

Usage (PowerShell):
    $env:VECTOR_BACKEND="milvus"
    $env:MILVUS_URI="http://localhost:19530"
    $env:MILVUS_TOKEN=""
    python embeddings/docker/check_milvus.py
"""
import os
import sys

_HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.dirname(_HERE))          # embeddings/ root -> import poem_core
os.environ.setdefault("VECTOR_BACKEND", "milvus")

import numpy as np
from poem_core import config
from poem_core.corpus import load_embeddings
from poem_core.docker_preflight import ensure_milvus_ready, is_local_uri
from poem_core.vector_store import get_store, MilvusVectorStore, NumpyVectorStore
from pymilvus import MilvusClient


def _client():
    tok = config.milvus_token()
    return MilvusClient(uri=config.milvus_uri(), token=tok) if tok else MilvusClient(uri=config.milvus_uri())


def main() -> int:
    print(f"URI: {config.milvus_uri()} | token: {'set' if config.milvus_token() else 'none'} "
          f"| backend: {config.vector_backend()}")
    if is_local_uri(config.milvus_uri()):
        ensure_milvus_ready()
    emb, texts, secs = load_embeddings()
    print(f"corpus: {emb.shape}")

    store = get_store(emb, texts, secs)
    print("backend selected:", type(store).__name__)
    if not isinstance(store, MilvusVectorStore):
        print("FAIL: fell back to numpy (server unreachable). See warning above.")
        return 1

    ok = True

    # (3) parity vs numpy on a stored-vector query
    nps = NumpyVectorStore(emb, texts, secs)
    q = emb[0]
    for m in ["Cosine Similarity", "Dot Product", "Euclidean (L2)"]:
        ms, mt, _ = store.top_candidates(q, m, None, k=5)
        ns, nt, _ = nps.top_candidates(q, m, None, k=5)
        if len(mt) == 0:
            print(f"  parity {m:18s} match=FAIL (Milvus returned 0 hits)")
            ok = False
            continue
        match = str(mt[0]) == str(nt[int(np.argsort(ns)[::-1][0])])
        ok &= match
        print(f"  parity {m:18s} match={match}")

    # (2) collections + true count
    c = _client()
    base = config.milvus_collection()
    for name in (f"{base}_cosine", f"{base}_ip", f"{base}_l2"):
        if not c.has_collection(name):
            print(f"  collection {name}: MISSING")
            ok = False
            continue
        c.load_collection(name)
        res = c.query(collection_name=name, filter="id >= 0", output_fields=["count(*)"])
        n = int(res[0]["count(*)"]) if res else 0
        print(f"  collection {name}: count(*)={n} (expected {len(texts)})")
        ok &= (n == len(texts))

    # (4) reuse: a second store must not drop or insert
    drops, ins = [], []
    _od, _oi = MilvusClient.drop_collection, MilvusClient.insert
    MilvusClient.drop_collection = lambda self, *a, **k: (drops.append(1), _od(self, *a, **k))[1]
    MilvusClient.insert = lambda self, *a, **k: (ins.append(1), _oi(self, *a, **k))[1]
    try:
        s2 = MilvusVectorStore(emb, texts, secs)
        for m in ["Cosine Similarity", "Dot Product", "Euclidean (L2)"]:
            s2.top_candidates(q, m, None, k=3)
    finally:
        MilvusClient.drop_collection, MilvusClient.insert = _od, _oi
    reuse_ok = not drops and not ins
    print(f"  reuse: drops={len(drops)} inserts={len(ins)} -> {'OK' if reuse_ok else 'REBUILT'}")
    ok &= reuse_ok

    print("\nRESULT:", "PASS" if ok else "FAIL")
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())


'''
docker compose -f embeddings/docker/milvus-compose.yml up -d
python embeddings/docker/check_milvus.py 
'''
