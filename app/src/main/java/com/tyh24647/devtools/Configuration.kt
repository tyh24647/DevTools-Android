package com.tyh24647.devtools

import android.content.Context
import android.util.AtomicFile
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.File
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

/** Persisted rule configuration shares the iOS bridge schema; purchases stay native. */
class Configuration(context: Context) {
    private val file = AtomicFile(File(context.filesDir, "configuration.json"))
    private var corrupt = false
    var error by mutableStateOf<String?>(null)
        private set

    var data by mutableStateOf(load())
        private set

    var billingChecked by mutableStateOf(false)
    var pro by mutableStateOf(BuildConfig.PREVIEW_PRO)
    var previewPro by mutableStateOf(BuildConfig.PREVIEW_PRO)
    val enabled: Boolean
        get() = data.optBoolean("enabled", true)

    val lists: List<JSONObject>
        get() = data.getJSONArray("lists").objects()

    private fun load(): JSONObject {
        if (!file.baseFile.exists()) {
            return defaults()
        }
        return try {
            val parsed = JSONObject(file.openRead().bufferedReader().use { it.readText() })
            require(
                parsed.optInt("schema") == 1 &&
                    parsed.optJSONArray("lists") != null &&
                    parsed.optJSONObject("console") != null
            )
            parsed.getJSONArray("lists").objects().forEach { list ->
                require(
                    list.has("id") &&
                        list.has("name") &&
                        list.getString("kind") in listOf("allow", "block")
                )
                list.getJSONArray("rules").objects().forEach { rule ->
                    require(
                        rule.has("id") &&
                            rule.has("pattern") &&
                            rule.getString("kind") in listOf("domain", "url", "wildcard", "regex")
                    )
                }
            }
            parsed
        } catch (failure: Exception) {
            corrupt = true
            error =
                "Settings could not be read. DevTools is disabled until you reset or repair them."
            defaults().put("enabled", false)
        }
    }

    /** AtomicFile preserves the previous version if saving is interrupted. */
    fun change(edit: (JSONObject) -> Unit): Boolean {
        if (corrupt) {
            return false
        }
        val next = JSONObject(data.toString())
        edit(next)
        next.put("revision", UUID.randomUUID().toString())
        var stream: java.io.FileOutputStream? = null
        return try {
            stream = file.startWrite()
            stream.write(next.toString(2).toByteArray())
            file.finishWrite(stream)
            data = next
            error = null
            true
        } catch (failure: Exception) {
            stream?.let { file.failWrite(it) }
            error = "Settings could not be saved: ${failure.message}"
            false
        }
    }

    fun reset() {
        corrupt = false
        change { target ->
            target.keys().asSequence().toList().forEach { target.remove(it) }
            val fresh = defaults()
            fresh.keys().forEach { target.put(it, fresh.get(it)) }
        }
    }

    fun bridge(): JSONObject = JSONObject(data.toString()).apply { remove("browserProfiles") }.put("pro", pro)

    fun profile(browserID: String): JSONObject =
        if (browserID == "devtools") data
        else data.optJSONObject("browserProfiles")?.optJSONObject(browserID)
            ?: defaults().put("enabled", true).put("revision", "initial")

    fun listsFor(browserID: String): List<JSONObject> = profile(browserID).getJSONArray("lists").objects()

    fun changeProfile(browserID: String, edit: (JSONObject) -> Unit): Boolean = change { root ->
        if (browserID == "devtools") {
            edit(root)
        } else {
            val profiles = root.optJSONObject("browserProfiles") ?: JSONObject().also { root.put("browserProfiles", it) }
            val profile = profiles.optJSONObject(browserID) ?: defaults().also { profiles.put(browserID, it) }
            edit(profile)
            profile.put("revision", UUID.randomUUID().toString())
        }
    }

    fun newList(name: String, kind: String, browserID: String = "devtools"): String {
        val id = UUID.randomUUID().toString()
        val saved = changeProfile(browserID) { it.getJSONArray("lists").put(list(id, name.trim().take(80), kind)) }
        return if (saved) id else ""
    }

    fun editList(id: String, browserID: String = "devtools", edit: (JSONObject) -> Unit) {
        changeProfile(browserID) { config ->
            config
                .getJSONArray("lists")
                .objects()
                .firstOrNull { it.getString("id") == id }
                ?.let(edit)
        }
    }

    fun deleteList(id: String, browserID: String = "devtools") {
        changeProfile(browserID) {
            it.put(
                "lists",
                JSONArray(
                    it.getJSONArray("lists").objects().filter { row -> row.getString("id") != id }
                ),
            )
        }
    }

    fun addRule(id: String, kind: String, pattern: String, browserID: String = "devtools"): Boolean {
        if (listsFor(browserID).none { it.getString("id") == id }) {
            if (error == null) {
                error = "Choose a saved list before adding this rule."
            }
            return false
        }
        return changeProfile(browserID) { config ->
            val target = config.getJSONArray("lists").objects().first { it.getString("id") == id }
            target
                .getJSONArray("rules")
                .put(
                    JSONObject()
                        .put("id", UUID.randomUUID().toString())
                        .put("kind", kind)
                        .put("pattern", pattern.trim())
                        .put("enabled", true)
                )
        }
    }

    val plugins: List<JSONObject> get() = data.optJSONArray("plugins")?.objects() ?: emptyList()

    fun savePlugin(name: String, source: String, id: String = UUID.randomUUID().toString(), kind: String = "eruda"): Boolean {
        require(name.isNotBlank() && source.isNotBlank()) { "Enter a name and JavaScript source." }
        require(source.toByteArray(Charsets.UTF_8).size <= 262144) { "Plugin source must be 256 KB or smaller." }
        require(plugins.any { it.optString("id") == id } || plugins.size < 20) { "You can save up to 20 plugins." }
        require(kind in listOf("eruda", "userscript"))
        if (kind == "userscript") UserscriptMetadata.validate(source)
        return change { root ->
            val rows = root.optJSONArray("plugins")?.objects() ?: emptyList()
            val plugin = JSONObject(rows.firstOrNull { it.optString("id") == id }?.toString() ?: "{}").put("id", id).put("name", name.trim().take(80))
                .put("source", source).put("enabled", false).put("kind", kind)
            root.put("plugins", JSONArray(rows.filter { it.optString("id") != id } + plugin))
        }
    }

    fun removePlugin(id: String) = change { root ->
        root.put("plugins", JSONArray(plugins.filter { it.optString("id") != id }))
    }


    val scriptFolders:List<JSONObject> get()=data.optJSONArray("scriptFolders")?.objects()?:emptyList()
    val scriptTags:List<JSONObject> get()=data.optJSONArray("scriptTags")?.objects()?:emptyList()
    fun addScriptFolder(name:String,parent:String,kind:String) = change {root->
        require(name.isNotBlank() && name.length<=80){"Enter a folder name (up to 80 characters)."}
        require(parent.isEmpty() || scriptFolders.any{it.optString("id")==parent && it.optString("kind")==kind})
        require(scriptFolders.size<100){"Folder limit reached."}
        require(scriptFolders.none{it.optString("parent")==parent && it.optString("kind")==kind && it.optString("name")==name.trim()}){"Folder already exists."}
        root.put("scriptFolders",org.json.JSONArray(scriptFolders+JSONObject().put("id",UUID.randomUUID().toString()).put("name",name.trim()).put("parent",parent).put("kind",kind)))
    }
    fun removeScriptFolder(id:String) = change {root->
        val removed=mutableSetOf(id)
        var added=true
        while(added){added=false;scriptFolders.forEach{if(it.optString("parent") in removed && removed.add(it.optString("id")))added=true}}
        root.put("scriptFolders",org.json.JSONArray(scriptFolders.filter{it.optString("id") !in removed}))
        root.optJSONArray("plugins")?.objects()?.forEach {if(it.optString("folder") in removed)it.put("folder","")}
    }
    fun scriptOrganization(id:String,folder:String,tags:List<String>) = change {root->
        val plugin=root.getJSONArray("plugins").objects().first{it.optString("id")==id}
        require(folder.isEmpty() || scriptFolders.any{it.optString("id")==folder && it.optString("kind")==plugin.optString("kind","eruda")})
        require(tags.all{tag->scriptTags.any{it.optString("id")==tag}})
        plugin.put("folder",folder).put("tags",org.json.JSONArray(tags.distinct()))
    }
    fun saveScriptTag(name:String,color:String,id:String=UUID.randomUUID().toString()) = change {root->
        require(name.isNotBlank() && name.length<=40){"Enter a tag name (up to 40 characters)."}
        require(Regex("#[0-9a-fA-F]{6}").matches(color)){"Choose a valid color."}
        require(scriptTags.any{it.optString("id")==id} || scriptTags.size<100)
        root.put("scriptTags",org.json.JSONArray(scriptTags.filter{it.optString("id")!=id}+JSONObject().put("id",id).put("name",name.trim()).put("color",color)))
    }
    fun removeScriptTag(id:String)=change {root->
        root.put("scriptTags",org.json.JSONArray(scriptTags.filter{it.optString("id")!=id}))
        root.optJSONArray("plugins")?.objects()?.forEach{plugin->plugin.put("tags",org.json.JSONArray((plugin.optJSONArray("tags")?:org.json.JSONArray()).let{array->(0 until array.length()).map{array.getString(it)}.filter{it!=id}}))}
    }
    companion object {
        fun defaults(): JSONObject =
            JSONObject()
                .put("schema", 1)
                .put("revision", UUID.randomUUID().toString())
                .put("enabled", true)
                .put("runEverywhere", true)
                .put("automaticallyInstallUpdates", true)
                .put("appearance", "system")
                .put("accent", "violet")
                .put(
                    "console",
                    JSONObject()
                        .put("displaySize", 55.0)
                        .put("transparency", 0.98)
                        .put("theme", "Material Palenight")
                        .put("rememberPosition", true)
                        .put("positionX", 263.807642)
                        .put("positionY", 0)
                        .put("backend", "eruda")
                        .put("vueAdapter", "modern")
                        .put("vue", true)
                        .put("resourceTiming", true)
                        .put("code", true)
                        .put("dom", true)
                        .put("fps", false)
                        .put("timing", true)
                        .put("features", false),
                )
                .put(
                    "lists",
                    JSONArray()
                        .put(list("default-allow", "My development sites", "allow"))
                        .put(list("default-block", "Excluded sites", "block")),
                )

        private fun list(id: String, name: String, kind: String): JSONObject =
            JSONObject()
                .put("id", id)
                .put("name", name)
                .put("kind", kind)
                .put("selected", true)
                .put("rules", JSONArray())
    }
}

fun JSONArray.objects(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }
