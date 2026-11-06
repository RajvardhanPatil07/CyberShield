// UI text: the Android app's strings (src/strings.js, generated) plus web-only wording.
// Web overrides replace phrases such as "on this phone" with "in your browser".
import { appStrings } from './strings.js';

const web = {
  en: {
    scamlens_subtitle: 'Paste a suspicious SMS, WhatsApp message or offer. It is analysed in your browser.',
    rec_stay_cautious: 'No strong red flags were found. Still verify the sender before acting.',
    cloud_consent_link: 'Only this link’s address is sent to the CyberShield server, which looks it up in Google Safe Browsing, VirusTotal and URLhaus. Nothing else leaves your browser.',
    disclaimer: 'Result from rules that run in your browser. It is not proof that something is malicious or safe.',
    qr_camera_rationale: 'Camera access is requested only when you choose to scan. Frames are processed in your browser.',
    qr_camera_error: 'The camera could not start. Allow camera access, or scan a QR from an image.',
    cloud_not_configured: 'Cloud checks are not set up on this site. Local analysis still works.',
    web_privacy: 'Everything is checked in your browser. Nothing is sent anywhere unless you tap a cloud check and agree.',
    web_get_app: 'Get the Android app',
    web_examples: 'Try an example',
    web_demo_note: 'Web demo of the CyberShield Android app, using the same detection rules.',
    web_camera_start: 'Scan with camera',
    web_camera_stop: 'Stop camera',
    web_qr_paste: 'Or paste the text inside a QR code (for example upi://pay?…)',
    web_check_qr: 'Check QR content',
    web_source: 'Source code',
    web_site: 'Website: %1$s',
    web_copy_failed: 'Could not copy. Select the text and copy it yourself.',
    web_ai_not_configured: 'AI explanations are not set up on the CyberShield server yet. The result above still applies.',
  },
  hi: {
    scamlens_subtitle: 'संदिग्ध SMS, WhatsApp संदेश या ऑफ़र पेस्ट करें। जाँच इसी ब्राउज़र में होती है।',
    rec_stay_cautious: 'कोई बड़ा ख़तरा संकेत नहीं मिला। फिर भी कुछ करने से पहले भेजने वाले की पुष्टि करें।',
    cloud_consent_link: 'सिर्फ़ इस लिंक का पता CyberShield सर्वर पर भेजा जाएगा, जो इसे Google Safe Browsing, VirusTotal और URLhaus में खोजता है। आपके ब्राउज़र से और कुछ नहीं जाता।',
    disclaimer: 'यह परिणाम ब्राउज़र में चलने वाले नियमों से है। यह किसी चीज़ के ख़तरनाक या सुरक्षित होने का प्रमाण नहीं है।',
    qr_camera_rationale: 'कैमरे की अनुमति सिर्फ़ तब माँगी जाती है जब आप स्कैन करना चुनते हैं। फ़्रेम इसी ब्राउज़र में प्रोसेस होते हैं।',
    qr_camera_error: 'कैमरा शुरू नहीं हो सका। कैमरे की अनुमति दें, या तस्वीर से QR स्कैन करें।',
    cloud_not_configured: 'इस साइट पर क्लाउड जाँच सेट नहीं है। लोकल जाँच फिर भी काम करती है।',
    web_privacy: 'सब कुछ आपके ब्राउज़र में जाँचा जाता है। जब तक आप क्लाउड जाँच पर टैप करके सहमति न दें, कुछ भी कहीं नहीं भेजा जाता।',
    web_get_app: 'Android ऐप डाउनलोड करें',
    web_examples: 'उदाहरण आज़माएँ',
    web_demo_note: 'CyberShield Android ऐप का वेब डेमो, उन्हीं जाँच नियमों के साथ।',
    web_camera_start: 'कैमरे से स्कैन करें',
    web_camera_stop: 'कैमरा बंद करें',
    web_qr_paste: 'या QR कोड के अंदर का टेक्स्ट पेस्ट करें (जैसे upi://pay?…)',
    web_check_qr: 'QR सामग्री जाँचें',
    web_source: 'सोर्स कोड',
    web_site: 'वेबसाइट: %1$s',
    web_copy_failed: 'कॉपी नहीं हो सका। टेक्स्ट चुनकर ख़ुद कॉपी करें।',
    web_ai_not_configured: 'CyberShield सर्वर पर AI व्याख्या अभी सेट नहीं है। ऊपर दिया परिणाम फिर भी लागू होता है।',
  },
  mr: {
    scamlens_subtitle: 'संशयास्पद SMS, WhatsApp संदेश किंवा ऑफर पेस्ट करा. तपासणी याच ब्राउझरमध्ये होते.',
    rec_stay_cautious: 'कोणतेही मोठे धोक्याचे संकेत आढळले नाहीत. तरीही कृती करण्यापूर्वी पाठवणाऱ्याची खात्री करा.',
    cloud_consent_link: 'फक्त या लिंकचा पत्ता CyberShield सर्व्हरला पाठवला जाईल, जो तो Google Safe Browsing, VirusTotal आणि URLhaus मध्ये शोधतो. तुमच्या ब्राउझरमधून इतर काहीही जात नाही.',
    disclaimer: 'हा निकाल ब्राउझरमधील नियमांवर आधारित आहे. एखादी गोष्ट धोकादायक किंवा सुरक्षित असल्याचा हा पुरावा नाही.',
    qr_camera_rationale: 'तुम्ही स्कॅन करायचे ठरवल्यावरच कॅमेऱ्याची परवानगी मागितली जाते. फ्रेम याच ब्राउझरमध्ये प्रोसेस होतात.',
    qr_camera_error: 'कॅमेरा सुरू होऊ शकला नाही. कॅमेऱ्याची परवानगी द्या, किंवा प्रतिमेतून QR स्कॅन करा.',
    cloud_not_configured: 'या साइटवर क्लाउड तपासणी सेट केलेली नाही. स्थानिक तपासणी तरीही चालते.',
    web_privacy: 'सर्व काही तुमच्या ब्राउझरमध्ये तपासले जाते. तुम्ही क्लाउड तपासणीवर टॅप करून संमती देईपर्यंत काहीही कुठेही पाठवले जात नाही.',
    web_get_app: 'Android ॲप डाउनलोड करा',
    web_examples: 'उदाहरण वापरून पहा',
    web_demo_note: 'CyberShield Android ॲपचा वेब डेमो, त्याच तपासणी नियमांसह.',
    web_camera_start: 'कॅमेऱ्याने स्कॅन करा',
    web_camera_stop: 'कॅमेरा बंद करा',
    web_qr_paste: 'किंवा QR कोडमधील मजकूर पेस्ट करा (उदा. upi://pay?…)',
    web_check_qr: 'QR मजकूर तपासा',
    web_source: 'सोर्स कोड',
    web_site: 'वेबसाइट: %1$s',
    web_copy_failed: 'कॉपी करता आले नाही. मजकूर निवडून स्वतः कॉपी करा.',
    web_ai_not_configured: 'CyberShield सर्व्हरवर AI स्पष्टीकरण अजून सेट केलेले नाही. वरील निकाल तरीही लागू होतो.',
  },
};

export const LANGS = ['en', 'hi', 'mr'];
let lang = 'en';

export function setLang(l) {
  lang = LANGS.includes(l) ? l : 'en';
  document.documentElement.lang = lang;
}
export const getLang = () => lang;

/** Looks up a string, falling back to English; %1$s, %2$s… are replaced by args. */
export function t(key, ...args) {
  const s = web[lang][key] ?? appStrings[lang]?.[key] ?? web.en[key] ?? appStrings.en[key] ?? key;
  return s.replace(/%(\d)\$s/g, (_, i) => String(args[Number(i) - 1] ?? ''));
}

export const findingText = (code) => t(`f_${code.toLowerCase()}`);
export const recText = (rec) => t(`rec_${rec.toLowerCase()}`);
export const levelText = (level) => t(`level_${level.toLowerCase()}`);

const langKeys = {
  ENGLISH: 'lang_detect_en', HINDI: 'lang_detect_hi', MARATHI: 'lang_detect_mr', ROMANISED_HINDI: 'lang_detect_hinglish',
  DEVANAGARI_UNKNOWN: 'lang_detect_devanagari', MIXED: 'lang_detect_mixed', UNKNOWN: 'lang_detect_unknown',
};
export const detectedLanguageText = (l) => t(langKeys[l] ?? 'lang_detect_unknown');

const qrTypeKeys = {
  WEB_URL: 'qr_type_web', UPI_PAYMENT: 'qr_type_upi', PHONE: 'qr_type_phone', SMS: 'qr_type_sms', EMAIL: 'qr_type_email', WIFI: 'qr_type_wifi',
  CONTACT: 'qr_type_contact', LOCATION: 'qr_type_location', APP_LINK: 'qr_type_app', TEXT: 'qr_type_text', INVALID: 'qr_type_invalid',
};
export const qrTypeText = (type) => t(qrTypeKeys[type] ?? 'qr_type_text');
