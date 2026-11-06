"""Train the baseline model and write a versioned model directory + metrics card."""
from __future__ import annotations

import json
from pathlib import Path

from .config import ML_ROOT, load_config
from .data import load_tsv, split_calibration
from .evaluate import card, evaluate
from .features import Featurizer
from .model import LogisticRegression

import sys as _sys
_DATA_DIR = ML_ROOT / "data"
if str(_DATA_DIR) not in _sys.path:
    _sys.path.insert(0, str(_DATA_DIR))
from build_snapshot import build_snapshot, latest_snapshot  # noqa: E402


def _read_hash(snapshot: Path) -> str:
    manifest = json.loads((snapshot / "manifest.json").read_text(encoding="utf-8"))
    return manifest["data_hash"]


def train(config: dict | None = None) -> Path:
    cfg = config or load_config()
    dcfg, fcfg, tcfg = cfg["data"], cfg["features"], cfg["train"]

    snapshot = latest_snapshot() or build_snapshot(cfg)
    data_hash = _read_hash(snapshot)

    dev = load_tsv(snapshot / dcfg["messages_dev"], has_language=True)
    holdout = load_tsv(snapshot / dcfg["messages_holdout"], has_language=True)

    train_set, cal_set = split_calibration(dev, float(dcfg["calibration_fraction"]), int(tcfg["seed"]))

    feat = Featurizer(fcfg["char_ngrams"], fcfg["word_ngrams"], int(fcfg["dim"]), bool(fcfg["lowercase"]))
    X = [feat.transform(s.text) for s in train_set]
    y = [1 if s.scam else 0 for s in train_set]

    model = LogisticRegression(int(fcfg["dim"]))
    model.fit(X, y, epochs=int(tcfg["epochs"]), lr=float(tcfg["learning_rate"]),
              l2=float(tcfg["l2"]), seed=int(tcfg["seed"]))
    model.calibrate_temperature([feat.transform(s.text) for s in cal_set],
                                [1 if s.scam else 0 for s in cal_set])

    threshold = float(cfg["gate"]["flag_threshold"])
    metrics = [
        evaluate("Messages (dev)", dev, feat, model, threshold),
        evaluate("Messages (holdout)", holdout, feat, model, threshold),
    ]

    version = str(cfg["model_version"])
    out = ML_ROOT / "models" / version
    out.mkdir(parents=True, exist_ok=True)
    (out / "model.json").write_text(json.dumps({
        "model_version": version,
        "data_hash": data_hash,
        "featurizer": feat.to_dict(),
        "model": model.to_dict(),
        "threshold": threshold,
    }, ensure_ascii=False), encoding="utf-8")
    (out / "card.md").write_text(card(metrics, version, data_hash), encoding="utf-8")
    return out
