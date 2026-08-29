"""Self-healing preflight for the local Milvus Docker stack.

``ensure_milvus_ready()`` is what makes "just start using Milvus" work without
a manual ``docker compose up -d`` after every reboot:

  1. Confirm the Docker daemon answers ``docker info``; if not, launch Docker
     Desktop (Windows/macOS; best-effort ``systemctl`` on Linux) and wait.
  2. Confirm the ``milvus-compose.yml`` stack is running; if not, run
     ``docker compose up -d`` (the compose file's ``restart: unless-stopped``
     handles the common case of "Docker just came up" on its own — this covers
     "Docker itself was never started").
  3. Wait for the standalone container's ``/healthz`` to report ready.

Called from ``vector_store.get_store()`` (only when the target is local — a
remote/cloud ``MILVUS_URI`` never triggers a local Docker launch) and from the
``docker/`` maintenance scripts. Never raises: callers treat a ``False``
return the same way an unreachable server already works today — fall back to
numpy, or report the failure themselves. Set ``MILVUS_SKIP_ENSURE=1`` to
disable this entirely (CI, headless boxes, or Docker installed somewhere this
module doesn't know to look).
"""
from __future__ import annotations

import json
import os
import platform
import subprocess
import sys
import time
import urllib.error
import urllib.request
from urllib.parse import urlparse

_HERE = os.path.dirname(os.path.abspath(__file__))
COMPOSE_FILE = os.path.join(os.path.dirname(_HERE), "docker", "milvus-compose.yml")
HEALTHZ_PATH = "/healthz"
HEALTHZ_PORT = 9091

_WINDOWS_DOCKER_DESKTOP_PATHS = [
    r"C:\Program Files\Docker\Docker\Docker Desktop.exe",
    r"C:\Program Files (x86)\Docker\Docker\Docker Desktop.exe",
]


def is_local_uri(uri: str) -> bool:
    """True if ``uri`` points at this machine (the only case we can self-heal)."""
    host = urlparse(uri).hostname or ""
    return host in ("localhost", "127.0.0.1", "::1")


def _run(cmd: list[str], timeout: float | None = None) -> subprocess.CompletedProcess:
    return subprocess.run(cmd, capture_output=True, text=True, timeout=timeout)


def docker_daemon_alive(timeout: float = 5) -> bool:
    try:
        return _run(["docker", "info"], timeout=timeout).returncode == 0
    except (subprocess.TimeoutExpired, FileNotFoundError, OSError):
        return False


def _find_docker_desktop_windows() -> str | None:
    override = os.environ.get("DOCKER_DESKTOP_EXE")
    if override and os.path.isfile(override):
        return override
    for path in _WINDOWS_DOCKER_DESKTOP_PATHS:
        if os.path.isfile(path):
            return path
    return None


def start_docker_desktop(log=print) -> bool:
    """Best-effort launch of the Docker daemon. Returns True iff a launch was attempted."""
    system = platform.system()
    if system == "Windows":
        exe = _find_docker_desktop_windows()
        if not exe:
            log("[docker_preflight] Docker Desktop.exe not found in standard install paths "
                "(set DOCKER_DESKTOP_EXE to override). Start it manually.")
            return False
        log(f"[docker_preflight] Docker daemon unreachable; launching {exe} ...")
        subprocess.Popen([exe], close_fds=True)
        return True
    if system == "Darwin":
        log("[docker_preflight] Docker daemon unreachable; launching Docker Desktop ...")
        subprocess.Popen(["open", "-a", "Docker"], close_fds=True)
        return True
    if system == "Linux":
        log("[docker_preflight] Docker daemon unreachable; trying `systemctl start docker` ...")
        try:
            return _run(["systemctl", "start", "docker"], timeout=30).returncode == 0
        except (FileNotFoundError, OSError):
            return False
    log(f"[docker_preflight] Unrecognized platform '{system}'; start Docker manually.")
    return False


def wait_for_docker(timeout: float = 150, interval: float = 5) -> bool:
    deadline = time.monotonic() + timeout
    while True:
        if docker_daemon_alive():
            return True
        if time.monotonic() >= deadline:
            return False
        time.sleep(interval)


def _compose(*args: str, timeout: float = 60) -> subprocess.CompletedProcess:
    return _run(["docker", "compose", "-f", COMPOSE_FILE, *args], timeout=timeout)


def stack_running() -> bool:
    """True iff every service in the compose file reports state 'running'."""
    try:
        result = _compose("ps", "--format", "json", timeout=20)
    except (subprocess.TimeoutExpired, FileNotFoundError, OSError):
        return False
    if result.returncode != 0 or not result.stdout.strip():
        return False
    services = []
    for line in result.stdout.splitlines():
        line = line.strip()
        if not line:
            continue
        try:
            services.append(json.loads(line))
        except json.JSONDecodeError:
            return False
    return len(services) >= 3 and all(s.get("State") == "running" for s in services)


def start_stack(log=print) -> bool:
    log("[docker_preflight] Bringing up the Milvus compose stack ...")
    try:
        result = _compose("up", "-d", timeout=180)
    except (subprocess.TimeoutExpired, FileNotFoundError, OSError) as e:
        log(f"[docker_preflight] `docker compose up -d` failed to run: {e}")
        return False
    if result.returncode != 0:
        log(f"[docker_preflight] `docker compose up -d` failed:\n{result.stderr}")
        return False
    return True


def wait_for_healthz(timeout: float = 150, interval: float = 5) -> bool:
    url = f"http://localhost:{HEALTHZ_PORT}{HEALTHZ_PATH}"
    deadline = time.monotonic() + timeout
    while True:
        try:
            with urllib.request.urlopen(url, timeout=5) as resp:
                if resp.status == 200:
                    return True
        except (urllib.error.URLError, OSError):
            pass
        if time.monotonic() >= deadline:
            return False
        time.sleep(interval)


def ensure_milvus_ready(daemon_timeout: float = 150, healthz_timeout: float = 150, quiet: bool = False) -> bool:
    """Make sure the Docker daemon and the local Milvus stack are up and healthy.

    Returns True once Milvus is reachable, False if it gave up (never raises).
    Skips everything (returns True) if MILVUS_SKIP_ENSURE is set.
    """
    if os.environ.get("MILVUS_SKIP_ENSURE"):
        return True

    log = (lambda *_: None) if quiet else print

    if not docker_daemon_alive():
        if not start_docker_desktop(log=log):
            return False
        log("[docker_preflight] Waiting for Docker daemon ...")
        if not wait_for_docker(timeout=daemon_timeout):
            log(f"[docker_preflight] Docker daemon did not come up within {daemon_timeout:.0f}s.")
            return False
        log("[docker_preflight] Docker daemon is up.")

    if not stack_running():
        if not start_stack(log=log):
            return False

    log("[docker_preflight] Waiting for Milvus standalone health check ...")
    if not wait_for_healthz(timeout=healthz_timeout):
        log(f"[docker_preflight] Milvus did not report healthy within {healthz_timeout:.0f}s.")
        return False
    log("[docker_preflight] Milvus is up and healthy.")
    return True


if __name__ == "__main__":
    sys.exit(0 if ensure_milvus_ready() else 1)
