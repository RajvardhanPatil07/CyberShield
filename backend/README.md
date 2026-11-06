# CyberShield backend

A small Node.js service that the Android app calls **only when the user taps** "Check with threat intelligence" or "Explain with AI". It keeps every provider API key on the server.

- No npm dependencies (Node ≥ 22.9 built-ins only), so there is nothing to `npm install` or patch.
- Stateless apart from in-memory caches and rate limits, unless you turn on donations (below).
- No database. Supabase was considered and not used. The only thing the service can store is opt-in, redacted donations, written as daily JSONL files when `CONTRIBUTIONS_DIR` is set.

## Endpoints

| Method | Path | Purpose |
|---|---|---|
| GET | `/health` | Liveness, plus which providers are configured (booleans only) |
| POST | `/v1/url/check` | `{"url": "..."}` → reputation from Safe Browsing, VirusTotal, URLhaus |
| POST | `/v1/ai/analyze` | `{"text", "kind", "language", "localFindings"}` → structured AI explanation |
| POST | `/v1/contribute` | `{"text", "label", "language", "findings"}` → stores an opt-in, redacted sample for ML training. 503 `not_configured` unless `CONTRIBUTIONS_DIR` is set |

`/v1/contribute` rejects text that still looks like it has personal data (e-mail address, Indian mobile number, or 5+ digits in a row) with 400 `unredacted`, because the app must redact before sending. A stored record has the redacted text, label, language, finding codes and a timestamp. It has no install ID or IP address. Its `id` is a hash of the text and label. Fold the files into the training data with `cd ml && make ingest` (see `ml/data/README.md`).

`/v1/url/check` response:

```json
{
  "verdict": "malicious | suspicious | no_known_threat | unavailable",
  "checkedUrl": "https://example.com/path",
  "expandedUrl": null,
  "providers": [{ "name": "google_safe_browsing", "status": "malicious", "threats": ["SOCIAL_ENGINEERING"] }],
  "checkedAt": "2026-11-04T12:00:00.000Z",
  "cached": false
}
```

Only `verdict: "malicious"` (at least one provider has the URL on a threat list) is shown in the app as **confirmed**. Everything from the on-device engine is labelled as a heuristic. `unavailable` means no provider answered; it is never treated as safe.

`/v1/ai/analyze` response: `{"analysis": {riskLevel, scamType, summary, techniques[], redFlags[], safeSteps[], confidence}, "model", "disclaimer"}`. The model is asked for JSON matching a schema (`generationConfig.responseJsonSchema`), and the server validates the result again before returning it.

Errors are always `{"error": {"code", "message"}}`: `invalid_request` / `invalid_json` (400), `unsupported_media_type` (415), `payload_too_large` (413), `rate_limited` (429 + `Retry-After`), `not_configured` (503), `provider_*` (502/503/504).

## Run locally

```bash
cd backend
cp .env.example .env      # fill in whichever keys you have
npm start                 # http://localhost:8787
npm test                  # 28 tests, no network or keys needed
```

The Android debug build calls `http://10.0.2.2:8787`, which the emulator maps to your computer. For a physical phone over USB run `adb reverse tcp:8787 tcp:8787` and set the server address to `http://127.0.0.1:8787` in the app's Settings.

## Getting credentials

| Variable | Provider | How to get it | Free tier and terms (checked Nov 2026) |
|---|---|---|---|
| `GOOGLE_SAFE_BROWSING_API_KEY` | Google Safe Browsing v5 | Google Cloud console → create project → enable **Safe Browsing API** → Credentials → API key. Restrict the key to that API. | Free. **Non-commercial use only**; commercial products must use [Web Risk](https://cloud.google.com/web-risk). ([terms](https://developers.google.com/safe-browsing/terms)) |
| `VIRUSTOTAL_API_KEY` | VirusTotal v3 | Sign up at virustotal.com → profile → API key. | Public API: 4 requests/min, 500/day; must not be used in commercial products. ([docs](https://docs.virustotal.com/docs/api-overview)) The server enforces 4/min locally. |
| `URLHAUS_AUTH_KEY` | abuse.ch URLhaus | Log in at [auth.abuse.ch](https://auth.abuse.ch/) → Auth-Key. | Free; an Auth-Key has been mandatory since 2025. Covers malware-distribution URLs more than phishing. |
| `GEMINI_API_KEY` | Google Gemini API | [aistudio.google.com/apikey](https://aistudio.google.com/apikey) | Free tier with per-model rate limits; on the free tier Google may use prompts to improve its products, so the app redacts personal data before sending. Paid tier is billed per token. |

**PhishTank** was evaluated and not integrated: new-user and API-key registration has been closed since 2020. **OpenPhish**'s community feed is licensed for personal/academic research only, which does not fit a distributed app.

None of these integrations has been run against a live key during development. The request/response handling is covered by tests with recorded response shapes taken from each provider's documentation. After adding a key, verify it with:

```bash
curl -s -XPOST localhost:8787/v1/url/check -H 'content-type: application/json' \
  -d '{"url":"http://testsafebrowsing.appspot.com/s/phishing.html"}'
```

Google publishes that URL as a Safe Browsing test page; it should return `"verdict":"malicious"`.

## Security model

- **Secrets** come only from environment variables; they are never logged or returned. `/health` reports configured/not configured.
- **Input validation**: JSON only, 16 KB body cap, URL ≤ 2048 chars and http/https only, AI text ≤ 4000 chars, enums for `kind` and `language`, finding codes matched against `^[A-Z_]+$`.
- **Privacy**: URL credentials and fragments are stripped before lookup. VirusTotal is queried for existing reports only; URLs are never submitted for scanning (submissions are shared with VirusTotal's community). Cache keys are SHA-256 hashes. Request logs contain method, route, status and latency only: no bodies, URLs, IPs or headers.
- **Rate limiting**: per IP and per random install ID (`X-CyberShield-Install`), 20 URL checks and 5 AI calls per minute by default. Limits apply before validation, so floods of invalid requests are throttled too.
- **Outbound safety (SSRF)**: the server contacts only fixed provider hosts. Short-link expansion sends `HEAD` requests, with redirects not followed automatically, and only to a fixed allow-list of shortener hosts. The destination page is never fetched.
- **Resilience**: 5 s provider timeout, one retry on network errors or 5xx, providers queried in parallel and isolated from each other's failures. Responses are cached up to 1 h (never beyond Safe Browsing's `cacheDuration` for matches, never beyond 24 h).
- **Prompt injection**: user text is wrapped in delimiters, the system prompt says to ignore embedded instructions, and output must pass schema validation.
- **Authentication**: there is no user login. Any secret embedded in an APK can be extracted, so the backend relies on rate limits instead of a fake "app secret". Before a public launch, add [Play Integrity](https://developer.android.com/google/play/integrity) or Firebase App Check.

Limits are in memory, so with several instances each instance enforces its own limits. A shared store (e.g. Redis) would be needed to scale horizontally.

## Deploy

Any container host works (Cloud Run, Render, Fly.io, Railway):

```bash
docker build -t cybershield-backend backend
docker run -p 8787:8787 --env-file backend/.env cybershield-backend
```

On Render, `render.yaml` at the repository root sets this up as a Blueprint (Docker, `/health` check, `TRUST_PROXY=true`); see `web/README.md` for the step-by-step.

To collect donations, `CONTRIBUTIONS_DIR` must point at storage that survives restarts. Render's free plan wipes the filesystem on every restart, redeploy and spin-down, so the Blueprint leaves donations off; `render.yaml` shows the paid-plan disk settings that turn them on.

Set the keys as the host's secret environment variables, serve over HTTPS, set `TRUST_PROXY=true` if the platform puts a proxy in front, and set `ALLOWED_ORIGINS` to the web demo's address (for example `https://cybershield.vercel.app`; comma-separate several) so browsers may call the API. Without `ALLOWED_ORIGINS` the server sends no CORS headers, which is fine for the Android app. Then build the app with:

```bash
CYBERSHIELD_BACKEND_URL=https://your-host.example ./gradlew :app:assembleRelease
```

Release builds refuse any backend URL that is not HTTPS.
