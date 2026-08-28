#!/usr/bin/env python3
"""Terminal chat agent for grounded POEM answers (Phase 2).

A minimal LLM agent loop that puts a local, tool-calling chat model in front of
the POEM search tools. The model decides when to call ``search`` /
``get_statements``; this agent executes those calls and feeds the JSON back, so
every answer is grounded in the ontology and cites entity ids.

Tool transport (POEM_TOOLS env var):

* ``mcp`` (default) -- the agent spawns ``../MCP/mcp_server.py`` as a subprocess
  and talks MCP over stdio, exactly like LM Studio or any other MCP host. The
  tool list and parameter schemas are discovered from the server at startup
  (``list_tools``), so they can never drift from the implementation. One
  terminal, no separate API process; the corpus + graph load on agent start.

      you --> chat model (OpenAI-compatible: Ollama / LM Studio / vLLM)
                  --tool calls--> MCP client --stdio--> mcp_server.py
                  --> grounded answer with [entity-id] citations

* ``rest`` -- the previous behavior: call the POEM REST API
  (``embeddings/API/api_server.py``, run it separately) over HTTP.

Run (one terminal, MCP mode):
    <python-with-deps> o:\\POEM\\embeddings\\agent\\chat_agent.py
    # e.g. the per-device venv created by MCP/setup_lmstudio.ps1:
    #   %LOCALAPPDATA%\\POEM\\mcp-venv\\Scripts\\python.exe
    # or the dev venv on the machine that built it: MCP/.venv-mcp

Preconditions: none to remember for the LM Studio default -- ``main()`` calls
``lmstudio_preflight.ensure_lmstudio_ready()`` first, which launches LM Studio
and loads CHAT_MODEL if either isn't already up (set CHAT_SKIP_ENSURE=1 to skip).
A non-default/remote CHAT_BASE_URL (e.g. Ollama) is untouched by this. The
``search`` tool additionally needs the embedding endpoint (RPI VPN, or a local
qwen3-embedding). ``get_statements`` works fully offline.

Config (env vars):
    CHAT_BASE_URL    default http://localhost:1234/v1    (or Ollama :11434/v1, vLLM, ...)
    CHAT_MODEL       default google/gemma-4-e4b            (any tool-calling model)
    CHAT_API_KEY     default "lm-studio"                   (ignored by local servers)
    CHAT_SKIP_ENSURE set to skip the LM Studio self-heal preflight entirely
    POEM_TOOLS       default "mcp"; set "rest" for the HTTP API path
    POEM_MCP_PYTHON  interpreter used to spawn the MCP server (default: this one)
    POEM_MCP_SERVER  path to mcp_server.py (default: ../MCP/mcp_server.py)
    POEM_API_URL     default http://localhost:8000         (rest mode only)

Requires ``openai`` and ``fastmcp`` (``httpx`` for rest mode); all ship with the
MCP venv.
"""
from __future__ import annotations

import asyncio
import json
import os
import sys

from openai import AsyncOpenAI

from lmstudio_preflight import ensure_lmstudio_ready

CHAT_BASE_URL = os.environ.get("CHAT_BASE_URL", "http://localhost:1234/v1")
CHAT_MODEL = os.environ.get("CHAT_MODEL", "google/gemma-4-e4b")
CHAT_API_KEY = os.environ.get("CHAT_API_KEY", "lm-studio")
POEM_TOOLS = os.environ.get("POEM_TOOLS", "mcp").strip().lower()
POEM_API_URL = os.environ.get("POEM_API_URL", "http://localhost:8000").rstrip("/")

_HERE = os.path.dirname(os.path.abspath(__file__))
MCP_SERVER = os.path.abspath(
    os.environ.get("POEM_MCP_SERVER", os.path.join(_HERE, "..", "MCP", "mcp_server.py")))
MCP_PYTHON = os.environ.get("POEM_MCP_PYTHON", sys.executable)

chat = AsyncOpenAI(base_url=CHAT_BASE_URL, api_key=CHAT_API_KEY)

SYSTEM = (
    "You are the POEM assistant, an expert on the POEM catalogue of mental-health "
    "instruments, scales, and collections. Answer ONLY from tool results — never "
    "invent instruments, ids, or facts. Workflow: call `search` to find relevant "
    "entities, then `get_statements` on a result's id to read its details. Cite the "
    "entity id(s) you used, e.g. [RCADS-25-CG-EN]. If search returns nothing relevant, "
    "say so plainly. Do not give clinical or diagnostic advice beyond describing what "
    "the catalogue contains."
)

# Hand-written schemas for rest mode only. In MCP mode the schemas come from the
# server itself (list_tools), so they cannot drift.
REST_TOOLS = [
    {"type": "function", "function": {
        "name": "search",
        "description": "Semantic search over POEM instruments/scales/collections; returns ranked entities.",
        "parameters": {"type": "object", "properties": {
            "query": {"type": "string", "description": "Natural-language search text (topic, symptom, item wording)."},
            "top_k": {"type": "integer", "description": "How many unique entities to return (default 5)."},
            "section": {"type": "string", "enum": ["instruments", "scales", "collections"],
                        "description": "Optional section filter; omit to search all."},
        }, "required": ["query"]},
    }},
    {"type": "function", "function": {
        "name": "get_statements",
        "description": "Return a POEM entity's relationships from the graph. Pass an id from a search result.",
        "parameters": {"type": "object", "properties": {
            "entity_id": {"type": "string", "description": "An id from search, e.g. 'RCADS-25-CG-EN' or 'SP'."},
        }, "required": ["entity_id"]},
    }},
]


async def chat_once(messages: list, tools: list, call_tool) -> str:
    """One user turn: let the model call tools until it produces a final answer."""
    for _ in range(6):  # cap tool-call rounds so a confused model can't loop forever
        resp = await chat.chat.completions.create(model=CHAT_MODEL, messages=messages, tools=tools)
        msg = resp.choices[0].message
        messages.append(msg.model_dump(exclude_none=True))
        if not msg.tool_calls:
            return msg.content or ""
        for tc in msg.tool_calls:
            try:
                args = json.loads(tc.function.arguments or "{}")
            except json.JSONDecodeError:
                args = {}
            print(f"   · {tc.function.name}({args})", file=sys.stderr)
            result = await call_tool(tc.function.name, args)
            messages.append({"role": "tool", "tool_call_id": tc.id, "content": result})
    return "(stopped after too many tool-call rounds)"


async def repl(tools: list, call_tool) -> None:
    print(f"Chat model: {CHAT_MODEL} @ {CHAT_BASE_URL}")
    print("Ask about POEM instruments/scales/collections. Type 'exit' or Ctrl-C to quit.\n")
    messages: list = [{"role": "system", "content": SYSTEM}]
    while True:
        try:
            user = (await asyncio.to_thread(input, "you> ")).strip()
        except (EOFError, KeyboardInterrupt):
            print()
            break
        if user.lower() in {"exit", "quit"}:
            break
        if not user:
            continue
        messages.append({"role": "user", "content": user})
        try:
            answer = await chat_once(messages, tools, call_tool)
        except Exception as e:
            print(f"!! chat error ({type(e).__name__}: {e}). Is the chat server up at {CHAT_BASE_URL}?\n")
            continue
        print(f"\npoem> {answer}\n")


# ---------------------------------------------------------------------------
# Transport: MCP (default) -- spawn mcp_server.py over stdio, discover tools.
#
# make_mcp_call_tool is pulled out as its own function (rather than a closure
# defined inline in run_mcp) so tests can construct it against a fake or a
# real-but-directly-driven fastmcp Client without going through the full
# subprocess-spawn + interactive-REPL setup in run_mcp itself (see
# agent/test_chat_agent.py).
# ---------------------------------------------------------------------------
def make_mcp_call_tool(client):
    """Build the ``call_tool(name, args) -> str`` callable for MCP transport.

    Never raises: a tool error (e.g. an unknown entity id -- ``ToolError``) or
    a transport-level failure is caught and turned into a JSON ``{"error": ...}``
    string, so a bad id from the model degrades to a message it can react to
    instead of crashing the turn.
    """
    async def call_tool(name: str, args: dict) -> str:
        try:
            res = await client.call_tool(name, args)
            return json.dumps(res.data, default=str)[:6000]
        except Exception as e:  # ToolError (e.g. bad entity id), transport errors
            return json.dumps({"error": f"{type(e).__name__}: {e}"})

    return call_tool


async def run_mcp() -> None:
    from fastmcp import Client
    from fastmcp.client.transports import StdioTransport

    if not os.path.isfile(MCP_SERVER):
        print(f"!! MCP server script not found: {MCP_SERVER} (set POEM_MCP_SERVER)")
        return
    print(f"Starting POEM MCP server over stdio (corpus + graph load; takes a minute) ...")
    print(f"  {MCP_PYTHON} {MCP_SERVER}")
    client = Client(StdioTransport(command=MCP_PYTHON, args=[MCP_SERVER]))
    async with client:
        mcp_tools = await client.list_tools()
        # MCP tool schemas -> OpenAI function-calling format, verbatim.
        tools = [{"type": "function", "function": {
            "name": t.name,
            "description": t.description or "",
            "parameters": t.inputSchema,
        }} for t in mcp_tools]
        print(f"POEM tools (via MCP): {', '.join(t.name for t in mcp_tools)}")

        await repl(tools, make_mcp_call_tool(client))


# ---------------------------------------------------------------------------
# Transport: REST -- the previous behavior (embeddings/API/api_server.py).
#
# make_rest_call_tool is likewise pulled out so tests can drive it against an
# httpx.AsyncClient wired to httpx.MockTransport (in-process fake HTTP, no
# live api_server.py needed) instead of only via the full run_rest() flow.
# ---------------------------------------------------------------------------
def make_rest_call_tool(http):
    """Build the ``call_tool(name, args) -> str`` callable for REST transport.

    Never raises: an HTTP error status (e.g. 404 for an unknown entity id) or
    any other request failure is caught and turned into a JSON ``{"error": ...}``
    string, matching make_mcp_call_tool's contract.
    """
    import httpx

    async def call_tool(name: str, args: dict) -> str:
        try:
            if name == "search":
                params = {"query": args["query"], "top_k": int(args.get("top_k", 5) or 5)}
                if args.get("section"):
                    params["section"] = args["section"]
                r = await http.get("/search", params=params)
            elif name == "get_statements":
                r = await http.get(f"/statements/{args['entity_id']}")
            else:
                return json.dumps({"error": f"unknown tool {name}"})
            r.raise_for_status()
            return json.dumps(r.json())[:6000]
        except httpx.HTTPStatusError as e:
            return json.dumps({"error": f"HTTP {e.response.status_code}: {e.response.text[:200]}"})
        except Exception as e:
            return json.dumps({"error": f"{type(e).__name__}: {e}"})

    return call_tool


async def run_rest() -> None:
    import httpx

    http = httpx.AsyncClient(base_url=POEM_API_URL, timeout=60)
    try:
        h = (await http.get("/health")).json()
        print(f"POEM API: {POEM_API_URL} (backend={h.get('vector_backend')}, "
              f"{h.get('num_vectors')} vectors, embed={h.get('embed_model')})")
    except Exception as e:
        print(f"!! POEM REST API not reachable at {POEM_API_URL} ({type(e).__name__}). "
              f"Start it first:\n   <venv-python> embeddings/API/api_server.py\n"
              f"   (or unset POEM_TOOLS to use the MCP transport instead)")
        return

    try:
        await repl(REST_TOOLS, make_rest_call_tool(http))
    finally:
        await http.aclose()


def main() -> None:
    ensure_lmstudio_ready(CHAT_BASE_URL, CHAT_MODEL)
    if POEM_TOOLS == "rest":
        asyncio.run(run_rest())
    else:
        asyncio.run(run_mcp())


if __name__ == "__main__":
    main()

r'''
o:\POEM\embeddings\MCP\.venv-mcp\Scripts\python.exe o:\POEM\embeddings\agent\chat_agent.py
Which caregiver-report instrument covers depression in children, and what kinds of things does it ask about?
'''