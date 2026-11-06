# Data & label schema

## Message records

Training and evaluation records come from the labelled TSVs in
`../android/app/src/test/resources/eval/` — the same files that gate the rule engine
(`EvaluationTest.kt`). The ML loader (`src/cybershield_ml/data.py`) parses them **identically** to the
Kotlin loader so the two engines are always measured on the exact same rows.

TSV columns (tab-separated, `#` comment lines and the `id\t...` header are skipped):

| Column | Values | Notes |
|---|---|---|
| `id` | unique string | Must be unique within a file (loader asserts this). |
| `label` | `scam` \| `legit` | The binary target. |
| `lang` | `en` \| `hi` \| `mr` \| `hinglish` \| `mixed` | Used for per-language metrics, not as a feature. |
| `text` | free text | `\n` is a literal line-break escape, un-escaped on load. |

URL and QR TSVs use a 3-column form (`id`, `label`, `payload`) with no language column; those remain
rule-only in Phase 0 (see the plan — ML there is a later, smaller gain).

## Label taxonomy

`labels.json` is the machine-readable taxonomy. The **binary** task (`scam` vs `legit`) is the Phase-0/1
target. The **categories** list mirrors the message-relevant `FindingCode` values in `Model.kt`
one-for-one, including each code's `weight`. This is deliberate: it lets a future multi-label head
(Phase 5) predict the same categories the rules already emit, so an ML category and a rule finding mean
the same thing to the UI and to `Recommendation` mapping. `tests/test_pipeline.py` fails if the two
drift apart on the codes it checks.

## Splits

- **dev** (`messages-dev.tsv`): may be used for training and tuning. A `calibration_fraction` slice is
  carved off *inside* dev for temperature calibration and is never trained on.
- **holdout** (`messages-holdout.tsv`): the honest estimate. The pipeline trains on dev only and
  reports holdout separately. In Phase 2 this is replaced by a frozen split of **real, consented,
  redacted** messages — the only number that should drive the enforce-mode gate.

## Governance

All Phase-0 data is synthetic (written from public advisory patterns; see `docs/EVALUATION.md`).
Real data may only enter through the opt-in, redacted donation flow described in `data/README.md`.
Every snapshot is content-hashed (`data/snapshots/manifest.json`) so a model always records exactly
which data produced it.
