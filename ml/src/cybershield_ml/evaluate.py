"""Evaluation harness.

Mirrors EvaluationTest.kt flag semantics: a sample is "flagged" when its (calibrated) scam probability
clears the flag threshold, analogous to the app showing a warning at MEDIUM+. Reports precision,
recall, false-positive rate, F1, Expected Calibration Error, and a per-language recall breakdown.
"""
from __future__ import annotations

from dataclasses import dataclass, field

from .data import Sample
from .features import Featurizer
from .model import LogisticRegression


@dataclass
class Metrics:
    name: str
    tp: int = 0
    fp: int = 0
    tn: int = 0
    fn: int = 0
    ece: float = 0.0
    per_lang: dict = field(default_factory=dict)  # lang -> (recall, n_scam)
    misses: list = field(default_factory=list)

    @property
    def precision(self) -> float:
        return 1.0 if self.tp + self.fp == 0 else self.tp / (self.tp + self.fp)

    @property
    def recall(self) -> float:
        return 1.0 if self.tp + self.fn == 0 else self.tp / (self.tp + self.fn)

    @property
    def false_positive_rate(self) -> float:
        return 0.0 if self.fp + self.tn == 0 else self.fp / (self.fp + self.tn)

    @property
    def f1(self) -> float:
        p, r = self.precision, self.recall
        return 0.0 if p + r == 0 else 2 * p * r / (p + r)


def _ece(probs: list[float], labels: list[int], bins: int = 10) -> float:
    if not probs:
        return 0.0
    total = len(probs)
    acc = 0.0
    for b in range(bins):
        lo, hi = b / bins, (b + 1) / bins
        idx = [i for i, p in enumerate(probs) if (p > lo or (b == 0 and p == 0.0)) and p <= hi]
        if not idx:
            continue
        conf = sum(probs[i] for i in idx) / len(idx)
        actual = sum(labels[i] for i in idx) / len(idx)
        acc += (len(idx) / total) * abs(conf - actual)
    return acc


def evaluate(name: str, samples: list[Sample], feat: Featurizer, model: LogisticRegression,
             threshold: float) -> Metrics:
    m = Metrics(name)
    probs: list[float] = []
    labels: list[int] = []
    lang_tp: dict[str, int] = {}
    lang_fn: dict[str, int] = {}
    for s in samples:
        p = model.predict_proba(feat.transform(s.text))
        probs.append(p)
        labels.append(1 if s.scam else 0)
        flagged = p >= threshold
        if s.scam and flagged:
            m.tp += 1
            lang_tp[s.lang] = lang_tp.get(s.lang, 0) + 1
        elif s.scam:
            m.fn += 1
            lang_fn[s.lang] = lang_fn.get(s.lang, 0) + 1
            m.misses.append(f"missed {s.id} ({s.lang}, p={p:.2f}): {s.text[:90]!r}")
        elif flagged:
            m.fp += 1
            m.misses.append(f"false alarm {s.id} ({s.lang}, p={p:.2f}): {s.text[:90]!r}")
        else:
            m.tn += 1
    m.ece = _ece(probs, labels)
    for lang in sorted(set(lang_tp) | set(lang_fn)):
        tp, fn = lang_tp.get(lang, 0), lang_fn.get(lang, 0)
        m.per_lang[lang] = (tp / (tp + fn) if tp + fn else 1.0, tp + fn)
    return m


def card(metrics: list[Metrics], model_version: str, data_hash: str) -> str:
    def pct(v: float) -> str:
        return f"{v * 100:.1f}%"

    out = [f"# Model card — {model_version}", "",
           f"- Data snapshot: `{data_hash}`", ""]
    out.append("| Set | Scams | Legit | Precision | Recall | FPR | F1 | ECE |")
    out.append("|---|---|---|---|---|---|---|---|")
    for m in metrics:
        out.append(
            f"| {m.name} | {m.tp + m.fn} | {m.fp + m.tn} | {pct(m.precision)} | "
            f"{pct(m.recall)} | {pct(m.false_positive_rate)} | {pct(m.f1)} | {m.ece:.3f} |"
        )
    for m in metrics:
        if m.per_lang:
            out += ["", f"**{m.name} — recall by language:**", "", "| Lang | Recall | Scams |", "|---|---|---|"]
            out += [f"| {lang} | {pct(r)} | {n} |" for lang, (r, n) in m.per_lang.items()]
    for m in metrics:
        if m.misses:
            out += ["", f"**{m.name} — misses / false alarms:**", ""] + [f"- {x}" for x in m.misses]
    return "\n".join(out) + "\n"
