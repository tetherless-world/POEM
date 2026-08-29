# Build context MUST be embeddings/ (not this MCP/ folder, not the repo root):
#   docker build -f embeddings/MCP/Dockerfile -t poem-mcp:latest embeddings
# (embeddings/docker/mcp-compose.yml sets this automatically.)
#
# Ships the MCP server + the shared poem_core package + the versioned corpus
# (.npy vectors under Pipeline/{instruments,scales,collections}). The POEM RDF
# graph (individualsFull.ttl, ontology/, POEM.rdf, poem-demo/dist/data/) lives
# one level above embeddings/ in the repo and is intentionally NOT baked into
# this image -- Docker can't COPY across the build-context boundary, and the
# graph is refreshed independently of the code/corpus. Bind-mount the repo
# root read-only at runtime instead and point POEM_PROJECT_ROOT at it:
#   docker run -v <repo-root>:/data/repo:ro -e POEM_PROJECT_ROOT=/data/repo ...
# See MCP.md "Container deployment" for the full explanation and the compose
# file that wires this up automatically.
FROM python:3.12-slim

WORKDIR /app

# Only what's actually needed at runtime -- see .dockerignore for the rest.
COPY pyproject.toml MAIN.md ./
COPY poem_core ./poem_core
COPY MCP ./MCP
COPY Pipeline/instruments ./Pipeline/instruments
COPY Pipeline/scales ./Pipeline/scales
COPY Pipeline/collections ./Pipeline/collections

# This project's network intercepts TLS to pypi.org, which breaks plain pip
# installs with CERTIFICATE_VERIFY_FAILED (the same issue documented in
# MCP.md's setup instructions). --trusted-host works around it for the build
# only; override to an empty string with --build-arg on a network that
# doesn't need it:
#   docker build --build-arg PIP_TRUSTED_HOST_FLAGS= ...
ARG PIP_TRUSTED_HOST_FLAGS="--trusted-host pypi.org --trusted-host files.pythonhosted.org --trusted-host pypi.python.org"

# python:3.12-slim ships neither setuptools nor wheel; install them first,
# then install this package with --no-build-isolation so pip doesn't also try
# to fetch its own private copy into an ephemeral build env (which would hit
# the same certificate issue a second time, in a context these trusted-host
# flags don't reach).
RUN pip install --no-cache-dir $PIP_TRUSTED_HOST_FLAGS "setuptools>=61" wheel
RUN pip install --no-cache-dir --no-build-isolation $PIP_TRUSTED_HOST_FLAGS -e ".[mcp,milvus]"

# Container default is http -- stdio needs a parent process spawning and
# talking to this over stdin/stdout, which doesn't apply to a detached /
# orchestrated container. MILVUS_SKIP_ENSURE=1: this image never manages a
# host Docker daemon (docker_preflight's self-heal is a bare-metal/dev-machine
# feature); an unreachable Milvus just falls back to numpy, same as today.
# GRPC_EXPERIMENTAL_ENABLE_HAPPY_EYEBALLS=false works around a real, reproduced
# hang: pymilvus's gRPC channel construction against a Docker Compose service
# hostname (e.g. MILVUS_URI=http://milvus-standalone:19530) can hang
# indefinitely even though plain TCP/HTTP to the same hostname succeeds
# instantly and connecting by raw container IP works fine -- a gRPC
# happy-eyeballs resolver quirk against Docker's embedded DNS, not a wiring
# problem in this image or docker/mcp-compose.yml. Harmless if MILVUS_URI
# targets a real DNS name (Zilliz Cloud, etc.) instead of a compose hostname.
ENV MCP_TRANSPORT=http \
    MCP_HOST=0.0.0.0 \
    MCP_PORT=8100 \
    MILVUS_SKIP_ENSURE=1 \
    GRPC_EXPERIMENTAL_ENABLE_HAPPY_EYEBALLS=false \
    PYTHONUNBUFFERED=1

EXPOSE 8100

# Assumes MCP_TRANSPORT=http (the default above); running this image with
# MCP_TRANSPORT=stdio has no HTTP surface to probe and will report unhealthy
# -- that mode only makes sense for a client that spawns `docker run -i`
# itself, which is not the deployment shape this image targets.
HEALTHCHECK --interval=30s --timeout=5s --start-period=45s --retries=3 \
  CMD python -c "import os, urllib.request as u; u.urlopen('http://127.0.0.1:' + os.environ.get('MCP_PORT', '8100') + '/health', timeout=3)" || exit 1

ENTRYPOINT ["python", "MCP/mcp_server.py"]
