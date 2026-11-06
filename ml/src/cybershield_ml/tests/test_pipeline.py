"""Fast, dependency-free self-tests. Run: python -m cybershield_ml.tests.test_pipeline"""
from __future__ import annotations

import hashlib
import json
import sys

from ..config import ML_ROOT, load_config
from ..data import load_tsv, split_calibration
from ..features import Featurizer
from ..model import LogisticRegression


def _check(cond: bool, msg: str):
    if not cond:
        raise AssertionError(msg)


def test_config_loads():
    cfg = load_config()
    _check(cfg["features"]["char_ngrams"] == [3, 4, 5], "char_ngrams parse")
    _check(cfg["gate"]["enforce"] is False, "enforce bool parse")
    _check(isinstance(cfg["train"]["learning_rate"], float), "float parse")


def test_loader_matches_kotlin_contract():
    cfg = load_config()
    path = (ML_ROOT / cfg["data"]["eval_dir"] / cfg["data"]["messages_holdout"]).resolve()
    samples = load_tsv(path, has_language=True)
    _check(len(samples) > 0, "loaded holdout")
    _check(len({s.id for s in samples}) == len(samples), "unique ids")
    _check(all(s.lang in ("en", "hi", "mr", "hinglish", "mixed") for s in samples), "known langs")


def test_labels_align_with_findingcode():
    labels = json.loads((ML_ROOT / "schema" / "labels.json").read_text(encoding="utf-8"))
    model_kt = (ML_ROOT / ".." / "android" / "app" / "src" / "main" / "java" / "io"
                / "cybershield" / "app" / "security" / "Model.kt").resolve()
    text = model_kt.read_text(encoding="utf-8")
    for cat in labels["categories"]:
        code = cat["code"]
        _check(f"{code}(" in text, f"FindingCode {code} missing from Model.kt (taxonomy drift)")


def test_featurizer_deterministic_and_multilingual():
    f = Featurizer([3, 4], [1, 2], 2048)
    a = f.transform("your KYC will be blocked")
    b = f.transform("your KYC will be blocked")
    _check(a == b, "featurizer deterministic")
    dev = f.transform("आपका खाता बंद हो जाएगा")  # Devanagari produces features (script-agnostic)
    _check(len(dev) > 0, "devanagari featurised")


def test_model_learns_toy_separation():
    f = Featurizer([3, 4], [1, 2], 2048)
    pos = ["send your otp now", "share upi pin to receive money", "update kyc or account blocked"]
    neg = ["lunch at 1pm?", "happy birthday", "meeting moved to tuesday"]
    X = [f.transform(t) for t in pos + neg]
    y = [1, 1, 1, 0, 0, 0]
    m = LogisticRegression(2048).fit(X, y, epochs=60, lr=0.5, l2=0.0, seed=1)
    _check(m.predict_proba(f.transform("send your otp now")) > 0.5, "learns positive")
    _check(m.predict_proba(f.transform("happy birthday")) < 0.5, "learns negative")


def test_calibration_runs():
    f = Featurizer([3], [1], 1024)
    X = [f.transform(t) for t in ("otp scam", "hello friend")]
    m = LogisticRegression(1024).fit(X, [1, 0], epochs=20, lr=0.5, l2=0.0, seed=1)
    t = m.calibrate_temperature(X, [1, 0])
    _check(t > 0, "temperature positive")


def test_contribution_split_is_deterministic_and_frozen():
    from ..contributions import split_for
    ids = [hashlib.sha1(str(i).encode()).hexdigest() for i in range(200)]
    first = {i: split_for(i, 20) for i in ids}
    # Same ids always land in the same split; roughly holdout_pct land in holdout.
    _check(all(split_for(i, 20) == first[i] for i in ids), "split deterministic")
    holdout = sum(1 for v in first.values() if v == "holdout")
    _check(5 <= holdout <= 60, f"holdout share plausible, got {holdout}/200")


def test_ingest_dedups_skips_unredacted_and_respects_labels():
    from ..contributions import ingest
    lines = [
        json.dumps({"id": "a", "label": "scam", "language": "en", "kind": "message", "text": "update your kyc now"}),
        json.dumps({"id": "a", "label": "scam", "language": "en", "kind": "message", "text": "update your kyc now"}),  # dup id
        json.dumps({"id": "b", "label": "scam", "language": "en", "kind": "message", "text": "call 9876543210"}),       # unredacted
        json.dumps({"id": "c", "label": "legit", "language": "mr", "kind": "message", "text": "उद्या भेटू"}),
        json.dumps({"id": "d", "label": "unsure", "language": "en", "kind": "message", "text": "maybe a scam"}),         # excluded label
    ]
    dev, holdout, skipped = ingest(lines, holdout_pct=20)
    kept = {r[0] for r in dev + holdout}
    _check(kept == {"a", "c"}, f"kept wrong ids: {kept}")
    _check(skipped == 3, f"expected 3 skipped, got {skipped}")


def main():
    tests = [v for k, v in sorted(globals().items()) if k.startswith("test_") and callable(v)]
    failed = 0
    for t in tests:
        try:
            t()
            print(f"ok   {t.__name__}")
        except Exception as e:  # noqa: BLE001
            failed += 1
            print(f"FAIL {t.__name__}: {e}")
    print(f"\n{len(tests) - failed}/{len(tests)} passed")
    sys.exit(1 if failed else 0)


if __name__ == "__main__":
    main()
