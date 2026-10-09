package com.tyh24647.devtools

import android.app.Activity
import android.os.Bundle
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.android.gms.ads.*
import com.google.android.gms.ads.appopen.AppOpenAd
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.ump.*

/** Ads never receive visited URLs, rules, log contents or tool configuration. */
class Ads(private val activity: Activity, private val config: Configuration) {
    private val consent = UserMessagingPlatform.getConsentInformation(activity)
    private var initialized = false
    private var interstitial: InterstitialAd? = null
    private var appOpen: AppOpenAd? = null
    private var openLoadedAt = 0L
    private var lastFullScreen = 0L
    private var lastOpportunity = SystemClock.elapsedRealtime()
    private var ruleAdded = false
    var bannerRevision by mutableStateOf(0)
        private set

    var available by mutableStateOf(false)
        private set

    var showing by mutableStateOf(false)
        private set

    val eligible: Boolean
        get() = BuildConfig.ADS_ENABLED && config.billingChecked && !config.pro && available

    val privacyRequired: Boolean
        get() =
            consent.privacyOptionsRequirementStatus ==
                ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED

    fun start() {
        if (!BuildConfig.ADS_ENABLED || !config.billingChecked || config.pro) {
            return
        }
        consent.requestConsentInfoUpdate(
            activity,
            ConsentRequestParameters.Builder().build(),
            { UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { initialize() } },
            { initialize() },
        )
        initialize()
    }

    private fun initialize() {
        if (initialized || !consent.canRequestAds() || config.pro) {
            return
        }
        initialized = true
        MobileAds.initialize(activity) {
            activity.runOnUiThread {
                available = true
                preload()
            }
        }
    }

    fun request(): AdRequest {
        val extras = Bundle().apply { putString("npa", "1") }
        return AdRequest.Builder()
            .addNetworkExtrasBundle(com.google.ads.mediation.admob.AdMobAdapter::class.java, extras)
            .build()
    }

    fun addedRule() {
        ruleAdded = true
        bannerRevision++
    }

    /** Called at completed edits/navigation, never by a timer during typing or a purchase. */
    fun opportunity() {
        val now = SystemClock.elapsedRealtime()
        if (
            !eligible ||
                showing ||
                now - lastFullScreen < 30000 ||
                (!ruleAdded && now - lastOpportunity < 180000)
        ) {
            return
        }
        val ad = interstitial ?: return
        interstitial = null
        ruleAdded = false
        lastOpportunity = now
        showing = true
        lastFullScreen = now
        ad.show(activity)
    }

    fun foreground() {
        if (!eligible || showing || SystemClock.elapsedRealtime() - lastFullScreen < 30000) {
            return
        }
        val ad = appOpen ?: return
        if (SystemClock.elapsedRealtime() - openLoadedAt > 4 * 60 * 60 * 1000L) {
            appOpen = null
            preload()
            return
        }
        appOpen = null
        ad.fullScreenContentCallback = callback { preload() }
        showing = true
        lastFullScreen = SystemClock.elapsedRealtime()
        ad.show(activity)
    }

    private fun callback(completed: () -> Unit) =
        object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                showing = false
                completed()
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                showing = false
                completed()
            }
        }

    private fun preload() {
        if (!eligible) {
            return
        }
        InterstitialAd.load(
            activity,
            BuildConfig.AD_INTERSTITIAL,
            request(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    ad.fullScreenContentCallback = callback { preload() }
                    interstitial = ad
                }
            },
        )
        AppOpenAd.load(
            activity,
            BuildConfig.AD_OPEN,
            request(),
            object : AppOpenAd.AppOpenAdLoadCallback() {
                override fun onAdLoaded(ad: AppOpenAd) {
                    appOpen = ad
                    openLoadedAt = SystemClock.elapsedRealtime()
                }
            },
        )
    }

    fun privacy() {
        UserMessagingPlatform.showPrivacyOptionsForm(activity) {
            available = consent.canRequestAds()
            initialize()
        }
    }
}
