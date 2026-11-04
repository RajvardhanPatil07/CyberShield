# Background protection: what Android allows and what CyberShield does

CyberShield does **not** provide universal background protection. This page records what was investigated and why.

| Capability | Android reality | CyberShield decision |
|---|---|---|
| Read all SMS automatically | Needs `READ_SMS`/`RECEIVE_SMS`. Google Play allows these only for default SMS apps and a few approved exceptions. | **Not used.** Users share or paste a message. |
| Read WhatsApp / Instagram / Telegram chats | Not possible for ordinary apps; data is sandboxed. | **Not possible.** Users share text or a screenshot. |
| Read other apps' notifications | `NotificationListenerService` sees notification text from every app. Play restricts it and it is highly sensitive. | **Not implemented.** It would expose private messages from every app. A future opt-in, per-app, on-device-only design would need a separate threat model and Play policy review. |
| Monitor the screen via Accessibility Service | Play allows Accessibility only for genuine accessibility tools. It is also a common malware technique. | **Never.** |
| Intercept or block links system-wide | Requires a VPN service or becoming the default browser. | **Not implemented.** LinkGuard checks links the user shares or pastes. |
| Intercept UPI payments | Impossible without compromising the UPI app; also unlawful. | **Never.** QRGuard warns before you open the UPI app. |
| Periodic threat-feed updates | Possible with WorkManager, but the free feeds' licences (OpenPhish, PhishTank) do not allow redistribution inside an app. | **Not implemented.** Live lookups go through the backend instead. |

## Legitimate entry points that are implemented

| Entry point | How to use it | Verified (Android 15 emulator) |
|---|---|---|
| Share text → CyberShield | In any app: Share → CyberShield. A lone link opens LinkGuard; other text opens ScamLens. | ✅ Instrumented tests send the same `ACTION_SEND` intent; Hindi/Marathi text also checked via `adb am start`, including delivery to an already-running app |
| Share image → CyberShield | Share a screenshot → on-device OCR → ScamLens | ⚠️ Code path present; not verified end-to-end. `adb` cannot grant URI permission the way a real Sharesheet does. The same OCR pipeline is verified through the photo picker. |
| Text selection → "Check with CyberShield" | Long-press text in an app that supports the `PROCESS_TEXT` menu | ✅ Intent handling via instrumented test; the menu entry inside other apps not checked |
| Launcher shortcuts | Long-press the app icon → Scan QR / Check link / Check message | ✅ Registered (`dumpsys shortcut`) and routing checked |
| Photo picker | ScamLens / Dashboard → Check a screenshot; QRGuard → Scan QR from an image | ✅ Driven through the real system picker |

Whether a particular app (WhatsApp, Messages, Instagram) offers Share or text selection for a given message depends on that app. This has **not** been tested on a physical device.
