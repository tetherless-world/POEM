#!/usr/bin/env python3
"""Generate text templates for instruments, scales, and collections.

Output format:
  RCADS-25-Y-EN. Attributes include:
  - instance of: Psychometric Questionnaire
  - has member: I don't feel happy anymore
  - has attribute: Youth
  - has attribute: Social Phobia (9.1)

Usage:
    python scripts/generate_text_templates.py
    python scripts/generate_text_templates.py --output templates.txt
"""
from __future__ import annotations

import os
import re
import sys
import argparse
from collections import defaultdict

_EMB_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
if _EMB_ROOT not in sys.path:
    sys.path.insert(0, _EMB_ROOT)

from rdflib import Graph, Namespace, URIRef, BNode
from rdflib.namespace import RDF, RDFS, SKOS, OWL

from poem_core import config
from poem_core.entities import readable_local_name  # re-exported for graph_lookup
from poem_core.graph import load_graph              # re-exported; loader lives in core

# Repo paths come from the central config (honors POEM_PROJECT_ROOT / POEM_DATA_DIR).
PROJECT_ROOT = config.PROJECT_ROOT
DATA_DIR = config.DATA_DIR

POEM = Namespace("http://purl.org/twc/poem/")
FHIR_CODE = URIRef("http://hl7.org/fhir/code")

# CURIE prefixes accepted by --section NAME=prefix:Class
_PREFIXES = {
    "poem:":  "http://purl.org/twc/poem/",
    "sio:":   "http://semanticscience.org/resource/",
    "vstoi:": "http://purl.org/twc/vstoi/",
    "fhir:":  "http://hl7.org/fhir/",
    "skos:":  "http://www.w3.org/2004/02/skos/core#",
}


def expand_uri(s: str) -> URIRef:
    """Expand a CURIE (poem:Foo) or pass through a full URI as a URIRef."""
    for pfx, base in _PREFIXES.items():
        if s.startswith(pfx):
            return URIRef(base + s[len(pfx):])
    return URIRef(s)


# ---------------------------------------------------------------------------
# SPARQL queries — each uses OPTIONAL for labels so missing labels don't
# drop the row; Python falls back to readable_local_name() for any None values.
# All three share one PREFIX block (defined once) to avoid drift between them.
# ---------------------------------------------------------------------------

_SPARQL_PREFIXES = """\
PREFIX sio:   <http://semanticscience.org/resource/>
PREFIX rdf:   <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
PREFIX rdfs:  <http://www.w3.org/2000/01/rdf-schema#>
PREFIX skos:  <http://www.w3.org/2004/02/skos/core#>
PREFIX fhir:  <http://hl7.org/fhir/>
PREFIX dc:    <http://purl.org/dc/terms/>
PREFIX poem:  <http://purl.org/twc/poem/>
PREFIX vstoi: <http://purl.org/twc/vstoi/>
"""

INSTRUMENT_QUERY = _SPARQL_PREFIXES + """
SELECT ?instrument ?code ?predicate ?objectURI ?objectLabel
WHERE {
  ?instrument a poem:PsychometricQuestionnaire .
  ?instrument fhir:code ?code .

  {
    # instance of — OPTIONAL label, fall back to localname in Python
    ?instrument rdf:type ?objectURI .
    OPTIONAL { ?objectURI rdfs:label ?objectLabel }
    BIND("instance of" AS ?predicate)
  }
  UNION
  {
    # has member — item -> English stem -> label
    ?instrument sio:SIO_000059 ?item .
    ?item       sio:SIO_000253 ?stem .
    ?stem       dc:language    <http://www.fao.org/aims/aos/languagecode.owl#ENG> .
    ?stem       rdfs:label     ?objectLabel .
    BIND(?stem AS ?objectURI)
    BIND("has member" AS ?predicate)
  }
  UNION
  {
    # has attribute — informant (Youth, Caregiver, etc.)
    ?instrument sio:SIO_000008 ?objectURI .
    ?objectURI  a vstoi:Informant .
    OPTIONAL { ?objectURI rdfs:label ?objectLabel }
    BIND("has attribute" AS ?predicate)
  }
  UNION
  {
    # has attribute — scales (from scalesInstrument.ttl)
    ?instrument sio:SIO_000008 ?objectURI .
    ?objectURI  a poem:QuestionnaireScale .
    OPTIONAL { ?objectURI rdfs:label ?objectLabel }
    BIND("has attribute" AS ?predicate)
  }
}
ORDER BY ?code ?predicate ?objectLabel
"""

SCALE_QUERY = _SPARQL_PREFIXES + """
SELECT ?scale ?scaleLabel ?predicate ?objectURI ?objectLabel
WHERE {
  ?scale a poem:QuestionnaireScale .
  OPTIONAL { ?scale rdfs:label ?scaleLabel }

  {
    # instance of
    ?scale rdf:type ?objectURI .
    OPTIONAL { ?objectURI rdfs:label ?objectLabel }
    BIND("instance of" AS ?predicate)
  }
  UNION
  {
    # has member — item stem concepts
    ?scale sio:SIO_000059 ?objectURI .
    OPTIONAL { ?objectURI rdfs:label ?objectLabel }
    BIND("has member" AS ?predicate)
  }
  UNION
  {
    # has attribute — notation (SP, PD, GAD, etc.)
    ?scale skos:notation ?objectLabel .
    BIND(?scale AS ?objectURI)
    BIND("has attribute (notation)" AS ?predicate)
  }
}
ORDER BY ?scaleLabel ?predicate ?objectLabel
"""

COLLECTION_QUERY = _SPARQL_PREFIXES + """
SELECT ?collection ?collectionLabel ?predicate ?objectURI ?objectLabel
WHERE {
  ?collection a poem:InstrumentCollection .
  OPTIONAL { ?collection rdfs:label ?collectionLabel }

  {
    # instance of
    ?collection rdf:type ?objectURI .
    OPTIONAL { ?objectURI rdfs:label ?objectLabel }
    BIND("instance of" AS ?predicate)
  }
  UNION
  {
    # has member — instruments in this collection. The data links collection ->
    # instrument via SIO "has member" (sio:SIO_000059), not resource:hasMember.
    ?collection sio:SIO_000059 ?objectURI .
    OPTIONAL { ?objectURI fhir:code ?objectLabel }
    BIND("has member" AS ?predicate)
  }
}
ORDER BY ?collection ?predicate ?objectLabel
"""


def resolve_label(label, uri) -> str:
    """Return label if present, otherwise derive readable name from URI."""
    if label is not None:
        return str(label)
    if uri is not None:
        return readable_local_name(str(uri))
    return "(unknown)"


def format_template(identifier: str, data: dict) -> str:
    """Format one node's attribute data — one block per has member item."""
    header_preds = ["instance of"]
    attr_preds = ["has attribute", "has attribute (notation)"]

    header_lines = []
    for pred in header_preds:
        for value in sorted(set(data.get(pred, []))):
            header_lines.append(f"  - {pred}: {value}")

    attr_lines = []
    for pred in attr_preds:
        for value in sorted(set(data.get(pred, []))):
            attr_lines.append(f"  - {pred}: {value}")

    # Any remaining predicates not in the known sets
    known = set(header_preds + ["has member"] + attr_preds)
    for pred, values in data.items():
        if pred not in known:
            for value in sorted(set(values)):
                attr_lines.append(f"  - {pred}: {value}")

    members = sorted(set(data.get("has member", [])))

    if not members:
        lines = [f"{identifier}. Attributes include:"] + header_lines + attr_lines
        return "\n".join(lines)

    blocks = []
    for member in members:
        lines = (
            [f"{identifier}. Attributes include:"]
            + header_lines
            + [f"  - has member: {member}"]
            + attr_lines
        )
        blocks.append("\n".join(lines))
    return "\n\n".join(blocks)


def run_instruments(g: Graph) -> list:
    results = g.query(INSTRUMENT_QUERY)
    nodes = defaultdict(lambda: defaultdict(list))
    for row in results:
        code = str(row.code)
        pred = str(row.predicate)
        val  = resolve_label(row.objectLabel, row.objectURI)
        nodes[code][pred].append(val)
    return [format_template(code, data) for code, data in sorted(nodes.items())]


def run_scales(g: Graph) -> list:
    results = g.query(SCALE_QUERY)
    nodes = defaultdict(lambda: defaultdict(list))
    identifiers = {}
    for row in results:
        uri = str(row.scale)
        label = resolve_label(row.scaleLabel, row.scale)
        identifiers[uri] = label
        pred = str(row.predicate)
        val  = resolve_label(row.objectLabel, row.objectURI)
        nodes[uri][pred].append(val)
    return [format_template(identifiers[uri], data) for uri, data in sorted(nodes.items())]


# Map informant codes (as they appear inside instrument identifiers like
# "RCADS-25-Y-EN") to human-readable names. Used to enrich collection
# paragraphs without changing the .ttl ontology.
INFORMANT_NAMES = {
    "Y": "youth",
    "CG": "caregiver",
    "A": "adult",
    "T": "teacher",
}

# Map language codes that appear as suffixes in instrument identifiers to
# human-readable names. Unknown codes fall through as the raw code so the
# .ttl remains the source of truth for any new language added later.
LANGUAGE_NAMES = {
    "EN": "English", "ES": "Spanish", "FR": "French", "DE": "German",
    "ZH-HANS": "Chinese (Simplified)", "ZH-HANT": "Chinese (Traditional)",
    "CH-HANS": "Chinese (Simplified)",
    "JA": "Japanese", "KO": "Korean", "AR": "Arabic", "BN": "Bengali",
    "HI": "Hindi", "PA": "Punjabi", "MR": "Marathi", "MS": "Malay",
    "PT": "Portuguese", "PT-BR": "Brazilian Portuguese",
    "FR-CA": "Canadian French", "FR-FR": "European French",
    "RU": "Russian", "PL": "Polish", "NL": "Dutch", "FI": "Finnish",
    "ET": "Estonian", "EL": "Greek", "HU": "Hungarian", "IS": "Icelandic",
    "IT": "Italian", "LT": "Lithuanian", "NO": "Norwegian", "FA": "Persian",
    "SL": "Slovenian", "SV": "Swedish", "TR": "Turkish", "UR": "Urdu",
    "VI": "Vietnamese", "ZU": "Zulu", "NY": "Chichewa", "SR": "Serbian",
    "SW": "Swahili", "MN": "Mongolian", "DA": "Danish",
}

# Parses identifiers like RCADS-25-Y-EN, MTT-35-CG-EN-1, PHQ-9-A-EN,
# RCADS-47-CG-ZH-HANS, RCADS-47-Y-FR-CA-1.
_CODE_PATTERN = re.compile(
    r"^(?P<family>[A-Z]+(?:-\d+)?)"
    r"-(?P<informant>Y|CG|A|T)"
    r"-(?P<language>[A-Z]+(?:-[A-Z]+)?)"
    r"(?:-(?P<variant>\d+))?$"
)


def summarize_collection_members(members: list) -> list:
    """Synthesize descriptive attribute lines from a collection's member codes.

    Collections in the .ttl have no rdfs:label and no description — only
    membership links. Aggregating across member codes (which encode family,
    informant, and language) lets the embedded paragraph describe *what is
    in* the collection without modifying the ontology.
    """
    families = defaultdict(int)
    informants = set()
    languages = set()
    unparsed = 0

    for code in members:
        match = _CODE_PATTERN.match(code)
        if not match:
            unparsed += 1
            continue
        families[match.group("family")] += 1
        informants.add(match.group("informant"))
        languages.add(match.group("language"))

    lines = [f"  - has member count: {len(members)}"]

    if families:
        family_str = ", ".join(
            f"{fam} ({count})" for fam, count in sorted(families.items())
        )
        lines.append(f"  - has instrument family: {family_str}")

    if informants:
        readable = sorted(INFORMANT_NAMES.get(code, code) for code in informants)
        lines.append(f"  - has informant: {', '.join(readable)}")

    if languages:
        readable_langs = sorted(LANGUAGE_NAMES.get(code, code) for code in languages)
        lines.append(f"  - has language count: {len(languages)}")
        lines.append(f"  - has language: {', '.join(readable_langs)}")

    if unparsed:
        lines.append(f"  - has member with unrecognized code: {unparsed}")

    return lines


def run_collections(g: Graph) -> list:
    """Emit one rich paragraph per collection (not per member).

    The previous implementation emitted one paragraph per (collection,
    member) pair, producing ~300 nearly-identical low-information
    paragraphs that polluted unscoped semantic search. This version
    consolidates each collection into a single paragraph and uses
    summarize_collection_members() to inject descriptive attributes
    derived from the member identifiers.
    """
    results = g.query(COLLECTION_QUERY)
    nodes = defaultdict(lambda: defaultdict(list))
    identifiers = {}
    for row in results:
        uri = str(row.collection)
        label = resolve_label(row.collectionLabel, row.collection)
        identifiers[uri] = label
        pred = str(row.predicate)
        val  = resolve_label(row.objectLabel, row.objectURI)
        nodes[uri][pred].append(val)

    paragraphs = []
    for uri in sorted(nodes):
        data = nodes[uri]
        identifier = identifiers[uri]
        members = sorted(set(data.get("has member", [])))
        instance_of = sorted(set(data.get("instance of", [])))

        lines = [f"{identifier}. Attributes include:"]
        for io in instance_of:
            lines.append(f"  - instance of: {io}")
        lines.extend(summarize_collection_members(members))
        for m in members:
            lines.append(f"  - has member: {m}")

        paragraphs.append("\n".join(lines))

    return paragraphs


# ---------------------------------------------------------------------------
# Generic section runner — turns *any* class into a section without a curated
# SPARQL query. Used for ad-hoc sections (e.g. --section items=poem:Item) and
# for registry entries that specify a class but no dedicated runner.
# ---------------------------------------------------------------------------

def _node_identifier(g: Graph, subj: URIRef) -> str:
    """Best stable identifier for a node: notation -> fhir:code -> label -> localname."""
    for getter in (SKOS.notation, FHIR_CODE, RDFS.label):
        for obj in g.objects(subj, getter):
            return str(obj)
    return readable_local_name(str(subj))


def _readable_predicate(g: Graph, pred: URIRef) -> str:
    """rdfs:label of the predicate if the ontology defines one, else localname."""
    for obj in g.objects(pred, RDFS.label):
        return str(obj)
    return readable_local_name(str(pred))


def _object_label(g: Graph, obj) -> str | None:
    """Label for a triple object: literal text, an entity's rdfs:label, or None
    (so resolve_label falls back to readable_local_name for unlabeled URIs)."""
    if isinstance(obj, URIRef):
        for lbl in g.objects(obj, RDFS.label):
            return str(lbl)
        return None
    return str(obj)


def run_generic(g: Graph, class_uri: URIRef) -> list:
    """Emit one paragraph per *named individual* of ``class_uri``.

    Each outgoing predicate is rendered with its readable name and each object
    with its label/notation/localname. Blank nodes are skipped (no stable
    identity). This is the fallback that lets a brand-new section type work with
    no curated query.
    """
    nodes = defaultdict(lambda: defaultdict(list))
    identifiers = {}
    for subj in g.subjects(RDF.type, class_uri):
        if not isinstance(subj, URIRef):
            continue
        uri = str(subj)
        identifiers[uri] = _node_identifier(g, subj)
        for pred, obj in g.predicate_objects(subj):
            if pred == RDF.type and obj == OWL.NamedIndividual:
                continue
            if isinstance(obj, BNode):
                continue  # structural blank nodes carry no readable value
            pred_name = _readable_predicate(g, pred)
            val = resolve_label(_object_label(g, obj), obj)
            nodes[uri][pred_name].append(val)
    return [format_template(identifiers[uri], data) for uri, data in sorted(nodes.items())]


# ---------------------------------------------------------------------------
# Section registry — maps a section name to how its paragraphs are produced.
#   * curated sections provide a dedicated ``runner`` (tuned SPARQL).
#   * generic sections provide a ``class`` URI and use run_generic().
# Add a new section either here, or ad-hoc on the CLI: --section NAME=poem:Class
# ---------------------------------------------------------------------------
SECTION_REGISTRY: dict[str, dict] = {
    "instruments": {"runner": run_instruments},
    "scales":      {"runner": run_scales},
    "collections": {"runner": run_collections},
}


def build_section(g: Graph, entry: dict) -> list:
    runner = entry.get("runner")
    if runner is not None:
        return runner(g)
    return run_generic(g, entry["class"])


def main():
    default_out = os.environ.get(
        "TEMPLATES_OUTPUT",
        os.path.join(os.path.dirname(os.path.abspath(__file__)), "templates.txt"),
    )
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", default=default_out,
                        help="File to write templates to (default: embeddings/Pipeline/templates.txt)")
    parser.add_argument("--input", default=DATA_DIR,
                        help="Folder of instance TTLs to read (default: poem-demo/dist/data)")
    parser.add_argument("--only", default=None,
                        help="Comma-separated section name(s) to generate (default: all registered)")
    parser.add_argument("--section", action="append", default=[], metavar="NAME=CLASS",
                        help="Register an ad-hoc generic section, e.g. --section items=poem:Item "
                             "(repeatable). Renders one paragraph per named individual of CLASS.")
    args = parser.parse_args()

    # Merge ad-hoc --section entries into the registry for this run.
    registry = dict(SECTION_REGISTRY)
    for spec in args.section:
        name, sep, cls = spec.partition("=")
        if not sep or not cls.strip():
            parser.error(f"--section expects NAME=CLASS, got {spec!r}")
        registry[name.strip().lower()] = {"class": expand_uri(cls.strip())}

    wanted = ([s.strip().lower() for s in args.only.split(",")]
              if args.only else list(registry.keys()))

    print("Loading graph...")
    g = load_graph(args.input)

    sections = []
    for name in wanted:
        entry = registry.get(name)
        if entry is None:
            print(f"  Skipping unknown section {name!r} "
                  f"(register it with --section {name}=<class>)")
            continue
        print(f"Generating {name} templates...")
        blocks = build_section(g, entry)
        print(f"  {len(blocks)} {name}")
        sections.append(f"=== {name.upper()} ===\n\n" + "\n\n".join(blocks))

    output = "\n\n\n".join(sections)

    if args.output:
        with open(args.output, "w", encoding="utf-8") as f:
            f.write(output)
        print(f"\nWritten to {args.output}")
    else:
        print()
        print(output)


if __name__ == "__main__":
    main()
