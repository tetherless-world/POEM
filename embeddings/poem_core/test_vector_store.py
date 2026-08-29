#!/usr/bin/env python3
"""Intensive tests for poem_core/vector_store.py's backend-selection and
Milvus-unreachable-to-numpy fallback logic -- complements
test_docker_preflight.py by proving *this module's* wiring to the Docker
self-heal (local URI -> triggered; remote URI -> skipped) and its graceful
degradation when Milvus is genuinely unreachable (the "Docker isn't running"
scenario, one layer up from docker_preflight's own unit tests).

Fully OFFLINE and fast: MilvusVectorStore construction is mocked throughout,
so this never opens a real network connection or shells out to Docker.

Run with the MCP venv interpreter:
    embeddings\\MCP\\.venv-mcp\\Scripts\\python.exe -m pytest \\
        embeddings\\poem_core\\test_vector_store.py -v
"""
from __future__ import annotations

import numpy as np
import pytest

from poem_core import docker_preflight, vector_store


@pytest.fixture
def corpus():
    rng = np.random.default_rng(0)
    emb = rng.random((5, 8), dtype=np.float32)
    texts = np.array([f"text {i}" for i in range(5)], dtype=object)
    sections = np.array(["instruments"] * 5, dtype=object)
    return emb, texts, sections


@pytest.fixture(autouse=True)
def _clean_env(monkeypatch):
    monkeypatch.delenv("VECTOR_BACKEND", raising=False)
    monkeypatch.delenv("MILVUS_URI", raising=False)
    monkeypatch.delenv("MILVUS_SKIP_ENSURE", raising=False)


def _spy(return_value=True):
    def fn(*args, **kwargs):
        fn.calls += 1
        return return_value
    fn.calls = 0
    return fn


def test_get_store_numpy_when_backend_is_numpy(corpus, monkeypatch):
    monkeypatch.setenv("VECTOR_BACKEND", "numpy")
    ensure = _spy()
    monkeypatch.setattr(docker_preflight, "ensure_milvus_ready", ensure)
    store = vector_store.get_store(*corpus)
    assert isinstance(store, vector_store.NumpyVectorStore)
    assert ensure.calls == 0  # numpy path never even looks at Docker/Milvus


def test_get_store_falls_back_to_numpy_on_milvus_connection_error(corpus, monkeypatch, capsys):
    monkeypatch.setenv("VECTOR_BACKEND", "milvus")
    monkeypatch.setenv("MILVUS_URI", "http://milvus.example.invalid:19530")  # non-local

    def boom(*args, **kwargs):
        raise ConnectionError("could not connect to Milvus")

    monkeypatch.setattr(vector_store, "MilvusVectorStore", boom)
    store = vector_store.get_store(*corpus)
    assert isinstance(store, vector_store.NumpyVectorStore)
    assert "falling back to numpy" in capsys.readouterr().err


def test_get_store_falls_back_to_numpy_when_pymilvus_missing(corpus, monkeypatch, capsys):
    monkeypatch.setenv("VECTOR_BACKEND", "milvus")
    monkeypatch.setenv("MILVUS_URI", "http://milvus.example.invalid:19530")

    def boom(*args, **kwargs):
        raise ImportError("No module named 'pymilvus'")

    monkeypatch.setattr(vector_store, "MilvusVectorStore", boom)
    store = vector_store.get_store(*corpus)
    assert isinstance(store, vector_store.NumpyVectorStore)
    assert "ImportError" in capsys.readouterr().err


def test_get_store_triggers_docker_preflight_for_local_uri(corpus, monkeypatch):
    monkeypatch.setenv("VECTOR_BACKEND", "milvus")
    monkeypatch.setenv("MILVUS_URI", "http://localhost:19530")
    ensure = _spy(return_value=True)
    monkeypatch.setattr(docker_preflight, "ensure_milvus_ready", ensure)
    monkeypatch.setattr(vector_store, "MilvusVectorStore", lambda *a, **kw: object())
    vector_store.get_store(*corpus)
    assert ensure.calls == 1


def test_get_store_skips_docker_preflight_for_remote_uri(corpus, monkeypatch):
    monkeypatch.setenv("VECTOR_BACKEND", "milvus")
    monkeypatch.setenv("MILVUS_URI", "https://in03-abc.serverless.gcp-us-west1.cloud.zilliz.com:443")
    ensure = _spy(return_value=True)
    monkeypatch.setattr(docker_preflight, "ensure_milvus_ready", ensure)
    monkeypatch.setattr(vector_store, "MilvusVectorStore", lambda *a, **kw: object())
    vector_store.get_store(*corpus)
    assert ensure.calls == 0


def test_get_store_docker_never_comes_up_still_falls_back_to_numpy(corpus, monkeypatch, capsys):
    """The end-to-end "Docker really isn't running" scenario at this layer:
    ensure_milvus_ready gives up (False, e.g. Docker Desktop never started),
    but get_store still attempts the real connection per its own
    "best-effort" contract -- which then also fails -- and the overall result
    is still a clean numpy fallback, not a crash."""
    monkeypatch.setenv("VECTOR_BACKEND", "milvus")
    monkeypatch.setenv("MILVUS_URI", "http://localhost:19530")
    monkeypatch.setattr(docker_preflight, "ensure_milvus_ready", _spy(return_value=False))

    def boom(*args, **kwargs):
        raise ConnectionRefusedError("Milvus still unreachable")

    monkeypatch.setattr(vector_store, "MilvusVectorStore", boom)
    store = vector_store.get_store(*corpus)
    assert isinstance(store, vector_store.NumpyVectorStore)
    assert "falling back to numpy" in capsys.readouterr().err


if __name__ == "__main__":
    import sys
    sys.exit(pytest.main([__file__, "-v"]))
