#!/usr/bin/env python3
"""Backward-compatible shim — the vector store now lives in ``poem_core``.

Kept so existing imports (``from vector_store import get_store``) keep resolving.
All logic — ``NumpyVectorStore``, the external-Milvus backend, and ``get_store``
— is implemented once in ``poem_core.vector_store``.
"""
from __future__ import annotations

import os as _os
import sys as _sys

_EMB_ROOT = _os.path.dirname(_os.path.dirname(_os.path.abspath(__file__)))
if _EMB_ROOT not in _sys.path:
    _sys.path.insert(0, _EMB_ROOT)

from poem_core.vector_store import (  # noqa: F401,E402
    NumpyVectorStore,
    MilvusVectorStore,
    get_store,
    _METRIC_TO_MILVUS,
)
