# Google AdMob Setup Guide

Multi Calculator cleanly separates **Debug Test Ads** from **Production Release Ads** in `app/build.gradle.kts`.

## 1. Debug vs. Release Behavior
- **`debug` buildType (`BuildConfig.USE_TEST_ADS = true`)**: Automatically uses Google's official sample AdMob App ID and Banner / Interstitial / Rewarded test ad unit IDs so you can safely test without violating invalid-traffic policies.
- **`release` buildType (`BuildConfig.USE_TEST_ADS = false`)**: Never falls back to Google test IDs. It reads production IDs from environment variables or Gradle properties:
  - `ADMOB_APP_ID`
  - `ADMOB_BANNER_ID`
  - `ADMOB_INTERSTITIAL_ID`
  - `ADMOB_REWARDED_ID`

## 2. Configure Production IDs
1. Create an Android app in [Google AdMob](https://admob.google.com/).
2. Create three Ad Units:
   - **Banner** (shown on Notes, History, and Settings — never overlapping calculator buttons)
   - **Interstitial** (frequency-capped with a 3-minute cooldown after secondary milestones)
   - **Rewarded** (voluntary support button in Settings)
3. Set the IDs securely in **GitHub Actions Repository Secrets** (or your local `.env` / Gradle environment, never committed to git):
   - `ADMOB_APP_ID=ca-app-pub-XXXXXXXXXXXXXXXX~YYYYYYYYYY`
   - `ADMOB_BANNER_ID=ca-app-pub-XXXXXXXXXXXXXXXX/1111111111`
   - `ADMOB_INTERSTITIAL_ID=ca-app-pub-XXXXXXXXXXXXXXXX/2222222222`
   - `ADMOB_REWARDED_ID=ca-app-pub-XXXXXXXXXXXXXXXX/3333333333`

## 3. Consent & Privacy (UMP)
`AdMobManager` integrates Google's **User Messaging Platform (UMP)** SDK (`UserMessagingPlatform`) to request consent in EEA/UK/applicable regions and surfaces a **Manage Ad Consent Preferences** button in Settings whenever required.
