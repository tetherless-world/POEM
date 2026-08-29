"""Self-healing preflight for a local LM Studio chat server.

Mirrors ``poem_core/docker_preflight.py``'s role for Milvus, but for
``chat_agent.py``'s default chat backend (LM Studio, ``CHAT_BASE_URL``):

  1. Confirm the server answers ``<base_url>/models``; if not, try
     ``lms server start``. If *that* fails (the LM Studio application itself
     isn't running -- ``lms server start`` alone cannot wake a fully-closed
     app, confirmed empirically: it times out after ~30s with "Waking up LM
     Studio service..."), launch the LM Studio application and retry
     ``lms server start`` until it succeeds.
  2. Confirm ``CHAT_MODEL`` is loaded (present in ``/models``); if not,
     ``lms load <model>``.

Only ever acts on a *local* base_url (localhost/127.0.0.1) -- a remote
CHAT_BASE_URL never triggers a local launch. Never raises: a ``False`` return
just means "still down," same as the existing chat error message already
handles. Set ``CHAT_SKIP_ENSURE=1`` to disable entirely (mirrors
``MILVUS_SKIP_ENSURE``).
"""
from __future__ import annotations

import json
import os
import platform
import shutil
import subprocess
import time
import urllib.error
import urllib.request
from urllib.parse import urljoin, urlparse

_WINDOWS_LM_STUDIO_PATHS = [
    os.path.expandvars(r"%LOCALAPPDATA%\Programs\LM Studio\LM Studio.exe"),
]


def is_local_url(url: str) -> bool:
    """True if ``url`` points at this machine (the only case we can self-heal)."""
    return (urlparse(url).hostname or "") in ("localhost", "127.0.0.1", "::1")


def _run(cmd: list[str], timeout: float | None = None) -> subprocess.CompletedProcess:
    return subprocess.run(cmd, capture_output=True, text=True, timeout=timeout)


def _lms_path() -> str | None:
    return shutil.which("lms") or os.environ.get("LMS_CLI_EXE")


def list_models(base_url: str, timeout: float = 5) -> list[str] | None:
    """Model ids currently loaded, or None if the server isn't reachable."""
    url = urljoin(base_url.rstrip("/") + "/", "models")
    try:
        with urllib.request.urlopen(url, timeout=timeout) as resp:
            data = json.loads(resp.read())
        return [m["id"] for m in data.get("data", [])]
    except (urllib.error.URLError, OSError, ValueError):
        return None


def server_alive(base_url: str, timeout: float = 5) -> bool:
    return list_models(base_url, timeout=timeout) is not None


def _find_lm_studio_windows() -> str | None:
    override = os.environ.get("LM_STUDIO_EXE")
    if override and os.path.isfile(override):
        return override
    for path in _WINDOWS_LM_STUDIO_PATHS:
        if os.path.isfile(path):
            return path
    return None


def start_lm_studio_app(log=print) -> bool:
    """Best-effort launch of the LM Studio application. True iff a launch was attempted."""
    system = platform.system()
    if system == "Windows":
        exe = _find_lm_studio_windows()
        if not exe:
            log("[lmstudio_preflight] LM Studio.exe not found in the standard install path "
                "(set LM_STUDIO_EXE to override). Start it manually.")
            return False
        log(f"[lmstudio_preflight] Launching {exe} ...")
        subprocess.Popen([exe], close_fds=True)
        return True
    if system == "Darwin":
        log("[lmstudio_preflight] Launching LM Studio ...")
        subprocess.Popen(["open", "-a", "LM Studio"], close_fds=True)
        return True
    log(f"[lmstudio_preflight] Unrecognized platform '{system}'; start LM Studio manually.")
    return False


def _try_server_start(lms: str, timeout: float) -> bool:
    try:
        return _run([lms, "server", "start"], timeout=timeout).returncode == 0
    except (subprocess.TimeoutExpired, OSError):
        return False


def ensure_server_running(base_url: str, timeout: float = 150, log=print) -> bool:
    """Get the LM Studio server answering at base_url, launching the app if needed."""
    if server_alive(base_url):
        return True

    lms = _lms_path()
    if not lms:
        log("[lmstudio_preflight] `lms` CLI not found on PATH (set LMS_CLI_EXE to override). "
            "Start LM Studio's server manually.")
        return False

    deadline = time.monotonic() + timeout
    log("[lmstudio_preflight] LM Studio server unreachable; trying `lms server start` ...")
    # Cheap first: works instantly if the app is already open, just with the server off.
    if _try_server_start(lms, timeout=30) and server_alive(base_url):
        return True

    # The app itself likely isn't running -- `lms server start` alone cannot wake a
    # fully-closed app (it times out after ~30s). Launch it and keep retrying.
    if not start_lm_studio_app(log=log):
        return False
    log("[lmstudio_preflight] Waiting for the LM Studio application to initialize ...")
    while time.monotonic() < deadline:
        if _try_server_start(lms, timeout=30) and server_alive(base_url):
            return True
        time.sleep(5)
    log(f"[lmstudio_preflight] LM Studio server did not come up within {timeout:.0f}s.")
    return False


def ensure_model_loaded(base_url: str, model: str, timeout: float = 150, log=print) -> bool:
    lms = _lms_path()
    models = list_models(base_url)
    if models is None:
        return False
    if model in models:
        return True
    if not lms:
        log(f"[lmstudio_preflight] Model '{model}' not loaded and `lms` CLI not found; "
            f"load it manually in LM Studio.")
        return False
    log(f"[lmstudio_preflight] Loading model '{model}' ...")
    try:
        result = _run([lms, "load", model], timeout=timeout)
    except (subprocess.TimeoutExpired, OSError) as e:
        log(f"[lmstudio_preflight] `lms load {model}` failed to run: {e}")
        return False
    if result.returncode != 0:
        log(f"[lmstudio_preflight] `lms load {model}` failed:\n{result.stderr}")
        return False
    return True


def ensure_lmstudio_ready(base_url: str, model: str, timeout: float = 150, quiet: bool = False) -> bool:
    """Make sure a local LM Studio server is up with ``model`` loaded.

    Returns True once ready, False if it gave up (never raises). Skips
    everything (returns True) if CHAT_SKIP_ENSURE is set, or if base_url
    isn't local (a remote chat server is someone else's problem to keep up).
    """
    if os.environ.get("CHAT_SKIP_ENSURE") or not is_local_url(base_url):
        return True

    log = (lambda *_: None) if quiet else print

    if not ensure_server_running(base_url, timeout=timeout, log=log):
        return False
    if not ensure_model_loaded(base_url, model, timeout=timeout, log=log):
        return False
    log(f"[lmstudio_preflight] LM Studio is up with '{model}' loaded.")
    return True
