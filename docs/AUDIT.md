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

---

# Status after the v0.3.0 work (4 November 2026)

Legend: ✅ verified · ⚠️ implemented, partially verified · ⏳ implemented, awaiting credentials or hardware · ❌ not done

| Acceptance criterion | Status | Evidence |
|---|---|---|
| Android app compiles | ✅ | Local (JDK 21, Gradle 8.11.1 wrapper and Gradle 9.8.2) and CI (JDK 17) |
| Unit tests pass | ✅ | 62/62 JVM tests; 19/19 backend tests |
| Debug APK builds | ✅ | CI artifact `cybershield-debug-apk` (per-ABI + universal) |
| Release APK builds | ✅ unsigned | R8-shrunk; signed when the secrets in RELEASE.md are added |
| ScamLens works | ✅ | Unit + instrumented tests; emulator runs in EN/HI/MR |
| LinkGuard works | ✅ | Unit + instrumented tests; emulator |
| QRGuard works | ⚠️ | Decoding from images verified on the emulator; live camera opens and binds but decoding a real QR needs a physical device |
| Scan history works | ✅ | Unit + instrumented tests; search, filter, delete, clear, "Confirmed threat" |
| Dashboard works | ✅ | Emulator |
| Share-to-scan works | ⚠️ | Text: ✅ (intents, instrumented). Image share: code present, not verified end-to-end (the picker path with the same OCR is verified) |
| Screenshot OCR works | ✅ emulator | Devanagari + Latin recognised from the demo image on-device; not yet tried on real screenshots |
| Multilingual implemented and tested to documented scope | ✅ | docs/MULTILINGUAL.md; completeness tests; no native-speaker review |
| Threat intelligence connected or documented as awaiting credentials | ⏳ | Implemented for Safe Browsing v5, VirusTotal v3, URLhaus; not run against live keys |
| AI connected or documented as awaiting credentials | ⏳ | Gemini structured output implemented; not run against a live key |
| Privacy and security review | ✅ | docs/SECURITY_REVIEW.md, PRIVACY.md, lint 0 issues, OSV 0 vulnerabilities |
| GitHub Actions succeeds | ✅ | See README badge |
| README complete | ✅ | |
| APK artifact available | ✅ | CI artifacts (retained 90 days by default) |
| Remaining limitations documented | ✅ | README → Limitations |

Bugs found and fixed while testing on the emulator:

1. OCR always failed for images coming from the dashboard or the Sharesheet: clearing the pending image re-keyed the `LaunchedEffect` and cancelled the OCR coroutine.
2. A multi-language OCR result containing a URL was labelled "English" because link characters counted as English text.
3. The "I understand the risk" label was not tappable; only the small checkbox was.
4. All tabs shared one scroll position, so Home opened scrolled past its header.
5. With no provider keys configured, LinkGuard said providers "could not answer" (it implied an outage) instead of "not configured".
6. Marathi locale formatted the score as "५५/100" while the badge showed "55".
7. Lint (never run before): missing `ExperimentalGetImage` opt-in in the QR scanner.
