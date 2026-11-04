# CyberShield: hackathon pitch summary

## Problem

Digital payment fraud in India now reaches people through the channels they trust most: SMS, WhatsApp and UPI QR codes. The common scripts are fake KYC updates, electricity-disconnection threats, "enter your PIN to receive cashback" collect requests, prepaid task jobs and "digital arrest" calls. Victims are often first-time smartphone users who read Hindi or Marathi more comfortably than English. Most protection tools are English-only, need broad permissions, or upload private messages.

## Solution

CyberShield is an Android app that answers one question before you act: **"Is this a scam?"**

- **ScamLens:** paste or share a message, or pick a screenshot. On-device rules in English, Hindi, Marathi and romanised Hindi flag OTP/PIN requests, KYC and disconnection threats, reward and fee lures, remote-access apps and digital-arrest language. It highlights the exact phrases and says what to do (never share OTP, call 1930, report on Chakshu).
- **LinkGuard:** checks a link without opening it. It catches brand impersonation (`hdfcbank-kyc.xyz`), misspellings (`icicbank.com`), Cyrillic look-alikes, shorteners, free-hosting pages, IP hosts, APK downloads and hidden redirects.
- **QRGuard:** decodes UPI QR codes and explains them in plain words: who gets paid, how much, and the rule that **a PIN is only needed to send money**.
- **Optional cloud layer:** one tap checks a link against Google Safe Browsing, VirusTotal and URLhaus, or asks Gemini for a plain-language explanation. It runs only after consent and redaction, and only the server holds the API keys.

## What makes it different

1. **Private by default.** No SMS, accessibility or notification permissions, no background monitoring. History never stores message text or numbers.
2. **Built for Indian scams and languages.** UPI-specific logic, Indian bank and government domains (`.bank.in`, `.gov.in`), Hindi and Marathi interface, dictionaries and OCR.
3. **Honest results.** Heuristic warnings are labelled as heuristics. "Confirmed malicious" appears only when a threat-intelligence provider lists the URL. Genuine bank OTP messages are not flagged.
4. **Engineering quality.** 62 JVM unit tests, 9 instrumented UI/OCR tests, 19 backend tests, Android lint at 0 issues, 0 known dependency vulnerabilities, a dependency-free backend, and CI that builds tested debug and release APKs on every push.

## Technology

Kotlin, Jetpack Compose (Material 3), CameraX, ML Kit (barcode, Devanagari+Latin OCR), AppCompat per-app locales; Node.js 22 backend with Google Safe Browsing v5, VirusTotal v3, abuse.ch URLhaus and the Gemini API (structured JSON output); GitHub Actions.

## Honest status

- Works on an Android 15 emulator: all local features, share-to-scan (text), screenshot OCR and QR decoding from images, history, Hindi/Marathi switching, app ↔ backend calls.
- Implemented, awaiting credentials: threat intelligence and AI explanation (backend tested with scripted provider responses).
- Not yet done: physical-device testing, native-speaker review of Hindi/Marathi, accuracy measurement on a labelled dataset, app attestation for the backend.

## Roadmap

1. Field test with 20 users across Hindi- and Marathi-speaking households; measure false positives.
2. Labelled evaluation set from public advisories, with precision and recall published.
3. More languages (Tamil, Telugu, Bengali, Gujarati).
4. Play Integrity on the backend; deploy on Cloud Run.
5. "Report to Chakshu" deep link with pre-filled, redacted details.
