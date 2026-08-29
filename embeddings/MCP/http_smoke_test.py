#!/usr/bin/env python3
"""Tiny standalone check of the MCP server's HTTP transport.

Offline-safe: only exercises `get_statements` (a pure graph lookup), so it
proves the container/HTTP path end-to-end without needing the RPI VPN or a
reachable embedding endpoint (`search` needs that; transport is orthogonal).

Run against a running container or `MCP_TRANSPORT=http` server (default
http://127.0.0.1:8100/mcp):

    o:\\POEM\\embeddings\\MCP\\.venv-mcp\\Scripts\\python.exe embeddings\\MCP\\http_smoke_test.py
"""
from __future__ import annotations

import asyncio
import os
import sys

from fastmcp import Client

URL = os.environ.get("MCP_HTTP_URL", "http://127.0.0.1:8100/mcp")
ENTITY_ID = os.environ.get("MCP_SMOKE_ENTITY_ID", "RCADS-25-CG-EN")


async def main() -> None:
    print(f"Connecting to {URL} ...")
    async with Client(URL) as client:
        tools = [t.name for t in await client.list_tools()]
        print(f"Tools: {tools}")
        assert "search" in tools and "get_statements" in tools, (
            f"expected 'search' and 'get_statements', got {tools}"
        )

        result = await client.call_tool("get_statements", {"entity_id": ENTITY_ID})
        stmts = result.data
        assert isinstance(stmts, list) and stmts, f"expected a non-empty list, got {stmts!r}"

        print(f"\nget_statements({ENTITY_ID!r}) -> {len(stmts)} statements (first 5):")
        for s in stmts[:5]:
            chain = f"  -> id={s['value_id']}" if s.get("value_id") else ""
            print(f"  {s['property']}: {s['value']}{chain}")

        print(f"\nOK -- HTTP transport is serving both tools correctly.")


if __name__ == "__main__":
    try:
        asyncio.run(main())
    except Exception as e:
        print(f"FAILED: {e}", file=sys.stderr)
        sys.exit(1)
