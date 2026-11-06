# CyberShield web demo

A browser version of the CyberShield Android app for demos: **ScamLens** (messages), **LinkGuard** (links) and **QRGuard** (camera, image upload or pasted QR text, including UPI payment checks), in English, हिन्दी and मराठी.

- Static files only: no build step, no npm dependencies. Deploy the `web/` folder as is.
- The detection engine in `src/engine/` is a JavaScript port of `android/app/src/main/java/io/cybershield/app/security/`. A test checks that it gives **exactly** the same scores, findings, highlight positions, recommendations and redaction as the Kotlin engine on 383 cases (all labelled eval sets plus `test/fixtures/extra-cases.tsv`).
- Everything runs in the browser. The optional "Check with threat intelligence" and "Explain with AI" buttons appear only when a backend is configured, and send data only after the user confirms, like the app.
- Not in the web version: screenshot OCR, scan history and share-to-scan.

## Run locally

```bash
cd web
npm run serve          # http://localhost:5173  (sends the same security headers as production)
npm test               # parity with the Kotlin engine + translations
```

Camera scanning needs HTTPS or `localhost`.

## Deploy for the hackathon

You need: a GitHub account with this repository, a free [Vercel](https://vercel.com) account and a free [Render](https://render.com) account. Steps 1 and 2 are optional: without them the site still works fully, with the cloud buttons hidden.

### 1. Backend on Render (optional, enables threat intelligence and AI explanations)

1. Get at least a Gemini key at [aistudio.google.com/apikey](https://aistudio.google.com/apikey). The other keys are optional; see `backend/README.md`.
2. Render → **New** → **Blueprint** → choose this repository. Render reads `render.yaml` at the repo root.
3. When asked for values, paste your keys. For `ALLOWED_ORIGINS` enter your Vercel address from step 3 (for example `https://cybershield.vercel.app`). If you don't know it yet, leave it empty and add it later under the service's **Environment** tab.
4. After the deploy, open `https://<your-service>.onrender.com/health`. It should say `"status":"ok"` and list the providers you configured.

The free Render plan sleeps after about 15 minutes without traffic, and the first request after that takes roughly a minute. Open `/health` a few minutes before you present.

### 2. Point the site at the backend

Edit `web/src/config.js`:

```js
backendUrl: 'https://<your-service>.onrender.com',
```

Commit and push.

### 3. Website on Vercel

1. Vercel → **Add New** → **Project** → import this repository.
2. Set **Root Directory** to `web`. Framework preset: **Other**. Leave the build command empty.
3. Deploy. Vercel gives you an address like `https://cybershield-xxxx.vercel.app`; you can rename the project to get a nicer one.
4. If you use the backend, make sure that exact address (no trailing slash) is in the backend's `ALLOWED_ORIGINS`. Separate several addresses with commas.

Vercel redeploys on every push to `main`. `vercel.json` sets a strict Content-Security-Policy and allows the camera only for the site itself.

### 4. "Get the Android app" button (optional)

The button appears when `apkUrl` in `src/config.js` is set. The repository is private, so GitHub release downloads are not public. The simplest option is to serve the APK from the site itself:

1. Download the **cybershield-preview-apk** artifact from the latest GitHub Actions run. It is a release-optimised build signed with the debug key, so phones can install it. (Use a properly signed release APK instead if you set up signing, see `docs/RELEASE.md`.)
2. Copy the universal APK to `web/downloads/cybershield.apk`.
3. `.gitignore` excludes `*.apk`, so add it with `git add -f web/downloads/cybershield.apk`.
4. Set `apkUrl: '/downloads/cybershield.apk'` in `src/config.js`, then commit and push.

Phones will warn that the app comes from an unknown source. That is expected for an APK outside the Play Store.

## When the Android rules change

The phrase lists and UI strings are generated from the Android sources, and the parity test compares against results recorded from the Kotlin engine. After changing a rule:

```bash
cd web && npm run sync      # regenerates src/engine/phrases.js and src/strings.js
cd ../android && CYBERSHIELD_WRITE_WEB_FIXTURE=1 ./gradlew :app:testDebugUnitTest --tests '*WebReferenceTest*'
cd ../web && npm test       # fails until src/engine/*.js matches the Kotlin change
```

CI fails if the generated files are out of date or the engines disagree.

## Files

| Path | What it is |
|---|---|
| `index.html`, `styles.css`, `favicon.svg` | Page and "Cobalt Silk" styling from the app |
| `src/app.js` | UI: tabs, results, camera/image QR decoding, consent and report dialogs |
| `src/engine/` | Detection engine (port of the Kotlin engine) |
| `src/strings.js`, `src/engine/phrases.js` | Generated from the Android sources by `scripts/sync-from-android.mjs` |
| `src/i18n.js` | Web-only wording in EN/HI/MR (mainly "in your browser" instead of "on this phone") |
| `src/config.js` | Backend URL and APK link |
| `vendor/jsQR.js` | QR decoder for browsers without `BarcodeDetector` (jsQR 1.4.0, Apache-2.0) |
| `test/` | Parity and translation tests; `fixtures/kotlin-reference.json` is written by `WebReferenceTest.kt` |

The Hindi and Marathi web-only strings in `src/i18n.js` were adapted from the app's existing translations and have not been reviewed by a native speaker.
