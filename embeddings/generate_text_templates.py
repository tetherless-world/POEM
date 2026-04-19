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

import os
import re
import sys
import argparse
import glob
from collections import defaultdict

PROJECT_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, PROJECT_ROOT)

from rdflib import Graph


def readable_local_name(uri: str) -> str:
    """Derive a human-readable label from a URI when no rdfs:label exists.

    Extracts everything after the last / or #, then:
    - Splits camelCase into words  (PsychometricQuestionnaire -> Psychometric Questionnaire)
    - Replaces underscores with spaces
    """
    local = uri.split("#")[-1] if "#" in uri else uri.rstrip("/").split("/")[-1]
    local = re.sub(r"([a-z])([A-Z])", r"\1 \2", local)  # camelCase split
    local = re.sub(r"([A-Z]+)([A-Z][a-z])", r"\1 \2", local)  # e.g. SIOCode -> SIO Code
    local = local.replace("_", " ")
    return local.strip()


def load_graph() -> Graph:
    """Load all relevant TTL files into a single rdflib graph."""
    g = Graph()

    # 1. Main data file (all individuals consolidated)
    full_path = os.path.join(PROJECT_ROOT, "individualsFull.ttl")
    if os.path.exists(full_path):
        print(f"  Loading {os.path.basename(full_path)}...")
        g.parse(full_path, format="turtle")

    # 2. All TTL files whose name contains "collection", "instrument", or "scale"
    #    — covers both individuals/ and poem-demo/dist/data/ variants.
    #    Excludes rcads/rml-* which are mapping files, not data.
    KEYWORDS = ("collection", "instrument", "scale")
    for ttl_file in glob.glob(os.path.join(PROJECT_ROOT, "**", "*.ttl"), recursive=True):
        basename = os.path.basename(ttl_file).lower()
        in_rcads = os.sep + "rcads" + os.sep in ttl_file
        if any(kw in basename for kw in KEYWORDS) and not in_rcads:
            rel = os.path.relpath(ttl_file, PROJECT_ROOT)
            print(f"  Loading {rel}...")
            try:
                g.parse(ttl_file, format="turtle")
            except Exception as e:
                print(f"    Warning: could not load {rel}: {e}")

    # 3. Ontology files (OWL, PROV, RDF Schema)
    ontology_dir = os.path.join(PROJECT_ROOT, "ontology")
    for ttl_file in glob.glob(os.path.join(ontology_dir, "*.ttl")):
        print(f"  Loading ontology/{os.path.basename(ttl_file)}...")
        try:
            g.parse(ttl_file, format="turtle")
        except Exception as e:
            print(f"    Warning: could not load {ttl_file}: {e}")

    # 4. Main POEM ontology schema (class definitions)
    poem_rdf = os.path.join(PROJECT_ROOT, "POEM.rdf")
    if os.path.exists(poem_rdf):
        print(f"  Loading POEM.rdf...")
        try:
            g.parse(poem_rdf, format="xml")
        except Exception as e:
            print(f"    Warning: could not load POEM.rdf: {e}")

    print(f"  Total triples: {len(g)}\n")
    return g


# ---------------------------------------------------------------------------
# SPARQL queries — each uses OPTIONAL for labels so missing labels don't
# drop the row; Python falls back to readable_local_name() for any None values
# ---------------------------------------------------------------------------

INSTRUMENT_QUERY = """
PREFIX sio:   <http://semanticscience.org/resource/>
PREFIX rdf:   <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
PREFIX rdfs:  <http://www.w3.org/2000/01/rdf-schema#>
PREFIX fhir:  <http://hl7.org/fhir/>
PREFIX dc:    <http://purl.org/dc/terms/>
PREFIX poem:  <http://purl.org/twc/poem/>
PREFIX vstoi: <http://purl.org/twc/vstoi/>

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

SCALE_QUERY = """
PREFIX sio:  <http://semanticscience.org/resource/>
PREFIX rdf:  <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
PREFIX skos: <http://www.w3.org/2004/02/skos/core#>
PREFIX poem: <http://purl.org/twc/poem/>

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

COLLECTION_QUERY = """
PREFIX sio:      <http://semanticscience.org/resource/>
PREFIX resource: <http://semanticscience.org/resource/>
PREFIX rdf:      <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
PREFIX rdfs:     <http://www.w3.org/2000/01/rdf-schema#>
PREFIX fhir:     <http://hl7.org/fhir/>
PREFIX poem:     <http://purl.org/twc/poem/>

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
    # has member — instruments in this collection
    ?collection resource:hasMember ?objectURI .
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


def run_collections(g: Graph) -> list:
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
    return [format_template(identifiers[uri], data) for uri, data in sorted(nodes.items())]


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", default=os.path.join(os.path.dirname(os.path.abspath(__file__)), "templates.txt"),
                        help="File to write templates to (default: embeddings/templates.txt)")
    parser.add_argument("--only", choices=["instruments", "scales", "collections"],
                        default=None, help="Generate templates for one node type only")
    args = parser.parse_args()

    print("Loading graph...")
    g = load_graph()

    sections = []

    if args.only in (None, "instruments"):
        print("Generating instrument templates...")
        instrument_templates = run_instruments(g)
        print(f"  {len(instrument_templates)} instruments")
        sections.append("=== INSTRUMENTS ===\n\n" + "\n\n".join(instrument_templates))

    if args.only in (None, "scales"):
        print("Generating scale templates...")
        scale_templates = run_scales(g)
        print(f"  {len(scale_templates)} scales")
        sections.append("=== SCALES ===\n\n" + "\n\n".join(scale_templates))

    if args.only in (None, "collections"):
        print("Generating collection templates...")
        collection_templates = run_collections(g)
        print(f"  {len(collection_templates)} collections")
        sections.append("=== COLLECTIONS ===\n\n" + "\n\n".join(collection_templates))

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
