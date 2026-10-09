package com.tyh24647.devtools

import org.junit.Assert.*
import org.junit.Test

/** Navigation must not execute incoming intent text as a script or privileged URI. */
class WebURLsTest {
    @Test
    fun bareDomainsBecomeHttps() {
        assertEquals("https://example.com/path?q=1", WebURLs.normalize("example.com/path?q=1"))
    }

    @Test
    fun localHttpDevelopmentIsSupported() {
        assertEquals("http://10.0.2.2:8080/", WebURLs.normalize("http://10.0.2.2:8080/"))
    }

    @Test
    fun privilegedSchemesAndCredentialsAreRejected() {
        listOf(
                "javascript:alert(1)",
                "file:///etc/passwd",
                "content://provider/data",
                "intent://app",
                "https://user:password@example.com/",
                "ftp://example.com/",
            )
            .forEach { assertNull(WebURLs.normalize(it)) }
    }

    @Test
    fun multilineOrOversizedNavigationIsRejected() {
        assertNull(WebURLs.normalize("https://example.com/\njavascript:alert(1)"))
        assertNull(WebURLs.normalize("https://example.com/" + "a".repeat(4096)))
    }

    @Test
    fun sharesExtractLinksWithoutExecutingOtherText() {
        assertEquals(
            "https://example.com/docs",
            WebURLs.fromShare("Documentation https://example.com/docs is here"),
        )
        assertNull(WebURLs.fromShare("javascript:alert(1)"))
    }
}
