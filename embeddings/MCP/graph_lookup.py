#!/usr/bin/env python3
"""RDF graph access for the POEM MCP server.

Encapsulates everything the MCP tools need from the POEM knowledge graph so
``mcp_server.py`` stays thin:

  * ``resolve_entity(entity, section)`` -- turn a search hit's entity string
    (the first line of its embedding template) into a stable ``(id, label)``
    pair, where ``id`` is the ``skos:notation`` where one exists.
  * ``get_statements(entity_id)`` -- given an id from a prior ``search`` result,
    return that entity's immediate outgoing relationships as a list of
    ``{property, value, value_id}`` dicts (mirrors the Wikidata MCP tool).

The graph is the *same* one the embedding templates were built from: this
module reuses ``load_graph()`` / ``readable_local_name()`` from the sibling
``Pipeline/generate_text_templates.py`` (which merges individualsFull.ttl, every
instrument/scale/collection TTL in the repo, the ontology, and POEM.rdf). So
every entity string the ``search`` tool can return is resolvable here.

Loading is lazy and warmed explicitly via ``ensure_loaded()`` so the caller can
control *when* the (chatty) load happens -- e.g. the server warms it at startup
under ``redirect_stdout(sys.stderr)`` to keep the stdio JSON-RPC stream clean.
"""
from __future__ import annotations

import os
import sys

# The shared core package lives one level up (embeddings/poem_core); make it
# importable whether this module is imported by the server, a test, or run
# standalone.
_HERE = os.path.dirname(os.path.abspath(__file__))
_EMB_ROOT = os.path.dirname(_HERE)  # embeddings/
if _EMB_ROOT not in sys.path:
    sys.path.insert(0, _EMB_ROOT)

# poem_core.config reads POEM_PROJECT_ROOT at import time to find the TTLs; point
# it at the repo root (embeddings -> POEM). The config default already resolves
# there, so this is belt-and-suspenders for unusual launch directories.
_REPO_ROOT = os.path.dirname(_EMB_ROOT)
os.environ.setdefault("POEM_PROJECT_ROOT", _REPO_ROOT)

from rdflib import Graph, URIRef, Literal, Namespace, RDF  # noqa: E402,F401
from rdflib.namespace import RDFS, SKOS, OWL  # noqa: E402

from poem_core.graph import load_graph  # noqa: E402
from poem_core.entities import readable_local_name  # noqa: E402

FHIR = Namespace("http://hl7.org/fhir/")
DCT = Namespace("http://purl.org/dc/terms/")
_SIO = "http://semanticscience.org/resource/"
# item -> item stem ("is referred to by"); used to render an instrument's
# member items (which carry no label of their own) as their stem text, matching
# how the embedding templates represent instrument members.
_HAS_STEM = URIRef(_SIO + "SIO_000253")

# Opaque predicates -> the same human-readable vocabulary the templates use, so
# get_statements output reads naturally. Keyed by full predicate URI string.
# Anything not here falls back to the predicate's own rdfs:label (from the
# ontology) or readable_local_name().
PREDICATE_OVERRIDES: dict[str, str] = {
    str(RDF.type):         "instance of",
    _SIO + "SIO_000059":   "has member",
    _SIO + "hasMember":    "has member",
    _SIO + "SIO_000008":   "has attribute",
    _SIO + "hasAttribute": "has attribute",
    str(SKOS.notation):    "notation",
    str(RDFS.label):     "label",
    str(FHIR.code):      "code",
}

# ---------------------------------------------------------------------------
# Lazy-loaded graph + reverse indexes (built once)
# ---------------------------------------------------------------------------
_G: Graph | None = None

# value-string -> IRI  (deterministic: smallest IRI string wins on collision)
_notation_to_iri: dict[str, URIRef] = {}
_code_to_iri: dict[str, URIRef] = {}
_label_to_iri: dict[str, URIRef] = {}
# IRI -> value-string
_iri_to_notation: dict[URIRef, str] = {}
_iri_to_code: dict[URIRef, str] = {}
_iri_to_label: dict[URIRef, str] = {}
# IRI -> enrichment (built once in ensure_loaded)
_iri_to_description: dict[URIRef, str] = {}
_iri_to_aliases: dict[URIRef, list[str]] = {}


def _index(forward: dict[str, URIRef], reverse: dict[URIRef, str], predicate) -> None:
    """Populate value<->IRI maps for one predicate over the whole graph."""
    assert _G is not None
    for subj, obj in _G.subject_objects(predicate):
        if not isinstance(subj, URIRef):
            continue
        val = str(obj)
        # Forward map: keep the lexicographically smallest IRI for determinism.
        existing = forward.get(val)
        if existing is None or str(subj) < str(existing):
            forward[val] = subj
        # Reverse map: first value seen for an IRI wins (stable across runs
        # because triple order within a value is irrelevant for our use).
        reverse.setdefault(subj, val)


def ensure_loaded() -> None:
    """Load the POEM graph and build reverse indexes (idempotent).

    Call this once up front (e.g. under redirect_stdout) to control when the
    chatty load happens; the lookup functions also call it on demand.
    """
    global _G
    if _G is not None:
        return
    _G = load_graph()
    _index(_notation_to_iri, _iri_to_notation, SKOS.notation)
    _index(_code_to_iri, _iri_to_code, FHIR.code)
    _index(_label_to_iri, _iri_to_label, RDFS.label)
    _index_enrichment()


def _index_enrichment() -> None:
    """Build IRI -> description / aliases maps for richer search results.

    description: first available of rdfs:comment / dc:description / skos:definition
                 (deterministic: smallest string wins on collision).
    aliases:     all skos:altLabel values, sorted, deduplicated.
    """
    assert _G is not None
    # (priority, value): lower priority number wins; smallest string breaks ties.
    best: dict[URIRef, tuple[int, str]] = {}
    for prio, pred in enumerate((RDFS.comment, DCT.description, SKOS.definition)):
        for subj, obj in _G.subject_objects(pred):
            if not isinstance(subj, URIRef):
                continue
            val = str(obj).strip()
            if not val:
                continue
            cur = best.get(subj)
            if cur is None or (prio, val) < cur:
                best[subj] = (prio, val)
    for subj, (_, val) in best.items():
        _iri_to_description[subj] = val

    aliases: dict[URIRef, set[str]] = {}
    for subj, obj in _G.subject_objects(SKOS.altLabel):
        if isinstance(subj, URIRef):
            aliases.setdefault(subj, set()).add(str(obj).strip())
    for subj, vals in aliases.items():
        _iri_to_aliases[subj] = sorted(v for v in vals if v)


# ---------------------------------------------------------------------------
# Entity resolution
# ---------------------------------------------------------------------------

def resolve_entity(entity: str, section: str | None = None) -> tuple[str, str]:
    """Map a search hit's entity string to a stable ``(id, label)``.

    ``id`` is the ``skos:notation`` where one exists; otherwise the fhir:code
    or, failing both, the entity string itself. The cascade matches the three
    section shapes:

      * instruments: notation == code == label == entity  -> matched by notation
      * scales:      entity is the rdfs:label ("Social Phobia (9.1)"); the id
                     is recovered as the notation ("SP")   -> matched by label
      * collections: no notation; id falls back to the label ("RCADS")
    """
    ensure_loaded()

    iri = _notation_to_iri.get(entity)
    if iri is not None:
        return entity, _iri_to_label.get(iri, entity)

    iri = _code_to_iri.get(entity)
    if iri is not None:
        return _iri_to_notation.get(iri, entity), _iri_to_label.get(iri, entity)

    iri = _label_to_iri.get(entity)
    if iri is not None:
        eid = _iri_to_notation.get(iri) or _iri_to_code.get(iri) or entity
        return eid, entity

    return entity, entity


def find_iri(entity_id: str) -> URIRef | None:
    """Resolve an id (as returned by ``search``) to a graph IRI, or None.

    Same cascade as resolve_entity: notation -> fhir:code -> rdfs:label.
    """
    ensure_loaded()
    return (
        _notation_to_iri.get(entity_id)
        or _code_to_iri.get(entity_id)
        or _label_to_iri.get(entity_id)
    )


def _readable_type(iri: URIRef) -> str | None:
    """A human-readable rdf:type for an entity.

    Skips owl:NamedIndividual (true of everything) and prefers a POEM-namespace
    class. Returns the type's rdfs:label if present, else its readable localname.
    """
    assert _G is not None
    types = [t for t in _G.objects(iri, RDF.type)
             if isinstance(t, URIRef) and t != OWL.NamedIndividual]
    if not types:
        return None
    types.sort(key=lambda t: (0 if "purl.org/twc/poem" in str(t) else 1, str(t)))
    chosen = types[0]
    return _iri_to_label.get(chosen) or readable_local_name(str(chosen))


def resolve_entity_rich(entity: str, section: str | None = None) -> dict:
    """Like ``resolve_entity`` but with extra graph-internal fields for the LLM.

    Returns ``{id, label, description, aliases, type}``: ``description`` from
    rdfs:comment / dc:description / skos:definition, ``aliases`` from
    skos:altLabel (≤3), ``type`` the readable rdf:type. Missing fields are None
    / ``[]`` so the shape is stable.
    """
    ensure_loaded()
    eid, label = resolve_entity(entity, section)
    iri = find_iri(entity) or find_iri(eid)
    if iri is None:
        return {"id": eid, "label": label, "description": None, "aliases": [], "type": None}
    return {
        "id": eid,
        "label": label,
        "description": _iri_to_description.get(iri),
        "aliases": _iri_to_aliases.get(iri, [])[:3],
        "type": _readable_type(iri),
    }


# ---------------------------------------------------------------------------
# Statements (immediate outgoing relationships)
# ---------------------------------------------------------------------------

def _predicate_label(predicate: URIRef) -> str:
    override = PREDICATE_OVERRIDES.get(str(predicate))
    if override is not None:
        return override
    return _iri_to_label.get(predicate) or readable_local_name(str(predicate))


def _object_to_value(obj) -> tuple[str, str | None]:
    """Render a triple object as ``(value, value_id)``.

    Literals -> (text, None). Entity IRIs -> (best label, chainable id) where
    the id is the object's notation/code if it has one (so the model can feed
    it back into get_statements), else None.
    """
    if isinstance(obj, URIRef):
        value = (
            _iri_to_label.get(obj)
            or _iri_to_notation.get(obj)
            or _iri_to_code.get(obj)
            or _stem_label(obj)
            or readable_local_name(str(obj))
        )
        value_id = _iri_to_notation.get(obj) or _iri_to_code.get(obj)
        return value, value_id
    # Literal or BNode
    return str(obj), None


def _stem_label(obj: URIRef) -> str | None:
    """Render an instrument's member item by dereferencing item -> stem label.

    Member items (``.../item/N``) carry no label of their own; the human text
    lives one hop away on the item stem. Returns the (deterministic) stem label
    or None if the object isn't an item-with-stem.
    """
    assert _G is not None
    labels = [
        str(lbl)
        for stem in _G.objects(obj, _HAS_STEM)
        for lbl in _G.objects(stem, RDFS.label)
    ]
    return min(labels) if labels else None


def get_statements(entity_id: str) -> list[dict]:
    """Return ``entity_id``'s immediate outgoing relationships from the graph.

    Each item is ``{"property": str, "value": str, "value_id": str | None}``,
    deduplicated and stably sorted. Raises ValueError if the id resolves to no
    entity.
    """
    iri = find_iri(entity_id)
    if iri is None:
        raise ValueError(f"No entity found for id {entity_id!r}")

    assert _G is not None
    seen: set[tuple[str, str, str | None]] = set()
    out: list[dict] = []
    for predicate, obj in _G.predicate_objects(iri):
        # owl:NamedIndividual is true of every individual -- pure noise.
        if predicate == RDF.type and obj == OWL.NamedIndividual:
            continue
        prop = _predicate_label(predicate)
        value, value_id = _object_to_value(obj)
        # Drop unresolved references: a bare-integer value with no chainable id
        # is an entity URI (e.g. .../item/16) that no loaded snapshot gave a
        # label/stem -- noise, never a meaningful relationship in this data.
        if value_id is None and value.isdigit():
            continue
        key = (prop, value, value_id)
        if key in seen:
            continue
        seen.add(key)
        out.append({"property": prop, "value": value, "value_id": value_id})

    out.sort(key=lambda d: (d["property"], d["value"]))
    return out
