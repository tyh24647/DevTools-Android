package com.tyh24647.devtools
object UserscriptMetadata {
    fun validate(source:String):Map<String,List<String>> {
        val block=Regex("//\\s*==UserScript==([\\s\\S]*?)//\\s*==/UserScript==").find(source)?.groupValues?.get(1)
            ?: error("Include a UserScript header with @match or @include.")
        val data=Regex("(?m)^\\s*//\\s*@([\\w-]+)\\s*(.*)$").findAll(block).groupBy({it.groupValues[1]},{it.groupValues[2].trim()})
        require(!data["match"].isNullOrEmpty() || !data["include"].isNullOrEmpty()) {"Add @match or @include."}
        val unsupported=listOf("require","resource","connect","webRequest","run-in","sandbox")
        require(unsupported.none(data::containsKey)) {"External dependencies and privileged network APIs are not supported."}
        val grants=setOf("none","unsafeWindow","GM_info","GM.info","GM_addStyle","GM.addStyle","GM_log","GM.log","GM_getValue","GM.getValue","GM_setValue","GM.setValue","GM_deleteValue","GM.deleteValue","GM_listValues","GM.listValues")
        require(data["grant"].orEmpty().all {it in grants}) {"This script requests an unsupported GM API."}
        require(data["run-at"].orEmpty().all {it in listOf("document-start","document-end","document-idle")}) {"Unsupported @run-at."}
        for(tag in listOf("include","exclude")) require(data[tag].orEmpty().none {it.startsWith("/") || it.length>2048}) {"Use URL wildcards instead of regex includes."}
        return data
    }
}
