package com.tyh24647.devtools
import org.json.JSONArray
import org.json.JSONObject

/** Stable screen IDs preserve existing navigation while labels describe their roles. */
object AppNavigation {
    val defaults = listOf("Browser", "Browsers", "Plugins", "Resources", "Tools", "Pro", "Settings")
    fun label(id: String) = when(id) {"Browsers" -> "Browser settings";"Resources" -> "Downloads";else -> id}
    fun order(data: JSONObject): List<String> {
        val saved=data.optJSONArray("navigationOrder")
        val values=if(saved==null)emptyList() else (0 until saved.length()).map {saved.optString(it)}.filter {it in defaults}.distinct()
        return values + defaults.filter {it !in values}
    }
    fun visible(data: JSONObject): List<String> {
        val hidden=data.optJSONArray("hiddenTabs")
        val disabled=if(hidden==null)emptyList() else (0 until hidden.length()).map {hidden.optString(it)}
        return order(data).filter {it=="Settings" || it !in disabled}
    }
    fun show(data:JSONObject,id:String,visible:Boolean) {
        val hidden=data.optJSONArray("hiddenTabs")?:JSONArray()
        val values=(0 until hidden.length()).map {hidden.optString(it)}.filter {it in defaults && it!="Settings" && it!=id}.toMutableList()
        if(!visible && id!="Settings" && id in defaults)values.add(id)
        data.put("hiddenTabs",JSONArray(values))
    }
    fun move(data:JSONObject,id:String,delta:Int) {
        val items=order(data).toMutableList();val index=items.indexOf(id)
        if(index<0 || index+delta !in items.indices)return
        items.removeAt(index);items.add(index+delta,id);data.put("navigationOrder",JSONArray(items))
    }
}
