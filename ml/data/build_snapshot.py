"""Build a hash-pinned snapshot of the labelled data.

Copies the current eval TSVs into ml/data/snapshots/<timestamp>/, writes manifest.json with a
SHA-256 over the normalised rows and the per-label / per-language counts. A trained model records
the manifest hash so you can always answer "which exact data produced this model?".
"""
from __future__ import annotations

import datetime as _dt
import hashlib
import json
import shutil
from pathlib import Path

from cybershield_ml.config import ML_ROOT, load_config
from cybershield_ml.data import load_tsv


def build_snapshot(config: dict | None = None) -> Path:
    cfg = config or load_config()
    dcfg = cfg["data"]
    eval_dir = (ML_ROOT / dcfg["eval_dir"]).resolve()
    files = [dcfg["messages_dev"], dcfg["messages_holdout"]]

    ts = _dt.datetime.now(_dt.timezone.utc).strftime("%Y%m%dT%H%M%SZ")
    out = ML_ROOT / "data" / "snapshots" / ts
    out.mkdir(parents=True, exist_ok=True)

    hasher = hashlib.sha256()
    manifest: dict = {"created_utc": ts, "eval_dir": str(eval_dir), "files": {}}
    for name in files:
        src = eval_dir / name
        samples = load_tsv(src, has_language=True)
        shutil.copy2(src, out / name)
        # Normalised rows for a stable, order-independent hash.
        rows = sorted(f"{s.id}\t{'scam' if s.scam else 'legit'}\t{s.lang}\t{s.text}" for s in samples)
        for r in rows:
            hasher.update(r.encode("utf-8"))
            hasher.update(b"\n")
        langs: dict[str, int] = {}
        for s in samples:
            langs[s.lang] = langs.get(s.lang, 0) + 1
        manifest["files"][name] = {
            "rows": len(samples),
            "scam": sum(1 for s in samples if s.scam),
            "legit": sum(1 for s in samples if not s.scam),
            "languages": langs,
        }
    manifest["data_hash"] = hasher.hexdigest()
    (out / "manifest.json").write_text(json.dumps(manifest, indent=2, ensure_ascii=False), encoding="utf-8")

    # A stable pointer to the latest snapshot.
    latest = ML_ROOT / "data" / "snapshots" / "latest.txt"
    latest.write_text(str(out), encoding="utf-8")
    return out


def latest_snapshot() -> Path | None:
    latest = ML_ROOT / "data" / "snapshots" / "latest.txt"
    if latest.exists():
        p = Path(latest.read_text(encoding="utf-8").strip())
        if p.exists():
            return p
    return None
