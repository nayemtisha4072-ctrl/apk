package com.example.ads

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.BuildConfig
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError

/**
 * Non-intrusive Banner Ad bar placed outside active calculator controls.
 * Collapses smoothly when offline or when ad loading fails.
 */
@Composable
fun AdBannerBar(
    adMobManager: AdMobManager,
    isOnline: Boolean,
    modifier: Modifier = Modifier
) {
    val canRequestAds by adMobManager.canRequestAds.collectAsStateWithLifecycle()
    val bannerId = BuildConfig.ADMOB_BANNER_ID
    val canShow = isOnline && canRequestAds && adMobManager.isAdUnitValidForBuild(bannerId)

    var adLoaded by remember { mutableStateOf(false) }

    if (!canShow) return

    val context = LocalContext.current
    val adView = remember(bannerId) {
        AdView(context).apply {
            setAdSize(AdSize.BANNER)
            adUnitId = bannerId
            adListener = object : AdListener() {
                override fun onAdLoaded() {
                    adLoaded = true
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    adLoaded = false
                }
            }
            loadAd(AdRequest.Builder().build())
        }
    }

    DisposableEffect(adView) {
        onDispose {
            adView.destroy()
        }
    }

    AnimatedVisibility(visible = adLoaded, modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                .padding(vertical = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            AndroidView(
                factory = { adView }
            )
        }
    }
}
