"""Quick standalone check of the two tools (no MCP protocol involved).

Loads mcp_server.py and calls its `search` and `get_statements` functions
directly, demonstrating the two-call flow a chatbot would use: search by
topic, then read one result's relationships from the graph. Run with the MCP
venv python:
    o:\\POEM\\embeddings\\MCP\\.venv-mcp\\Scripts\\python.exe o:\\POEM\\embeddings\\MCP\\try_search.py

`search` hits the embedding server (idea-llm-01:11435) and only succeeds on the
RPI network/VPN; off-network it times out. `get_statements` is offline (pure
graph lookup) -- pass an id on the command line to exercise it without network:
    ... try_search.py RCADS-25-CG-EN
"""
import os
import sys
import importlib.util as u

_HERE = os.path.dirname(os.path.abspath(__file__))
s = u.spec_from_file_location("mcp_server", os.path.join(_HERE, "mcp_server.py"))
m = u.module_from_spec(s)
s.loader.exec_module(m)


def show_statements(entity_id: str) -> None:
    print(f"\nget_statements({entity_id!r}):")
    for st in m.get_statements(entity_id):
        chain = f"  -> id={st['value_id']}" if st["value_id"] else ""
        print(f"    {st['property']}: {st['value']}{chain}")


# Offline path: an id was supplied -> just exercise get_statements (no network).
if len(sys.argv) > 1:
    show_statements(sys.argv[1])
    sys.exit(0)

# Full flow: search (needs RPI network), then describe the top hit.
results = m.search("instruments that measure anxiety in children", top_k=5)
for r in results:
    print(f"[{r['section']}] {r['id']}  ({r['label']})  score={r['score']:+.4f}")

if results:
    show_statements(results[0]["id"])

'''
MCP\.venv-mcp\Scripts\python.exe embeddings/MCP/try_search.py RCADS-25-CG-EN   # offline
fastmcp dev inspector embeddings/MCP/mcp_server.py
'''