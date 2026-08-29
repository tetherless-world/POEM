#!/usr/bin/env python3
"""Intensive tests for poem_core/docker_preflight.py -- the "Docker isn't
running" self-heal logic that vector_store.get_store() (and the docker/
maintenance scripts) run automatically.

Fully OFFLINE and fast: every subprocess/network call is mocked, so this
never actually launches Docker Desktop, runs `docker compose`, or hits a real
HTTP endpoint. Covers every branch of the Docker-daemon-down /
stack-not-running / never-becomes-healthy state machine, on every platform
branch (Windows/macOS/Linux/unknown), plus the MILVUS_SKIP_ENSURE escape hatch
and the local-vs-remote URI gating.

Run with the MCP venv interpreter:
    embeddings\\MCP\\.venv-mcp\\Scripts\\python.exe -m pytest \\
        embeddings\\poem_core\\test_docker_preflight.py -v
"""
from __future__ import annotations

import subprocess
import time

import pytest

from poem_core import docker_preflight as dp


def cp(returncode: int = 0, stdout: str = "", stderr: str = "") -> subprocess.CompletedProcess:
    """Build a fake subprocess.CompletedProcess for a scripted _run/_compose call."""
    return subprocess.CompletedProcess(args=[], returncode=returncode, stdout=stdout, stderr=stderr)


class Scripted:
    """Stand-in for a mocked function: replays one result per call, in order.

    Each scripted item is either an exception *instance* (raised) or a plain
    value (returned). Raises AssertionError if called more times than scripted
    -- a signal the test's call-count expectation was wrong.
    """

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
    """Every test in this file runs with time.sleep patched to a no-op so
    polling-loop tests (wait_for_docker, wait_for_healthz) never actually wait
    in wall-clock time, regardless of the interval passed."""
    monkeypatch.setattr(dp.time, "sleep", lambda s: None)


# ---------------------------------------------------------------------------
# is_local_uri
# ---------------------------------------------------------------------------

@pytest.mark.parametrize("uri,expected", [
    ("http://localhost:19530", True),
    ("http://127.0.0.1:19530", True),
    ("http://[::1]:19530", True),
    ("http://LOCALHOST:19530", True),           # hostname lowercased by urlparse
    ("http://milvus-standalone:19530", False),  # a compose service hostname, not local
    ("http://192.168.1.5:19530", False),        # a LAN IP is not "local" to this check
    ("https://in03-abc.serverless.gcp-us-west1.cloud.zilliz.com:443", False),
    ("", False),
    ("not a uri at all", False),
])
def test_is_local_uri(uri, expected):
    assert dp.is_local_uri(uri) is expected


# ---------------------------------------------------------------------------
# docker_daemon_alive
# ---------------------------------------------------------------------------

def test_docker_daemon_alive_true(monkeypatch):
    monkeypatch.setattr(dp, "_run", Scripted(cp(returncode=0)))
    assert dp.docker_daemon_alive() is True


def test_docker_daemon_alive_false_nonzero_exit(monkeypatch):
    monkeypatch.setattr(dp, "_run", Scripted(cp(returncode=1)))
    assert dp.docker_daemon_alive() is False


def test_docker_daemon_alive_false_docker_not_installed(monkeypatch):
    monkeypatch.setattr(dp, "_run", Scripted(FileNotFoundError()))
    assert dp.docker_daemon_alive() is False


def test_docker_daemon_alive_false_on_timeout(monkeypatch):
    monkeypatch.setattr(dp, "_run", Scripted(subprocess.TimeoutExpired(cmd="docker info", timeout=5)))
    assert dp.docker_daemon_alive() is False


def test_docker_daemon_alive_false_on_os_error(monkeypatch):
    monkeypatch.setattr(dp, "_run", Scripted(OSError("permission denied")))
    assert dp.docker_daemon_alive() is False


# ---------------------------------------------------------------------------
# start_docker_desktop -- every platform branch
# ---------------------------------------------------------------------------

def test_start_docker_desktop_windows_uses_env_override(monkeypatch, tmp_path):
    fake_exe = tmp_path / "Docker Desktop.exe"
    fake_exe.write_text("not a real exe")
    monkeypatch.setattr(dp.platform, "system", lambda: "Windows")
    monkeypatch.setenv("DOCKER_DESKTOP_EXE", str(fake_exe))
    popen = Scripted(None)
    monkeypatch.setattr(dp.subprocess, "Popen", popen)
    assert dp.start_docker_desktop(log=lambda *_: None) is True
    assert popen.call_count == 1
    assert popen.calls[0][0] == ([str(fake_exe)],)


def test_start_docker_desktop_windows_finds_standard_path(monkeypatch):
    monkeypatch.setattr(dp.platform, "system", lambda: "Windows")
    monkeypatch.delenv("DOCKER_DESKTOP_EXE", raising=False)
    standard = dp._WINDOWS_DOCKER_DESKTOP_PATHS[0]
    monkeypatch.setattr(dp.os.path, "isfile", lambda p: p == standard)
    popen = Scripted(None)
    monkeypatch.setattr(dp.subprocess, "Popen", popen)
    assert dp.start_docker_desktop(log=lambda *_: None) is True
    assert popen.calls[0][0] == ([standard],)


def test_start_docker_desktop_windows_not_found(monkeypatch):
    monkeypatch.setattr(dp.platform, "system", lambda: "Windows")
    monkeypatch.delenv("DOCKER_DESKTOP_EXE", raising=False)
    monkeypatch.setattr(dp.os.path, "isfile", lambda p: False)
    popen = Scripted()
    monkeypatch.setattr(dp.subprocess, "Popen", popen)
    assert dp.start_docker_desktop(log=lambda *_: None) is False
    assert popen.call_count == 0


def test_start_docker_desktop_macos(monkeypatch):
    monkeypatch.setattr(dp.platform, "system", lambda: "Darwin")
    popen = Scripted(None)
    monkeypatch.setattr(dp.subprocess, "Popen", popen)
    assert dp.start_docker_desktop(log=lambda *_: None) is True
    assert popen.calls[0][0] == (["open", "-a", "Docker"],)


def test_start_docker_desktop_linux_systemctl_succeeds(monkeypatch):
    monkeypatch.setattr(dp.platform, "system", lambda: "Linux")
    monkeypatch.setattr(dp, "_run", Scripted(cp(returncode=0)))
    assert dp.start_docker_desktop(log=lambda *_: None) is True


def test_start_docker_desktop_linux_systemctl_fails(monkeypatch):
    monkeypatch.setattr(dp.platform, "system", lambda: "Linux")
    monkeypatch.setattr(dp, "_run", Scripted(cp(returncode=1)))
    assert dp.start_docker_desktop(log=lambda *_: None) is False


def test_start_docker_desktop_linux_systemctl_missing(monkeypatch):
    monkeypatch.setattr(dp.platform, "system", lambda: "Linux")
    monkeypatch.setattr(dp, "_run", Scripted(FileNotFoundError()))
    assert dp.start_docker_desktop(log=lambda *_: None) is False


def test_start_docker_desktop_unknown_platform(monkeypatch):
    monkeypatch.setattr(dp.platform, "system", lambda: "FreeBSD")
    popen = Scripted()
    monkeypatch.setattr(dp.subprocess, "Popen", popen)
    assert dp.start_docker_desktop(log=lambda *_: None) is False
    assert popen.call_count == 0


# ---------------------------------------------------------------------------
# wait_for_docker
# ---------------------------------------------------------------------------

def test_wait_for_docker_comes_up_after_n_polls(monkeypatch):
    counter = {"n": 0}

    def fake_alive(timeout: float = 5) -> bool:
        counter["n"] += 1
        return counter["n"] >= 3

    monkeypatch.setattr(dp, "docker_daemon_alive", fake_alive)
    assert dp.wait_for_docker(timeout=100, interval=0.01) is True
    assert counter["n"] == 3


def test_wait_for_docker_times_out(monkeypatch):
    monkeypatch.setattr(dp, "docker_daemon_alive", lambda timeout=5: False)
    assert dp.wait_for_docker(timeout=0.05, interval=0.01) is False


# ---------------------------------------------------------------------------
# stack_running
# ---------------------------------------------------------------------------

def _services_json(*states: str) -> str:
    return "\n".join(f'{{"State": "{s}"}}' for s in states)


def test_stack_running_all_three_running(monkeypatch):
    monkeypatch.setattr(dp, "_compose", Scripted(cp(stdout=_services_json("running", "running", "running"))))
    assert dp.stack_running() is True


def test_stack_running_one_not_running(monkeypatch):
    monkeypatch.setattr(dp, "_compose", Scripted(cp(stdout=_services_json("running", "running", "exited"))))
    assert dp.stack_running() is False


def test_stack_running_fewer_than_three_services(monkeypatch):
    monkeypatch.setattr(dp, "_compose", Scripted(cp(stdout=_services_json("running", "running"))))
    assert dp.stack_running() is False


def test_stack_running_more_than_three_all_running(monkeypatch):
    monkeypatch.setattr(dp, "_compose",
                         Scripted(cp(stdout=_services_json("running", "running", "running", "running"))))
    assert dp.stack_running() is True


def test_stack_running_empty_stdout(monkeypatch):
    monkeypatch.setattr(dp, "_compose", Scripted(cp(stdout="")))
    assert dp.stack_running() is False


def test_stack_running_malformed_json(monkeypatch):
    monkeypatch.setattr(dp, "_compose", Scripted(cp(stdout="not json\nnot json either\nnope")))
    assert dp.stack_running() is False


def test_stack_running_nonzero_returncode(monkeypatch):
    monkeypatch.setattr(dp, "_compose", Scripted(cp(returncode=1, stdout=_services_json("running", "running", "running"))))
    assert dp.stack_running() is False


def test_stack_running_compose_timeout(monkeypatch):
    monkeypatch.setattr(dp, "_compose", Scripted(subprocess.TimeoutExpired(cmd="docker compose ps", timeout=20)))
    assert dp.stack_running() is False


def test_stack_running_docker_not_installed(monkeypatch):
    monkeypatch.setattr(dp, "_compose", Scripted(FileNotFoundError()))
    assert dp.stack_running() is False


# ---------------------------------------------------------------------------
# start_stack
# ---------------------------------------------------------------------------

def test_start_stack_succeeds(monkeypatch):
    monkeypatch.setattr(dp, "_compose", Scripted(cp(returncode=0)))
    assert dp.start_stack(log=lambda *_: None) is True


def test_start_stack_nonzero_exit(monkeypatch):
    monkeypatch.setattr(dp, "_compose", Scripted(cp(returncode=1, stderr="some compose error")))
    assert dp.start_stack(log=lambda *_: None) is False


def test_start_stack_timeout(monkeypatch):
    monkeypatch.setattr(dp, "_compose", Scripted(subprocess.TimeoutExpired(cmd="docker compose up -d", timeout=180)))
    assert dp.start_stack(log=lambda *_: None) is False


def test_start_stack_docker_not_installed(monkeypatch):
    monkeypatch.setattr(dp, "_compose", Scripted(FileNotFoundError()))
    assert dp.start_stack(log=lambda *_: None) is False


# ---------------------------------------------------------------------------
# wait_for_healthz
# ---------------------------------------------------------------------------

class _FakeResponse:
    def __init__(self, status: int):
        self.status = status

    def __enter__(self):
        return self

    def __exit__(self, *exc):
        return False


def test_wait_for_healthz_immediate_200(monkeypatch):
    monkeypatch.setattr(dp.urllib.request, "urlopen", Scripted(_FakeResponse(200)))
    assert dp.wait_for_healthz(timeout=10, interval=0.01) is True


def test_wait_for_healthz_never_ready(monkeypatch):
    monkeypatch.setattr(dp.urllib.request, "urlopen",
                         lambda url, timeout=5: (_ for _ in ()).throw(dp.urllib.error.URLError("refused")))
    assert dp.wait_for_healthz(timeout=0.05, interval=0.01) is False


def test_wait_for_healthz_ready_after_n_polls(monkeypatch):
    responses = Scripted(
        dp.urllib.error.URLError("refused"),
        dp.urllib.error.URLError("refused"),
        _FakeResponse(200),
    )

    def fake_urlopen(url, timeout=5):
        result = responses(url, timeout=timeout)
        return result

    monkeypatch.setattr(dp.urllib.request, "urlopen", fake_urlopen)
    assert dp.wait_for_healthz(timeout=10, interval=0.01) is True
    assert responses.call_count == 3


# ---------------------------------------------------------------------------
# ensure_milvus_ready -- the full "Docker not running" orchestration matrix.
# Each sub-function is mocked, so this exercises only ensure_milvus_ready's
# own branching, not the internals already covered above.
# ---------------------------------------------------------------------------

def test_ensure_milvus_ready_skip_env_short_circuits(monkeypatch):
    monkeypatch.setenv("MILVUS_SKIP_ENSURE", "1")
    start_desktop = Scripted()
    monkeypatch.setattr(dp, "start_docker_desktop", start_desktop)
    monkeypatch.setattr(dp, "docker_daemon_alive", Scripted())
    assert dp.ensure_milvus_ready(quiet=True) is True
    assert start_desktop.call_count == 0  # never even checked


def test_ensure_milvus_ready_everything_already_up(monkeypatch):
    monkeypatch.delenv("MILVUS_SKIP_ENSURE", raising=False)
    start_desktop = Scripted()
    start_stack = Scripted()
    monkeypatch.setattr(dp, "docker_daemon_alive", lambda: True)
    monkeypatch.setattr(dp, "start_docker_desktop", start_desktop)
    monkeypatch.setattr(dp, "stack_running", lambda: True)
    monkeypatch.setattr(dp, "start_stack", start_stack)
    monkeypatch.setattr(dp, "wait_for_healthz", Scripted(True))
    assert dp.ensure_milvus_ready(quiet=True) is True
    assert start_desktop.call_count == 0
    assert start_stack.call_count == 0


def test_ensure_milvus_ready_daemon_down_launch_fails(monkeypatch):
    monkeypatch.delenv("MILVUS_SKIP_ENSURE", raising=False)
    monkeypatch.setattr(dp, "docker_daemon_alive", lambda: False)
    monkeypatch.setattr(dp, "start_docker_desktop", lambda log=print: False)
    wait_for_docker = Scripted()
    monkeypatch.setattr(dp, "wait_for_docker", wait_for_docker)
    assert dp.ensure_milvus_ready(quiet=True) is False
    assert wait_for_docker.call_count == 0  # short-circuited before waiting


def test_ensure_milvus_ready_daemon_down_never_comes_up(monkeypatch):
    monkeypatch.delenv("MILVUS_SKIP_ENSURE", raising=False)
    monkeypatch.setattr(dp, "docker_daemon_alive", lambda: False)
    monkeypatch.setattr(dp, "start_docker_desktop", lambda log=print: True)
    monkeypatch.setattr(dp, "wait_for_docker", lambda timeout=150: False)
    stack_running = Scripted()
    monkeypatch.setattr(dp, "stack_running", stack_running)
    assert dp.ensure_milvus_ready(quiet=True) is False
    assert stack_running.call_count == 0


def test_ensure_milvus_ready_stack_down_start_fails(monkeypatch):
    monkeypatch.delenv("MILVUS_SKIP_ENSURE", raising=False)
    monkeypatch.setattr(dp, "docker_daemon_alive", lambda: True)
    monkeypatch.setattr(dp, "stack_running", lambda: False)
    monkeypatch.setattr(dp, "start_stack", lambda log=print: False)
    wait_for_healthz = Scripted()
    monkeypatch.setattr(dp, "wait_for_healthz", wait_for_healthz)
    assert dp.ensure_milvus_ready(quiet=True) is False
    assert wait_for_healthz.call_count == 0


def test_ensure_milvus_ready_stack_starts_but_never_healthy(monkeypatch):
    monkeypatch.delenv("MILVUS_SKIP_ENSURE", raising=False)
    monkeypatch.setattr(dp, "docker_daemon_alive", lambda: True)
    monkeypatch.setattr(dp, "stack_running", lambda: False)
    monkeypatch.setattr(dp, "start_stack", lambda log=print: True)
    monkeypatch.setattr(dp, "wait_for_healthz", lambda timeout=150: False)
    assert dp.ensure_milvus_ready(quiet=True) is False


def test_ensure_milvus_ready_full_cold_start_happy_path(monkeypatch):
    """The complete "Docker isn't running at all" scenario: daemon down ->
    launched -> comes up -> stack not running -> started -> becomes healthy."""
    monkeypatch.delenv("MILVUS_SKIP_ENSURE", raising=False)
    daemon_state = {"alive": False}
    monkeypatch.setattr(dp, "docker_daemon_alive", lambda: daemon_state["alive"])

    def fake_start_desktop(log=print):
        daemon_state["alive"] = True  # simulate Docker Desktop finishing boot
        return True

    monkeypatch.setattr(dp, "start_docker_desktop", fake_start_desktop)
    monkeypatch.setattr(dp, "wait_for_docker", lambda timeout=150: daemon_state["alive"])
    monkeypatch.setattr(dp, "stack_running", lambda: False)
    monkeypatch.setattr(dp, "start_stack", lambda log=print: True)
    monkeypatch.setattr(dp, "wait_for_healthz", lambda timeout=150: True)
    assert dp.ensure_milvus_ready(quiet=True) is True


if __name__ == "__main__":
    import sys
    sys.exit(pytest.main([__file__, "-v"]))
