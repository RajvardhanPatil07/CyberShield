"""Deterministic, multilingual feature hashing.

Character n-grams make this script-agnostic: the same code handles English, Devanagari (Hindi/Marathi)
and romanised Hindi without a per-language vocabulary. Word n-grams add phrase-level signal. Hashing
uses md5 (not Python's salted built-in `hash`) so features are reproducible across processes and CI.
"""
from __future__ import annotations

import hashlib
import math
import re

_WORD_RE = re.compile(r"[^\s]+")


def _bucket(token: str, dim: int) -> int:
    h = hashlib.md5(token.encode("utf-8")).digest()
    return int.from_bytes(h[:4], "big") % dim


class Featurizer:
    def __init__(self, char_ngrams, word_ngrams, dim: int, lowercase: bool = True):
        self.char_ngrams = list(char_ngrams)
        self.word_ngrams = list(word_ngrams)
        self.dim = int(dim)
        self.lowercase = lowercase

    def to_dict(self) -> dict:
        return {
            "char_ngrams": self.char_ngrams,
            "word_ngrams": self.word_ngrams,
            "dim": self.dim,
            "lowercase": self.lowercase,
        }

    @classmethod
    def from_dict(cls, d: dict) -> "Featurizer":
        return cls(d["char_ngrams"], d["word_ngrams"], d["dim"], d.get("lowercase", True))

    def transform(self, text: str) -> dict[int, float]:
        if self.lowercase:
            text = text.lower()
        counts: dict[int, float] = {}

        padded = f" {text} "
        for n in self.char_ngrams:
            if n <= 0 or len(padded) < n:
                continue
            for i in range(len(padded) - n + 1):
                gram = "c%d:%s" % (n, padded[i : i + n])
                b = _bucket(gram, self.dim)
                counts[b] = counts.get(b, 0.0) + 1.0

        words = _WORD_RE.findall(text)
        for n in self.word_ngrams:
            if n <= 0 or len(words) < n:
                continue
            for i in range(len(words) - n + 1):
                gram = "w%d:%s" % (n, " ".join(words[i : i + n]))
                b = _bucket(gram, self.dim)
                counts[b] = counts.get(b, 0.0) + 1.0

        # L2-normalise so message length doesn't dominate.
        norm = math.sqrt(sum(v * v for v in counts.values())) or 1.0
        return {k: v / norm for k, v in counts.items()}
