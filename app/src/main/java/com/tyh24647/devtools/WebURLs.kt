package com.tyh24647.devtools

import java.net.URI

/** Treat shared text as data. Only HTTP(S), without embedded credentials, can navigate. */
object WebURLs {
    fun normalize(input: String): String? {
        val text = input.trim()
        if (text.length > 4096 || text.any { it.isISOControl() }) {
            return null
        }
        return try {
            val uri = URI(if (text.contains("://")) text else "https://$text")
            if (
                uri.scheme !in listOf("http", "https") ||
                    uri.host.isNullOrBlank() ||
                    uri.userInfo != null
            ) {
                null
            } else {
                uri.toASCIIString()
            }
        } catch (_: Exception) {
            null
        }
    }

    fun fromShare(text: String): String? {
        val match = Regex("https?://[^\\s<>]+", RegexOption.IGNORE_CASE).find(text)?.value
        return normalize(match ?: text)
    }
}
