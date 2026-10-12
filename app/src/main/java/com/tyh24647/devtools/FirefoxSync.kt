package com.tyh24647.devtools

import android.app.Activity
import android.util.Base64
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.security.MessageDigest
import java.security.SecureRandom
import kotlinx.coroutines.*
import org.json.JSONObject

/** Authenticated companion API on loopback. Media saves always require native confirmation. */
class FirefoxSync(private val activity: MainActivity, private val config: Configuration) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var server: ServerSocket? = null
    private val token: String = activity.getSharedPreferences("firefox-pairing", 0).let { prefs ->
        prefs.getString("token", null) ?: Base64.encodeToString(
            ByteArray(32).also { SecureRandom().nextBytes(it) },
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING,
        ).also { prefs.edit().putString("token", it).apply() }
    }
    val pairingCode: String get() = "18746.$token"

    fun start() {
        scope.launch {
            try {
                val listener = ServerSocket().apply {
                    reuseAddress = true
                    bind(java.net.InetSocketAddress(InetAddress.getByName("127.0.0.1"), 18746), 4)
                }
                server = listener
                listener.use {
                    while (isActive) {
                        val socket = listener.accept()
                        launch { socket.use { serve(it) } }
                    }
                }
            } catch (_: java.io.IOException) {
                // App can still run if another instance owns the local port.
            }
        }
    }

    private suspend fun serve(socket: Socket) {
        socket.soTimeout = 4000
        try {
            val input = socket.getInputStream().buffered()
            fun line(): String {
                val bytes = java.io.ByteArrayOutputStream()
                while (true) {
                    val next = input.read()
                    if (next == -1 || next == 10) break
                    require(bytes.size() < 4096)
                    if (next != 13) bytes.write(next)
                }
                return bytes.toString("UTF-8")
            }
            val request = line().split(' ')
            val headers = mutableMapOf<String, String>()
            var headerBytes = 0
            while (true) {
                val row = line()
                if (row.isEmpty()) break
                headerBytes += row.length
                require(headerBytes <= 16384)
                require(':' in row)
                headers[row.substringBefore(':').lowercase()] = row.substringAfter(':').trim()
            }
            val authorized = MessageDigest.isEqual(
                headers["authorization"].orEmpty().toByteArray(), "Bearer $token".toByteArray(),
            )
            if (!authorized) { respond(socket, 401, JSONObject().put("error", "Pairing code required")); return }
            if (request.size < 2 || request[1] !in listOf("/firefox", "/firefox/script", "/firefox/media", "/firefox/media-state")) { respond(socket, 404, JSONObject()); return }
            if (request[0] !in listOf("GET", "POST")) { respond(socket, 405, JSONObject()); return }
            val length = headers["content-length"]?.toIntOrNull() ?: 0
            require(length in 0..65536)
            val body = ByteArray(length)
            var offset = 0
            while (offset < length) {
                val count = input.read(body, offset, length - offset)
                require(count > 0)
                offset += count
            }
            val patch = if (request[0] == "POST") JSONObject(body.toString(Charsets.UTF_8)) else null
            val result = CompletableDeferred<Pair<Int, JSONObject>>()
            activity.runOnUiThread {
                try {
                    if (request[1] != "/firefox") {
                        result.complete(200 to companion(request[1], request[0], patch))
                        return@runOnUiThread
                    }
                    val current = config.profile("firefox")
                    if (patch != null && patch.optString("revision") != current.optString("revision")) {
                        result.complete(409 to snapshot())
                    } else {
                        if (patch != null) {
                            require(patch.has("enabled") || patch.has("runEverywhere"))
                            val saved = config.changeProfile("firefox") { profile ->
                                for (key in listOf("enabled", "runEverywhere")) {
                                    if (patch.has(key)) {
                                        require(patch.get(key) is Boolean)
                                        profile.put(key, patch.getBoolean(key))
                                    }
                                }
                            }
                            check(saved)
                        }
                        result.complete(200 to snapshot())
                    }
                } catch (_: Exception) { result.complete(400 to JSONObject().put("error", "Settings could not be saved")) }
            }
            val (status, json) = withTimeout(5000) { result.await() }
            respond(socket, status, json)
        } catch (_: Exception) {
            runCatching { respond(socket, 400, JSONObject().put("error", "Invalid settings request")) }
        }
    }

    private fun snapshot(): JSONObject {
        val font = CodeFonts.options(activity)
        val profile = config.profile("firefox")
        return JSONObject().put("schema", 1).put("revision", profile.optString("revision"))
            .put("enabled", profile.optBoolean("enabled", true))
            .put("runEverywhere", profile.optBoolean("runEverywhere", true))
            .put("lists", profile.getJSONArray("lists"))
            .put("pro", config.pro)
            .put("console", JSONObject(config.data.getJSONObject("console").toString()).apply { font.keys().forEach { key -> put(key,font.get(key)) } }.put("cssEditor", config.plugins.any { it.optString("id") == "devtools-css-editor" && it.optBoolean("enabled") }))
            .put("scripts", org.json.JSONArray(config.plugins.map { script -> JSONObject(script.toString()).apply {remove("source"); put("hash", sourceHash(script.getString("source")))} }))
            .put("scriptFolders", org.json.JSONArray(config.scriptFolders))
            .put("scriptTags", org.json.JSONArray(config.scriptTags))
    }

    private fun sourceHash(source: String) = MessageDigest.getInstance("SHA-256").digest(source.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

    private fun companion(path: String, method: String, body: JSONObject?): JSONObject {
        val library = MediaLibrary.get(activity)
        if (path == "/firefox/script") {
            require(method == "POST" && body != null)
            val row = config.plugins.firstOrNull {it.optString("id") == body.optString("id")} ?: error("Script not found.")
            return JSONObject(row.toString()).put("hash", sourceHash(row.getString("source")))
        }
        if (path == "/firefox/media-state") {
            require(method == "GET")
            return JSONObject().put("tasks", org.json.JSONArray(library.tasks.filter {it.media.tab.startsWith("firefox:")}.takeLast(100).map {task ->
                JSONObject().put("tab", task.media.tab.removePrefix("firefox:")).put("url", task.media.url).put("status", task.status).put("progress", task.progress ?: JSONObject.NULL).put("busy", !task.done).put("live", task.choice.live)
            }))
        }
        require(method == "POST" && body != null)
        val id = body.optInt("tabId", -1); require(id >= 0)
        val tab = "firefox:$id"
        val action = body.optString("type")
        if (action == "leave") {library.leave(tab); return JSONObject().put("status", "Recording finalized on page leave.")}
        val url = WebURLs.normalize(body.optString("url")) ?: error("Invalid resource URL.")
        if (action in listOf("stop", "unload")) {library.unloaded(tab, url); return JSONObject().put("status", "Recording finalizing.")}
        require(action == "save")
        val page = WebURLs.normalize(body.optString("page")) ?: error("Invalid page URL.")
        val cookie = body.optString("cookie"); require(cookie.length <= 16384 && !cookie.contains('\r') && !cookie.contains('\n'))
        val cookies = if(cookie.isBlank())emptyMap() else mapOf(MediaLibrary.origin(url) to cookie)
        check(library.pending == null || library.pending?.url == url) {"Confirm the previous download in DevTools first."}
        library.pending = PageMedia(url, page, tab, body.optString("title").take(160), body.optBoolean("hls"), body.optString("mime").take(80), cookies)
        activity.screen = "Resources"
        return JSONObject().put("status", "Open DevTools to confirm this download.")
    }

    private fun respond(socket: Socket, status: Int, body: JSONObject) {
        val bytes = body.toString().toByteArray(Charsets.UTF_8)
        val header = "HTTP/1.1 $status Result\r\nContent-Type: application/json\r\nContent-Length: ${bytes.size}\r\nCache-Control: no-store\r\nConnection: close\r\n\r\n"
        socket.getOutputStream().apply { write(header.toByteArray()); write(bytes); flush() }
    }

    fun close() { server?.close(); scope.cancel() }
}
