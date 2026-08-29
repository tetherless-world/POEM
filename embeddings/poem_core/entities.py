"""Entity/URI naming helpers shared across the pipeline.

Previously ``extract_entity_name`` lived in ``search_similarity``,
``readable_local_name`` in ``generate_text_templates``, and
``entity_slug_from_text`` in ``generate_embeddings`` — three modules parsing the
same "ENTITY. Attributes include:" template header and URI shapes. Centralized
here so the template format is interpreted in exactly one place.
"""
from __future__ import annotations

import re

_ATTR_MARKER = ". Attributes include:"


def _entity_header(text: str) -> str:
    """The entity name from a paragraph's first line (before the attr marker)."""
    first_line = text.split("\n")[0]
    if _ATTR_MARKER in first_line:
        return first_line.split(_ATTR_MARKER)[0].strip()
    return first_line.strip()


def extract_entity_name(text: str) -> str:
    """Extract the entity code/name from the first line of a paragraph block.

    Template format: ``"ENTITY_NAME. Attributes include: ..."``
    """
    return _entity_header(text)


def entity_slug_from_text(text: str) -> str:
    """Filesystem-safe slug derived from the entity name in a text block.

    The slug is part of the ``.npy`` filename so the embeddings directory stays
    human-browsable.
    """
    name = _entity_header(text)
    slug = re.sub(r"[^\w\-]", "-", name)        # keep word chars and hyphens
    slug = re.sub(r"-{2,}", "-", slug).strip("-")
    return slug[:40] or "unknown"


def readable_local_name(uri: str) -> str:
    """Derive a human-readable label from a URI when no rdfs:label exists.

    Extracts everything after the last ``/`` or ``#``, then splits camelCase into
    words (``PsychometricQuestionnaire`` -> ``Psychometric Questionnaire``) and
    replaces underscores with spaces.
    """
    local = uri.split("#")[-1] if "#" in uri else uri.rstrip("/").split("/")[-1]
    local = re.sub(r"([a-z])([A-Z])", r"\1 \2", local)        # camelCase split
    local = re.sub(r"([A-Z]+)([A-Z][a-z])", r"\1 \2", local)  # e.g. SIOCode -> SIO Code
    local = local.replace("_", " ")
    return local.strip()
