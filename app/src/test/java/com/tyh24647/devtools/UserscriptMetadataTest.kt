package com.tyh24647.devtools
import org.junit.Assert.*
import org.junit.Test
class UserscriptMetadataTest {
    @Test fun importsStandardHeader() {
        val data=UserscriptMetadata.validate("// ==UserScript==\n// @name Test\n// @match https://example.com/*\n// @grant GM_addStyle\n// ==/UserScript==\n")
        assertEquals("Test",data["name"]!!.first())
    }
    @Test(expected=IllegalArgumentException::class) fun rejectsUnsupportedNetworkGrant() {
        UserscriptMetadata.validate("// ==UserScript==\n// @match https://example.com/*\n// @grant GM_xmlhttpRequest\n// ==/UserScript==")
    }
}
