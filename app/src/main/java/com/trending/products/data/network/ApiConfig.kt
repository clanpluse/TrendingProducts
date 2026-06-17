package com.trending.products.data.network

/**
 * Runtime-configurable API keys.
 * Keys are entered by the user in SetupActivity and stored in SharedPreferences.
 *
 * HOW TO GET FREE KEYS:
 *
 * 1. EBAY APP ID (free — 5 minutes):
 *    → https://developer.ebay.com/my/keys
 *    → Sign in → Create Application → Copy "App ID (Client ID)"
 *
 * 2. ALIEXPRESS AFFILIATE API (free):
 *    → https://portals.aliexpress.com
 *    → Register → Tools → API → Get App Key + App Secret
 *
 * 3. GOOGLE TRENDS: No key needed — works automatically.
 */
object ApiConfig {

    // Runtime-mutable keys — set by SetupActivity from SharedPreferences
    var runtimeEbayAppId: String = ""
    var runtimeAliKey: String = ""
    var runtimeAliSecret: String = ""

    val EBAY_APP_ID: String get() = runtimeEbayAppId
    val ALIEXPRESS_APP_KEY: String get() = runtimeAliKey
    val ALIEXPRESS_APP_SECRET: String get() = runtimeAliSecret

    const val GOOGLE_TRENDS_GEO = "US"

    fun isEbayConfigured() = runtimeEbayAppId.isNotEmpty()
    fun isAliExpressConfigured() = runtimeAliKey.isNotEmpty() && runtimeAliSecret.isNotEmpty()
}
