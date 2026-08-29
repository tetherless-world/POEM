"""Pytest config for the MCP suite.

Pins the vector backend to numpy so importing ``mcp_server`` (which builds the
store at import time) is deterministic and offline — no running Milvus server
needed. An explicit VECTOR_BACKEND in the environment still wins.
"""
import os

os.environ.setdefault("VECTOR_BACKEND", "numpy")
