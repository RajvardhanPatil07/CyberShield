package io.cybershield.app.security

/**
 * Scam phrase dictionaries. English, romanised Hindi ("Hinglish"), Hindi and Marathi (Devanagari).
 *
 * Matching rules (see [ScamLens]):
 * - Matching is case-insensitive and any run of whitespace in the text matches a single space here.
 * - Latin phrases must start and end on a word boundary.
 * - Devanagari phrases must start on a word boundary but may be followed by inflections.
 *
 * Coverage note: these lists were compiled from public advisories (RBI, NPCI, CERT-In, I4C) and
 * common scam samples. The Hindi and Marathi phrases have not been reviewed by a native-speaker panel
 * or measured on a labelled dataset; see docs/MULTILINGUAL.md.
 */
object ScamPhrases {
    val english: Map<FindingCode, List<String>> = mapOf(
        FindingCode.URGENCY to listOf(
            "urgent", "urgently", "immediately", "within 24 hours", "within 24 hrs", "within 2 hours", "today itself",
            "last chance", "final warning", "final notice", "act now", "expires today", "expire today", "verify immediately",
            "urgent action", "limited time", "hurry", "asap", "before midnight", "last date today", "claim now",
        ),
        FindingCode.ACCOUNT_THREAT to listOf(
            "account will be blocked", "account will be suspended", "account has been blocked", "account is blocked",
            "account suspended", "account will be closed", "account will be deactivated", "account deactivated",
            "will be blocked", "blocked today", "suspended today", "sim will be blocked", "card will be blocked",
            "card has been blocked", "kyc expired", "kyc has expired", "kyc is pending", "pan card blocked",
            "pan will be deactivated", "wallet will be blocked", "account has been suspended", "account freeze", "account frozen",
            "will be deactivated", "will be blacklisted",
        ),
        FindingCode.SERVICE_DISCONNECTION to listOf(
            "electricity will be disconnected", "power will be disconnected", "electricity connection will be",
            "power will be cut", "connection will be cut", "will be disconnected", "sim will be deactivated",
            "mobile number will be blocked", "gas connection will be", "electricity bill not updated", "previous month bill was not updated",
        ),
        FindingCode.KYC_UPDATE to listOf(
            "kyc", "update kyc", "kyc update", "complete kyc", "kyc verification", "re-kyc", "rekyc", "ekyc", "e-kyc",
            "pan update", "update pan", "update your pan", "link aadhaar", "link aadhar", "aadhaar update", "aadhar update",
            "verify your account", "update your account", "update your aadhaar", "update aadhaar", "aadhaar details", "update your details",
        ),
        FindingCode.REWARD_LURE to listOf(
            "you have won", "you won", "winner", "congratulations", "prize", "lottery", "lucky draw", "jackpot",
            "free gift", "gift card", "gift voucher", "reward points", "redeem now", "claim your", "free reward", "bonus",
            "cashback of", "won rs", "selected for", "kbc",
        ),
        FindingCode.REFUND_LURE to listOf("refund", "tax refund", "income tax refund", "excess payment", "refund amount"),
        FindingCode.LOAN_LURE to listOf(
            "instant loan", "pre-approved loan", "pre approved loan", "preapproved loan", "loan approved", "loan sanctioned",
            "without cibil", "no cibil", "low interest loan", "loan without documents", "loan is approved", "loan has been approved",
        ),
        FindingCode.JOB_TASK_SCAM to listOf(
            "work from home", "part time job", "part-time job", "earn daily", "daily income", "per day income",
            "like youtube videos", "rate hotels", "task based", "prepaid task", "telegram task", "simple tasks",
            "earn money online", "earn upto", "earn up to", "liking youtube videos", "like videos", "hotel review", "per task",
        ),
        FindingCode.INVESTMENT_LURE to listOf(
            "guaranteed return", "guaranteed returns", "guaranteed profit", "double your money", "100% profit", "daily profit",
            "crypto investment", "stock tips", "trading group", "high returns", "risk free returns", "risk-free returns",
        ),
        FindingCode.FEE_REQUEST to listOf(
            "registration fee", "processing fee", "advance fee", "small fee", "security deposit", "delivery charge",
            "customs duty", "verification fee", "activation fee", "pay a fee", "token amount", "file charges", "deposit rs",
        ),
        FindingCode.AUTHORITY_IMPERSONATION to listOf(
            "cbi", "cyber crime", "cybercrime", "cyber cell", "narcotics", "ncb", "enforcement directorate", "trai",
            "customs department", "income tax department", "court notice", "arrest warrant", "digital arrest",
            "money laundering", "illegal parcel", "parcel contains drugs", "your parcel", "police case", "fir registered",
            "case registered against you", "case will be registered", "customs officer", "seized by", "illegal items", "will file an fir",
        ),
        FindingCode.REMOTE_ACCESS_APP to listOf(
            "anydesk", "any desk", "teamviewer", "team viewer", "quicksupport", "quick support", "rustdesk", "airdroid",
            "screen share", "screen sharing", "install this app", "download this app", "download the app from",
        ),
        FindingCode.APK_LURE to listOf(".apk", "apk file", "install apk", "download apk"),
        FindingCode.SECRECY to listOf(
            "don't tell anyone", "do not tell anyone", "keep this confidential", "keep it secret", "do not inform",
            "don't inform", "do not disconnect the call", "don't disconnect", "stay on the call", "do not tell your family",
        ),
        FindingCode.CALLBACK_REQUEST to listOf(
            "call this number", "call immediately", "call us on", "call us at", "contact customer care", "call now",
            "whatsapp us", "contact our officer", "call our executive", "call on", "press 1", "press 9", "press 0", "dial 9",
        ),
        FindingCode.PAYMENT_REQUEST to listOf(
            "collect request", "payment request", "approve the request", "accept the request", "request money", "pay now",
            "make payment", "payment pending", "pending payment", "pay immediately", "clear your dues", "pay the bill",
        ),
    )

    /** Romanised Hindi as typically typed in SMS/WhatsApp. */
    val hinglish: Map<FindingCode, List<String>> = mapOf(
        FindingCode.URGENCY to listOf("turant", "jaldi karein", "jaldi karo", "aaj hi", "24 ghante"),
        FindingCode.ACCOUNT_THREAT to listOf("account band", "khata band", "block ho jayega", "band ho jayega", "band kar diya jayega"),
        FindingCode.SERVICE_DISCONNECTION to listOf("bijli kat", "bijli connection", "light kat"),
        FindingCode.REWARD_LURE to listOf("inaam", "jeeta", "jeete", "lottery lagi"),
        FindingCode.JOB_TASK_SCAM to listOf("ghar baithe", "ghar se kaam"),
        FindingCode.INVESTMENT_LURE to listOf("paisa double", "paise double"),
        FindingCode.SECRECY to listOf("kisi ko mat batana", "kisi ko na batayein"),
    )

    val hindi: Map<FindingCode, List<String>> = mapOf(
        FindingCode.URGENCY to listOf("तुरंत", "तत्काल", "जल्दी करें", "आज ही", "24 घंटे", "अंतिम चेतावनी", "अंतिम मौका", "आखिरी मौका"),
        FindingCode.ACCOUNT_THREAT to listOf(
            "खाता बंद", "अकाउंट बंद", "ब्लॉक हो जाएगा", "बंद हो जाएगा", "बंद कर दिया जाएगा", "निलंबित", "खाता ब्लॉक", "कार्ड ब्लॉक",
        ),
        FindingCode.SERVICE_DISCONNECTION to listOf("बिजली कनेक्शन", "बिजली काट", "बिजली बिल अपडेट नहीं", "सिम बंद"),
        FindingCode.KYC_UPDATE to listOf("केवाईसी", "पैन कार्ड अपडेट", "आधार अपडेट", "आधार लिंक"),
        FindingCode.REWARD_LURE to listOf("आपने जीता", "आपने जीते", "इनाम", "लॉटरी", "बधाई हो", "उपहार", "कैशबैक", "पुरस्कार"),
        FindingCode.REFUND_LURE to listOf("रिफंड", "धनवापसी"),
        FindingCode.LOAN_LURE to listOf("तुरंत लोन", "लोन मंजूर", "बिना दस्तावेज"),
        FindingCode.JOB_TASK_SCAM to listOf("घर बैठे कमाई", "घर बैठे", "पार्ट टाइम जॉब", "रोज कमाएं", "वीडियो लाइक"),
        FindingCode.INVESTMENT_LURE to listOf("पैसा डबल", "गारंटीड रिटर्न", "निश्चित मुनाफा"),
        FindingCode.FEE_REQUEST to listOf("रजिस्ट्रेशन शुल्क", "प्रोसेसिंग फीस", "पंजीकरण शुल्क", "शुल्क जमा"),
        FindingCode.AUTHORITY_IMPERSONATION to listOf("सीबीआई", "पुलिस", "गिरफ्तारी", "डिजिटल अरेस्ट", "साइबर क्राइम", "कस्टम विभाग", "मनी लॉन्ड्रिंग"),
        FindingCode.REMOTE_ACCESS_APP to listOf("ऐप डाउनलोड करें", "ऐप इंस्टॉल करें", "स्क्रीन शेयर"),
        FindingCode.SECRECY to listOf("किसी को मत बताना", "किसी को न बताएं", "कॉल बंद मत करना"),
        FindingCode.CALLBACK_REQUEST to listOf("इस नंबर पर कॉल", "कॉल करें", "संपर्क करें"),
        FindingCode.PAYMENT_REQUEST to listOf("भुगतान करें", "पेमेंट करें", "भुगतान लंबित"),
    )

    val marathi: Map<FindingCode, List<String>> = mapOf(
        FindingCode.URGENCY to listOf("त्वरित", "ताबडतोब", "लगेच", "आजच", "24 तासांत", "शेवटची संधी", "अंतिम इशारा"),
        FindingCode.ACCOUNT_THREAT to listOf("खाते बंद", "बंद होईल", "ब्लॉक होईल", "बंद करण्यात येईल", "खाते ब्लॉक"),
        FindingCode.SERVICE_DISCONNECTION to listOf("वीज कनेक्शन", "वीज खंडित", "वीज पुरवठा खंडित", "वीज बिल अपडेट", "कापले जाईल"),
        FindingCode.KYC_UPDATE to listOf("केवायसी", "पॅन कार्ड अपडेट", "आधार अपडेट", "आधार लिंक"),
        FindingCode.REWARD_LURE to listOf("तुम्ही जिंकला", "तुम्ही जिंकले", "बक्षीस", "लॉटरी", "अभिनंदन", "भेटवस्तू", "कॅशबॅक"),
        FindingCode.REFUND_LURE to listOf("परतावा", "रिफंड"),
        FindingCode.LOAN_LURE to listOf("त्वरित कर्ज", "कर्ज मंजूर"),
        FindingCode.JOB_TASK_SCAM to listOf("घरबसल्या कमाई", "घरबसल्या", "पार्ट टाइम नोकरी", "दररोज कमवा", "व्हिडिओ लाईक"),
        FindingCode.INVESTMENT_LURE to listOf("पैसे दुप्पट", "दुप्पट परतावा", "हमखास परतावा"),
        FindingCode.FEE_REQUEST to listOf("नोंदणी शुल्क", "प्रक्रिया शुल्क"),
        FindingCode.AUTHORITY_IMPERSONATION to listOf("सीबीआय", "पोलीस", "अटक", "डिजिटल अरेस्ट", "सायबर क्राईम"),
        FindingCode.REMOTE_ACCESS_APP to listOf("अ‍ॅप डाउनलोड करा", "ॲप डाउनलोड करा", "अ‍ॅप इन्स्टॉल करा", "स्क्रीन शेअर"),
        FindingCode.SECRECY to listOf("कोणालाही सांगू नका हे", "कॉल बंद करू नका"),
        FindingCode.CALLBACK_REQUEST to listOf("या नंबरवर कॉल", "कॉल करा", "संपर्क साधा"),
        FindingCode.PAYMENT_REQUEST to listOf("पेमेंट करा", "भरणा करा", "रक्कम भरा"),
    )

    /** Payment secrets. A mention alone is weak; combined with a request verb it is a strong signal. */
    val secrets: Map<FindingCode, List<String>> = mapOf(
        FindingCode.OTP_REQUEST to listOf("otp", "one time password", "one-time password", "verification code", "ओटीपी", "ओ.टी.पी"),
        FindingCode.UPI_PIN_REQUEST to listOf("upi pin", "mpin", "m-pin", "atm pin", "pin number", "your pin", "यूपीआई पिन", "पिन नंबर", "एटीएम पिन", "यूपीआय पिन"),
        FindingCode.CARD_DETAILS_REQUEST to listOf("cvv", "card number", "expiry date", "card details", "debit card number", "credit card number", "कार्ड नंबर", "सीवीवी"),
        FindingCode.PASSWORD_REQUEST to listOf("password", "net banking login", "netbanking password", "login id", "पासवर्ड"),
    )

    /** Verbs that turn a secret mention into a request. */
    val requestVerbs = listOf(
        "share", "send", "tell", "give", "provide", "enter", "forward", "reply with", "submit", "read out", "tell me", "say",
        "bata", "batao", "bataiye", "bhejo", "bhej", "dijiye",
        "दें", "दीजिए", "बताएं", "बताइए", "बताओ", "भेजें", "भेजो", "शेयर करें", "डालें", "दर्ज करें",
        "सांगा", "पाठवा", "द्या", "शेअर करा", "टाका", "प्रविष्ट करा",
    )

    /** Standard bank/UPI safety notices ("Do not share your OTP"). Request verbs inside these are ignored. */
    val safetyNotices = listOf(
        "do not share", "don't share", "dont share", "never share", "not share", "never ask", "will never ask", "never asks",
        "do not disclose", "don't disclose", "never disclose", "is confidential", "not to share", "do not give", "never give",
        "kisi ko na", "share na kare", "share na karein",
        "किसी के साथ साझा न करें", "किसी को न बताएं", "साझा न करें", "शेयर न करें", "किसी को न दें", "कभी नहीं मांगता", "कभी नहीं मांगते",
        "कोणालाही सांगू नका", "शेअर करू नका", "कोणाशीही शेअर करू नका", "कधीही विचारत नाही",
    )
}
