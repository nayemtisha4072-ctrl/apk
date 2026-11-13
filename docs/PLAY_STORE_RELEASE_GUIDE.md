# Google Play Store Publishing Guide — Multi Calculator

> **Note:** Preparing release artifacts does not imply Google Play approval. Final publication depends on Google Play review, your developer account standing, and accurate console declarations.

---

## 1. Build Configuration Checklist
- **Application ID:** `com.aistudio.multicalc.kxpqzm` (set in `app/build.gradle.kts`; update before first Play upload if you use a custom domain package).
- **Target & Min SDK:** `minSdk = 24`, `targetSdk = 36`, `compileSdk = 36`.
- **Version:** `versionCode = 1`, `versionName = "1.0.0"`.
- **Permissions:** Only `INTERNET` and `ACCESS_NETWORK_STATE` (no broad storage, camera, microphone, or location permissions).

## 2. Generate Upload Key & Signed Android App Bundle (AAB)
1. Generate a private upload keystore (keep it offline / in CI secrets, **never commit to GitHub**):
   ```bash
   keytool -genkey -v -keystore my-upload-key.jks -keyalg RSA -keysize 2048 -validity 10000 -alias upload
   ```
2. Build the signed release AAB locally or via `.github/workflows/android-ci.yml`:
   ```bash
   export KEYSTORE_PATH=/path/to/my-upload-key.jks
   export STORE_PASSWORD="your-store-password"
   export KEY_PASSWORD="your-key-password"
   export ADMOB_APP_ID="ca-app-pub-..."
   export ADMOB_BANNER_ID="ca-app-pub-..."
   export ADMOB_INTERSTITIAL_ID="ca-app-pub-..."
   export ADMOB_REWARDED_ID="ca-app-pub-..."
   gradle :app:bundleRelease
   ```
   Output: `app/build/outputs/bundle/release/app-release.aab`
3. Enroll in **Play App Signing** in Google Play Console when uploading the first `.aab`.

## 3. Store Listing Details
- **App Title (≤ 30 chars):** `Multi Calculator` (16 characters, no emojis or promotional buzzwords)
- **Short Description (≤ 80 chars):**
  `Arbitrary-precision scientific calculator, notepad, and calculation history.`
- **Full Description:**
  Highlight exact large-integer & configurable decimal math (no 15-digit limit), Standard & Scientific modes (trig, log, factorial, DEG/RAD), searchable calculation history, pinned-first notepad with auto-save, offline Room storage, and lossless JSON backup/restore.

## 4. Visual Assets Required for Play Console
- **App Icon:** 512 × 512 PNG (included in `app/src/main/res/drawable/multi_calc_logo.png`).
- **Feature Graphic:** 1024 × 500 PNG/JPG showcasing Calculator + Notepad + History.
- **Phone Screenshots:** Minimum 2–8 screenshots (1080 × 1920 or 9:16) covering Standard Calculator, Scientific Mode, Notepad, History, and Dark Mode.
- **Tablet Screenshots:** 7-inch and 10-inch adaptive layout screenshots showing the side-by-side NavigationRail & wide keypad.

## 5. Play Console Declarations
- **App Category:** Tools / Productivity
- **Ads Declaration:** Yes, contains ads (Google AdMob Banner, Interstitial, Rewarded).
- **Advertising ID Declaration:** Yes (via Google Mobile Ads SDK for advertising/analytics & UMP consent).
- **Data Safety Form:**
  - Notes and calculation history are stored locally on-device unless the user exports a JSON backup or enables optional cloud sync.
  - AdMob SDK collects Device / Advertising ID and basic diagnostics/ad interaction events for advertising and fraud prevention (encrypted in transit).
- **Privacy Policy URL:** Host your public Privacy Policy URL matching the in-app Privacy Policy text in Settings.
- **Content Rating (IARC):** Complete the questionnaire accurately (Utility/Calculator with standard AdMob ads → typically rated Everyone / PEGI 3).
