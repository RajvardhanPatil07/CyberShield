# Data sources & governance

## Phase 0 (now) — synthetic seed corpus

The only data used today is the labelled eval TSVs in
`../../android/app/src/test/resources/eval/`. They were written for this project from public advisory
patterns (RBI, NPCI, CERT-In, I4C, state police) and ordinary Indian SMS/WhatsApp traffic. They are
**not** copies of real messages; numbers and links are fictitious. See `docs/EVALUATION.md`.

Consequence: any model trained only on this data reports accuracy on *these patterns*, not the real
world. This is why the model ships disabled and the gate runs in report mode until Phase 2/3.

## Candidate public sources to grow the seed corpus (document licence before use)

| Source | Use | Licence note |
|---|---|---|
| RBI / CERT-In / I4C advisories, Sanchar Saathi (Chakshu) published samples | scam positives | Public advisories; cite, don't claim ownership |
| Bank fraud-awareness pages | scam positives / legit negatives | Public; check reuse terms |
| Public SMS-spam datasets (e.g. UCI SMS Spam Collection) | English pos/neg | Research use; record the dataset licence |

Record each source's licence in this table before adding its rows to a snapshot.

## Phase 2 — opt-in, redacted real data (the important one)

Real Indian scam text is the only thing that makes the model trustworthy, and it can only be gathered
ethically:

1. On a risky result, the app offers "donate this message to improve detection" (opt-in, off by default).
2. The message is **redacted on-device** with the existing `Redactor` (numbers, e-mails, UPI IDs,
   card/Aadhaar removed) and previewed to the user before anything leaves the phone.
3. Only on explicit tap is the redacted text uploaded, labelled by the user's own report action.
4. A fraction is frozen into an untouched **real holdout** that is never trained on — this becomes the
   number the enforce-mode gate checks.

This matches the README roadmap item ("real, consented and anonymised messages … as an untouched test
split") and the privacy contract in `PRIVACY.md`. No raw message content is ever collected.

## Ingesting contributions

Once the backend has collected opt-in, redacted donations (JSONL from `POST /v1/contribute`, see
`backend/src/contribute.js`), point `contributions.jsonl` in `../config.yaml` at the file and run:

```bash
make ingest     # or: python3 -m cybershield_ml.cli ingest
```

This writes `data/real/messages-real-{dev,holdout}.tsv` in the eval format. The split is deterministic
by record id, so the **real holdout is frozen**: a given sample always lands in the same split as the
corpus grows, and the holdout is never trained on. Ingestion re-checks redaction (`looks_unredacted`)
and drops duplicates and `unsure` labels. With no file configured, `ingest` reports the empty state and
does nothing — the expected state until real submissions arrive.

## Snapshots

`build_snapshot.py` (run via `make snapshot`) copies the current sources into
`snapshots/<timestamp>/`, writes a `manifest.json` with a SHA-256 over the concatenated normalised
rows, and records the row/label/language counts. A trained model stores this hash, so you can always
answer "which exact data produced this model?". Snapshots are git-ignored; the manifest hash is what
gets referenced in model cards and PRs.
