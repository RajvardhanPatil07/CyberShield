# CyberShield

**Your everyday digital safety companion.** A native Android app for inspecting suspicious messages, links and QR destinations.

## Implemented in this repository

- **ScamLens:** local heuristic analysis for pasted messages.
- **LinkGuard:** local URL-structure checks (HTTPS, user-info tricks, IP hosts, punycode and suspicious hostname terms).
- **QRGuard:** live camera QR decoding with CameraX and Google ML Kit; decoded destinations are shown for review and can be passed to LinkGuard. The app never opens a QR destination automatically.
- **Share to CyberShield:** receive plain text shared from compatible Android apps through the Android Sharesheet and open it in ScamLens.
- **Privacy screen:** explains what the prototype does and does not monitor.

## Important limitations

This is a hackathon MVP, not a finished security product.

- Risk scores are simple heuristics, not trained AI, verified reputation checks, or proof of maliciousness.
- No live VirusTotal, Google Safe Browsing, URLhaus or CyberStrike integration is configured.
- The app does not monitor WhatsApp/Instagram private chats, intercept all links, inspect notification content, run a VPN, or block network traffic.
- QR camera access is requested only when the user opens QRGuard.
- Share-to-check works only when another app offers text through Android's Sharesheet.
- No content is uploaded by the implemented scanning features.

## Build

Requirements: Android Studio, JDK 17, Android SDK Platform 35.

1. Open the android/ folder in Android Studio.
2. Allow Gradle sync and install any prompted SDK components.
3. Run the app configuration on an Android device or emulator with a camera for QR scanning.

A GitHub Actions workflow builds the debug APK on pushes and pull requests. The repository currently does not include a Gradle wrapper; Android Studio can generate/manage the wrapper, or CI uses a pinned Gradle 8.9 installation.

## Stack

- Kotlin + Jetpack Compose + Material 3
- CameraX + ML Kit Barcode Scanning
- Android Sharesheet integration
- Local on-device heuristic analysis

## Roadmap before a public release

1. Add domain reputation providers through a backend proxy, with clear source attribution and rate limits.
2. Add screenshot OCR and multilingual scam classification.
3. Add local scan history with deletion controls.
4. Design any notification or VPN protection as separate opt-in features with a threat model, disclosure, data minimization and Android/Google Play policy review.
5. Add automated unit/instrumentation tests and verify builds on physical Android devices.

## Security and privacy principles

- Never ask for or collect OTPs, passwords, UPI PINs or CVVs.
- Never automatically open suspicious destinations.
- Keep user content on-device unless a future feature clearly explains a separate upload and obtains consent.
- Do not expose CyberStrike offensive execution tools to consumer users. Any future integration must be a narrow, read-only analysis service.

No license has been selected yet. Choose one only after reviewing dependency and integration obligations.