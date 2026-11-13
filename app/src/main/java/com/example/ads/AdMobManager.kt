package com.example.ads

import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import com.example.BuildConfig
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface AdActionState {
    data object Idle : AdActionState
    data object Loading : AdActionState
    data object Ready : AdActionState
    data class Failed(val reason: String) : AdActionState
}

/**
 * Centralized Google Mobile Ads & UMP Consent manager.
 * - Strictly isolates test ad IDs to debug builds ([BuildConfig.USE_TEST_ADS]).
 * - Never interrupts active calculations.
 * - Degrades gracefully offline or when ads fail to load.
 */
class AdMobManager(private val appContext: Context) {

    private val _isInitialized = MutableStateFlow(false)
    val isInitialized: StateFlow<Boolean> = _isInitialized.asStateFlow()

    private val _canRequestAds = MutableStateFlow(false)
    val canRequestAds: StateFlow<Boolean> = _canRequestAds.asStateFlow()

    private val _isPrivacyOptionsRequired = MutableStateFlow(false)
    val isPrivacyOptionsRequired: StateFlow<Boolean> = _isPrivacyOptionsRequired.asStateFlow()

    private val _rewardedState = MutableStateFlow<AdActionState>(AdActionState.Idle)
    val rewardedState: StateFlow<AdActionState> = _rewardedState.asStateFlow()

    private var interstitialAd: InterstitialAd? = null
    private var rewardedAd: RewardedAd? = null
    private var lastInterstitialShownMillis: Long = 0L
    private var eligibleActionsSinceLastAd: Int = 0

    fun isAdUnitValidForBuild(adUnitId: String): Boolean {
        if (!BuildConfig.ADS_ENABLED || adUnitId.isBlank()) return false
        val isGoogleSampleUnit = adUnitId.startsWith(GOOGLE_TEST_PUB_PREFIX)
        if (!BuildConfig.USE_TEST_ADS && isGoogleSampleUnit) {
            Log.e(TAG, "Blocked test ad unit ID in release build")
            return false
        }
        return true
    }

    fun hasValidManifestAppId(): Boolean {
        if (!BuildConfig.ADS_ENABLED) return false
        return runCatching {
            val appInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                appContext.packageManager.getApplicationInfo(
                    appContext.packageName,
                    PackageManager.ApplicationInfoFlags.of(PackageManager.GET_META_DATA.toLong())
                )
            } else {
                @Suppress("DEPRECATION")
                appContext.packageManager.getApplicationInfo(
                    appContext.packageName,
                    PackageManager.GET_META_DATA
                )
            }
            val appId = appInfo.metaData?.getString("com.google.android.gms.ads.APPLICATION_ID").orEmpty()
            appId.isNotBlank() && appId != UNCONFIGURED_RELEASE_APP_ID
        }.getOrDefault(false)
    }

    fun gatherConsentAndInitialize(activity: Activity) {
        if (!BuildConfig.ADS_ENABLED || !hasValidManifestAppId()) {
            Log.i(TAG, "Ads disabled for this build.")
            return
        }

        val params = ConsentRequestParameters.Builder()
            .setTagForUnderAgeOfConsent(false)
            .build()

        val consentInfo = UserMessagingPlatform.getConsentInformation(activity)
        consentInfo.requestConsentInfoUpdate(
            activity,
            params,
            {
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { loadError ->
                    if (loadError != null) {
                        Log.w(TAG, "UMP consent form warning: ${loadError.message}")
                    }
                    _isPrivacyOptionsRequired.value =
                        consentInfo.privacyOptionsRequirementStatus ==
                            ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED
                    if (consentInfo.canRequestAds()) {
                        initializeMobileAds()
                    }
                }
            },
            { requestError ->
                Log.w(TAG, "Consent info update unavailable (offline or unconfigured): ${requestError.message}")
                if (consentInfo.canRequestAds()) {
                    initializeMobileAds()
                } else if (BuildConfig.USE_TEST_ADS) {
                    // Allow debug test ads locally when offline/headless consent fails
                    initializeMobileAds()
                }
            }
        )
    }

    fun showPrivacyOptionsForm(activity: Activity, onDismissed: (String?) -> Unit) {
        UserMessagingPlatform.showPrivacyOptionsForm(activity) { formError ->
            onDismissed(formError?.message)
        }
    }

    private fun initializeMobileAds() {
        if (_isInitialized.value) return
        _canRequestAds.value = true
        runCatching {
            MobileAds.initialize(appContext) {
                _isInitialized.value = true
                preloadInterstitial()
                preloadRewarded()
            }
        }.onFailure { err ->
            Log.w(TAG, "MobileAds initialization skipped: ${err.message}")
        }
    }

    fun preloadInterstitial() {
        val unitId = BuildConfig.ADMOB_INTERSTITIAL_ID
        if (!_canRequestAds.value || !isAdUnitValidForBuild(unitId) || interstitialAd != null) return

        val request = AdRequest.Builder().build()
        InterstitialAd.load(
            appContext,
            unitId,
            request,
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    interstitialAd = ad
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    interstitialAd = null
                    Log.d(TAG, "Interstitial failed to load: ${error.message}")
                }
            }
        )
    }

    /**
     * Shows an interstitial ad only after non-calculation milestones (e.g., export completion)
     * and enforces strict cooldown + action count limits so UX is never disrupted.
     */
    fun maybeShowInterstitialAfterMilestone(activity: Activity) {
        eligibleActionsSinceLastAd++
        val now = System.currentTimeMillis()
        val cooldownElapsed = now - lastInterstitialShownMillis >= INTERSTITIAL_COOLDOWN_MS
        if (!cooldownElapsed || eligibleActionsSinceLastAd < MIN_ACTIONS_BETWEEN_INTERSTITIALS) {
            return
        }
        val ad = interstitialAd ?: run {
            preloadInterstitial()
            return
        }

        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                interstitialAd = null
                lastInterstitialShownMillis = System.currentTimeMillis()
                eligibleActionsSinceLastAd = 0
                preloadInterstitial()
            }

            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                interstitialAd = null
                preloadInterstitial()
            }
        }
        ad.show(activity)
    }

    fun preloadRewarded() {
        val unitId = BuildConfig.ADMOB_REWARDED_ID
        if (!_canRequestAds.value || !isAdUnitValidForBuild(unitId)) {
            _rewardedState.value = AdActionState.Failed("Rewarded ads unavailable in this build")
            return
        }
        if (rewardedAd != null) {
            _rewardedState.value = AdActionState.Ready
            return
        }

        _rewardedState.value = AdActionState.Loading
        RewardedAd.load(
            appContext,
            unitId,
            AdRequest.Builder().build(),
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    rewardedAd = ad
                    _rewardedState.value = AdActionState.Ready
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    rewardedAd = null
                    _rewardedState.value = AdActionState.Failed("Ad unavailable right now")
                }
            }
        )
    }

    fun showRewardedAd(
        activity: Activity,
        onRewardEarned: () -> Unit,
        onUnavailable: (String) -> Unit
    ) {
        val ad = rewardedAd
        if (ad == null) {
            preloadRewarded()
            onUnavailable("Rewarded ad is still loading or offline. Try again shortly.")
            return
        }

        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                rewardedAd = null
                _rewardedState.value = AdActionState.Idle
                preloadRewarded()
            }

            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                rewardedAd = null
                _rewardedState.value = AdActionState.Failed(adError.message)
                onUnavailable("Unable to display ad right now.")
                preloadRewarded()
            }
        }
        ad.show(activity) {
            onRewardEarned()
        }
    }

    companion object {
        private const val TAG = "AdMobManager"
        private const val GOOGLE_TEST_PUB_PREFIX = "ca-app-pub-3940256099942544"
        private const val UNCONFIGURED_RELEASE_APP_ID = "ca-app-pub-0000000000000000~0000000000"
        private const val INTERSTITIAL_COOLDOWN_MS = 180_000L // 3 minutes minimum cooldown
        private const val MIN_ACTIONS_BETWEEN_INTERSTITIALS = 3
    }
}
