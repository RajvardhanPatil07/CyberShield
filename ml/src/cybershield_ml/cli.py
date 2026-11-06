"""One entry point for the pipeline: python -m cybershield_ml.cli <stage>."""
from __future__ import annotations

import sys

from .config import load_config

STAGES = ("snapshot", "train", "eval", "gate", "ingest", "export", "all")


def _snapshot(cfg):
    from pathlib import Path
    sys.path.insert(0, str(Path(__file__).resolve().parents[2] / "data"))
    from build_snapshot import build_snapshot
    out = build_snapshot(cfg)
    print(f"[snapshot] wrote {out}")
    return out


def _train(cfg):
    from .train import train
    out = train(cfg)
    print(f"[train] wrote {out}")
    print((out / "card.md").read_text(encoding="utf-8"))
    return out


def _eval(cfg):
    # Evaluation is produced as part of train (writes the card). Re-run train to refresh it.
    return _train(cfg)


def _gate(cfg):
    from .gate import run_gate
    ok = run_gate(cfg)
    if not ok:
        sys.exit(1)


def _export(cfg):
    from .export import export
    export(cfg)


def _ingest(cfg):
    from pathlib import Path
    from .config import ML_ROOT
    from .contributions import ingest, write_tsv

    ccfg = cfg.get("contributions", {})
    jsonl = str(ccfg.get("jsonl", "")).strip()
    if not jsonl:
        print("[ingest] contributions.jsonl is empty — no real data collected yet (expected in early Phase 2).")
        return
    src = (ML_ROOT / jsonl).resolve()
    if not src.exists():
        print(f"[ingest] no file at {src}")
        return
    pct = int(ccfg.get("real_holdout_pct", 20))
    out_dir = ML_ROOT / str(ccfg.get("out_dir", "data/real"))
    out_dir.mkdir(parents=True, exist_ok=True)
    with src.open(encoding="utf-8") as fh:
        dev, holdout, skipped = ingest(fh, pct)
    write_tsv(out_dir / "messages-real-dev.tsv", dev, "dev")
    write_tsv(out_dir / "messages-real-holdout.tsv", holdout, "holdout")
    print(f"[ingest] dev={len(dev)} holdout={len(holdout)} skipped={skipped} -> {out_dir}")


def main(argv: list[str] | None = None) -> None:
    argv = argv if argv is not None else sys.argv[1:]
    stage = argv[0] if argv else "all"
    if stage not in STAGES:
        print(f"usage: python -m cybershield_ml.cli [{'|'.join(STAGES)}]")
        sys.exit(2)
    cfg = load_config()
    if stage == "snapshot":
        _snapshot(cfg)
    elif stage == "train":
        _train(cfg)
    elif stage == "eval":
        _eval(cfg)
    elif stage == "gate":
        _gate(cfg)
    elif stage == "ingest":
        _ingest(cfg)
    elif stage == "export":
        _export(cfg)
    elif stage == "all":
        _snapshot(cfg)
        _train(cfg)
        _gate(cfg)
        _export(cfg)


if __name__ == "__main__":
    main()
