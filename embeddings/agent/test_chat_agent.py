#!/usr/bin/env python3
"""Intensive tests for agent/chat_agent.py:

  * chat_once()'s tool-call loop, including the 6-round cap for a looping
    model, malformed tool-call arguments, and a tool call that comes back as
    an error (the "nonexistent id" shape a real MCP/REST call produces).
  * repl()'s error handling: a chat-server failure must not crash the loop.
  * make_mcp_call_tool / make_rest_call_tool in isolation (fake transports),
    plus one real end-to-end check: a genuine mcp_server.py subprocess
    (numpy backend, offline-safe) asked for a nonexistent entity id, driven
    through the exact same call_tool the real agent uses.
  * main()'s POEM_TOOLS dispatch.

Async code is driven via asyncio.run() inside plain `def test_...` functions
rather than `async def test_...`, so this suite has no dependency on
pytest-asyncio/anyio's pytest plugin being configured -- it runs under plain
pytest everywhere the rest of this project's suites do.

Run with the MCP venv interpreter:
    embeddings\\MCP\\.venv-mcp\\Scripts\\python.exe -m pytest \\
        embeddings\\agent\\test_chat_agent.py -v
"""
from __future__ import annotations

import asyncio
import json
import os
import sys
from unittest.mock import AsyncMock

import pytest

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import chat_agent  # noqa: E402

_MCP_SERVER_PATH = os.path.abspath(
    os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "MCP", "mcp_server.py")
)


# ---------------------------------------------------------------------------
# Fakes mimicking just enough of the OpenAI SDK's response shape for
# chat_once() to operate on (message.tool_calls, message.content,
# message.model_dump()).
# ---------------------------------------------------------------------------

class FakeFunction:
    def __init__(self, name: str, arguments: str):
        self.name = name
        self.arguments = arguments


class FakeToolCall:
    def __init__(self, id: str, name: str, arguments: str):
        self.id = id
        self.function = FakeFunction(name, arguments)


class FakeMessage:
    def __init__(self, content: str | None = None, tool_calls: list | None = None):
        self.content = content
        self.tool_calls = tool_calls or []

    def model_dump(self, exclude_none: bool = True) -> dict:
        d: dict = {"role": "assistant"}
        if self.content is not None:
            d["content"] = self.content
        if self.tool_calls:
            d["tool_calls"] = [
                {"id": tc.id, "type": "function",
                 "function": {"name": tc.function.name, "arguments": tc.function.arguments}}
                for tc in self.tool_calls
            ]
        return d


class FakeChoice:
    def __init__(self, message: FakeMessage):
        self.message = message


class FakeResponse:
    def __init__(self, message: FakeMessage):
        self.choices = [FakeChoice(message)]


def _patch_create(monkeypatch, *responses: FakeResponse) -> AsyncMock:
    create = AsyncMock(side_effect=list(responses))
    monkeypatch.setattr(chat_agent.chat.chat.completions, "create", create)
    return create


# ---------------------------------------------------------------------------
# chat_once
# ---------------------------------------------------------------------------

def test_chat_once_returns_immediately_with_no_tool_calls(monkeypatch):
    _patch_create(monkeypatch, FakeResponse(FakeMessage(content="Hello there.")))

    async def call_tool(name, args):
        raise AssertionError("call_tool should not be invoked when the model doesn't call a tool")

    messages = [{"role": "system", "content": "sys"}]
    result = asyncio.run(chat_agent.chat_once(messages, tools=[], call_tool=call_tool))
    assert result == "Hello there."


def test_chat_once_calls_tool_then_answers(monkeypatch):
    tool_msg = FakeMessage(tool_calls=[FakeToolCall("tc1", "get_statements", '{"entity_id": "RCADS-25-CG-EN"}')])
    final_msg = FakeMessage(content="It measures depression. [RCADS-25-CG-EN]")
    _patch_create(monkeypatch, FakeResponse(tool_msg), FakeResponse(final_msg))

    seen = []

    async def call_tool(name, args):
        seen.append((name, args))
        return json.dumps({"ok": True})

    messages = [{"role": "system", "content": "sys"}]
    result = asyncio.run(chat_agent.chat_once(messages, tools=[], call_tool=call_tool))
    assert result == "It measures depression. [RCADS-25-CG-EN]"
    assert seen == [("get_statements", {"entity_id": "RCADS-25-CG-EN"})]
    assert any(m.get("role") == "tool" for m in messages)


def test_chat_once_malformed_tool_arguments_default_to_empty_dict(monkeypatch):
    tool_msg = FakeMessage(tool_calls=[FakeToolCall("tc1", "search", "{not valid json")])
    final_msg = FakeMessage(content="done")
    _patch_create(monkeypatch, FakeResponse(tool_msg), FakeResponse(final_msg))

    seen_args = {}

    async def call_tool(name, args):
        seen_args.update(args)
        return "{}"

    asyncio.run(chat_agent.chat_once([{"role": "system", "content": "s"}], [], call_tool))
    assert seen_args == {}


def test_chat_once_continues_gracefully_after_a_tool_error(monkeypatch):
    """The nonexistent-id shape: call_tool returns a JSON error string (as
    make_mcp_call_tool / make_rest_call_tool both do -- they never raise) and
    the model gets a normal turn to react to it."""
    tool_msg = FakeMessage(tool_calls=[FakeToolCall("tc1", "get_statements", '{"entity_id":"NO-SUCH-ID"}')])
    final_msg = FakeMessage(content="I couldn't find an instrument with that id.")
    _patch_create(monkeypatch, FakeResponse(tool_msg), FakeResponse(final_msg))

    async def call_tool(name, args):
        return json.dumps({"error": "ValueError: No entity found for id 'NO-SUCH-ID'"})

    result = asyncio.run(chat_agent.chat_once([{"role": "system", "content": "s"}], [], call_tool))
    assert result == "I couldn't find an instrument with that id."


def test_chat_once_stops_after_six_rounds_for_a_looping_model(monkeypatch):
    looping_msg = FakeMessage(tool_calls=[FakeToolCall("tc", "search", '{"query":"x"}')])
    create = AsyncMock(return_value=FakeResponse(looping_msg))  # never produces a final answer
    monkeypatch.setattr(chat_agent.chat.chat.completions, "create", create)

    calls = {"n": 0}

    async def call_tool(name, args):
        calls["n"] += 1
        return "{}"

    result = asyncio.run(chat_agent.chat_once([{"role": "system", "content": "s"}], [], call_tool))
    assert result == "(stopped after too many tool-call rounds)"
    assert create.call_count == 6
    assert calls["n"] == 6


# ---------------------------------------------------------------------------
# repl -- error handling around chat_once and the input loop.
# ---------------------------------------------------------------------------

async def _fake_to_thread(func, *args, **kwargs):
    """Runs func synchronously in-place -- avoids real thread scheduling so
    input() can be scripted deterministically."""
    return func(*args, **kwargs)


def test_repl_processes_a_turn_then_exits(monkeypatch, capsys):
    monkeypatch.setattr(chat_agent.asyncio, "to_thread", _fake_to_thread)
    inputs = iter(["hello", "exit"])
    monkeypatch.setattr("builtins.input", lambda prompt="": next(inputs))

    async def fake_chat_once(messages, tools, call_tool):
        return "hi there"

    monkeypatch.setattr(chat_agent, "chat_once", fake_chat_once)
    asyncio.run(chat_agent.repl(tools=[], call_tool=None))
    assert "hi there" in capsys.readouterr().out


def test_repl_survives_a_chat_error_without_crashing(monkeypatch, capsys):
    monkeypatch.setattr(chat_agent.asyncio, "to_thread", _fake_to_thread)
    inputs = iter(["hello", "exit"])
    monkeypatch.setattr("builtins.input", lambda prompt="": next(inputs))

    async def raising_chat_once(messages, tools, call_tool):
        raise ConnectionError("chat server down")

    monkeypatch.setattr(chat_agent, "chat_once", raising_chat_once)
    asyncio.run(chat_agent.repl(tools=[], call_tool=None))  # must not raise
    out = capsys.readouterr().out
    assert "chat error" in out


def test_repl_breaks_cleanly_on_eof(monkeypatch):
    monkeypatch.setattr(chat_agent.asyncio, "to_thread", _fake_to_thread)

    def raise_eof(prompt=""):
        raise EOFError()

    monkeypatch.setattr("builtins.input", raise_eof)
    asyncio.run(chat_agent.repl(tools=[], call_tool=None))  # must return, not raise


def test_repl_skips_blank_input_without_calling_chat_once(monkeypatch, capsys):
    monkeypatch.setattr(chat_agent.asyncio, "to_thread", _fake_to_thread)
    inputs = iter(["", "   ", "exit"])
    monkeypatch.setattr("builtins.input", lambda prompt="": next(inputs))

    async def fake_chat_once(messages, tools, call_tool):
        raise AssertionError("chat_once should not run for blank input")

    monkeypatch.setattr(chat_agent, "chat_once", fake_chat_once)
    asyncio.run(chat_agent.repl(tools=[], call_tool=None))


# ---------------------------------------------------------------------------
# make_mcp_call_tool -- fake MCP client (no real subprocess).
# ---------------------------------------------------------------------------

class FakeToolResult:
    def __init__(self, data):
        self.data = data


class FakeMcpClient:
    def __init__(self, response=None, exception=None):
        self.response = response
        self.exception = exception
        self.calls = []

    async def call_tool(self, name, args):
        self.calls.append((name, args))
        if self.exception is not None:
            raise self.exception
        return FakeToolResult(self.response)


def test_make_mcp_call_tool_returns_json_of_tool_data():
    client = FakeMcpClient(response=[{"property": "label", "value": "GAD-7", "value_id": None}])
    call_tool = chat_agent.make_mcp_call_tool(client)
    result = asyncio.run(call_tool("get_statements", {"entity_id": "GAD-7"}))
    assert json.loads(result) == [{"property": "label", "value": "GAD-7", "value_id": None}]
    assert client.calls == [("get_statements", {"entity_id": "GAD-7"})]


def test_make_mcp_call_tool_nonexistent_id_becomes_error_json_not_a_raise():
    client = FakeMcpClient(exception=ValueError("No entity found for id 'NOPE'"))
    call_tool = chat_agent.make_mcp_call_tool(client)
    result = asyncio.run(call_tool("get_statements", {"entity_id": "NOPE"}))
    parsed = json.loads(result)
    assert "error" in parsed and "NOPE" in parsed["error"]


@pytest.mark.slow
def test_make_mcp_call_tool_real_server_nonexistent_id(monkeypatch):
    """End-to-end proof: a genuine mcp_server.py subprocess (numpy backend,
    offline-safe -- no VPN needed), driven through the exact call_tool
    closure run_mcp() uses, turns a real nonexistent-id ValueError from the
    server into a JSON error string instead of raising through the agent.
    Spawns a real subprocess (full corpus + graph load) -- see TESTING.md
    "Fast vs. full test runs" to skip this in a quick dev loop."""
    monkeypatch.setenv("VECTOR_BACKEND", "numpy")

    async def _drive():
        from fastmcp import Client
        from fastmcp.client.transports import StdioTransport

        client = Client(StdioTransport(command=sys.executable, args=[_MCP_SERVER_PATH]))
        async with client:
            call_tool = chat_agent.make_mcp_call_tool(client)
            return await call_tool("get_statements", {"entity_id": "TOTALLY-NOT-A-REAL-POEM-ID"})

    result = asyncio.run(_drive())
    parsed = json.loads(result)
    assert "error" in parsed
    assert "TOTALLY-NOT-A-REAL-POEM-ID" in parsed["error"]


# ---------------------------------------------------------------------------
# make_rest_call_tool -- fake HTTP via httpx.MockTransport (no live
# api_server.py needed).
# ---------------------------------------------------------------------------

def _rest_call_tool_against(handler):
    import httpx

    http = httpx.AsyncClient(base_url="http://testserver", transport=httpx.MockTransport(handler))
    return chat_agent.make_rest_call_tool(http), http


def test_make_rest_call_tool_search_success():
    import httpx

    def handler(request: httpx.Request) -> httpx.Response:
        assert request.url.path == "/search"
        return httpx.Response(200, json=[{"id": "GAD-7", "label": "GAD-7"}])

    call_tool, http = _rest_call_tool_against(handler)

    async def _run():
        async with http:
            return await call_tool("search", {"query": "anxiety", "top_k": 3})

    result = asyncio.run(_run())
    assert json.loads(result) == [{"id": "GAD-7", "label": "GAD-7"}]


def test_make_rest_call_tool_nonexistent_id_404_becomes_error_json():
    import httpx

    def handler(request: httpx.Request) -> httpx.Response:
        assert request.url.path == "/statements/NO-SUCH-ID"
        return httpx.Response(404, text="No entity found for id 'NO-SUCH-ID'")

    call_tool, http = _rest_call_tool_against(handler)

    async def _run():
        async with http:
            return await call_tool("get_statements", {"entity_id": "NO-SUCH-ID"})

    result = asyncio.run(_run())
    parsed = json.loads(result)
    assert "error" in parsed
    assert "404" in parsed["error"]


def test_make_rest_call_tool_unknown_tool_name():
    def handler(request):
        raise AssertionError("no HTTP call should be made for an unknown tool")

    call_tool, http = _rest_call_tool_against(handler)

    async def _run():
        async with http:
            return await call_tool("not_a_real_tool", {})

    result = asyncio.run(_run())
    assert json.loads(result) == {"error": "unknown tool not_a_real_tool"}


def test_make_rest_call_tool_connection_failure_becomes_error_json():
    import httpx

    def handler(request):
        raise httpx.ConnectError("connection refused")

    call_tool, http = _rest_call_tool_against(handler)

    async def _run():
        async with http:
            return await call_tool("get_statements", {"entity_id": "RCADS-25-CG-EN"})

    result = asyncio.run(_run())
    parsed = json.loads(result)
    assert "error" in parsed
    assert "ConnectError" in parsed["error"]


# ---------------------------------------------------------------------------
# main() -- POEM_TOOLS dispatch.
# ---------------------------------------------------------------------------

def test_main_dispatches_to_mcp_by_default(monkeypatch):
    monkeypatch.setattr(chat_agent, "ensure_lmstudio_ready", lambda *a, **kw: True)
    monkeypatch.setattr(chat_agent, "POEM_TOOLS", "mcp")
    calls = {"mcp": 0, "rest": 0}

    async def fake_run_mcp():
        calls["mcp"] += 1

    async def fake_run_rest():
        calls["rest"] += 1

    monkeypatch.setattr(chat_agent, "run_mcp", fake_run_mcp)
    monkeypatch.setattr(chat_agent, "run_rest", fake_run_rest)
    chat_agent.main()
    assert calls == {"mcp": 1, "rest": 0}


def test_main_dispatches_to_rest_when_configured(monkeypatch):
    monkeypatch.setattr(chat_agent, "ensure_lmstudio_ready", lambda *a, **kw: True)
    monkeypatch.setattr(chat_agent, "POEM_TOOLS", "rest")
    calls = {"mcp": 0, "rest": 0}

    async def fake_run_mcp():
        calls["mcp"] += 1

    async def fake_run_rest():
        calls["rest"] += 1

    monkeypatch.setattr(chat_agent, "run_mcp", fake_run_mcp)
    monkeypatch.setattr(chat_agent, "run_rest", fake_run_rest)
    chat_agent.main()
    assert calls == {"mcp": 0, "rest": 1}


def test_main_calls_lmstudio_preflight_with_configured_model(monkeypatch):
    seen = {}

    def fake_ensure(base_url, model, *a, **kw):
        seen["base_url"] = base_url
        seen["model"] = model
        return True

    monkeypatch.setattr(chat_agent, "ensure_lmstudio_ready", fake_ensure)
    monkeypatch.setattr(chat_agent, "POEM_TOOLS", "mcp")

    async def fake_run_mcp():
        pass

    monkeypatch.setattr(chat_agent, "run_mcp", fake_run_mcp)
    chat_agent.main()
    assert seen == {"base_url": chat_agent.CHAT_BASE_URL, "model": chat_agent.CHAT_MODEL}


if __name__ == "__main__":
    sys.exit(pytest.main([__file__, "-v"]))
