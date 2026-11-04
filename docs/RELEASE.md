# Release builds and signing

`./gradlew :app:assembleRelease` always builds R8-shrunk release APKs (per-ABI plus universal). Signing is applied only when all four environment variables below are set; otherwise the files are named `*-release-unsigned.apk`. Unsigned APKs cannot be installed, so use the debug APK for demos until signing is configured.

No keystore, password or `keystore.properties` is in the repository, and `.gitignore` blocks `*.jks`/`*.keystore`.

## 1. Create an upload keystore (once, on your machine)

```bash
keytool -genkeypair -v -keystore cybershield-release.jks -alias cybershield \
  -keyalg RSA -keysize 4096 -validity 10000
```

Store the `.jks` file and both passwords in a password manager. If you lose them you cannot update the app under the same signature.

## 2. Sign locally

```bash
export CYBERSHIELD_KEYSTORE_PATH=/absolute/path/cybershield-release.jks
export CYBERSHIELD_KEYSTORE_PASSWORD='…'
export CYBERSHIELD_KEY_ALIAS=cybershield
export CYBERSHIELD_KEY_PASSWORD='…'
export CYBERSHIELD_BACKEND_URL=https://your-backend.example   # optional; must be https
cd android && ./gradlew :app:assembleRelease
```

## 3. Sign in GitHub Actions

Repository → Settings → Secrets and variables → Actions:

| Type | Name | Value |
|---|---|---|
| Secret | `CYBERSHIELD_KEYSTORE_BASE64` | output of `base64 -i cybershield-release.jks` (macOS) or `base64 -w0 cybershield-release.jks` (Linux) |
| Secret | `CYBERSHIELD_KEYSTORE_PASSWORD` | keystore password |
| Secret | `CYBERSHIELD_KEY_ALIAS` | `cybershield` |
| Secret | `CYBERSHIELD_KEY_PASSWORD` | key password |
| Variable | `CYBERSHIELD_BACKEND_URL` | `https://…` of your deployed backend (optional) |

The workflow decodes the keystore into the runner's temp directory, builds, uploads the `cybershield-release-apk` artifact and deletes the keystore. Secrets are not available to pull requests from forks, so those builds stay unsigned.

## 4. Verify a signed APK

```bash
$ANDROID_HOME/build-tools/35.0.0/apksigner verify --print-certs app-arm64-v8a-release.apk
```

For Google Play, build an App Bundle instead (`./gradlew :app:bundleRelease`) and enrol in Play App Signing.
