#!/usr/bin/env python3
"""Evaluate POEM embedding search quality against a dataset of test queries.

Each query is tagged with an *expected section* — the type of result that
should dominate the top results:

  * ``"instruments"`` — queries like "English instrument", "depression instrument"
    should surface full questionnaires/instruments.
  * ``"scales"`` — queries like "Anxiety", "Depression", "Social phobia" should
    surface the subscales that measure that construct.
  * ``None`` — no expectation; section breakdown is still reported.

For each query the script:
  1. Retrieves top-k raw results (default: 20).
  2. Deduplicates by entity so the same instrument/scale does not fill all slots.
  3. Reports the top unique entities (default: 5).
  4. Computes a match% — what fraction of the top unique results belong to the
     expected section.

A summary table at the end groups queries by expected section and shows the
average match% for each group, making it easy to see whether the search is
doing what it is supposed to.

By default each query is searched only within its expected section (section-scoped
mode).  This eliminates cross-section competition so instrument queries only rank
against other instruments and scale queries only rank against other scales.
Use ``--no-scope`` to search all sections together (the original behaviour).

Usage:
    # Section-scoped evaluation (default)
    python embeddings/evaluate_search.py

    # All-sections evaluation (original behaviour)
    python embeddings/evaluate_search.py --no-scope

    # Adjust result counts
    python embeddings/evaluate_search.py --top-k-search 30 --top-k-unique 10

Output is printed to stdout and saved to:
    embeddings/evaluation_results/evaluation_{timestamp}.txt
"""
from __future__ import annotations

import argparse
import os
import sys
from collections import Counter, defaultdict
from datetime import datetime

import numpy as np

_HERE = os.path.dirname(os.path.abspath(__file__))
_EMB_ROOT = os.path.dirname(_HERE)
if _EMB_ROOT not in sys.path:
    sys.path.insert(0, _EMB_ROOT)

from poem_core import config
from poem_core.embedding_client import embed_query
from poem_core.corpus import load_embeddings
from poem_core.dedup import get_unique_top_results
from poem_core.vector_store import get_store

# ---------------------------------------------------------------------------
# Query dataset
# Each entry: (query_string, expected_section)
#   expected_section: "instruments" | "scales" | "collections" | None
#
# Rule of thumb:
#   query ends with "instrument" / "questionnaire"  →  expected: "instruments"
#   bare construct name (Anxiety, Depression, ...)  →  expected: "scales"
# ---------------------------------------------------------------------------

QUERY_DATASET: list[tuple[str, str | None]] = [
    # --- Instrument queries (top results should be questionnaires/instruments) ---
    # Probe informant roles, instrument families, item-count variants, and
    # cross-language coverage. The instrument templates expose informant
    # (Caregiver/Youth/Adult) and scale-attribute lines; languages appear only
    # as suffixes in the instrument code (e.g. -ES, -BN), so language-name
    # queries also probe whether the embedding bridges that gap.
    ("English instrument",                 "instruments"),
    ("Youth instrument",                   "instruments"),
    ("Depression instrument",              "instruments"),
    ("Anxiety questionnaire",              "instruments"),
    ("caregiver instrument",               "instruments"),
    ("teacher instrument",                 "instruments"),
    ("adult questionnaire",                "instruments"),
    ("child questionnaire",                "instruments"),
    ("PHQ-9",                              "instruments"),
    ("GAD-7",                              "instruments"),
    ("RCADS-25",                           "instruments"),
    ("RCADS-47",                           "instruments"),
    ("MTT-35",                             "instruments"),
    ("RCADS questionnaire",                "instruments"),
    ("MTT questionnaire",                  "instruments"),
    ("patient health questionnaire",       "instruments"),
    ("therapeutic alliance instrument",    "instruments"),
    ("OCD instrument",                     "instruments"),
    ("Spanish questionnaire",              "instruments"),
    ("Chinese questionnaire",              "instruments"),

    # --- Scale queries (top results should be subscales) ---
    # Covers RCADS clinical subscales (SP/PD/GAD/MDD/SAD/OCD), composite/total
    # scales, and the 5 MTT therapeutic-alliance subscales (Relationship,
    # Expectancy, Attendance, Clarity, Homework). The last two probe the
    # skos:notation field exposed as "has attribute (notation): <code>".
    ("Anxiety",                            "scales"),
    ("Depression",                         "scales"),
    ("Social phobia",                      "scales"),
    ("OCD",                                "scales"),
    ("Panic disorder",                     "scales"),
    ("Separation anxiety",                 "scales"),
    ("Generalized anxiety disorder",       "scales"),
    ("Major depressive disorder",          "scales"),
    ("Obsessive compulsive disorder",      "scales"),
    ("Total anxiety",                      "scales"),
    ("Total depression",                   "scales"),
    ("Total anxiety and depression",       "scales"),
    ("Therapeutic relationship",           "scales"),
    ("Treatment expectancy",               "scales"),
    ("Treatment attendance",               "scales"),
    ("Homework completion",                "scales"),
    ("Therapy clarity",                    "scales"),
    ("questionnaire scale",                "scales"),
    ("notation SP",                        "scales"),
    ("notation GAD",                       "scales"),

    # --- Collection queries (top results should be instrument collections) ---
    # Collection templates contain only "instance of: Instrument Collection"
    # plus member codes — no descriptive text. These queries test whether the
    # collection section is reachable at all.
    ("Instrument collection",              "collections"),
    ("Group of instruments",               "collections"),
    ("Collection of questionnaires",       "collections"),
    ("multilingual instrument set",        "collections"),
    ("instrument set RCADS",               "collections"),

    # --- No-expectation / exploratory queries (probe cross-section behavior) ---
    # Item-stem text appears in both instrument and scale templates (as
    # "has member: <item>"), so these queries legitimately straddle sections
    # and are useful diagnostics rather than pass/fail tests.
    ("afraid of being in crowded places",  None),
    ("feels nothing is much fun anymore",  None),
    ("worries about mistakes",             None),
    ("I actively participate",             None),
    ("counselor understands my culture",   None),
]

# ---------------------------------------------------------------------------
# Default parameters
# ---------------------------------------------------------------------------

DEFAULT_TOP_K_SEARCH = 20   # raw results retrieved before deduplication
DEFAULT_TOP_K_UNIQUE = 5    # unique entities to report per query

SECTIONS = list(config.PREFERRED_SECTION_ORDER)

# ---------------------------------------------------------------------------
# Core functions
# ---------------------------------------------------------------------------
# get_unique_top_results now lives in poem_core.dedup (imported above) so the MCP
# server and this evaluator share one dedup implementation.


def compute_metrics(
    results: list[dict],
    expected_section: str | None,
) -> dict:
    """Return section breakdown and match% for a list of unique results."""
    if not results:
        metrics = {s: 0.0 for s in SECTIONS}
        metrics["match_pct"] = 0.0
        return metrics

    counts = Counter(r["section"] for r in results)
    total = len(results)
    metrics = {s: counts.get(s, 0) / total * 100 for s in SECTIONS}
    metrics["match_pct"] = metrics.get(expected_section, 0.0) if expected_section else None
    return metrics


def format_query_block(
    query: str,
    expected_section: str | None,
    results: list[dict],
    metrics: dict,
    top_k_search: int,
    scoped: bool = False,
) -> str:
    lines = []
    lines.append("=" * 72)
    expect_label = f"expected: {expected_section}" if expected_section else "no expectation"
    scope_label  = f"searched: {expected_section} only" if scoped else "searched: all sections"
    lines.append(f'Query: "{query}"  [{expect_label}  |  {scope_label}]')
    lines.append(
        f"(top {len(results)} unique entities from top-{top_k_search} raw results)"
    )
    lines.append("-" * 72)

    for r in results:
        match_marker = ""
        if expected_section:
            match_marker = " ✓" if r["section"] == expected_section else " ✗"
        lines.append(
            f"  #{r['unique_rank']:2d}  [{r['section']:12s}]{match_marker}"
            f"  score={r['score']:+.4f}  (raw #{r['raw_rank']:2d})"
        )
        lines.append(f"       Entity : {r['entity']}")
        lines.append(f"       Preview: {r['preview']}")

    lines.append("")
    breakdown = "  Section breakdown: " + "  |  ".join(
        f"{s}: {metrics[s]:.0f}%" for s in SECTIONS
    )
    if metrics["match_pct"] is not None:
        breakdown += f"  →  match%: {metrics['match_pct']:.0f}%"
    lines.append(breakdown)
    lines.append("")
    return "\n".join(lines)


# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------

def main() -> None:
    parser = argparse.ArgumentParser(
        description="Evaluate POEM search quality across a query dataset."
    )
    parser.add_argument(
        "--top-k-search",
        type=int,
        default=DEFAULT_TOP_K_SEARCH,
        help=f"Raw results retrieved before deduplication (default: {DEFAULT_TOP_K_SEARCH})",
    )
    parser.add_argument(
        "--top-k-unique",
        type=int,
        default=DEFAULT_TOP_K_UNIQUE,
        help=f"Unique entities to report per query (default: {DEFAULT_TOP_K_UNIQUE})",
    )
    parser.add_argument(
        "--no-scope",
        action="store_true",
        help="Search all sections together instead of restricting each query to its expected section",
    )
    args = parser.parse_args()

    print("Loading embeddings...")
    embeddings, texts, sections = load_embeddings()
    print(f"Total paragraphs loaded: {len(texts)}\n")

    # One vector store for the whole run (built once). Section scoping is pushed
    # into the store: a server-side filter for Milvus, a numpy mask otherwise.
    store = get_store(embeddings, texts, sections)

    output_lines: list[str] = []
    all_metrics: list[dict] = []
    group_metrics: dict[str, list[float]] = defaultdict(list)

    mode_label = "all sections (--no-scope)" if args.no_scope else "section-scoped"
    header = (
        f"POEM Search Evaluation\n"
        f"Date       : {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}\n"
        f"Queries    : {len(QUERY_DATASET)}\n"
        f"Mode       : {mode_label}\n"
        f"Top-k search (before dedup): {args.top_k_search}\n"
        f"Top-k unique (reported)    : {args.top_k_unique}\n"
    )
    print(header)
    output_lines.append(header)

    for query, expected_section in QUERY_DATASET:
        print(f'Embedding query: "{query}"')
        query_vec = embed_query(query)

        # Section-scoped: restrict to expected_section when one is set.
        scoped = bool(expected_section and not args.no_scope)
        section = expected_section if scoped else None
        scores, search_txt, search_sec = store.top_candidates(
            query_vec, "Cosine Similarity", section, k=args.top_k_search
        )
        results = get_unique_top_results(
            scores, search_txt, search_sec, args.top_k_search, args.top_k_unique
        )
        metrics = compute_metrics(results, expected_section)
        all_metrics.append(metrics)
        if expected_section and metrics["match_pct"] is not None:
            group_metrics[expected_section].append(metrics["match_pct"])

        block = format_query_block(
            query, expected_section, results, metrics, args.top_k_search, scoped
        )
        print(block)
        output_lines.append(block)

    # Summary
    summary_lines = [
        "=" * 72,
        "SUMMARY",
        "-" * 72,
        "Average section breakdown across ALL queries:",
    ]
    for s in SECTIONS:
        avg = sum(m[s] for m in all_metrics) / len(all_metrics) if all_metrics else 0.0
        summary_lines.append(f"  {s:12s}: {avg:.1f}%")

    summary_lines.append("")
    summary_lines.append("Average match% by expected section type:")
    for section, match_list in sorted(group_metrics.items()):
        avg_match = sum(match_list) / len(match_list)
        summary_lines.append(
            f"  {section:12s}: {avg_match:.1f}%  ({len(match_list)} queries)"
        )
    summary_lines.append("")

    summary = "\n".join(summary_lines)
    print(summary)
    output_lines.append(summary)

    # Save to file
    results_dir = os.path.join(_HERE, "evaluation_results")
    os.makedirs(results_dir, exist_ok=True)
    timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
    out_path = os.path.join(results_dir, f"evaluation_{timestamp}.txt")
    with open(out_path, "w", encoding="utf-8") as f:
        f.write("\n".join(output_lines))
    print(f"Results saved to: {out_path}")


if __name__ == "__main__":
    main()
