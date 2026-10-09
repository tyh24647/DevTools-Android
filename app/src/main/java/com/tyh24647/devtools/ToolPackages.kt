package com.tyh24647.devtools

import android.content.Context
import android.util.Base64
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** Downloads only catalogued, version-pinned JS packages; no native bridge is exposed. */
class ToolPackages(private val context: Context) {
    val catalog =
        JSONObject(
            context.assets.open("tools/tool-catalog.json").bufferedReader().use { it.readText() }
        )
    private val cache = File(context.filesDir, "tool-cache").apply { mkdirs() }
    var message by mutableStateOf("Bundled tools are ready offline.")
        private set

    var checking by mutableStateOf(false)
        private set

    private var inFlight = false

    fun versions(): List<Pair<String, String>> =
        catalog.getJSONArray("assets").objects().map {
            val marker = File(cache, "${it.getString("id")}.json")
            val cached =
                runCatching { JSONObject(marker.readText()).getString("version") }.getOrNull()
            it.getString("package") to (cached ?: it.getString("version"))
        }

    /** Hashes are compared on every read, so incomplete/corrupt caches fall back offline. */
    fun source(id: String, bundledOnly: Boolean = false): String {
        val item = catalog.getJSONArray("assets").objects().first { it.getString("id") == id }
        if (!bundledOnly) {
            val installed =
                runCatching {
                        val meta = JSONObject(File(cache, "$id.json").readText())
                        val bytes = File(cache, "$id.js").readBytes()
                        require(hash(bytes) == meta.getString("sha256"))
                        val version = meta.getString("version")
                        wrap(item, version, bytes.toString(Charsets.UTF_8))
                    }
                    .getOrNull()
            if (installed != null) {
                return installed
            }
        }
        return context.assets.open("tools/${item.getString("file")}").bufferedReader().use {
            it.readText()
        }
    }

    fun clear() {
        cache.listFiles()?.forEach { it.delete() }
        message = "Downloaded packages cleared. New pages use bundled tools."
    }

    suspend fun check(enabled: Boolean) {
        if (!enabled || inFlight) {
            return
        }
        inFlight = true
        checking = true
        message = "Checking npm for package updates…"
        val result =
            withContext(Dispatchers.IO) {
                var updates = 0
                var failures = 0
                for (item in catalog.getJSONArray("assets").objects()) {
                    try {
                        val pkg = item.getString("package")
                        val latest =
                            runCatching {
                                    JSONObject(
                                            fetch("https://registry.npmjs.org/$pkg/latest")
                                                .toString(Charsets.UTF_8)
                                        )
                                        .getString("version")
                                }
                                .getOrElse {
                                    JSONObject(
                                            fetch(
                                                    "https://data.jsdelivr.com/v1/package/resolve/npm/$pkg@latest"
                                                )
                                                .toString(Charsets.UTF_8)
                                        )
                                        .getString("version")
                                }
                        require(
                            Regex("[0-9]+\\.[0-9]+\\.[0-9]+(?:[-+][A-Za-z0-9.-]+)?").matches(latest)
                        )
                        val installed = versions().first { it.first == pkg }.second
                        if (latest == installed) {
                            continue
                        }
                        val path = item.getString("path")
                        val files =
                            JSONObject(
                                fetch("https://data.jsdelivr.com/v1/package/npm/$pkg@$latest/flat")
                                    .toString(Charsets.UTF_8)
                            )
                        val metadata =
                            files.getJSONArray("files").objects().first {
                                it.getString("name") == "/$path"
                            }
                        val bytes =
                            fetch(
                                "https://cdn.jsdelivr.net/npm/$pkg@$latest/$path",
                                16 * 1024 * 1024,
                            )
                        require(hash(bytes) == metadata.getString("hash")) {
                            "Package integrity mismatch"
                        }
                        val id = item.getString("id")
                        val pending = File(cache, "$id.pending")
                        pending.writeBytes(bytes)
                        require(pending.renameTo(File(cache, "$id.js")))
                        val marker = File(cache, "$id.meta.pending")
                        marker.writeText(
                            JSONObject()
                                .put("version", latest)
                                .put("sha256", hash(bytes))
                                .toString()
                        )
                        require(marker.renameTo(File(cache, "$id.json")))
                        updates++
                    } catch (_: Exception) {
                        failures++
                    }
                }
                "Installed $updates update(s). " +
                    if (failures > 0)
                        "$failures package check(s) unavailable; bundled tools remain ready."
                    else "All package checks completed."
            }
        message = "$result Reload pages to apply."
        checking = false
        inFlight = false
    }

    private fun wrap(item: JSONObject, version: String, code: String): String {
        val id = JSONObject.quote(item.getString("id"))
        return "(function(){if(globalThis.__DTExpectedURL!==location.href||globalThis.__DTAssets?.[$id]){return;}var module={exports:{}};var exports=module.exports;var define;var process={env:{NODE_ENV:\"production\"}};\n$code\n" +
            "globalThis.__DTAssets=globalThis.__DTAssets||{};globalThis.__DTAssets[$id]=module.exports.default||module.exports;" +
            "globalThis.__DTVersions=globalThis.__DTVersions||{};globalThis.__DTVersions[$id]=${JSONObject.quote(version)};})();"
    }

    private fun hash(bytes: ByteArray): String =
        Base64.encodeToString(MessageDigest.getInstance("SHA-256").digest(bytes), Base64.NO_WRAP)

    private fun fetch(address: String, limit: Int = 4 * 1024 * 1024): ByteArray {
        val connection = URL(address).openConnection() as HttpURLConnection
        connection.connectTimeout = 10000
        connection.readTimeout = 20000
        connection.instanceFollowRedirects = false
        return try {
            require(connection.responseCode == 200)
            connection.inputStream.use { stream ->
                val buffer = java.io.ByteArrayOutputStream()
                val chunk = ByteArray(8192)
                while (true) {
                    val count = stream.read(chunk)
                    if (count < 0) {
                        break
                    }
                    require(buffer.size() + count <= limit) { "Package exceeds size limit" }
                    buffer.write(chunk, 0, count)
                }
                buffer.toByteArray()
            }
        } finally {
            connection.disconnect()
        }
    }
}
