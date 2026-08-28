"""poem_core — shared core for the POEM embeddings + search stack.

Single home for configuration, the embedding client, similarity metrics, the
corpus loader, the RDF graph loader, result deduplication, and the pluggable
vector store. Both the Pipeline scripts and the MCP server import from here so
there is exactly one implementation of each concern (no duplicated OpenAI
clients, metric tables, manifest readers, or cross-folder ``sys.path`` hacks).

The thin modules under ``Pipeline/`` and ``MCP/`` re-export from this package to
preserve their existing public import surface.
"""

__all__ = [
    "config",
    "entities",
    "metrics",
    "embedding_client",
    "corpus",
    "dedup",
    "graph",
    "vector_store",
]
