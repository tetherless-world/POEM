#!/usr/bin/env python3
"""Tests for the POEM MCP server tools.

Designed to run fully OFFLINE (no embedding server / RPI network needed):
  * graph_lookup / get_statements hit only the local RDF graph.
  * the search-shape test monkeypatches the embedding call.

Run with the MCP venv interpreter:
    o:\\POEM\\embeddings\\MCP\\.venv-mcp\\Scripts\\python.exe -m pytest \\
        o:\\POEM\\embeddings\\MCP\\test_mcp.py -v

A single live end-to-end test (real embedding call) is gated behind
POEM_TEST_NETWORK=1 and skipped by default.
"""
import os
import sys

import numpy as np
import pytest

# Make the MCP folder importable regardless of pytest's rootdir/cwd.
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import graph_lookup            # noqa: E402
import mcp_server as srv       # noqa: E402  (import loads corpus + graph once)

# Known fixtures from the POEM graph.
INSTRUMENT_ID = "RCADS-25-CG-EN"   # instrument: notation == code == label
SCALE_ID = "SP"                    # scale notation
SCALE_LABEL = "Social Phobia (9.1)"  # scale rdfs:label (the embedding entity)
COLLECTION_ID = "RCADS"            # collection: id falls back to rdfs:label


@pytest.fixture(scope="session", autouse=True)
def _graph():
    graph_lookup.ensure_loaded()


# ---------------------------------------------------------------------------
# Entity resolution
# ---------------------------------------------------------------------------

def test_find_iri_resolves_each_section():
    assert graph_lookup.find_iri(INSTRUMENT_ID) is not None
    assert graph_lookup.find_iri(SCALE_ID) is not None
    assert graph_lookup.find_iri(COLLECTION_ID) is not None


def test_find_iri_unknown_is_none():
    assert graph_lookup.find_iri("NO-SUCH-ID-123") is None


def test_resolve_instrument_id_equals_label():
    eid, label = graph_lookup.resolve_entity(INSTRUMENT_ID, "instruments")
    assert eid == INSTRUMENT_ID
    assert label == INSTRUMENT_ID


def test_resolve_scale_label_recovers_notation():
    # The scale's embedding entity is its rdfs:label; id must be the notation.
    eid, label = graph_lookup.resolve_entity(SCALE_LABEL, "scales")
    assert eid == SCALE_ID
    assert label == SCALE_LABEL


# ---------------------------------------------------------------------------
# get_statements
# ---------------------------------------------------------------------------

def test_get_statements_shape_and_keys():
    sts = graph_lookup.get_statements(INSTRUMENT_ID)
    assert isinstance(sts, list) and sts
    for s in sts:
        assert set(s) == {"property", "value", "value_id"}
        assert isinstance(s["property"], str) and s["property"]
        assert isinstance(s["value"], str) and s["value"]
        assert s["value_id"] is None or isinstance(s["value_id"], str)


def test_get_statements_instrument_has_type_members_and_chain():
    sts = graph_lookup.get_statements(INSTRUMENT_ID)
    props = {s["property"] for s in sts}
    assert "instance of" in props
    assert "has member" in props
    # At least one object is itself a graph entity (chainable id present).
    assert any(s["value_id"] for s in sts)
    # No bare unresolved item-id leaks through as a value.
    assert not any(s["value"].isdigit() for s in sts)


def test_get_statements_chained_value_id_resolves():
    sts = graph_lookup.get_statements(INSTRUMENT_ID)
    chain_ids = [s["value_id"] for s in sts if s["value_id"]]
    assert chain_ids
    # Feed a chainable id back in -- the described conversational flow.
    follow = graph_lookup.get_statements(chain_ids[0])
    assert isinstance(follow, list) and follow


def test_get_statements_scale_has_notation_and_label():
    sts = graph_lookup.get_statements(SCALE_ID)
    pairs = {(s["property"], s["value"]) for s in sts}
    assert ("notation", SCALE_ID) in pairs
    assert ("label", SCALE_LABEL) in pairs


def test_get_statements_unknown_raises():
    with pytest.raises(ValueError):
        graph_lookup.get_statements("NO-SUCH-ID-123")


# ---------------------------------------------------------------------------
# search tool (offline via a stubbed embedding call)
# ---------------------------------------------------------------------------

@pytest.fixture
def stub_embed(monkeypatch):
    """Replace the network embedding call with a fixed unit vector."""
    dim = srv._EMB.shape[1]
    vec = np.ones(dim, dtype=np.float32)
    monkeypatch.setattr(srv, "embed_query", lambda q: vec)
    return vec


def test_search_returns_enriched_shape(stub_embed):
    results = srv.search("anxiety in children", top_k=3)
    assert isinstance(results, list) and 1 <= len(results) <= 3
    for r in results:
        assert set(r) == {
            "id", "label", "section", "score",
            "type", "description", "aliases", "snippet",
        }
        assert r["section"] in srv.SECTIONS
        assert isinstance(r["score"], float)
        assert isinstance(r["id"], str) and r["id"]
        assert isinstance(r["label"], str) and r["label"]
        assert r["type"] is None or isinstance(r["type"], str)
        assert r["description"] is None or isinstance(r["description"], str)
        assert isinstance(r["aliases"], list)
        assert isinstance(r["snippet"], str)


def test_search_section_filter(stub_embed):
    results = srv.search("depression", top_k=5, section="scales")
    assert results
    assert all(r["section"] == "scales" for r in results)


def test_search_ids_are_chainable_into_get_statements(stub_embed):
    # The two-call flow: search -> take an id -> get_statements.
    results = srv.search("social phobia", top_k=3, section="scales")
    sts = srv.get_statements(results[0]["id"])
    assert isinstance(sts, list) and sts


def test_search_validates_arguments(stub_embed):
    with pytest.raises(ValueError):
        srv.search("x", metric="Nonexistent")
    with pytest.raises(ValueError):
        srv.search("x", section="nonexistent")
    with pytest.raises(ValueError):
        srv.search("x", top_k=0)


# ---------------------------------------------------------------------------
# Optional live end-to-end (real embedding server) -- skipped by default
# ---------------------------------------------------------------------------

@pytest.mark.skipif(
    os.environ.get("POEM_TEST_NETWORK") != "1",
    reason="set POEM_TEST_NETWORK=1 to run the live embedding-server test",
)
def test_search_live_then_statements():
    results = srv.search("caregiver report of child depression", top_k=3)
    assert results
    sts = srv.get_statements(results[0]["id"])
    assert sts


if __name__ == "__main__":
    sys.exit(pytest.main([__file__, "-v"]))
