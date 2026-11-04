# Multilingual support: scope and evidence

## What is implemented

| Area | English | Hindi | Marathi |
|---|---|---|---|
| Interface strings (254 + 1 plural) | ✅ source | ✅ draft translation | ✅ draft translation |
| Explanations and recommendations | ✅ | ✅ | ✅ |
| Scam phrase dictionaries | ✅ 215 category phrases | ✅ 67 Devanagari phrases + 23 romanised-Hindi phrases | ✅ 57 phrases |
| Secret / request-verb / safety-notice lists | ✅ | ✅ shared Devanagari entries (36 across the three lists, Hindi and Marathi) | ✅ |
| OTP-safety-notice recognition ("do not share") | ✅ | ✅ | ✅ |
| Screenshot OCR | ✅ Latin | ✅ Devanagari | ✅ Devanagari |
| AI explanation output language | ✅ | ✅ (requested from model) | ✅ (requested from model) |
| In-app language switch | Settings → App language (also Android 13+ system per-app language) | | |

Detection always runs every dictionary, whatever the UI language, because scam messages often mix scripts.

## What is tested

Automated (run in CI on every push):

- `LocalizationTest`: every English string exists in Hindi and Marathi, no extra keys, identical `%1$s`/`%1$d` placeholders, ≥85% of values contain Devanagari, and every engine code has a string.
- `RiskAnalyzerTest`: Hindi fake-KYC + OTP request → HIGH; Marathi electricity-disconnection scam → flagged; Hindi "do not share OTP" bank SMS → LOW; romanised-Hindi account threat → flagged; benign Hindi and Marathi family/school messages → LOW; language guesses for each.

## What is NOT verified

- **Translation quality.** The Hindi and Marathi strings were written by the developer and an AI assistant. A native-speaker review has not been done. Some phrasing may be unnatural.
- **Detection accuracy.** There is no labelled dataset of Indian-language scam and genuine messages, so precision and recall are **unknown**. The tests above are hand-written examples, not a benchmark.
- **Language detection** uses script counts and ~15 marker words per language. It is a best guess, labelled as such in the UI, and often cannot tell short Hindi and Marathi texts apart (it then reports "Devanagari").
- **OCR accuracy** on real, compressed WhatsApp screenshots has not been measured.
- Other Indian languages (Tamil, Telugu, Bengali, Gujarati, Kannada…) are not supported.

## How to improve it

1. Have native speakers review `values-hi/strings.xml` and `values-mr/strings.xml`.
2. Collect consented, anonymised samples (e.g. from Chakshu public advisories or bank warning pages) and add them as test cases with expected levels.
3. Report precision/recall on that set before claiming accuracy numbers.
