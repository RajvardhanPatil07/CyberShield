"""Export the trained baseline as an on-device asset + a parity reference for the Kotlin port.

Writes:
  android/app/src/main/assets/ml/scam_classifier.json   the bundled model (loaded by MlClassifierFactory)
  android/app/src/test/resources/ml/parity.json          text -> probability, checked by MlClassifierParityTest

The on-device classifier reproduces this model exactly (same md5 feature hashing, logistic regression and
temperature). It only RAISES risk and only fires above an activation threshold chosen here to keep the
false-positive rate at zero on the dev set, so the opt-in feature is conservative.
"""
from __future__ import annotations

import hashlib
import json
from pathlib import Path

from .config import ML_ROOT, load_config
from .data import load_tsv
from .features import Featurizer
from .model import LogisticRegression
from .train import train

import sys as _sys
_DATA_DIR = ML_ROOT / "data"
if str(_DATA_DIR) not in _sys.path:
    _sys.path.insert(0, str(_DATA_DIR))
from build_snapshot import latest_snapshot  # noqa: E402

ANDROID = (ML_ROOT / ".." / "android" / "app" / "src" / "main").resolve()
ASSET = ANDROID / "assets" / "ml" / "scam_classifier.json"
PARITY = (ML_ROOT / ".." / "android" / "app" / "src" / "test" / "resources" / "ml" / "parity.json").resolve()


def _choose_activation_threshold(dev, feat, model) -> float:
    """Smallest threshold in [0.50, 0.99] with zero dev false positives; 0.99 if none qualifies."""
    legit_probs = [model.predict_proba(feat.transform(s.text)) for s in dev if not s.scam]
    t = 0.50
    while t <= 0.99:
        if all(p < t for p in legit_probs):
            return round(t, 2)
        t += 0.01
    return 0.99


def export(config: dict | None = None) -> Path:
    cfg = config or load_config()
    out = train(cfg)  # ensures a fresh model + card
    bundle = json.loads((out / "model.json").read_text(encoding="utf-8"))
    feat = Featurizer.from_dict(bundle["featurizer"])
    model = LogisticRegression.from_dict(bundle["model"])

    snapshot = latest_snapshot()
    dev = load_tsv(snapshot / cfg["data"]["messages_dev"], has_language=True)
    holdout = load_tsv(snapshot / cfg["data"]["messages_holdout"], has_language=True)
    activation = _choose_activation_threshold(dev, feat, model)

    asset = {
        "model_version": bundle["model_version"],
        "data_hash": bundle["data_hash"],
        "activation_threshold": activation,
        "featurizer": feat.to_dict(),
        "dim": model.dim,
        "bias": model.b,
        "temperature": model.temperature,
        "weights": {str(i): w for i, w in enumerate(model.w) if w != 0.0},
    }
    ASSET.parent.mkdir(parents=True, exist_ok=True)
    ASSET.write_text(json.dumps(asset, ensure_ascii=False), encoding="utf-8")

    # Parity reference: a deterministic spread of dev + holdout texts and their raw probabilities.
    sample = (dev + holdout)[::7][:40]
    cases = [{"text": s.text, "prob": model.predict_proba(feat.transform(s.text))} for s in sample]
    PARITY.parent.mkdir(parents=True, exist_ok=True)
    PARITY.write_text(json.dumps({"activation_threshold": activation, "cases": cases}, ensure_ascii=False, indent=1), encoding="utf-8")

    sha = hashlib.sha256(ASSET.read_bytes()).hexdigest()
    size_kb = ASSET.stat().st_size / 1024
    print(f"[export] activation_threshold={activation}")
    print(f"[export] wrote {ASSET}  ({size_kb:.1f} KB, sha256 {sha[:16]}…)")
    print(f"[export] wrote {PARITY}  ({len(cases)} parity cases)")
    return ASSET
