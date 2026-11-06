# CyberShield ML — training & evaluation

Phase 0 foundations for the on-device scam-message classifier described in
[`../docs/AI_ML_PLAN.md`](../docs/AI_ML_PLAN.md).

This directory is a **reproducible training repo**: one command turns a pinned snapshot of the
labelled data into a model plus a metrics card, and a CI gate blocks any model that regresses
below the rule-based baseline. Nothing here ships in the app yet — the model is built and measured
in *shadow mode* and only promoted once it beats the rules on a **real** (not synthetic) holdout.

## What it is (and is not) in Phase 0

- **Is:** a working, dependency-free pipeline — data loading, a trainable baseline classifier,
  temperature calibration, an evaluation harness that mirrors the app's flag semantics, and a gate.
- **Is not:** the production model. The Phase-0 classifier is a pure-Python char-n-gram logistic
  regression so the whole pipeline runs today with **zero third-party dependencies and no network**.
  The production model (distilled MuRIL → LiteRT, see the plan §4.1) is a drop-in replacement at the
  `train`/`export` step; its pinned dependencies live in `requirements.txt` and are only needed then.

## One command

```bash
cd ml
make all          # snapshot -> train -> calibrate -> evaluate -> gate (report mode)
# or, without make:
python3 -m cybershield_ml.cli all
```

Individual stages:

```bash
make snapshot     # build a hash-pinned data snapshot from the Android eval TSVs
make train        # train the baseline, write models/<version>/model.json + metrics card
make eval         # evaluate on dev + holdout, write the metrics card
make gate         # compare holdout metrics to the rule baseline; see "The gate" below
make export       # write the bundled Android asset + the Kotlin parity reference (see below)
make ingest       # fold opt-in redacted contributions into the frozen real corpus (data/README.md)
make test         # fast self-tests for the pipeline
```

### The bundled on-device model

`make export` (part of `make all`) writes two committed files that ship the model to the app:

- `../android/app/src/main/assets/ml/scam_classifier.json` — the model loaded by `MlClassifierFactory`.
- `../android/app/src/test/resources/ml/parity.json` — text→probability reference.

`MlClassifierParityTest` on the Android side asserts the Kotlin port (`LogisticTextClassifier`) matches
these probabilities, exactly as the web demo pins its JS engine to Kotlin. **Always regenerate both with
`make export` after retraining** so the asset and the reference stay in lock-step.

No `pip install` is needed for the Phase-0 baseline. `requirements.txt` is only for the production
MuRIL/LiteRT path and is intentionally not installed by `make`.

## Layout

```
ml/
  config.yaml              training + gate configuration, data paths, model version
  Makefile                 one-command entry points
  requirements.txt         pinned deps for the PRODUCTION (MuRIL/LiteRT) path only
  schema/
    labels.json            label taxonomy, aligned 1:1 with FindingCode in Model.kt
    SCHEMA.md              data + label schema documentation and governance
  data/
    README.md              data sources, licences, consent/redaction policy
    build_snapshot.py      builds + hashes the data snapshot (invoked via the CLI)
    snapshots/             generated, git-ignored: pinned snapshots + manifest.json
  src/cybershield_ml/
    cli.py                 `python -m cybershield_ml.cli <stage>`
    data.py                TSV loader (mirrors EvaluationTest.kt parsing exactly)
    features.py            deterministic multilingual char/word n-gram hashing
    model.py               pure-Python logistic regression (baseline) + calibration
    train.py               trains, versions and serialises a model
    evaluate.py            precision / recall / FPR / F1 / ECE, per-language breakdown
    gate.py                CI gate vs. the rule baseline (report or enforce)
  models/                  generated, git-ignored except READMEs: versioned models + cards
  tests/                   pipeline self-tests
```

## The gate

`gate.py` mirrors the floors in `../android/app/src/test/java/io/cybershield/app/security/EvaluationTest.kt`:
a sample counts as *flagged* when the model's calibrated probability clears the MEDIUM threshold, and
the gate checks holdout **recall** against the rule baseline and holds **false-positive rate** at the
configured ceiling (0% by default — false alarms break trust faster than misses).

Two modes, set by `gate.enforce` in `config.yaml`:

- **report** (Phase 0 default): prints whether the model beats the rules and always exits 0. This is
  the shadow-mode rollout from the plan — the model is measured but never blocks CI or ships enabled.
- **enforce** (flip on in Phase 3, once a real holdout exists): exits non-zero if the model fails to
  beat the baseline, blocking the merge.

The rule baseline it compares against is recorded in `config.yaml` from the published numbers in
[`../docs/EVALUATION.md`](../docs/EVALUATION.md) and the Kotlin floors, because this Python harness
cannot run the Kotlin engine.

## Honesty note

Trained only on the current **synthetic** eval data, the baseline's numbers describe those patterns,
not real-world accuracy — exactly the caveat in `docs/EVALUATION.md`. Phase 0 exists to make the
pipeline and gate real; Phases 2–3 (opt-in redacted real data, frozen real holdout) are what make the
model trustworthy enough to enable. See the roadmap in `docs/AI_ML_PLAN.md`.
