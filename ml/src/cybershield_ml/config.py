"""Tiny YAML-subset reader so the pipeline has zero third-party dependencies.

Supports exactly what config.yaml uses: `key: value`, two-space nested maps, inline flow lists
like `[3, 4, 5]`, ints, floats, booleans, and quoted/plain strings. Not a general YAML parser.
"""
from __future__ import annotations

import os
from pathlib import Path

ML_ROOT = Path(__file__).resolve().parents[2]  # the ml/ directory


def _coerce(raw: str):
    s = raw.strip()
    if s.startswith("[") and s.endswith("]"):
        inner = s[1:-1].strip()
        return [_coerce(x) for x in inner.split(",")] if inner else []
    if (s.startswith('"') and s.endswith('"')) or (s.startswith("'") and s.endswith("'")):
        return s[1:-1]
    low = s.lower()
    if low in ("true", "false"):
        return low == "true"
    if low in ("null", "~", ""):
        return None
    try:
        return int(s)
    except ValueError:
        pass
    try:
        return float(s)
    except ValueError:
        pass
    return s


def load_config(path: str | os.PathLike | None = None) -> dict:
    path = Path(path) if path else ML_ROOT / "config.yaml"
    root: dict = {}
    stack = [(-1, root)]  # (indent, container)
    for line in path.read_text(encoding="utf-8").splitlines():
        if not line.strip() or line.lstrip().startswith("#"):
            continue
        indent = len(line) - len(line.lstrip(" "))
        key, _, rest = line.strip().partition(":")
        key = key.strip()
        while stack and indent <= stack[-1][0]:
            stack.pop()
        container = stack[-1][1]
        value = rest.strip()
        # Strip trailing inline comments, respecting quotes and flow-list brackets.
        if value.startswith("["):
            close = value.rfind("]")
            if close != -1:
                value = value[: close + 1]
        elif value and value[0] not in "\"'":
            value = value.split("#", 1)[0].strip()
        if value == "":
            child: dict = {}
            container[key] = child
            stack.append((indent, child))
        else:
            container[key] = _coerce(value)
    return root
