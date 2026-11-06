"""Load the labelled message TSVs.

Parsing mirrors EvaluationTest.kt exactly: skip blank lines, `#` comments and the `id\t` header;
tab-split; require the right column count; `scam`/`legit` label; un-escape `\n` in text; assert
unique ids. Keeping this identical to the Kotlin loader guarantees both engines see the same rows.
"""
from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path


@dataclass(frozen=True)
class Sample:
    id: str
    scam: bool
    lang: str
    text: str


def load_tsv(path: str | Path, has_language: bool = True) -> list[Sample]:
    path = Path(path)
    expected = 4 if has_language else 3
    samples: list[Sample] = []
    seen: set[str] = set()
    for line in path.read_text(encoding="utf-8").splitlines():
        if not line.strip() or line.startswith("#") or line.startswith("id\t"):
            continue
        cols = line.split("\t")
        if len(cols) != expected:
            raise ValueError(f"{path.name}: expected {expected} columns in: {line}")
        if cols[1] not in ("scam", "legit"):
            raise ValueError(f"{path.name}: bad label in: {line}")
        sid = cols[0]
        if sid in seen:
            raise ValueError(f"{path.name}: duplicate id {sid}")
        seen.add(sid)
        samples.append(
            Sample(
                id=sid,
                scam=cols[1] == "scam",
                lang=cols[2] if has_language else "-",
                text=cols[-1].replace("\\n", "\n"),
            )
        )
    if not samples:
        raise ValueError(f"{path.name}: no samples loaded")
    return samples


def split_calibration(samples: list[Sample], fraction: float, seed: int) -> tuple[list[Sample], list[Sample]]:
    """Carve a stratified calibration slice out of the training (dev) set, deterministically."""
    import random

    rng = random.Random(seed)
    scam = [s for s in samples if s.scam]
    legit = [s for s in samples if not s.scam]
    cal: list[Sample] = []
    train: list[Sample] = []
    for group in (scam, legit):
        g = group[:]
        rng.shuffle(g)
        k = max(1, round(len(g) * fraction)) if g else 0
        cal.extend(g[:k])
        train.extend(g[k:])
    rng.shuffle(cal)
    rng.shuffle(train)
    return train, cal
