package com.tyh24647.devtools

/** Small bundled ad-host list; exact host boundaries avoid blocking similarly named sites. */
object BrowserAds {
    private val hosts = setOf("doubleclick.net", "googlesyndication.com", "googleadservices.com", "adnxs.com", "adsrvr.org", "ads.yahoo.com", "amazon-adsystem.com", "taboola.com", "outbrain.com")
    fun blocked(host: String): Boolean = hosts.any { host.lowercase() == it || host.lowercase().endsWith(".$it") }
}
