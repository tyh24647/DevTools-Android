package com.tyh24647.devtools
import org.junit.Test
import org.junit.Assert.*
import org.json.JSONObject
import org.json.JSONArray
class AppNavigationTest {
    @Test fun hiddenTabsCanBeRestoredAndSettingsRemainsReachable() {
        val data=JSONObject()
        AppNavigation.defaults.forEach {AppNavigation.show(data,it,false)}
        assertEquals(listOf("Settings"),AppNavigation.visible(data))
        AppNavigation.show(data,"Browser",true)
        assertEquals(listOf("Browser","Settings"),AppNavigation.visible(data))
    }
    @Test fun reorderedTabsPersistAndInvalidEntriesAreIgnored() {
        val data=JSONObject().put("navigationOrder",JSONArray(listOf("Resources","Resources","bogus")))
        assertEquals("Resources",AppNavigation.order(data).first())
        AppNavigation.move(data,"Settings",-1)
        val restored=JSONObject(data.toString())
        assertEquals(AppNavigation.order(data),AppNavigation.order(restored))
        assertEquals(AppNavigation.defaults.size,AppNavigation.order(restored).distinct().size)
        assertEquals("Downloads",AppNavigation.label("Resources"))
        assertEquals("Browser settings",AppNavigation.label("Browsers"))
    }
}
