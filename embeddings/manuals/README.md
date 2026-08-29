embeddings/manuals — Purpose

This folder holds concise manuals and single-page quick references for the embeddings subsystem.

Files:
- DOCS_SUMMARY.md — single-page quick-start, troubleshooting cheatsheet, and pointers to detailed docs.
- FINAL_REPORT.md — a record of the documentation-accuracy pass across `embeddings/`, and what it changed.

Every other `.md` file under `embeddings/` links back to `DOCS_SUMMARY.md` as its
quick-reference (enforced by [`../check_doc_pointers.py`](../check_doc_pointers.py));
this folder is where that shared entry point lives.
