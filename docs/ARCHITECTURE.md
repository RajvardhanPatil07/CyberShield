# Architecture

## System overview

```mermaid
flowchart LR
    subgraph Phone["Android phone (offline by default)"]
        IN["Inputs<br/>paste · Sharesheet · text selection<br/>camera QR · photo picker"]
        UI["Compose UI<br/>Dashboard · ScamLens · LinkGuard<br/>QRGuard · History · Settings"]
        ENG["Local engine (pure Kotlin)<br/>ScamLens · LinkGuard · QrGuard<br/>Redactor"]
        ML["ML Kit (bundled)<br/>Barcode · Devanagari+Latin OCR"]
        DB[("SharedPreferences<br/>redacted history · settings")]
        IN --> UI --> ENG
        UI --> ML --> ENG
        ENG --> UI
        UI --> DB
    end
    subgraph Server["CyberShield backend (optional, user-triggered)"]
        API["Node.js HTTP<br/>validation · rate limits · cache"]
        TI["Threat-intel providers"]
        AI["AI explainer"]
        API --> TI
        API --> AI
    end
    UI -- "HTTPS, only on tap + consent<br/>URL or redacted text" --> API
    TI --> GSB["Google Safe Browsing v5"]
    TI --> VT["VirusTotal v3"]
    TI --> UH["URLhaus"]
    AI --> GEM["Google Gemini API"]
```

API keys exist only in the backend's environment. The APK contains the backend's public URL and nothing else.

## Android modules (`android/app/src/main/java/io/cybershield/app`)

| Package | Responsibility | Android dependencies |
|---|---|---|
| `security/` | `ScamLens` (messages), `LinkGuard` (URLs), `QrGuard` (QR/UPI), `UrlParser` + `DomainTools`, `ScamPhrases` (EN/HI/MR dictionaries), `Redactor`, model types | **None**, so it runs in plain JVM unit tests |
| `data/` | `ScanHistoryRepository` (redacted records, search, stats) over a `HistoryStore`; `AppSettings` | `SharedPreferences` behind an interface |
| `cloud/` | `ReputationService` / `AiExplainer` interfaces, `BackendClient` implementation | `HttpURLConnection` behind a `Transport` interface |
| `vision/` | `ImageReaders`: OCR and QR decoding from a picked image | ML Kit |
| `ui/` | Compose screens, localized string mapping, confirmation dialogs, `openExternally` (http/https/upi only) | Compose, CameraX, AppCompat |
| `MainActivity` | Entry point; parses Sharesheet, `PROCESS_TEXT` and shortcut intents | AppCompat |

## Feature flow

```mermaid
flowchart TD
    A[User has suspicious content] --> B{How?}
    B -->|SMS / chat text| C[Copy or Share → CyberShield]
    B -->|Link| D[Share link or paste in LinkGuard]
    B -->|QR code| E[QRGuard camera or QR image]
    B -->|Screenshot| F[Photo picker or Share image]
    F --> G[On-device OCR] --> C
    C --> H[ScamLens local analysis]
    H --> I[Each link in the text → LinkGuard]
    D --> J[LinkGuard local analysis]
    E --> K{QR payload}
    K -->|upi://| L[UPI checks: payee, handle, amount, note, collect]
    K -->|http/https| J
    K -->|text| H
    K -->|tel/sms/wifi/intent| M[Type warning]
    H & J & L & M --> N[Result: score 0–100, level, reasons,<br/>highlighted phrases, recommended actions]
    N --> O[Redacted record → local history]
    N --> P{User taps optional cloud action?}
    P -->|Explain with AI| Q[Redact → preview → consent → backend → Gemini]
    P -->|Check threat intelligence| R[Consent → backend → Safe Browsing / VirusTotal / URLhaus]
    R -->|malicious| S[Mark record 'Confirmed threat']
    N --> T{User taps Open?}
    T --> U[Confirmation dialog<br/>+ acknowledgement if high risk] --> V[Browser or UPI app]
```

## Threat-detection workflow (local engine)

```mermaid
flowchart TD
    T[Input text ≤ 10,000 chars] --> L[Lower-case per character<br/>keeps highlight offsets]
    L --> P[Phrase regexes per category<br/>English · Hinglish · Hindi · Marathi]
    L --> S[Secrets: OTP / PIN / CVV / password]
    S --> V{Request verb nearby<br/>outside a safety notice?}
    V -->|yes| SR[OTP_REQUEST / UPI_PIN_REQUEST / ...]
    V -->|no, notice present| SN[Ignored: genuine bank OTP SMS]
    V -->|no notice| SM[SECRET_MENTION, low weight]
    L --> R[Receive-money trick patterns]
    T --> X[Extract links] --> LG[LinkGuard on each link]
    P & SR & SM & R & LG --> W[Sum weights per code<br/>+ combination bonuses<br/>clamp 0–100]
    W --> LV{Score}
    LV -->|< 30| LOW
    LV -->|30–59| MEDIUM
    LV -->|≥ 60| HIGH
    W --> RC[Recommendations from codes]
    T --> LD[Script + marker-word language guess]
```

LinkGuard parses URLs with its own parser (`java.net.URI` rejects many malicious inputs), converts hosts with IDNA, and checks: scheme, HTTPS, user-info, IP/numeric hosts, percent-encoded hosts, punycode, mixed scripts, brand keywords on non-brand domains, misspellings (edit distance plus confusable characters such as `rn`→`m` and Cyrillic `а`→`a`), shorteners, free-hosting suffixes, abused TLDs, subdomain depth, hyphens, ports, APK downloads, embedded redirect parameters and length. The registrable domain is approximated with a curated suffix list (including `co.in`, `gov.in`, `bank.in` and free-hosting platforms), not the full Public Suffix List.

## Backend architecture

```mermaid
flowchart LR
    REQ[HTTPS request] --> RT[Route table<br/>/health · /v1/url/check · /v1/ai/analyze]
    RT --> RL[Rate limit<br/>per IP + install ID]
    RL --> BV[Body: JSON only, ≤16 KB]
    BV --> VAL[Schema validation]
    VAL --> URL{URL check}
    VAL --> AIQ{AI analyze}
    URL --> C{Cache hit?<br/>SHA-256 key}
    C -->|yes| OUT
    C -->|no| EX[Expand allow-listed shortener<br/>HEAD, Location only]
    EX --> PAR[Query providers in parallel<br/>5 s timeout, 1 retry]
    PAR --> SB[Safe Browsing] & VTP[VirusTotal<br/>local 4/min limiter] & UHP[URLhaus]
    SB & VTP & UHP --> AGG[Aggregate:<br/>malicious > suspicious > no_known_threat > unavailable]
    AGG --> CS[Cache ≤ 1 h] --> OUT[JSON response]
    AIQ --> PR[System prompt + delimited untrusted text]
    PR --> GM[Gemini generateContent<br/>responseJsonSchema]
    GM --> OV[Validate output schema] --> OUT
    OUT --> LOG[Log: method, route, status, ms only]
```

## Key decisions

| Decision | Alternatives considered | Reason |
|---|---|---|
| Local rules first, cloud optional | Cloud-first classification | Works offline, no data leaves the phone by default, instant results |
| Rule engine rather than an on-device ML classifier | TFLite text model | No labelled Indian-language scam dataset was available to train or evaluate one honestly. Rules are explainable and testable. |
| Dependency-free Node backend | Express + middleware, Supabase | Few endpoints and no persistent data; zero dependencies means no supply-chain surface. Supabase adds a database that nothing needs. |
| SharedPreferences JSON for history | Room/SQLite | ≤200 small records; avoids an annotation processor. Revisit if history grows. |
| AppCompat per-app locales | Custom `Configuration` wrapping | Standard API, integrates with Android 13+ system language settings |
| Bundled ML Kit models + ABI splits | Play-services (unbundled) models | Works offline on first use and on sideloaded APKs; ABI splits keep the arm64 release APK ≈21 MB |
| No VirusTotal URL submission | Submit unknown URLs for scanning | Submissions are shared with VirusTotal's community, which could expose private links |
