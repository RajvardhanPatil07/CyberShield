# CyberShield audit — starting state (commit 6127f2b, 2026-11-04)

This records what the repository contained **before** the hackathon-readiness work, so later claims can be compared against it.

## Repository contents

| Area | Files | Notes |
|---|---|---|
| Android app | 5 Kotlin files, 1 manifest, 1 test file (≈500 lines total) | Single-activity Jetpack Compose app |
| Build | `build.gradle.kts` (root + app), `settings.gradle.kts`, `gradle.properties` | AGP 8.7.3, Kotlin 2.0.21, compile/target SDK 35, min SDK 26, JDK 17 |
| CI | `.github/workflows/android-build.yml` | Unit tests + debug APK + artifact upload |
| Backend | none | |
| Gradle wrapper | **missing** | CI installed Gradle with `setup-gradle` |
| `.gitignore` | **missing** | `build/` and `.gradle/` would be committed by `git add .` |
| TODO/FIXME markers | none | |

## Build and CI

- Commit `6127f2b` replaced ambiguous `sumOf` calls with `fold`. CI run [41848573633](https://github.com/RajvardhanPatil07/CyberShield/actions/runs/41848573633) passed with that fix. The seven earlier runs failed.
- Verified locally on macOS (JDK 21, Android SDK 35): `clean`, `:app:testDebugUnitTest` (5 tests) and `:app:assembleDebug` all succeed with Gradle 9.8.2 and with Gradle 8.11.1.
- Warnings: deprecated `kotlinOptions` DSL; deprecated `Icons.Filled.Message`; CI actions on the deprecated Node 20 runtime.
- No release build, lint, or static analysis in CI.

## Feature checklist

### Implemented and verified (by code reading + unit tests)
- [x] Message heuristics: 7 urgency phrases, 7 lure phrases, link mention, OTP/PIN/password/CVV mention (English only).
- [x] URL heuristics: HTTPS, user-info, dotted IPv4 host, punycode, 6 hostname keywords, ≥3 hyphens.
- [x] Build of a debug APK.

### Implemented but unverified (no device or emulator test was ever run)
- [ ] CameraX + ML Kit live QR scanning.
- [ ] Share-to-scan (`ACTION_SEND` `text/plain` → ScamLens).
- [ ] Local history in SharedPreferences (10 items: type, score, verdict, time).

### Partially implemented
- [ ] QRGuard: decoded text goes to LinkGuard, but UPI URIs, plain text, `tel:`/`WIFI:` payloads all get URL-analysed and reported as "Could not parse".
- [ ] Dashboard: protection card is static text; history shows 4 items; no statistics.
- [ ] ScamLens explanations: one generic sentence per category, no recommended actions, no highlighted phrases.
- [ ] Analyzer pages share one `last` result, so a message result appears on the LinkGuard page and vice versa.
- [ ] Message analysis says "inspect the link separately" but never analyses the link it found.
- [ ] Every legitimate bank OTP message ("Do not share your OTP") is flagged as a credential request.

### Missing
- [ ] Threat intelligence (Safe Browsing, VirusTotal, URLhaus …) and any backend.
- [ ] AI analysis.
- [ ] Screenshot OCR.
- [ ] Hindi/Marathi strings or phrase dictionaries; language setting.
- [ ] UPI payment URI parsing.
- [ ] Lookalike/brand impersonation, shortener, suspicious-TLD, homograph (non-ASCII) checks.
- [ ] History search, filters, per-type records.
- [ ] Explicit "open destination" flow (there is no way to open a link at all).
- [ ] App icon, theme resource, string resources.
- [ ] Privacy policy document.
- [ ] Release build configuration and signing guide.

## Security and privacy review of the starting code

| Finding | Severity | Detail |
|---|---|---|
| No `.gitignore` | Medium | Build outputs, `local.properties` or a keystore could be committed by accident. |
| QR camera resources leak | Low | `Executors.newSingleThreadExecutor()` and the ML Kit scanner were never closed; the camera was never unbound when leaving the screen. |
| Camera errors swallowed | Low | `catch (_: Exception) {}` left a blank preview with no message. |
| `allowBackup=false` only | Low | Android 12+ device-to-device transfer ignores `allowBackup`; no `dataExtractionRules`. |
| No network security config | Info | The app made no network calls, so no exposure yet; needed before adding a backend. |
| Exported `MainActivity` | Info | Required for launcher and share target; incoming text was length-limited to 10,000 chars. Acceptable. |
| No secrets in repository | OK | No API keys, tokens or signing material found in history (`git log -p` scan). |
| Content storage | OK | History stored only type/score/verdict, not message text or URLs. |
| Permissions | OK | Only `CAMERA`, requested at runtime from QRGuard. |
