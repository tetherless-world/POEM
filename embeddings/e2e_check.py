#!/usr/bin/env python3
"""One-command end-to-end acceptance harness for the POEM stack.

Runs the *automatable* gates of the whole-process test and prints a checklist for
the interactive ones. Target backend + embedding endpoint come from the environment
(see TESTING.md "End-to-end acceptance test"). Run with the MCP venv python:

    $env:VECTOR_BACKEND="milvus"
    $env:MILVUS_URI="https://<cluster>.zillizcloud.com:19540"
    $env:MILVUS_TOKEN="db_admin:****"       # or an API key
    $env:GRPC_DEFAULT_SSL_ROOTS_FILE_PATH="C:\\path\\win_ca_bundle.pem"   # TLS-intercepted nets
    o:\\POEM\\embeddings\\MCP\\.venv-mcp\\Scripts\\python.exe o:\\POEM\\embeddings\\e2e_check.py

Gates:
  1  pytest suites (offline, numpy)                 [hard]
  2  live Milvus/Zilliz backend (check_milvus.py)   [hard, or SKIP if VECTOR_BACKEND!=milvus]
  4  REST surface on the configured backend         [hard; /search SKIPPED if embed endpoint down]
  3,5 real-query CLI + LLM agent                    [manual checklist]
Exit code 0 iff every hard gate that ran passed.
"""
import os
import subprocess
import sys

ROOT = os.path.dirname(os.path.abspath(__file__))     # embeddings/
PY = sys.executable
MILVUS = os.environ.get("VECTOR_BACKEND", "").lower() == "milvus"
results: dict[str, object] = {}


def hdr(t: str) -> None:
    print("\n" + "=" * 70 + f"\n{t}\n" + "=" * 70)


# ---- Gate 1: offline suites ------------------------------------------------
hdr("GATE 1 - offline pytest suites (numpy)")
r = subprocess.run(
    [PY, "-m", "pytest",
     os.path.join(ROOT, "MCP", "test_mcp.py"),
     os.path.join(ROOT, "Pipeline", "test_search_similarity.py"), "-q"],
    cwd=ROOT,
)
results["Gate 1 (pytest)"] = (r.returncode == 0)

# ---- Gate 2: live Milvus/Zilliz backend ------------------------------------
hdr("GATE 2 - live Milvus/Zilliz backend (check_milvus.py)")
if not MILVUS:
    print("SKIP: VECTOR_BACKEND != milvus. Set it + MILVUS_URI/TOKEN to test the live backend.")
    results["Gate 2 (milvus)"] = None
else:
    r = subprocess.run([PY, os.path.join(ROOT, "docker", "check_milvus.py")], cwd=ROOT)
    results["Gate 2 (milvus)"] = (r.returncode == 0)

# ---- Gate 2.5: docs pointer check -----------------------------------------
hdr("GATE 2.5 - docs pointer check")
print("Checking that every embeddings/*.md file includes the manual quick-reference...")
r = subprocess.run([PY, os.path.join(ROOT, "check_doc_pointers.py")], cwd=ROOT)
results["Gate 2.5 (docs pointer)"] = (r.returncode == 0)

# ---- Gate 4: REST surface (in-process TestClient) --------------------------
hdr("GATE 4 - REST surface on the configured backend")
try:
    sys.path.insert(0, os.path.join(ROOT, "API"))
    import api_server
    from fastapi.testclient import TestClient

    c = TestClient(api_server.app)
    health = c.get("/health").json()
    backend = health.get("vector_backend")
    print(f"/health -> backend={backend}, vectors={health.get('num_vectors')}")

    st = c.get("/statements/RCADS-25-CG-EN")
    print(f"/statements/RCADS-25-CG-EN -> {st.status_code}, "
          f"n={len(st.json()) if st.status_code == 200 else st.text[:80]}")

    want = "MilvusVectorStore" if MILVUS else "NumpyVectorStore"
    ok = (backend == want) and (st.status_code == 200 and len(st.json()) > 0)
    if backend != want:
        print(f"  !! expected backend {want}, got {backend} (silent fallback? check CA bundle / creds)")

    sr = c.get("/search", params={"query": "anxiety in children", "top_k": 3})
    if sr.status_code == 200:
        hits = sr.json()
        print(f"/search -> 200, {len(hits)} hits, top={hits[0]['id'] if hits else '-'}")
        ok = ok and len(hits) > 0
    elif sr.status_code == 503:
        print("/search -> 503 SKIP (embedding endpoint unreachable; needs VPN or local qwen3-embedding)")
    else:
        print(f"/search -> {sr.status_code} {sr.text[:120]}")
        ok = False
    results["Gate 4 (REST)"] = ok
except Exception as e:
    print(f"Gate 4 error: {type(e).__name__}: {e}")
    results["Gate 4 (REST)"] = False

# ---- Gates 3 & 5: manual ---------------------------------------------------
hdr("GATE 3 & 5 - manual (real-query CLI + LLM agent)")
print("Gate 3 (CLI; needs the embedding endpoint):")
print(f'  {PY} {os.path.join(ROOT, "Pipeline", "search_similarity.py")} '
      f'"instruments that measure anxiety in children"')
print("\nGate 5 (LLM agent; needs Ollama + api_server):")
print(f'  terminal A: {PY} {os.path.join(ROOT, "API", "api_server.py")}')
print(f'  terminal B: {PY} {os.path.join(ROOT, "agent", "chat_agent.py")}')
print('  ask: "Which instruments measure anxiety in children? Describe the top one."')
print("  PASS if the model calls search -> get_statements and cites entity ids.")

# ---- Summary ---------------------------------------------------------------
hdr("SUMMARY")
hard_fail = False
for k, v in results.items():
    tag = "PASS" if v is True else ("SKIP" if v is None else "FAIL")
    hard_fail = hard_fail or (v is False)
    print(f"  {k:22s} {tag}")
print("\nAutomated gates:", "FAIL" if hard_fail else "all green -> now run Gates 3 & 5 manually")
sys.exit(1 if hard_fail else 0)
