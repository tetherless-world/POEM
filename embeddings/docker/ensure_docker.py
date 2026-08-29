#!/usr/bin/env python3
r"""Manual preflight: make sure Docker + the local Milvus stack are up and healthy.

Thin CLI wrapper around ``poem_core.docker_preflight.ensure_milvus_ready()`` --
the same self-healing check that ``vector_store.get_store()`` and the other
docker/ scripts (check_milvus.py, milvus_demo.py, milvus_admin.py) run
automatically. Run this on its own when you just want the stack up (e.g. from
a shortcut) without running a Milvus-touching script.

Usage (PowerShell):
    python embeddings/docker/ensure_docker.py
"""
import os
import sys

_HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.dirname(_HERE))          # embeddings/ root -> import poem_core

from poem_core.docker_preflight import ensure_milvus_ready

if __name__ == "__main__":
    sys.exit(0 if ensure_milvus_ready() else 1)
