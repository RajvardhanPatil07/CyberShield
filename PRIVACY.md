# CyberShield privacy policy

_Last updated: 4 November 2026 · applies to app version 0.3.0 and the backend in this repository_

CyberShield checks messages, links, QR codes and screenshots for signs of fraud. It is designed so that, by default, nothing you check leaves your phone.

## 1. What is processed on your phone

| You do this | What CyberShield processes | Where |
|---|---|---|
| Paste or share a message into ScamLens | The text you provided | On the phone only |
| Paste or share a link into LinkGuard | The link text | On the phone only |
| Scan a QR code | Camera frames while the QRGuard screen is open, then the decoded QR content | On the phone only (Google ML Kit, bundled model) |
| Pick or share a screenshot | The selected image, converted to text | On the phone only (Google ML Kit, bundled model) |

CyberShield **does not**:

- read your SMS inbox, WhatsApp, Instagram or any other app's messages;
- read notifications, use an Accessibility Service, or run in the background;
- access your photo library (the Android photo picker gives the app only the single image you pick);
- open links or start payments automatically;
- include advertising SDKs or its own analytics.

**Google ML Kit diagnostics.** The QR scanner and screenshot reader use Google ML Kit. Images, QR content and recognised text are processed on the phone and are not uploaded. However, ML Kit itself sends Google diagnostic and usage data: device model and OS version, app package and version, a per-installation identifier, and performance metrics such as processing latency ([Google's ML Kit data disclosure](https://developers.google.com/ml-kit/android-data-disclosure)). CyberShield cannot turn this off. We observed these events being queued during on-device testing.

## 2. What may be sent to the CyberShield server, and only when you tap it

Both cloud features are **off until you tap them and agree**. The consent dialog shows you what will be sent.

| Feature | What is sent | Who receives it |
|---|---|---|
| **Check with threat intelligence** (LinkGuard) | The link, with any user name/password and `#fragment` removed | The CyberShield server, which looks it up with Google Safe Browsing, VirusTotal (report lookup only; the URL is never submitted for scanning) and abuse.ch URLhaus. If the link is from a known shortener (e.g. bit.ly), the server asks only that shortener where it redirects. |
| **Explain with AI** (ScamLens) | The text after on-device redaction: numbers (OTPs, phone, card, Aadhaar, account numbers), e-mail addresses, UPI IDs and PAN numbers are replaced with placeholders such as `[NUMBER]`, and query strings are removed from links | The CyberShield server, which forwards it to Google's Gemini API to generate an explanation |

Every request also includes a random installation ID, used only for rate limiting. It is not linked to your name, phone number, Google account or device identifiers, and is reset when you delete app data.

The CyberShield server **does not store** the content you send. Its logs contain only the endpoint, HTTP status and response time, not the link, the text or your IP address. Lookup results are kept in memory for up to one hour under a one-way hash of the link, to avoid repeated provider calls.

Third-party providers process the data under their own terms. In particular, Google may use content sent to the free tier of the Gemini API to improve its products; that is why CyberShield redacts personal values first. Do not submit content you are not comfortable sharing.

Names inside a message (for example "Dear Rahul") are **not** removed by redaction. Check the preview before sending.

## 3. What is stored on your phone

| Data | Contents | Can you delete it? |
|---|---|---|
| Scan history (up to 200 entries) | Type of scan, risk score and level, the names of the warning signals, time, and at most a website host name (e.g. `sbi-kyc.xyz`) or UPI handle suffix (e.g. `@ybl`). **Never** the message text, full link, UPI ID, amounts or any number from the content. | Yes: delete one entry, *History → Clear history*, or turn history off in *Settings* |
| Settings | Language, whether cloud checks are allowed, an optional server address, the random installation ID | Yes: *Settings → Delete all my data* |

Scan history and settings are excluded from Android cloud backup and device-to-device transfer.

Uninstalling the app deletes everything it stored.

## 4. Permissions

| Permission | Why | When |
|---|---|---|
| `CAMERA` | Live QR scanning on the QRGuard screen | Requested only when you open the scanner; you can scan a QR from an image instead |
| `INTERNET` | The two optional cloud features above | Only when you tap them |

No SMS, contacts, location, storage, notification-listener, accessibility or phone permissions are requested.

## 5. Opening links and payments

CyberShield never opens a link or a UPI payment by itself. When you choose to open one, the app shows the destination and asks you to confirm. For high-risk results you must also tick "I understand the risk". UPI payments open in your own UPI app, where you see the payee and amount before entering your PIN. CyberShield cannot make payments.

## 6. Children

CyberShield is a general-audience safety tool and does not knowingly collect personal data from anyone.

## 7. Changes and contact

Changes to this policy are committed to this file in the public repository, so the history is visible. Questions or security reports: open an issue at https://github.com/RajvardhanPatil07/CyberShield/issues (do not include personal data in public issues).

If you lost money to fraud, call **1930** or report at **cybercrime.gov.in** immediately.
