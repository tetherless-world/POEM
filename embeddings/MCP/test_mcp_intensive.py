#!/usr/bin/env python3
"""Intensive edge-case tests for the MCP server, complementing test_mcp.py:

  * A wide matrix of nonexistent/malformed entity ids passed to the actual
    `get_statements` tool (not just graph_lookup directly).
  * Search argument edge cases beyond the basic validation already covered.
  * The MCP_TRANSPORT resolution logic (stdio/http/unknown), including one
    real subprocess black-box check of the __main__ entrypoint.
  * The /health liveness route, exercised in-process over the real ASGI app
    (no network port bound).
  * The startup failure paths added for container deployments: the 0-triples
    graph guard and the generic-exception-exits-cleanly contract.

Designed to run fully OFFLINE (no embedding server needed): the search tests
monkeypatch the embedding call, exactly like test_mcp.py.

Run with the MCP venv interpreter:
    embeddings\\MCP\\.venv-mcp\\Scripts\\python.exe -m pytest \\
        embeddings\\MCP\\test_mcp_intensive.py -v
"""
from __future__ import annotations

import os
import subprocess
import sys

import numpy as np
import pytest

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import graph_lookup            # noqa: E402
import mcp_server as srv       # noqa: E402


@pytest.fixture
def stub_embed(monkeypatch):
    """Replace the network embedding call with a fixed unit vector (same
    convention as test_mcp.py)."""
    dim = srv._EMB.shape[1]
    vec = np.ones(dim, dtype=np.float32)
    monkeypatch.setattr(srv, "embed_query", lambda q: vec)
    return vec


# ---------------------------------------------------------------------------
# get_statements -- a wide matrix of ids that don't (or shouldn't) resolve.
# Every one of these must raise ValueError specifically -- not TypeError,
# AttributeError, KeyError, or anything else that would surface as a raw
# traceback in a real client instead of a clean tool error.
# ---------------------------------------------------------------------------

NONEXISTENT_IDS = [
    "",
    "   ",
    "NO-SUCH-ID-999",
    "rcads-25-cg-en",          # real id, wrong case -- resolution is case-sensitive
    "RCADS_25_CG_EN",          # underscores instead of hyphens
    "RCADS-25-CG-ÉN",          # unicode lookalike
    "x" * 500,                 # very long garbage
    "../../etc/passwd",        # path-traversal-shaped
    "'; DROP TABLE instruments; --",  # injection-shaped
    "12345",                   # plausible-looking numeric id
    "<script>alert(1)</script>",
    "RCADS-25-CG-EN ",         # trailing whitespace on an otherwise-real id
    " RCADS-25-CG-EN",         # leading whitespace on an otherwise-real id
]


@pytest.mark.parametrize("bad_id", NONEXISTENT_IDS)
def test_get_statements_rejects_nonexistent_ids(bad_id):
    with pytest.raises(ValueError):
        srv.get_statements(bad_id)


def test_get_statements_rejects_none():
    with pytest.raises(ValueError):
        srv.get_statements(None)  # type: ignore[arg-type]


def test_get_statements_error_message_names_the_bad_id():
    bad_id = "TOTALLY-MADE-UP-ID"
    with pytest.raises(ValueError, match=r"TOTALLY-MADE-UP-ID"):
        srv.get_statements(bad_id)


def test_get_statements_still_works_for_a_real_id_after_failures():
    """Guards against a stateful bug where a failed lookup corrupts the
    reverse indexes for subsequent (valid) lookups."""
    with pytest.raises(ValueError):
        srv.get_statements("NO-SUCH-ID")
    sts = srv.get_statements("RCADS-25-CG-EN")
    assert isinstance(sts, list) and sts


# ---------------------------------------------------------------------------
# search -- argument edge cases beyond test_mcp.py's basic validation.
# ---------------------------------------------------------------------------

@pytest.mark.parametrize("bad_top_k", [-1, 0, -1000000])
def test_search_rejects_non_positive_top_k(stub_embed, bad_top_k):
    with pytest.raises(ValueError):
        srv.search("anxiety", top_k=bad_top_k)


def test_search_rejects_wrong_case_section(stub_embed):
    with pytest.raises(ValueError):
        srv.search("anxiety", section="Instruments")  # real value is lowercase


def test_search_rejects_wrong_case_metric(stub_embed):
    with pytest.raises(ValueError):
        srv.search("anxiety", metric="cosine similarity")  # real value is title-case


def test_search_rejects_empty_string_section(stub_embed):
    with pytest.raises(ValueError):
        srv.search("anxiety", section="")


def test_search_handles_empty_query_without_crashing(stub_embed):
    results = srv.search("", top_k=3)
    assert isinstance(results, list) and len(results) <= 3


def test_search_handles_whitespace_only_query(stub_embed):
    results = srv.search("   ", top_k=3)
    assert isinstance(results, list) and len(results) <= 3


def test_search_handles_unicode_query(stub_embed):
    results = srv.search("不安障害の質問票", top_k=3)
    assert isinstance(results, list) and len(results) <= 3


def test_search_top_k_larger_than_corpus_does_not_crash(stub_embed):
    huge = 5000  # corpus is 778 vectors total
    results = srv.search("anxiety", top_k=huge)
    assert isinstance(results, list)
    assert 0 < len(results) <= huge


# ---------------------------------------------------------------------------
# _resolve_transport -- MCP_TRANSPORT/MCP_HOST/MCP_PORT validation.
# ---------------------------------------------------------------------------

@pytest.fixture(autouse=True)
def _clean_transport_env(monkeypatch):
    for var in ("MCP_TRANSPORT", "MCP_HOST", "MCP_PORT"):
        monkeypatch.delenv(var, raising=False)


def test_resolve_transport_default_is_stdio():
    assert srv._resolve_transport() == ("stdio", "127.0.0.1", 8100)


def test_resolve_transport_http(monkeypatch):
    monkeypatch.setenv("MCP_TRANSPORT", "http")
    assert srv._resolve_transport() == ("http", "127.0.0.1", 8100)


def test_resolve_transport_case_insensitive(monkeypatch):
    monkeypatch.setenv("MCP_TRANSPORT", "HTTP")
    transport, _, _ = srv._resolve_transport()
    assert transport == "http"


def test_resolve_transport_custom_host_and_port(monkeypatch):
    monkeypatch.setenv("MCP_TRANSPORT", "http")
    monkeypatch.setenv("MCP_HOST", "0.0.0.0")
    monkeypatch.setenv("MCP_PORT", "9999")
    assert srv._resolve_transport() == ("http", "0.0.0.0", 9999)


def test_resolve_transport_unknown_value_raises(monkeypatch):
    monkeypatch.setenv("MCP_TRANSPORT", "carrier-pigeon")
    with pytest.raises(ValueError, match="carrier-pigeon"):
        srv._resolve_transport()


@pytest.mark.slow
def test_mcp_server_subprocess_exits_cleanly_on_unknown_transport():
    """Black-box proof that the __main__ entrypoint actually surfaces
    _resolve_transport()'s ValueError as a clean non-zero exit, not just that
    the pure function raises in-process (see the unit tests above). Spawns a
    real subprocess (full corpus + graph load) -- see TESTING.md "Fast vs.
    full test runs" to skip this in a quick dev loop."""
    env = os.environ.copy()
    env["VECTOR_BACKEND"] = "numpy"
    env["MCP_TRANSPORT"] = "carrier-pigeon"
    script = os.path.join(os.path.dirname(os.path.abspath(__file__)), "mcp_server.py")
    result = subprocess.run(
        [sys.executable, script], env=env, capture_output=True, text=True, timeout=90,
    )
    assert result.returncode != 0
    assert "Unknown MCP_TRANSPORT" in result.stderr
    assert "carrier-pigeon" in result.stderr


# ---------------------------------------------------------------------------
# /health -- exercised in-process over the real ASGI app, no port bound.
# ---------------------------------------------------------------------------

def test_health_route_reports_ok_and_real_counts():
    from starlette.testclient import TestClient

    app = srv.mcp.http_app()
    with TestClient(app) as client:
        resp = client.get("/health")
    assert resp.status_code == 200
    body = resp.json()
    assert body["status"] == "ok"
    assert body["paragraphs"] == len(srv._TEXTS)
    assert body["graph_triples"] == len(graph_lookup._G)
    assert body["vector_backend"] in ("NumpyVectorStore", "MilvusVectorStore")


# ---------------------------------------------------------------------------
# _startup() failure paths -- the container-deployment guards.
#
# These intentionally corrupt module globals via mocked loaders, so each test
# snapshots and restores srv._EMB/_TEXTS/_SECS/_STORE itself (monkeypatch only
# undoes attribute swaps *it* performed, not reassignments _startup() makes to
# module globals internally) -- otherwise a failure here would silently break
# every other test in this file and in test_mcp.py that runs afterward in the
# same process.
# ---------------------------------------------------------------------------

@pytest.fixture
def _preserve_server_state():
    saved = (srv._EMB, srv._TEXTS, srv._SECS, srv._STORE)
    saved_graph = graph_lookup._G
    yield
    srv._EMB, srv._TEXTS, srv._SECS, srv._STORE = saved
    graph_lookup._G = saved_graph


def test_startup_zero_triples_is_a_fatal_error(_preserve_server_state, monkeypatch, capsys):
    real_emb, real_texts, real_secs = srv._EMB, srv._TEXTS, srv._SECS
    monkeypatch.setattr(srv, "load_embeddings", lambda: (real_emb, real_texts, real_secs))
    monkeypatch.setattr(graph_lookup, "ensure_loaded", lambda: None)
    monkeypatch.setattr(graph_lookup, "_G", [])  # simulate an empty/never-loaded graph

    with pytest.raises(SystemExit) as exc_info:
        srv._startup()
    assert exc_info.value.code == 1
    err = capsys.readouterr().err
    assert "0 triples" in err
    assert "FATAL" in err


def test_startup_generic_exception_exits_cleanly_not_a_traceback_escape(
    _preserve_server_state, monkeypatch, capsys,
):
    def boom():
        raise OSError("disk full")

    monkeypatch.setattr(srv, "load_embeddings", boom)

    with pytest.raises(SystemExit) as exc_info:
        srv._startup()
    assert exc_info.value.code == 1
    err = capsys.readouterr().err
    assert "FATAL" in err
    assert "disk full" in err


def test_startup_succeeds_again_after_a_prior_failure(_preserve_server_state):
    """Confirms _startup() is safely re-runnable: a failing call (mocked
    load_embeddings, scoped to the `with` block below) doesn't leave anything
    behind that breaks a subsequent real call with the real loaders."""
    with pytest.MonkeyPatch.context() as mp:
        mp.setattr(srv, "load_embeddings", lambda: (_ for _ in ()).throw(OSError("boom")))
        with pytest.raises(SystemExit):
            srv._startup()

    srv._startup()  # real loaders again, outside the `with` block
    assert srv._TEXTS is not None and len(srv._TEXTS) > 0
    assert len(graph_lookup._G) > 0


if __name__ == "__main__":
    sys.exit(pytest.main([__file__, "-v"]))
