# Demo script and device-testing guide

## Install

1. Download the `cybershield-debug-apk` artifact from the latest successful [GitHub Actions run](https://github.com/RajvardhanPatil07/CyberShield/actions/workflows/android-build.yml).
2. Unzip it and install `app-arm64-v8a-debug.apk` (most phones) or `app-universal-debug.apk` (any device):
   `adb install app-arm64-v8a-debug.apk`, or copy it to the phone and allow "Install unknown apps".

The debug APK is fine for demos. The release APK is unsigned until signing is configured ([RELEASE.md](RELEASE.md)).

## Optional: cloud features for the demo

```bash
cd backend && cp .env.example .env    # add the keys you have
npm start
adb reverse tcp:8787 tcp:8787          # physical phone over USB
```

In the app: Settings → Server address → `http://127.0.0.1:8787` (phone with `adb reverse`). The emulator uses `http://10.0.2.2:8787` by default. Without keys, every provider shows "Not configured", which is the honest state.

## Five-minute demo

Sample inputs are in [`docs/demo/`](demo/). Every sample is labelled "DEMO".

| # | Show | Input | Expected |
|---|---|---|---|
| 1 | **ScamLens, English** | `Dear customer, your SBI account will be blocked today. Update KYC immediately at http://sbi-kyc-update.xyz/login` | HIGH. Reasons: account threat, urgency, KYC, suspicious link. Phrases highlighted. Recommendations include 1930 and Chakshu. |
| 2 | **False-positive control** | `482913 is your OTP for Rs 1,250 at AMAZON. Do not share it with anyone. -HDFC Bank` | LOW. A genuine OTP SMS with "do not share" is not treated as an OTP request. |
| 3 | **Hindi** | `प्रिय ग्राहक, आपका खाता बंद हो जाएगा। तुरंत केवाईसी अपडेट करें और अपना ओटीपी बताएं।` | HIGH; "Detected language: Hindi". Then Settings → App language → हिन्दी and run it again: explanations appear in Hindi. |
| 4 | **Screenshot OCR** | ScamLens → Check a screenshot → `screenshot-hindi-kyc-scam.png` | Text is read on the device, then analysed. Mixed Hindi + English. |
| 5 | **LinkGuard** | `https://hdfcbank-kyc-update.xyz/verify`, then `https://раytm.com` (Cyrillic р, а) | HIGH: brand impersonation plus suspicious TLD; homograph flagged. Tap *Open link*: confirmation with a mandatory "I understand the risk" tick. |
| 6 | **QRGuard UPI** | QRGuard → Scan QR from an image → `qr-upi-scam.png` | Payee "SBI Customer Care", ₹24999, note about a KYC refund → HIGH. "Paying with UPI always takes money FROM your account." Compare with `qr-upi-shop.png` → LOW. |
| 7 | **Share-to-scan** | In Messages/Chrome: select text → Share → CyberShield, or long-press → *Check with CyberShield* | Opens ScamLens (or LinkGuard for a single link). |
| 8 | **History and privacy** | History tab → search "xyz" → delete one → Clear history | Records show only host names and signal names; no message text. |
| 9 | **Cloud (if backend has keys)** | LinkGuard → Check with threat intelligence on `http://testsafebrowsing.appspot.com/s/phishing.html` | "Confirmed malicious by Google Safe Browsing". The history entry becomes "Confirmed threat". |
| 10 | **AI (if Gemini key)** | After #1 → Explain with AI | Consent dialog shows redacted text (`[NUMBER]`…) → structured explanation in the app language. |

## What has been tested where

| Feature | JVM unit tests | Emulator (API 35) | Physical device |
|---|---|---|---|
| ScamLens / LinkGuard / QRGuard engines | ✅ 45 tests | ✅ via UI tests | ❌ not yet |
| History redaction, search, clear | ✅ 9 tests | ✅ UI test | ❌ |
| Backend client parsing / failure modes | ✅ 4 tests | – | ❌ |
| Hindi / Marathi completeness | ✅ 3 tests | manual language switch | ❌ |
| Share text / PROCESS_TEXT intents | – | ✅ UI tests | ❌ |
| Live camera QR scanning | – | ⚠️ emulator camera only | ❌ **needs a real phone** |
| Screenshot OCR (bundled ML Kit) | – | see results in README | ❌ **needs real screenshots** |
| Opening links / UPI apps | – | confirmation dialog only | ❌ **needs a phone with UPI apps** |
| Threat intelligence / AI live | backend tests with scripted responses | – | ❌ **needs API keys** |

## Physical-device checklist (not yet done)

- [ ] Install `app-arm64-v8a-debug.apk` on Android 8.0 and Android 14/15 phones.
- [ ] QRGuard: grant camera, scan a printed UPI QR, then deny the permission and confirm the rationale screen.
- [ ] Share a real SMS from Google Messages and a WhatsApp message into CyberShield.
- [ ] Share a WhatsApp screenshot (image) into CyberShield; check OCR on Hindi and Marathi text.
- [ ] "Open in UPI app" shows GPay/PhonePe/Paytm in the chooser, and the payee and amount match.
- [ ] Switch app language to Hindi and Marathi; check for truncated text on a small screen.
- [ ] Turn on airplane mode: local checks work; cloud buttons report "Could not reach the CyberShield server".
- [ ] Settings → Delete all my data → History is empty and cloud consent is off.
