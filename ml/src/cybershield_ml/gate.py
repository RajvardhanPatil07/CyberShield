"""CI gate: does the model beat the rule baseline without adding false alarms?

Mirrors the floors in EvaluationTest.kt. In "report" mode (Phase 0) it prints the verdict and always
returns success — the shadow-mode rollout. In "enforce" mode (Phase 3, once a real holdout exists) it
returns failure when the model regresses, blocking the merge.
"""
from __future__ import annotations

import json

from .config import ML_ROOT, load_config
from .data import load_tsv
from .evaluate import evaluate
from .features import Featurizer
from .model import LogisticRegression

import sys as _sys
_DATA_DIR = ML_ROOT / "data"
if str(_DATA_DIR) not in _sys.path:
    _sys.path.insert(0, str(_DATA_DIR))
from build_snapshot import latest_snapshot  # noqa: E402


def run_gate(config: dict | None = None) -> bool:
    """Returns True when the gate passes (or is in report mode)."""
    cfg = config or load_config()
    gcfg, dcfg = cfg["gate"], cfg["data"]
    version = str(cfg["model_version"])

    model_path = ML_ROOT / "models" / version / "model.json"
    if not model_path.exists():
        print(f"[gate] no model at {model_path}; run `make train` first.")
        return not bool(gcfg["enforce"])

    bundle = json.loads(model_path.read_text(encoding="utf-8"))
    feat = Featurizer.from_dict(bundle["featurizer"])
    model = LogisticRegression.from_dict(bundle["model"])
    threshold = float(bundle.get("threshold", gcfg["flag_threshold"]))

    snapshot = latest_snapshot()
    if snapshot is None:
        print("[gate] no data snapshot; run `make snapshot` first.")
        return not bool(gcfg["enforce"])
    holdout = load_tsv(snapshot / dcfg["messages_holdout"], has_language=True)
    m = evaluate("Messages (holdout)", holdout, feat, model, threshold)

    baseline_recall = float(gcfg["baseline_recall"])
    max_fpr = float(gcfg["max_false_positive_rate"])
    recall_ok = m.recall >= baseline_recall
    fpr_ok = m.false_positive_rate <= max_fpr
    passed = recall_ok and fpr_ok

    print("=" * 64)
    print(f"[gate] model {version} on holdout ({m.tp + m.fn} scams, {m.fp + m.tn} legit)")
    print(f"[gate]   recall {m.recall * 100:.1f}%  vs rule baseline {baseline_recall * 100:.1f}%  "
          f"-> {'OK' if recall_ok else 'BELOW'}")
    print(f"[gate]   false-positive rate {m.false_positive_rate * 100:.1f}%  "
          f"ceiling {max_fpr * 100:.1f}%  -> {'OK' if fpr_ok else 'OVER'}")
    print(f"[gate]   calibration ECE {m.ece:.3f}")
    mode = "ENFORCE" if gcfg["enforce"] else "REPORT"
    print(f"[gate] verdict: {'PASS' if passed else 'FAIL'}  (mode: {mode})")
    if not passed:
        for x in m.misses:
            print(f"[gate]   - {x}")
    print("=" * 64)

    if gcfg["enforce"]:
        return passed
    if not passed:
        print("[gate] report mode: not blocking. Model stays disabled/shadow until it beats the rules "
              "on a REAL holdout (see docs/AI_ML_PLAN.md phases 2-3).")
    return True
