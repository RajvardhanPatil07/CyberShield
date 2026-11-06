# Detection evaluation

This page records how well the on-device engines (ScamLens, LinkGuard, QRGuard) do on a labelled test set, and how the numbers were produced.

## Method

- The labelled sets are in `android/app/src/test/resources/eval/`: messages, links and QR payloads, each split into **dev** and **holdout** files.
- **Dev** files may be used to tune the rules. **Holdout** files must not be: they give the fairer estimate. When a holdout miss is worth fixing, add a similar but differently worded case to the dev file and fix that one. The holdout number then stops being independent, so write fresh holdout cases.
- A sample counts as **flagged** when the app shows a warning, i.e. the level is *Caution advised* or *High risk signals* (score ≥ 30).
- `EvaluationTest` runs on every `./gradlew :app:testDebugUnitTest`. It writes the table and every miss to `android/app/build/reports/eval/summary.md`, and fails if recall drops or a false alarm appears compared with the results below.

### What the sets are, and are not

All samples were **written for this project**, modelled on scam patterns described in public advisories (RBI, NPCI, CERT-In, I4C, state police) and on ordinary Indian SMS/WhatsApp traffic. They are not copies of real messages, and the phone numbers, UPI IDs and links are fictitious. The samples and the rule tuning come from the same author, so these figures show how the engines handle these known patterns. They do **not** measure real-world accuracy. A set of real, consented, anonymised messages would be the next step.

| Set | Dev | Holdout | Languages |
|---|---|---|---|
| Messages | 70 scam, 50 legit | 40 scam, 30 legit | English, Hindi, Marathi, romanised Hindi, mixed |
| Links | 25 phishing, 15 legit | 15 phishing, 10 legit | n/a |
| QR codes | 10 scam, 10 legit | 6 scam, 6 legit | n/a |

The legitimate samples deliberately include hard cases that share words with scams: genuine bank OTPs and debit alerts, "your KYC has been updated", real refunds and cashback, electricity bills and UIDAI notices.

## Results (5 November 2026)

| Set | Precision | Recall before tuning | Recall after tuning | False-positive rate |
|---|---|---|---|---|
| Messages (dev) | 100% | 87.1% | 100% | 0% |
| **Messages (holdout)** | 100% | 80.0% | **80.0%** | 0% |
| Links (dev) | 100% | 76.0% | 100% | 0% |
| **Links (holdout)** | 100% | 86.7% | **86.7%** | 0% |
| QR codes (dev) | 100% | 100% | 100% | 0% |
| **QR codes (holdout)** | 100% | 100% | **100%** | 0% |

**Reading the table.** No legitimate sample was flagged in any set. Tuning brought the dev sets to 100% but did not change the holdout recall at all. The phrase lists generalise poorly: each fix catches the wording it was written for and little else. A message that uses different words for the same trick ("power supply will be cut off" instead of "electricity will be disconnected") still scores LOW. This is the main limit of a rule-based engine.

### What tuning changed

- Messages: more threat phrases ("will be deactivated", "will be blacklisted"), customs and parcel-seizure wording, IVR prompts ("press 1"), loan-approval and task-job wording, Aadhaar-update wording, and Marathi and Hindi video-like task phrases. Advance-fee detection now also covers refunds, investments and "pay a fee to release / withdraw / receive the money". A fee request together with a link adds the same bonus as other link lures.
- Links: government and toll brands (Parivahan, PM-Kisan, FASTag), more cheap TLDs (`.online`, `.site`, `.shop`, `.store`, `.live`, `.cc`), digit-swapped brand names (`g00gle-pay`), up to two sensitive keywords per link with a bonus when the hostname itself stacks them (`electricity-bill-update`), and redirect targets now add part of their own score.

### Known misses (holdout)

These were left unfixed on purpose, so the holdout stays honest:

- Power-cut threat in other words: "power supply will be cut off", and the mixed Hindi-English "connection कट जाएगा".
- Prize and advance-fee variants: "lucky winner … pay GST", "won GBP 750,000 … send your bank details", "iPhone for Rs 999".
- Task jobs without the usual phrases: "Earn Rs 800-2000 per day … rate restaurants".
- "Instant personal loan … no documents … 100% approval".
- Boss / gift-card impersonation ("buy Amazon gift cards and send me the codes").
- Links: `customs-clearance-india.pages.dev` (free hosting only) and `myntra-lucky-draw.shop` (Myntra is not in the brand list).

## Next steps

1. Collect real examples with consent (or from published advisories that quote them), anonymise them, and add them as a third, untouched test split.
2. Generalise by category rather than phrase. For example, a threat detector that pairs a service noun (electricity, power, SIM, account) with a cut-off verb in any tense.
3. Consider a small on-device classifier trained on the labelled data, kept alongside the rules so the explanations still name exact phrases.
