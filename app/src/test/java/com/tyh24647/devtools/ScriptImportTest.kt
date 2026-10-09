package com.tyh24647.devtools
import org.junit.Test
import org.junit.Assert.*
class ScriptImportTest {
    @Test fun rejectsWebpagesBinaryAndOversizedSources() {
        listOf("<html>not a plugin</html>".toByteArray(),byteArrayOf(0),byteArrayOf(0xC3.toByte(),0x28),ByteArray(262145)).forEach {bytes->
            assertTrue(runCatching{ScriptImport.decode(bytes)}.isFailure)
        }
        assertEquals("return {};",ScriptImport.decode("\uFEFFreturn {};".toByteArray()))
    }
    @Test fun enforcesScriptKindAndUserscriptMetadata() {
        val script="// ==UserScript==\n// @name Test\n// @match https://example.com/*\n// ==/UserScript==\nconsole.log('hello');"
        assertEquals("Test",ScriptImport.draft(script,"userscript").name)
        assertTrue(runCatching{ScriptImport.draft(script,"eruda")}.isFailure)
        assertTrue(runCatching{ScriptImport.draft("console.log(1)","userscript")}.isFailure)
    }
}
