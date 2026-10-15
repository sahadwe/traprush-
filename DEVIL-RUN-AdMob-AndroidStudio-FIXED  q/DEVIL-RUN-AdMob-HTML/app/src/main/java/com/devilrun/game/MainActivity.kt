package com.devilrun.game

import android.annotation.SuppressLint
import android.app.Activity
import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback

class MainActivity : Activity() {
    private lateinit var webView: WebView
    private var rewardedAd: RewardedAd? = null
    private var isLoadingAd = false

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.decorView.systemUiVisibility = (4 or 1024 or 4096 or 512 or 2)
        webView = WebView(this)
        setContentView(webView)
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.webChromeClient = WebChromeClient()
        webView.webViewClient = WebViewClient()
        webView.addJavascriptInterface(GameBridge(), "Android")
        MobileAds.initialize(this) { loadRewardedAd() }
        webView.loadUrl("file:///android_asset/index.html")
    }

    private fun loadRewardedAd() {
        if (isLoadingAd) return
        isLoadingAd = true
        RewardedAd.load(this, REWARDED_AD_UNIT_ID, AdRequest.Builder().build(), object : RewardedAdLoadCallback() {
            override fun onAdLoaded(ad: RewardedAd) {
                rewardedAd = ad
                isLoadingAd = false
            }
            override fun onAdFailedToLoad(error: LoadAdError) {
                rewardedAd = null
                isLoadingAd = false
            }
        })
    }

    private inner class GameBridge {
        @JavascriptInterface
        fun showRewardedAd() {
            runOnUiThread {
                val ad = rewardedAd
                if (ad == null) {
                    callJs("window.onRewardedAdUnavailable('Ad is not ready yet. Please try again in a moment.');")
                    loadRewardedAd()
                    return@runOnUiThread
                }
                rewardedAd = null
                var earnedReward = false
                ad.show(this@MainActivity) {
                    earnedReward = true
                    callJs("window.onRewardedAdCompleted && window.onRewardedAdCompleted();")
                }
                ad.fullScreenContentCallback = object : com.google.android.gms.ads.FullScreenContentCallback() {
                    override fun onAdDismissedFullScreenContent() {
                        if (!earnedReward) callJs("window.onRewardedAdUnavailable('Ad closed before the reward was earned.');")
                        loadRewardedAd()
                    }
                    override fun onAdFailedToShowFullScreenContent(error: com.google.android.gms.ads.AdError) {
                        callJs("window.onRewardedAdUnavailable('Could not show ad. Please try again.');")
                        loadRewardedAd()
                    }
                }
            }
        }
    }

    private fun callJs(script: String) {
        if (::webView.isInitialized) webView.post { webView.evaluateJavascript(script, null) }
    }

    override fun onBackPressed() {
        if (::webView.isInitialized && webView.canGoBack()) webView.goBack() else super.onBackPressed()
    }

    companion object {
        // User's Rewarded Ad Unit ID. Use Google's test ad ID during development.
        private const val REWARDED_AD_UNIT_ID = "ca-app-pub-4904698997849918/6927843684"
    }
}
