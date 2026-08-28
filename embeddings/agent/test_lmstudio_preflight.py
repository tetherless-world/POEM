#!/usr/bin/env python3
"""Intensive tests for agent/lmstudio_preflight.py -- the chat agent's
self-heal logic for "the chat server (LM Studio) isn't running", mirroring
poem_core/test_docker_preflight.py's coverage of the analogous Milvus/Docker
case.

Fully OFFLINE and fast: every subprocess/network call is mocked, so this
never actually launches LM Studio, runs the `lms` CLI, or hits a real HTTP
endpoint.

Run with the MCP venv interpreter:
    embeddings\\MCP\\.venv-mcp\\Scripts\\python.exe -m pytest \\
        embeddings\\agent\\test_lmstudio_preflight.py -v
"""
from __future__ import annotations

import json
import os
import subprocess
import sys

import pytest

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import lmstudio_preflight as lsp  # noqa: E402


def cp(returncode: int = 0, stdout: str = "", stderr: str = "") -> subprocess.CompletedProcess:
    return subprocess.CompletedProcess(args=[], returncode=returncode, stdout=stdout, stderr=stderr)


class Scripted:
    """Replays one result per call, in order. See poem_core/test_docker_preflight.py."""

    def __init__(self, *script):
        self.script = list(script)
        self.calls: list[tuple] = []

    def __call__(self, *args, **kwargs):
        self.calls.append((args, kwargs))
        if not self.script:
            raise AssertionError(f"Scripted exhausted after {len(self.calls)} calls")
        item = self.script.pop(0)
        if isinstance(item, BaseException):
            raise item
        return item

    @property
    def call_count(self) -> int:
        return len(self.calls)


@pytest.fixture(autouse=True)
def _no_real_sleep(monkeypatch):
    monkeypatch.setattr(lsp.time, "sleep", lambda s: None)


class _FakeHttpResponse:
    def __init__(self, payload: dict):
        self._body = json.dumps(payload).encode()

    def __enter__(self):
        return self

    def __exit__(self, *exc):
        return False

    def read(self):
        return self._body


# ---------------------------------------------------------------------------
# is_local_url
# ---------------------------------------------------------------------------

@pytest.mark.parametrize("url,expected", [
    ("http://localhost:1234/v1", True),
    ("http://127.0.0.1:1234/v1", True),
    ("http://[::1]:1234/v1", True),
    ("http://localhost:11434/v1", True),  # Ollama's default port, still local
    ("http://my-lm-studio-box.local:1234/v1", False),
    ("https://api.openai.com/v1", False),
    ("", False),
])
def test_is_local_url(url, expected):
    assert lsp.is_local_url(url) is expected


# ---------------------------------------------------------------------------
# list_models / server_alive
# ---------------------------------------------------------------------------

def test_list_models_parses_ids(monkeypatch):
    monkeypatch.setattr(
        lsp.urllib.request, "urlopen",
        Scripted(_FakeHttpResponse({"data": [{"id": "qwen2.5-7b-instruct"}, {"id": "gemma-4"}]})),
    )
    assert lsp.list_models("http://localhost:1234/v1") == ["qwen2.5-7b-instruct", "gemma-4"]


def test_list_models_none_when_unreachable(monkeypatch):
    monkeypatch.setattr(
        lsp.urllib.request, "urlopen",
        lambda url, timeout=5: (_ for _ in ()).throw(lsp.urllib.error.URLError("refused")),
    )
    assert lsp.list_models("http://localhost:1234/v1") is None


def test_list_models_none_on_malformed_json(monkeypatch):
    class BadJson:
        def __enter__(self):
            return self

        def __exit__(self, *exc):
            return False

        def read(self):
            return b"not json"

    monkeypatch.setattr(lsp.urllib.request, "urlopen", Scripted(BadJson()))
    assert lsp.list_models("http://localhost:1234/v1") is None


def test_server_alive_true_and_false(monkeypatch):
    monkeypatch.setattr(lsp, "list_models", lambda base_url, timeout=5: ["m"])
    assert lsp.server_alive("http://localhost:1234/v1") is True
    monkeypatch.setattr(lsp, "list_models", lambda base_url, timeout=5: None)
    assert lsp.server_alive("http://localhost:1234/v1") is False


# ---------------------------------------------------------------------------
# _lms_path
# ---------------------------------------------------------------------------

def test_lms_path_prefers_path_lookup(monkeypatch):
    monkeypatch.setattr(lsp.shutil, "which", lambda name: r"C:\tools\lms.exe")
    monkeypatch.delenv("LMS_CLI_EXE", raising=False)
    assert lsp._lms_path() == r"C:\tools\lms.exe"


def test_lms_path_falls_back_to_env_override(monkeypatch):
    monkeypatch.setattr(lsp.shutil, "which", lambda name: None)
    monkeypatch.setenv("LMS_CLI_EXE", r"D:\custom\lms.exe")
    assert lsp._lms_path() == r"D:\custom\lms.exe"


def test_lms_path_none_when_not_found(monkeypatch):
    monkeypatch.setattr(lsp.shutil, "which", lambda name: None)
    monkeypatch.delenv("LMS_CLI_EXE", raising=False)
    assert lsp._lms_path() is None


# ---------------------------------------------------------------------------
# start_lm_studio_app -- every platform branch, including the "Linux has no
# handler" gap (unlike docker_preflight, which does support Linux).
# ---------------------------------------------------------------------------

def test_start_lm_studio_windows_env_override(monkeypatch, tmp_path):
    fake_exe = tmp_path / "LM Studio.exe"
    fake_exe.write_text("not real")
    monkeypatch.setattr(lsp.platform, "system", lambda: "Windows")
    monkeypatch.setenv("LM_STUDIO_EXE", str(fake_exe))
    popen = Scripted(None)
    monkeypatch.setattr(lsp.subprocess, "Popen", popen)
    assert lsp.start_lm_studio_app(log=lambda *_: None) is True
    assert popen.calls[0][0] == ([str(fake_exe)],)


def test_start_lm_studio_windows_not_found(monkeypatch):
    monkeypatch.setattr(lsp.platform, "system", lambda: "Windows")
    monkeypatch.delenv("LM_STUDIO_EXE", raising=False)
    monkeypatch.setattr(lsp.os.path, "isfile", lambda p: False)
    popen = Scripted()
    monkeypatch.setattr(lsp.subprocess, "Popen", popen)
    assert lsp.start_lm_studio_app(log=lambda *_: None) is False
    assert popen.call_count == 0


def test_start_lm_studio_macos(monkeypatch):
    monkeypatch.setattr(lsp.platform, "system", lambda: "Darwin")
    popen = Scripted(None)
    monkeypatch.setattr(lsp.subprocess, "Popen", popen)
    assert lsp.start_lm_studio_app(log=lambda *_: None) is True
    assert popen.calls[0][0] == (["open", "-a", "LM Studio"],)


def test_start_lm_studio_linux_is_unsupported(monkeypatch):
    """LM Studio has no Linux service-manager equivalent to `systemctl start
    docker` -- Linux falls through to the same "unrecognized platform" path
    as a genuinely unknown OS. Documenting this real gap with a test."""
    monkeypatch.setattr(lsp.platform, "system", lambda: "Linux")
    popen = Scripted()
    monkeypatch.setattr(lsp.subprocess, "Popen", popen)
    assert lsp.start_lm_studio_app(log=lambda *_: None) is False
    assert popen.call_count == 0


# ---------------------------------------------------------------------------
# ensure_server_running
# ---------------------------------------------------------------------------

BASE_URL = "http://localhost:1234/v1"


def test_ensure_server_running_already_alive(monkeypatch):
    monkeypatch.setattr(lsp, "server_alive", lambda base_url, timeout=5: True)
    lms_path = Scripted()
    monkeypatch.setattr(lsp, "_lms_path", lms_path)
    assert lsp.ensure_server_running(BASE_URL, log=lambda *_: None) is True
    assert lms_path.call_count == 0


def test_ensure_server_running_no_lms_cli(monkeypatch):
    monkeypatch.setattr(lsp, "server_alive", lambda base_url, timeout=5: False)
    monkeypatch.setattr(lsp, "_lms_path", lambda: None)
    assert lsp.ensure_server_running(BASE_URL, log=lambda *_: None) is False


def test_ensure_server_running_cheap_start_succeeds(monkeypatch):
    """The app is already open, just with the server off -- `lms server
    start` alone should be enough, no app launch needed."""
    alive_calls = {"n": 0}

    def fake_alive(base_url, timeout=5):
        alive_calls["n"] += 1
        return alive_calls["n"] >= 2  # false the first time, true after `lms server start`

    monkeypatch.setattr(lsp, "server_alive", fake_alive)
    monkeypatch.setattr(lsp, "_lms_path", lambda: "lms")
    monkeypatch.setattr(lsp, "_try_server_start", lambda lms, timeout: True)
    start_app = Scripted()
    monkeypatch.setattr(lsp, "start_lm_studio_app", start_app)
    assert lsp.ensure_server_running(BASE_URL, log=lambda *_: None) is True
    assert start_app.call_count == 0


def test_ensure_server_running_app_launch_fails(monkeypatch):
    monkeypatch.setattr(lsp, "server_alive", lambda base_url, timeout=5: False)
    monkeypatch.setattr(lsp, "_lms_path", lambda: "lms")
    monkeypatch.setattr(lsp, "_try_server_start", lambda lms, timeout: False)
    monkeypatch.setattr(lsp, "start_lm_studio_app", lambda log=print: False)
    assert lsp.ensure_server_running(BASE_URL, log=lambda *_: None) is False


def test_ensure_server_running_comes_up_after_app_launch_and_retries(monkeypatch):
    monkeypatch.setattr(lsp, "_lms_path", lambda: "lms")
    monkeypatch.setattr(lsp, "start_lm_studio_app", lambda log=print: True)

    state = {"tries": 0}

    def fake_try_start(lms, timeout):
        state["tries"] += 1
        return state["tries"] >= 3  # first cheap attempt + 2 retries after launch

    monkeypatch.setattr(lsp, "_try_server_start", fake_try_start)
    monkeypatch.setattr(lsp, "server_alive", lambda base_url, timeout=5: state["tries"] >= 3)
    assert lsp.ensure_server_running(BASE_URL, timeout=100, log=lambda *_: None) is True
    assert state["tries"] == 3


def test_ensure_server_running_never_comes_up(monkeypatch):
    monkeypatch.setattr(lsp, "server_alive", lambda base_url, timeout=5: False)
    monkeypatch.setattr(lsp, "_lms_path", lambda: "lms")
    monkeypatch.setattr(lsp, "_try_server_start", lambda lms, timeout: False)
    monkeypatch.setattr(lsp, "start_lm_studio_app", lambda log=print: True)
    assert lsp.ensure_server_running(BASE_URL, timeout=0.05, log=lambda *_: None) is False


# ---------------------------------------------------------------------------
# ensure_model_loaded
# ---------------------------------------------------------------------------

def test_ensure_model_loaded_server_unreachable(monkeypatch):
    monkeypatch.setattr(lsp, "list_models", lambda base_url, timeout=5: None)
    assert lsp.ensure_model_loaded(BASE_URL, "qwen2.5-7b-instruct", log=lambda *_: None) is False


def test_ensure_model_loaded_already_loaded(monkeypatch):
    monkeypatch.setattr(lsp, "list_models", lambda base_url, timeout=5: ["qwen2.5-7b-instruct"])
    # NOTE: the real ensure_model_loaded calls _lms_path() unconditionally up
    # front (before checking whether the model is already loaded), so it's
    # still invoked once here even though its result goes unused on this path.
    lms_path = Scripted("lms")
    monkeypatch.setattr(lsp, "_lms_path", lms_path)
    run = Scripted()
    monkeypatch.setattr(lsp, "_run", run)
    assert lsp.ensure_model_loaded(BASE_URL, "qwen2.5-7b-instruct", log=lambda *_: None) is True
    assert lms_path.call_count == 1
    assert run.call_count == 0  # but `lms load` itself must not run


def test_ensure_model_loaded_no_lms_cli(monkeypatch):
    monkeypatch.setattr(lsp, "list_models", lambda base_url, timeout=5: ["some-other-model"])
    monkeypatch.setattr(lsp, "_lms_path", lambda: None)
    assert lsp.ensure_model_loaded(BASE_URL, "qwen2.5-7b-instruct", log=lambda *_: None) is False


def test_ensure_model_loaded_lms_load_succeeds(monkeypatch):
    monkeypatch.setattr(lsp, "list_models", lambda base_url, timeout=5: ["some-other-model"])
    monkeypatch.setattr(lsp, "_lms_path", lambda: "lms")
    monkeypatch.setattr(lsp, "_run", Scripted(cp(returncode=0)))
    assert lsp.ensure_model_loaded(BASE_URL, "qwen2.5-7b-instruct", log=lambda *_: None) is True


def test_ensure_model_loaded_lms_load_fails(monkeypatch):
    monkeypatch.setattr(lsp, "list_models", lambda base_url, timeout=5: ["some-other-model"])
    monkeypatch.setattr(lsp, "_lms_path", lambda: "lms")
    monkeypatch.setattr(lsp, "_run", Scripted(cp(returncode=1, stderr="model not found")))
    assert lsp.ensure_model_loaded(BASE_URL, "nonexistent-model", log=lambda *_: None) is False


def test_ensure_model_loaded_lms_load_times_out(monkeypatch):
    monkeypatch.setattr(lsp, "list_models", lambda base_url, timeout=5: ["some-other-model"])
    monkeypatch.setattr(lsp, "_lms_path", lambda: "lms")
    monkeypatch.setattr(lsp, "_run", Scripted(subprocess.TimeoutExpired(cmd="lms load x", timeout=150)))
    assert lsp.ensure_model_loaded(BASE_URL, "slow-model", log=lambda *_: None) is False


# ---------------------------------------------------------------------------
# ensure_lmstudio_ready -- the top-level orchestration.
# ---------------------------------------------------------------------------

def test_ensure_lmstudio_ready_skip_env(monkeypatch):
    monkeypatch.setenv("CHAT_SKIP_ENSURE", "1")
    ensure_running = Scripted()
    monkeypatch.setattr(lsp, "ensure_server_running", ensure_running)
    assert lsp.ensure_lmstudio_ready(BASE_URL, "some-model", quiet=True) is True
    assert ensure_running.call_count == 0


def test_ensure_lmstudio_ready_skips_for_remote_url(monkeypatch):
    monkeypatch.delenv("CHAT_SKIP_ENSURE", raising=False)
    ensure_running = Scripted()
    monkeypatch.setattr(lsp, "ensure_server_running", ensure_running)
    assert lsp.ensure_lmstudio_ready("https://api.some-cloud-llm.example/v1", "gpt-x", quiet=True) is True
    assert ensure_running.call_count == 0


def test_ensure_lmstudio_ready_happy_path(monkeypatch):
    monkeypatch.delenv("CHAT_SKIP_ENSURE", raising=False)
    monkeypatch.setattr(lsp, "ensure_server_running", lambda base_url, timeout=150, log=print: True)
    monkeypatch.setattr(lsp, "ensure_model_loaded", lambda base_url, model, timeout=150, log=print: True)
    assert lsp.ensure_lmstudio_ready(BASE_URL, "qwen2.5-7b-instruct", quiet=True) is True


def test_ensure_lmstudio_ready_server_never_comes_up(monkeypatch):
    monkeypatch.delenv("CHAT_SKIP_ENSURE", raising=False)
    monkeypatch.setattr(lsp, "ensure_server_running", lambda base_url, timeout=150, log=print: False)
    ensure_model = Scripted()
    monkeypatch.setattr(lsp, "ensure_model_loaded", ensure_model)
    assert lsp.ensure_lmstudio_ready(BASE_URL, "qwen2.5-7b-instruct", quiet=True) is False
    assert ensure_model.call_count == 0


def test_ensure_lmstudio_ready_model_never_loads(monkeypatch):
    monkeypatch.delenv("CHAT_SKIP_ENSURE", raising=False)
    monkeypatch.setattr(lsp, "ensure_server_running", lambda base_url, timeout=150, log=print: True)
    monkeypatch.setattr(lsp, "ensure_model_loaded", lambda base_url, model, timeout=150, log=print: False)
    assert lsp.ensure_lmstudio_ready(BASE_URL, "does-not-exist", quiet=True) is False


if __name__ == "__main__":
    sys.exit(pytest.main([__file__, "-v"]))
