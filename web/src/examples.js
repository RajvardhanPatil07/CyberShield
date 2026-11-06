// One-click examples for the demo. Taken from the labelled evaluation sets in android/app/src/test/resources/eval
// (fictitious numbers, UPI IDs and domains) plus a few edge cases.
export const examples = {
  message: [
    { label: 'SBI KYC + OTP', text: 'Dear customer, your SBI account will be blocked today. Share OTP 482913 and update KYC immediately at sbi-kyc-update.xyz/login' },
    { label: 'हिन्दी: बैंक खाता', text: 'प्रिय ग्राहक, आपका बैंक खाता आज बंद हो जाएगा। तुरंत केवाईसी अपडेट करें: sbi-kyc-update.xyz' },
    { label: 'मराठी: बँक खाते', text: 'प्रिय ग्राहक, तुमचे बँक खाते आज बंद होईल. लगेच केवायसी अपडेट करा: sbi-kyc.top' },
    { label: 'Hinglish lottery', text: 'Badhai ho! Aapne KBC lottery mein 25 lakh jeete hain. Inaam lene ke liye Rs 5000 registration fee bhejo.' },
    { label: 'Digital arrest', text: 'This is CBI officer. A parcel in your name contains drugs and 5 fake passports. You are under digital arrest. Do not disconnect the call and do not tell your family.' },
    { label: 'Receive-money trick', text: 'Scan this QR code and enter your UPI PIN to receive Rs 5000 cashback in your account.' },
    { label: 'Genuine bank OTP', text: '482913 is your OTP for the transaction of Rs 1,250.00 at AMAZON. Valid for 5 minutes. Do not share this OTP with anyone. - HDFC Bank' },
  ],
  link: [
    { label: 'Fake HDFC KYC', text: 'https://hdfcbank-kyc-update.xyz/verify' },
    { label: 'Misspelt ICICI', text: 'www.icicbank.com/login' },
    { label: 'Cyrillic “paytm”', text: 'https://раytm.com/verify' },
    { label: 'Hidden real host', text: 'https://www.google.com@phish.top/' },
    { label: 'APK download', text: 'https://sbi-yono.web.app/yono-update.apk' },
    { label: 'Real HDFC', text: 'https://www.hdfcbank.com/personal' },
  ],
  qr: [
    { label: 'Fake refund desk', text: 'upi://pay?pa=refund.desk@ybl&pn=SBI%20Refund%20Desk&am=4999&tn=Refund%20processing' },
    { label: 'Collect request', text: 'upi://collect?pa=winner@paytm&pn=Prize&am=15000' },
    { label: 'Shop QR', text: 'upi://pay?pa=ravistores@okaxis&pn=Ravi%20Stores&am=250&cu=INR' },
    { label: 'Phishing link', text: 'https://amaz0n-prize.shop/claim' },
  ],
};
