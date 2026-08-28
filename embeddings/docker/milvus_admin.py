#!/usr/bin/env python3
"""Manage the POEM Milvus collections on any account (local Standalone or Zilliz Cloud).

One tool for the three account operations:
  * upload everything to a NEW account,
  * update an account after the .npy corpus changes,
  * switch which account you target.

Subcommands
-----------
  status   Show the target's collections + row counts vs the local corpus, plus a
           local corpus fingerprint. Read-only.
  push     (Re)build ALL metric collections on the target from the local .npy corpus
           (drop + recreate + insert). Idempotent. Use this to populate a new account
           or to refresh one after re-running generate_embeddings.py.
  drop     Drop the POEM collections on the target (cleanup / reset).

Target account
--------------
Selected by ``MILVUS_URI`` / ``MILVUS_TOKEN`` (env), overridable per-run with
``--uri`` / ``--token``. On a TLS-intercepting network also set
``GRPC_DEFAULT_SSL_ROOTS_FILE_PATH`` (see MILVUS.md §11).

Examples (PowerShell)
---------------------
  # Check the current account (env):
  python embeddings/docker/milvus_admin.py status

  # Upload the whole corpus to a NEW account:
  python embeddings/docker/milvus_admin.py push `
      --uri https://B-cluster.zillizcloud.com:19540 --token "db_admin:****"

  # After editing data + re-running generate_embeddings.py, refresh the current account:
  python embeddings/docker/milvus_admin.py push
"""
from __future__ import annotations

import argparse
import hashlib
import os
import sys

_HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.dirname(_HERE))          # embeddings/ root -> import poem_core
os.environ.setdefault("VECTOR_BACKEND", "milvus")

from poem_core import config
from poem_core.corpus import load_embeddings, discover_sections, read_manifest
from poem_core.docker_preflight import ensure_milvus_ready, is_local_uri
from poem_core.metrics import MILVUS_METRIC_TYPE
from poem_core.vector_store import MilvusVectorStore
from pymilvus import MilvusClient

# The three natively-indexed metrics (Manhattan/L1 has no Milvus collection).
METRIC_NAMES = ["Cosine Similarity", "Dot Product", "Euclidean (L2)"]


def _apply_target(args) -> None:
    """Let --uri/--token override the environment for this run."""
    if args.uri:
        os.environ["MILVUS_URI"] = args.uri
    if args.token is not None:
        os.environ["MILVUS_TOKEN"] = args.token
    if is_local_uri(config.milvus_uri()):
        ensure_milvus_ready()


def _client() -> MilvusClient:
    tok = config.milvus_token()
    return MilvusClient(uri=config.milvus_uri(), token=tok) if tok else MilvusClient(uri=config.milvus_uri())


def _metric_collections() -> dict[str, str]:
    base = config.milvus_collection()
    return {m: f"{base}_{MILVUS_METRIC_TYPE[m].lower()}" for m in METRIC_NAMES}


def corpus_fingerprint() -> str:
    """Stable short hash of the local corpus (over the content-hash manifests), so you
    can tell whether the data changed since a collection was last built."""
    h = hashlib.sha256()
    for section in discover_sections():
        for entry in read_manifest(os.path.join(config.EMBEDDINGS_DIR, section)):
            h.update(entry.get("hash", "").encode())
    return h.hexdigest()[:16]


def _count(c: MilvusClient, name: str) -> int:
    c.load_collection(name)
    res = c.query(collection_name=name, filter="id >= 0", output_fields=["count(*)"])
    return int(res[0]["count(*)"]) if res else 0


def cmd_status(args) -> int:
    _apply_target(args)
    emb, texts, secs = load_embeddings()
    print(f"target:       {config.milvus_uri()}  (token: {'set' if config.milvus_token() else 'none'})")
    print(f"local corpus: {len(texts)} vectors, dim {emb.shape[1]}, fingerprint {corpus_fingerprint()}")
    c = _client()
    all_ok = True
    for name in _metric_collections().values():
        if not c.has_collection(name):
            print(f"  {name:16s} MISSING")
            all_ok = False
            continue
        n = _count(c, name)
        ok = n == len(texts)
        all_ok &= ok
        print(f"  {name:16s} count={n}  {'OK' if ok else f'OUT-OF-SYNC (want {len(texts)})'}")
    print("STATUS:", "in sync" if all_ok else "needs `push`")
    return 0 if all_ok else 1


def cmd_push(args) -> int:
    _apply_target(args)
    emb, texts, secs = load_embeddings()
    print(f"pushing {len(texts)} vectors x {len(METRIC_NAMES)} metric collections "
          f"-> {config.milvus_uri()} ...")
    # rebuild=True forces drop+recreate+insert. __init__ rebuilds the Cosine
    # collection; touching the other metrics rebuilds theirs.
    store = MilvusVectorStore(emb, texts, secs, rebuild=True)
    for metric in METRIC_NAMES[1:]:
        store.top_candidates(emb[0], metric, None, k=1)
    print("done. verify with:  milvus_admin.py status")
    return 0


def cmd_drop(args) -> int:
    _apply_target(args)
    c = _client()
    for name in _metric_collections().values():
        if c.has_collection(name):
            c.drop_collection(name)
            print(f"  dropped {name}")
        else:
            print(f"  {name} (absent)")
    return 0


def main() -> int:
    parent = argparse.ArgumentParser(add_help=False)
    parent.add_argument("--uri", help="Milvus URI (overrides MILVUS_URI for this run).")
    parent.add_argument("--token", help="Milvus token (overrides MILVUS_TOKEN for this run).")

    p = argparse.ArgumentParser(description="Manage POEM Milvus collections on any account.")
    sub = p.add_subparsers(dest="cmd", required=True)
    sub.add_parser("status", parents=[parent], help="show target vs local corpus").set_defaults(func=cmd_status)
    sub.add_parser("push", parents=[parent], help="(re)build all collections from .npy").set_defaults(func=cmd_push)
    sub.add_parser("drop", parents=[parent], help="drop the POEM collections").set_defaults(func=cmd_drop)

    args = p.parse_args()
    return args.func(args)


if __name__ == "__main__":
    sys.exit(main())
