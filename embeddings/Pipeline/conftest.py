"""Pytest config for the Pipeline suite.

Pins the vector backend to numpy so offline tests are deterministic and do not
require a running Milvus server. An explicit VECTOR_BACKEND in the environment
still wins (setdefault), so a Milvus parity run can be requested deliberately.
"""
import os

os.environ.setdefault("VECTOR_BACKEND", "numpy")
