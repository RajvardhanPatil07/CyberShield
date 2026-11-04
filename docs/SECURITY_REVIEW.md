# Security and privacy review (v0.3.0, 4 November 2026)

Scope: Android app (`android/`), backend (`backend/`), CI (`.github/workflows/`). Method: code review of every file, Android lint (0 issues), OSV.dev lookup of all 128 resolved release dependencies (0 known vulnerabilities), backend test suite, and the checks listed below.

## Findings and status

| # | Area | Check | Result |
|---|---|---|---|
| 1 | API keys | Keys in Android source, resources, BuildConfig or git history | **None.** `BuildConfig` has only the public backend URL. History scanned with `git log -p` for key patterns. |
| 2 | API keys | Backend key handling | Environment only; never logged or returned. `/health` returns configured/not configured. `.env` is gitignored, `.env.example` has blanks. |
| 3 | Network | Cleartext traffic | Release: `cleartextTrafficPermitted=false`, system CAs only. Debug overlay allows HTTP only to `10.0.2.2`, `127.0.0.1`, `localhost`. The client also refuses non-HTTPS backends in release builds (unit-tested). |
| 4 | Network | Redirects, response size | `instanceFollowRedirects=false`; responses capped at 256 KB; 8 s connect timeout and 15/35 s read timeouts. |
| 5 | URL opening | Automatic or unsafe opens | Nothing opens automatically. `openExternally` accepts only `http`, `https` (with host) and `upi`. High-risk or provider-confirmed targets need an extra acknowledgement. `intent:`, `javascript:`, `data:`, `file:` and `content:` are flagged and never offered for opening. |
| 6 | Payments | Payment initiation | None. UPI URIs are handed to a system chooser only after confirmation; the user's UPI app shows payee and amount. |
| 7 | Input validation | Incoming intents | `MainActivity` (exported, required for launcher/share) accepts only `SEND` text/image, `PROCESS_TEXT` and three shortcut actions; text is truncated to 10,000 chars; image streams must be `content://`. |
| 8 | Input validation | Engine robustness | Custom URL parser rejects whitespace/control chars and invalid ports and hosts; IDNA failures are handled. Tests cover malformed URLs and 35,000-char messages. |
| 9 | Input validation | Backend | JSON only, 16 KB cap (declared and streamed), strict schemas, enums, URL ≤ 2048 and http/https only. |
| 10 | Logging | Private content in logs | App: no `Log` calls with content (release R8 strips `Log.v/d`). Backend: logs method, route, status, latency only; internal errors log the error class name, not the message. |
| 11 | Storage | Local data | History holds no message text, full URLs, UPI IDs or digit runs (unit- and instrumentation-tested with OTPs, tokens, phone and card numbers). `allowBackup=false`, `fullBackupContent=false` and `dataExtractionRules` exclude everything from backup and device transfer. SharedPreferences are app-private. Not encrypted at rest: the stored data is non-sensitive by design and Android's file-based encryption applies. |
| 12 | Permissions | Least privilege | `CAMERA` (runtime, QR screen only) and `INTERNET`. No SMS, storage, contacts, location, notification-listener or accessibility. The photo picker needs no permission. |
| 13 | Components | Exported components | Only `MainActivity`. AppCompat's locale service is `exported=false`. No content providers, receivers or services of our own. |
| 14 | Camera | Resource lifetime | Camera unbound, analyzer thread shut down and ML Kit client closed on leaving the screen (the v0.2 code leaked all three). Camera errors are shown instead of a blank preview. |
| 15 | Dependencies | Known CVEs | OSV.dev: 0 of 128 Android packages; backend has no dependencies. Gradle wrapper checksum pinned (`distributionSha256Sum`) and validated in CI. |
| 16 | Backend auth | Client authentication | No user accounts. Embedding a shared secret in the APK would give no real protection, so abuse is controlled by per-IP and per-install rate limits. **Gap:** add Play Integrity or App Check before public launch. |
| 17 | Backend | Rate limiting | 20 URL checks and 5 AI calls per minute per IP and per install ID, applied before validation; VirusTotal limited to its free-tier 4/min. In memory, so per instance. |
| 18 | Backend | SSRF | Outbound requests go only to fixed provider hosts and to an allow-list of URL shorteners (HEAD, `redirect: manual`). User-supplied destinations are never fetched. |
| 19 | Backend | Error handling | Structured `{error:{code,message}}`; provider failures are isolated per provider and never reported as safe; 5xx retried once; timeouts mapped to 504. |
| 20 | AI | Prompt injection / unsafe advice | User text is delimited and declared untrusted; the system prompt forbids asking for secrets or app installs; output must pass strict schema validation and length clamps. The UI labels it "AI-generated, can be wrong". |
| 21 | Privacy | Data sent to cloud | Only on tap plus consent dialog. URLs: credentials and fragment stripped. AI text: on-device redaction of numbers, e-mails, UPI IDs and PAN, with URL query strings dropped; the user sees the exact text first. **Gap:** personal names are not redacted (disclosed in the dialog preview and the privacy policy). |
| 22 | CI | Secrets | `permissions: contents: read`; signing secrets used only when present; the keystore is decoded to `$RUNNER_TEMP` and deleted. Fork PRs get no secrets. |

## Residual risks

- Heuristic false negatives: new scam wording or clean-looking domains score LOW. The UI says a low score is not proof of safety.
- Heuristic false positives: e.g. a small business with a hyphenated `.xyz` domain. Explanations let users judge.
- Lookalike detection uses a curated brand list and a partial suffix list, not the full Public Suffix List.
- Threat-intelligence and AI integrations have not been exercised against live keys (see backend/README.md).
- No physical-device testing yet.
