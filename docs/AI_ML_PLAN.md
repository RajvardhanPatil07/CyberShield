# CyberShield — AI/ML upgrade plan

Research and a production-grade roadmap for adding machine learning to CyberShield without breaking its two defining promises: **on-device by default** and **explainable results**.

Status: proposal (6 Nov 2026). Nothing here ships until the dataset and evaluation gates below are in place.

---

## 1. Why add ML, and where it actually helps

CyberShield's detection today is entirely rule-based: `ScamLens` matches phrase dictionaries across English/Hindi/Marathi/Hinglish, sums `FindingCode` weights into a 0–100 score, and `LinkGuard`/`QrGuard` do the same for URLs and UPI QR codes (`android/app/src/main/java/io/cybershield/app/security/`).

That design is fast, offline, and fully explainable — every point in the score traces to a named signal. Its weakness is stated plainly in the README and `docs/EVALUATION.md`:

> On the project's own holdout set it misses **1 in 5 scam messages** (80.0% recall), "mostly familiar tricks in unfamiliar words."

That is the single highest-value place for ML. Rules miss **novel phrasings** of known scams — the same intent ("you must pay a fee to release your refund") written in words that aren't in the dictionary, or mixed-script/transliterated variants. A trained classifier generalises over wording in a way hand-written phrase lists never will.

ML is **not** worth it where rules already win:
- **QR/UPI parsing** (100% on holdout) — deterministic structure, leave it rule-based.
- **URL structure** (86.7% recall) — char/lexical rules are strong and explainable; ML is a smaller, optional gain here (see §4.2).

So the plan is targeted, not a rewrite: **add an ML message classifier that runs alongside the rules and catches what they miss, while the rules keep providing the human-readable reasons.**

---

## 2. Design constraints (non-negotiable)

Any ML we add must respect what makes the app trustworthy:

1. **On-device for the default path.** The message/URL/QR detection path must keep working with zero network and zero consent dialog. That rules out a cloud-only classifier as the primary detector. Inference must be a bundled LiteRT (formerly TensorFlow Lite) model.
2. **Explainability is a feature, not a nice-to-have.** Users see *why* something is risky, in their language. A bare neural probability ("87% scam") is not acceptable on its own. The rule findings must remain the explanation; ML changes the *score/confidence*, and the UI gains a short "why the AI flagged this" from the top contributing signals — never an unexplained verdict.
3. **No raw content leaves the phone without the existing consent + redaction flow.** The cloud AI path (`backend/src/ai.js` + `Redactor`) already does this correctly. On-device training data collection must be opt-in and anonymised the same way.
4. **Size and latency budget.** APK is ~38 MB today. The model + runtime should add well under ~15 MB and classify a message in < 100 ms on a mid-range phone. This caps us at small/distilled models, not a 7B on-device LLM.
5. **No regressions on the labelled eval.** `EvaluationTest` already gates on precision/recall against `android/app/src/test/resources/eval/*.tsv`. The ML model must beat current recall **without dropping precision below 100% on legit messages** — false alarms destroy trust faster than misses.

---

## 3. Recommended architecture: a fused rule + ML scorer

Keep `ScamLens` as-is. Add a parallel ML classifier and a **fusion layer** that combines both into the final `RiskAssessment`.

```
             message text (on device)
                      │
        ┌─────────────┴─────────────┐
        ▼                           ▼
  ScamLens (rules)           ML classifier (LiteRT)
  findings + weights         P(scam) 0..1, calibrated
        │                           │
        └─────────────┬─────────────┘
                      ▼
              Fusion layer
   final score + level + reasons (from rules)
   + "AI confidence" badge (from ML)
                      │
                      ▼
          RiskAssessment (unchanged shape)
```

**Fusion rule (first version, deliberately conservative):**
- Rules produce `ruleScore` (0–100) and findings exactly as now.
- ML produces `mlProb` (0–1), calibrated (§6).
- `finalScore = max(ruleScore, round(mlProb * 100))` — the ML can only *raise* risk, never silence a rule hit. This directly attacks the recall gap (novel wording the rules miss) while making it structurally impossible for the model to hide a scam the rules already caught.
- When ML raises the level but rules found little, attach a finding like `ML_PATTERN_MATCH` ("Wording resembles known scam messages, even though no specific red-flag phrase matched") so the result is still explainable and honest about its source.
- A later version can let ML *lower* a false alarm, but only after the model is proven on real, consented data — not in v1.

This keeps `RiskAssessment` and the entire UI contract identical. The model is additive and reversible behind a flag.

---

## 4. The models

### 4.1 Message classifier (primary — highest ROI)

**Task:** binary scam / not-scam, multilingual (EN, HI, MR, romanised Hindi, mixed script), with per-category heads as a stretch goal.

**Model options evaluated:**

| Option | Multilingual Indian text | Size | Verdict |
|---|---|---|---|
| MediaPipe Text Classifier — **Average Word Embedding** (Model Maker) | Weak; needs a learned vocab, poor on code-mixed/Devanagari | ~1 MB | Fast baseline only |
| MediaPipe Text Classifier — **MobileBERT** | English-centric | ~25 MB | Too big + wrong languages |
| **Distilled MuRIL / IndicBERT head → LiteRT** | **Strong** — MuRIL is Google's model built for 17 Indian languages incl. transliteration | ~10–30 MB (distilled/quantised) | **Recommended** |
| On-device small LLM (Gemma-class) | Strong but heavy | 100s of MB | Overkill, blows size/latency budget |

**Recommendation:** fine-tune a **distilled, quantised MuRIL-style encoder** (MuRIL is purpose-built for Indian languages and handles Latin-script Hindi, which is exactly CyberShield's `hinglish` case) with a small binary classification head, export to **LiteRT** with int8 quantisation, and run it via MediaPipe Tasks `TextClassifier` or the LiteRT `NLClassifier` task library. Target < 15 MB quantised. This is the standard, supported Google on-device path as of 2026 ([MediaPipe Text Classifier for Android](https://developers.google.com/edge/mediapipe/solutions/text/text_classifier/android), [LiteRT NLClassifier](https://developers.google.com/edge/litert/libraries/task_library/nl_classifier)). *Content rephrased for compliance with licensing restrictions.*

If 15 MB is still too large after quantisation, fall back to a **Text Embedder + tiny classifier**: use MediaPipe's multilingual text embedder to produce a vector, then a few-KB logistic-regression/MLP head trained on our data. Smaller and nearly as good for a binary task.

### 4.2 URL classifier (optional, phase 2)

`LinkGuard` already hits 86.7% recall with lexical rules. A small **character-level CNN/GRU on the raw URL string** (trained on phishing vs. benign domains) catches lookalikes and DGA-style hosts that rules miss, in a few hundred KB. Fuse it the same max() way. Lower priority than messages.

### 4.3 Cloud AI path (already exists — upgrade, don't replace)

`backend/src/ai.js` already sends **redacted** text to Gemini with JSON-schema output. Production upgrades:
- **RAG over Indian scam advisories** (RBI/CERT-In/Chakshu bulletins) so the explanation cites current, real scam patterns instead of model memory.
- **Structured scoring**, not just prose: return a category + confidence that the app can reconcile with the on-device verdict and show disagreement honestly.
- Keep it strictly **opt-in, redacted, previewed** — no change to that contract.

---

## 5. Data: the real bottleneck

The model is the easy part. **Labelled, representative, legally clean data is the hard part**, and the current eval sets are explicitly synthetic ("written for this project from public advisory patterns… not real collected messages"). Production ML needs real distribution coverage.

Plan:
1. **Seed corpus** from public sources: RBI/CERT-In advisories, Chakshu/Sanchar Saathi published samples, bank fraud-awareness pages, public SMS-spam datasets (e.g. UCI SMS Spam as English negatives/positives), and the existing `*.tsv` files. Document licence of each source.
2. **Opt-in contribution** from the app: on a risky result, let the user tick "donate this message to improve detection" — the message is **redacted** (reuse `Redactor`), labelled by the user's own report action, and uploaded only on that explicit tap. This is the only ethical way to get real Indian scam text, and it matches the README roadmap item ("real, consented and anonymised messages").
3. **Labelling guide + schema**: binary label + the `FindingCode` categories already defined in `Model.kt`, so the ML categories line up with the rule taxonomy.
4. **Strict splits**: keep an **untouched holdout** of *real* messages that is never seen during training — the only honest accuracy estimate (README roadmap already calls for this).
5. **Class balance & language balance**: track per-language recall, not just overall, so Marathi/Hinglish don't silently underperform.

**Reality check:** until real consented data exists, any ML model trained only on synthetic data will report inflated numbers and may *underperform* the rules on real messages. The honest v1 scope is: build the full pipeline, train on seed+synthetic, ship it **behind a disabled-by-default flag**, and only enable it once it beats rules on a real holdout.

---

## 6. Calibration, because scores are shown to users

The app shows a 0–100 score and LOW/MEDIUM/HIGH bands (thresholds 30/60 in `Model.kt`). A raw neural sigmoid output is **not** a probability you can threshold meaningfully. Before fusion we must **calibrate** (temperature scaling or isotonic regression on a validation set) so that `mlProb` means what it says, then pick fusion thresholds on the validation set — not by guessing. Report Expected Calibration Error alongside precision/recall.

---

## 7. MLOps / production-grade pipeline

What makes this "production grade" rather than a notebook demo:

- **Training repo** (`ml/` or a sibling repo): reproducible training from pinned data snapshots; one command produces the `.tflite` + a metrics report. Seed-controlled, versioned.
- **Model registry & versioning**: each model has a semver, a data-snapshot hash, and a metrics card. The APK records which model version it shipped.
- **Model signing**: the bundled `.tflite` is checksum-pinned (the repo already checksum-pins the Gradle wrapper and Ed25519-signs other artefacts conceptually) so a tampered model can't be swapped in.
- **CI gate**: extend the existing `EvaluationTest` so CI **fails** if a new model drops recall or precision below the current rule baseline on the holdout. No model merges without beating the gate. Wire into `.github/workflows/android-build.yml`.
- **Offline/online parity**: the `web/` demo ports the Kotlin engine to JS and a test asserts they agree on 383 cases. The ML model must have the same parity story (e.g. run the same `.tflite` via LiteRT.js/ONNX Runtime Web) or the web demo clearly documents that it is rules-only.
- **Shadow mode first**: ship the model computing its verdict but **not** affecting the shown score, logging only aggregate agreement/disagreement (no content). Promote to active fusion only after the shadow metrics look right.
- **Drift monitoring**: track the distribution of `mlProb` and rule/ML disagreement over time (aggregates only, privacy-preserving) to catch scam-pattern drift and trigger retraining.
- **Rollback**: model behind a remote-configurable + local flag; a bad model is disabled without an app update where possible, and always revertible by shipping the previous version.

---

## 8. Phased roadmap

| Phase | Deliverable | Gate to exit | Status |
|---|---|---|---|
| **0. Foundations** | `ml/` training repo, data schema aligned to `FindingCode`, seed corpus with documented licences, calibration + eval harness extending `EvaluationTest` | Reproducible train→metrics in one command | **Done** — `ml/`, `make all`, report-mode gate, CI (`ml-eval.yml`) |
| **1. On-device message classifier (scaffolding)** | Fusion code + `MlClassifier` interface behind a **disabled** flag; `ML_PATTERN_MATCH` explainable finding; UI toggle | App behaves identically to rules-only when disabled; builds, tests, lint green | **Done (scaffolding)** — interface, `MlFusion`, flag, UI; LiteRT model still to drop in |
| **1b. Bundle a model** | An on-device classifier in assets; `MlClassifierFactory` loads it; Python↔Kotlin parity test | Model loads and runs on-device; opt-in; parity verified | **Done (baseline)** — logistic-regression model bundled (204 KB), exact Kotlin port, parity test. MuRIL→LiteRT upgrade optional later |
| **2. Consented data loop** | Opt-in redacted donation flow in-app, backend `/v1/contribute`, frozen real-holdout ingestion, per-language metrics | A real (not synthetic) holdout exists and is frozen | **Done (plumbing)** — donate flow, endpoint, `ingest`; awaiting real submissions |
| **3. Activate fusion by default** | Flip the gate to **enforce**, enable fusion in a shipped build (not just opt-in), calibrated thresholds | Real-holdout recall up, precision held, CI gate green | **Intentionally not done** — gated on real data (see below) |
| **4. URL char-model + cloud RAG** | Optional URL ML; Gemini RAG over live advisories with structured scoring | Measurable gain over `LinkGuard`; cloud path still opt-in/redacted | Pending |
| **5. Per-category heads** | Multi-label model predicting scam categories to enrich (not replace) rule findings | No regression; categories map to existing taxonomy | Pending |

Phases 0–2 build the machinery; 1b and 3 (bundling and enabling the model) depend on real-world data from the donation loop, and are where the project genuinely becomes production-grade.

### Implementation status (6 Nov 2026)

Phases 0, 1, 1b and 2 are implemented and verified:

- **Android:** `security/MlFusion.kt` (`max()` fusion, raise-only), `ml/MlClassifier.kt` (interface + `Disabled`), `ml/LogisticTextClassifier.kt` (on-device model, exact Kotlin port of the trained baseline), `ml/MlClassifierFactory.kt` (loads `assets/ml/scam_classifier.json`, falls back to `Disabled`), `RiskAnalyzer.message(..., classifier)`, a disabled-by-default `mlAssist` setting, and an opt-in redacted donation flow in `ScamLensScreen`. **78 JVM unit tests pass** (fusion + Python↔Kotlin parity), lint clean, the 204 KB model ships in the APK.
- **Backend:** `POST /v1/contribute` with server-side `looksUnredacted` guard, pluggable JSONL sink, rate limiting, and `not_configured` when `CONTRIBUTIONS_DIR` is unset. 28 tests pass.
- **ml/:** `make all` runs snapshot → train → gate → **export** (writes the Android asset + parity reference); `contributions` ingests donated JSONL into frozen dev/holdout TSVs; 8 self-tests pass.

**Why phase 3 (default-on) is intentionally not done.** The bundled classifier is trained only on the
synthetic eval data, so its real-world accuracy is unknown and its measured false-positive rate on the
synthetic holdout is high. The plan's own gate (§2, §5) forbids enabling a model by default until it
beats the rules on a **real, frozen holdout**, which the donation loop has not yet collected. The model
is therefore shipped **opt-in and labelled experimental**, and its activation threshold (0.84, chosen to
give zero false positives on the dev set) means it only ever raises risk when highly confident. Flipping
the CI gate to enforce and enabling fusion in a shipped build are the two remaining steps, and both are
correctly blocked on real data rather than forced through on synthetic data.

**The baseline vs. MuRIL.** The on-device model is a char/word-n-gram logistic regression, not the
distilled-MuRIL→LiteRT transformer from §4.1. This was a deliberate trade-off: it needs no 15 MB model,
no TFLite runtime dependency and no GPU, runs in well under the latency budget, and is reproducible and
parity-tested. When real data justifies it, the transformer drops in behind the same `MlClassifier`
interface — only `MlClassifierFactory` and a new wrapper class change.

---

## 9. Risks and honest limitations

- **Garbage-in:** a model trained on synthetic data will look great in tests and may fail on real scams. Mitigated by the shadow-mode + real-holdout gates — do not skip them.
- **False alarms** are worse than misses for user trust; the fusion `max()` rule and the precision floor exist specifically to prevent the model from inventing risk.
- **Explainability debt:** we must never show an unexplained ML verdict; the `ML_PATTERN_MATCH` finding and rule-sourced reasons are mandatory.
- **Language fairness:** overall accuracy can hide poor Marathi/Hinglish performance; gate on per-language recall.
- **Maintenance:** an ML model is a living asset needing retraining as scams evolve — the MLOps pipeline (§7) is the cost of doing this properly, not optional.

---

## 10. One-paragraph summary

Keep CyberShield's explainable rule engine as the backbone. Add a small, quantised, **on-device multilingual message classifier** (distilled MuRIL → LiteRT, run via MediaPipe/LiteRT) that runs beside the rules and can only *raise* risk, closing the 1-in-5 recall gap on novel scam wording while the rules keep providing the human-readable reasons. Build it production-grade: a reproducible training repo, calibrated scores, a CI accuracy gate that blocks regressions, an opt-in redacted data-donation loop for real Indian scam text, shadow-mode rollout, and drift monitoring. Ship disabled, enable only after it beats the rules on a *real* holdout.
