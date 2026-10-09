package com.tyh24647.devtools
import android.content.ContextWrapper
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Test
import org.junit.Assert.*
class ScriptOrganizationTest {
    @Test fun persistsOrganizationAndMovesScriptsWhenDeletingFolders() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val root=File(context.cacheDir,"script-config-test-"+System.nanoTime()).apply{mkdirs()}
        val isolated=object:ContextWrapper(context){override fun getFilesDir()=root}
        try {
            val config=Configuration(isolated)
            assertTrue(config.addScriptFolder("Panels","","eruda"))
            val parent=config.scriptFolders.single().getString("id")
            assertTrue(config.addScriptFolder("Nested",parent,"eruda"))
            val child=config.scriptFolders.last().getString("id")
            assertTrue(config.saveScriptTag("Work","#3B82F6","work"))
            assertTrue(config.savePlugin("Panel","return {name:'panel',init(){}};","panel"))
            assertTrue(config.scriptOrganization("panel",child,listOf("work")))
            assertTrue(config.savePlugin("Updated panel","return {name:'panel',init(){}};","panel"))
            val reloaded=Configuration(isolated)
            assertEquals(child,reloaded.plugins.single().getString("folder"))
            assertEquals("work",reloaded.plugins.single().getJSONArray("tags").getString(0))
            assertTrue(reloaded.removeScriptFolder(parent))
            assertTrue(reloaded.scriptFolders.isEmpty())
            assertEquals("",reloaded.plugins.single().getString("folder"))
            assertTrue(reloaded.removeScriptTag("work"))
            assertEquals(0,reloaded.plugins.single().getJSONArray("tags").length())
            assertEquals("Updated panel",Configuration(isolated).plugins.single().getString("name"))
        }finally{root.deleteRecursively()}
    }
}
