# CyberShield

Android-first digital safety companion for suspicious messages, links and QR destinations.

> Prototype status: manual message/link heuristic checks are implemented. QR camera scanning, reputation-service integration and notification content analysis are not yet implemented. No universal background protection is claimed.

## Android MVP
- Kotlin + Jetpack Compose Material 3
- ScamLens: local explainable message heuristic checks
- LinkGuard: URL structure checks (not a reputation verdict)
- Privacy settings and permission-gated notification listener stub
- QRGuard flow placeholder

## Run
Open `android/` in Android Studio with JDK 17 and Android SDK 35, sync Gradle, then run the `app` configuration. CLI build: `cd android && ./gradlew assembleDebug`.

## Privacy and limits
The notification listener is intentionally a no-content stub. It does not read, store, or upload notification text. A future notification feature must have clear consent, per-app controls, redaction and retention limits. Android does not allow a normal app to read every other app's private data. Risk scores are heuristic indicators, not proof of maliciousness.

## Roadmap
1. Add trusted domain-reputation APIs through a backend proxy.
2. Implement CameraX + ML Kit QR scanning with confirmation before opening.
3. Add multilingual scam detection and screenshot OCR.
4. Design and policy-review opt-in notification protection.
5. Consider a dedicated browser extension or user-controlled VPN protection mode.

## Stack
Kotlin, Jetpack Compose, Room, CameraX/ML Kit (planned), FastAPI/Supabase (planned).

No CyberStrike offensive execution tools are exposed to consumer users. Any future integration must be narrow, read-only and server-side.
