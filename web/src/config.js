// Deployment settings for the web demo. Edit before deploying; see web/README.md.
export const config = {
  // HTTPS address of the deployed backend (backend/), without a trailing slash, e.g. 'https://cybershield-api.onrender.com'.
  // Empty: the cloud buttons are hidden and only the in-browser checks run.
  // The backend must list this site's address in ALLOWED_ORIGINS.
  backendUrl: '',

  // Where judges can download the signed Android APK, e.g. a public GitHub release asset or '/downloads/cybershield.apk'.
  // Empty: the "Get the Android app" button is hidden.
  apkUrl: '',
};
