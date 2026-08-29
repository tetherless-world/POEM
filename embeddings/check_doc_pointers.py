#!/usr/bin/env python3
"""Check every markdown file under embeddings/ for a *working* manual quick-reference.

Beyond the original substring check (does the text mention
"manuals/DOCS_SUMMARY.md" at all?), this also resolves whatever relative path
was actually written -- "./manuals/...", "../manuals/...", "../../manuals/...",
etc. -- against the file's own directory, and confirms it lands on the real
DOCS_SUMMARY.md. A doc two directories deep that copy-pasted a one-directory-deep
relative path (wrong depth) now fails instead of silently passing, since a
plain substring match can't tell "this text mentions the right filename" apart
from "this text's actual link resolves to the right file".
"""
import os
import re
from pathlib import Path
from typing import List, NamedTuple, Optional

ROOT = Path(__file__).resolve().parent
TARGET = (ROOT / "manuals" / "DOCS_SUMMARY.md").resolve()

# Matches a relative reference ending in manuals/DOCS_SUMMARY.md, capturing
# whatever leading ./ or ../ (repeated) prefix was used, e.g.:
#   manuals/DOCS_SUMMARY.md
#   ./manuals/DOCS_SUMMARY.md
#   ../manuals/DOCS_SUMMARY.md
#   ../../manuals/DOCS_SUMMARY.md
REFERENCE_RE = re.compile(r"(?:\.{1,2}/)*manuals/DOCS_SUMMARY\.md")

# .venv-mcp is excluded via the dot-prefix check below (its own directory name
# starts with "."); no separate entry is needed for it.
EXCLUDE_DIRS = {"manuals", "venv", ".venv"}
EXCLUDE_FILES = {"README.md", "DOCS_SUMMARY.md", "FINAL_REPORT.md"}


class Problem(NamedTuple):
    path: Path
    reason: str


def is_excluded_path(rel: Path) -> bool:
    for part in rel.parts:
        if part.startswith("."):
            return True
        if part.lower().startswith("venv") or part.lower().endswith("venv"):
            return True
        if part in EXCLUDE_DIRS:
            return True
    return False


def find_reference_problem(doc_path: Path, text: str) -> Optional[str]:
    """None if a reference is present AND resolves to the real manual; else a reason."""
    found_any = False
    for m in REFERENCE_RE.finditer(text):
        found_any = True
        written = m.group(0)
        resolved = (doc_path.parent / written).resolve()
        if resolved == TARGET:
            return None
        # Wrong depth/location: text says the right filename but the path as
        # written doesn't actually reach embeddings/manuals/DOCS_SUMMARY.md.
    if found_any:
        return "reference present but does not resolve to embeddings/manuals/DOCS_SUMMARY.md (wrong relative depth?)"
    return "no reference to manuals/DOCS_SUMMARY.md found"


def find_problems(root: Path) -> List[Problem]:
    problems = []
    for path in sorted(root.rglob("*.md")):
        rel = path.relative_to(root)
        if is_excluded_path(rel):
            continue
        if path.name in EXCLUDE_FILES:
            continue
        try:
            text = path.read_text(encoding="utf-8")
        except UnicodeDecodeError:
            problems.append(Problem(rel, "not valid UTF-8"))
            continue
        reason = find_reference_problem(path, text)
        if reason:
            problems.append(Problem(rel, reason))
    return problems


def main() -> int:
    problems = find_problems(ROOT)
    if not problems:
        print("OK: all embeddings markdown files have a working manual quick-reference.")
        return 0

    print("WARNING: the following markdown files have a problem with their "
          "quick-reference to embeddings/manuals/DOCS_SUMMARY.md:")
    for problem in problems:
        print(f"  - {problem.path}: {problem.reason}")
    print()
    print("Run this script after adding or moving markdown files under embeddings/.")
    return 1


if __name__ == "__main__":
    raise SystemExit(main())
