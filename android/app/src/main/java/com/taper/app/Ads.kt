package com.taper.app

import android.content.Context
import android.util.Log
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.RequestConfiguration
import com.google.android.gms.ads.VideoOptions
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdOptions

/**
 * Native ads only. A native ad is a set of fields — headline, body, icon, a call to
 * action — that Taper lays out itself, so the ad sits inside the pause screen under
 * Taper's own wording and Taper's own timer. Nothing takes over the screen, nothing
 * plays sound, nothing is unskippable that wasn't already.
 *
 * The point is the timeout, not the impression. The ad fills a gap the app was going
 * to impose anyway; if none loads, the gap happens exactly the same.
 *
 * The unit ID comes from AdConfig — that's the one file you edit to start earning.
 */
object Ads {
    private const val TAG = "TaperAds"

    private var initialized = false
    private var loading = false
    private var cached: NativeAd? = null

    /** Why the last load failed, for the in-app preview. Null when it hasn't failed. */
    var lastError: String? = null
        private set

    /** Set once a load has been attempted, so the UI can tell "waiting" from "nothing". */
    var attempted = false
        private set

    fun init(c: Context) {
        if (initialized) return
        initialized = true
        if (AdConfig.TEST_DEVICE_IDS.isNotEmpty()) {
            MobileAds.setRequestConfiguration(
                RequestConfiguration.Builder()
                    .setTestDeviceIds(AdConfig.TEST_DEVICE_IDS)
                    .build()
            )
        }
        MobileAds.initialize(c) {}
    }

    /** Warms one ad. Safe to call often; no-ops while loading or when one is ready. */
    fun preload(c: Context, unitId: String = AdConfig.nativeUnitId) {
        init(c)
        if (loading || cached != null) return
        loading = true
        attempted = true
        AdLoader.Builder(c, unitId)
            .forNativeAd { ad ->
                loading = false
                lastError = null
                cached?.destroy()
                cached = ad
            }
            .withAdListener(object : com.google.android.gms.ads.AdListener() {
                override fun onAdFailedToLoad(error: LoadAdError) {
                    loading = false
                    lastError = "${error.code}: ${error.message}"
                    Log.d(TAG, "native load failed: ${error.code} ${error.message}")
                }
            })
            .withNativeAdOptions(
                NativeAdOptions.Builder()
                    // Muted and still by default — an ad that starts talking during a
                    // cooldown is the thing people uninstall over.
                    .setVideoOptions(VideoOptions.Builder().setStartMuted(true).build())
                    .setAdChoicesPlacement(NativeAdOptions.ADCHOICES_TOP_RIGHT)
                    .build()
            )
            .build()
            .loadAd(AdRequest.Builder().build())
    }

    fun isReady(): Boolean = cached != null

    fun isLoading(): Boolean = loading

    /**
     * A plain-language read on why no ad is showing — the difference between "still
     * loading", "Google has nothing to serve yet" and "the IDs are wrong" matters a
     * lot when you're setting this up, and the log is a bad place to learn it.
     */
    fun status(): String = when {
        cached != null -> "Ready"
        loading -> "Loading…"
        lastError?.startsWith("3") == true ->
            "No fill — Google has no ad for this unit yet. Normal for a new app; it " +
                "usually starts filling once the app is live on Play."
        lastError?.startsWith("1:") == true ->
            "Invalid request — check the unit ID in AdConfig and that it's a NATIVE unit."
        lastError?.startsWith("2:") == true -> "Network error — no connection."
        lastError != null -> "Failed — ${lastError}"
        !attempted -> "Not requested yet"
        else -> "Waiting"
    }

    /**
     * Hands over the cached ad, if any. The caller owns it and must [release] it.
     * Immediately starts warming the next one.
     */
    fun take(c: Context): NativeAd? {
        val ad = cached
        cached = null
        if (ad != null) preload(c)
        else if (!loading) preload(c)
        return ad
    }

    fun release(ad: NativeAd?) = ad?.destroy()
}
