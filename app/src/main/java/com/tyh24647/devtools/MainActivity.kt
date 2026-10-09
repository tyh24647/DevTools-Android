package com.tyh24647.devtools

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch

/** Entry point for launcher, default-browser links and six independent share targets. */
class MainActivity : ComponentActivity() {
    val webMediaPermissions = WebMediaPermissions(this)
    lateinit var configuration: Configuration
    lateinit var packages: ToolPackages
    lateinit var browser: BrowserController
    lateinit var purchases: PlayPurchases
    lateinit var firefoxSync: FirefoxSync
    lateinit var ads: Ads
    var pendingScript by mutableStateOf<ScriptDraft?>(null)
    var screen by mutableStateOf("Browsers")
    var shareRule by mutableStateOf<Pair<String, String>?>(null)
    private var fileCallback: ValueCallback<Array<Uri>>? = null
    private var backgrounded = false
    private var initialized = false
    private val filePicker =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            fileCallback?.onReceiveValue(
                WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data)
            )
            fileCallback = null
        }

    private val pluginPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) lifecycleScope.launch {
            try {
                val source = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    contentResolver.openInputStream(uri)?.use { stream ->
                        val output = java.io.ByteArrayOutputStream()
                        val buffer = ByteArray(8192)
                        while (output.size() <= 262144) {
                            val count = stream.read(buffer)
                            if (count < 0) break
                            output.write(buffer, 0, count)
                        }
                        val bytes = output.toByteArray()
                        require(bytes.size <= 262144) { "Plugin files must be 256 KB or smaller." }
                        ScriptImport.decode(bytes)
                    } ?: error("The plugin file could not be opened.")
                }
                pendingScript = ScriptImport.draft(source)
                screen = "Plugins"
            } catch (error: Exception) { browser.notice = error.message ?: "Plugin import failed." }
        }
    }
    fun importPlugin() { pluginPicker.launch(arrayOf("text/*", "application/javascript", "application/octet-stream")) }



    private val mediaImport = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) lifecycleScope.launch {
            try {
                val library = MediaLibrary.get(this@MainActivity)
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    val name = contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                        if (it.moveToFirst()) it.getString(0) else null
                    }?.replace(Regex("[/\\\\]"), "_")?.take(120) ?: "imported-" + System.currentTimeMillis()
                    val folder = library.path(library.defaultFolder)
                    var target = java.io.File(folder, name)
                    if (target.exists()) target = java.io.File(folder, System.currentTimeMillis().toString() + "-" + name)
                    val part = java.io.File(folder, "." + target.name + ".part")
                    try {
                        contentResolver.openInputStream(uri)?.use { input -> part.outputStream().use { input.copyTo(it) } } ?: error("File could not be read.")
                        check(part.renameTo(target))
                    } finally { part.delete() }
                }
                library.revision++
            } catch (error: Exception) { browser.notice = "Import failed: " + error.message }
        }
    }
    fun importMedia() { mediaImport.launch(arrayOf("*/*")) }

    private var exportingMedia: java.io.File? = null
    private val mediaExport = registerForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { uri ->
        val file = exportingMedia
        if (uri != null && file != null) lifecycleScope.launch {
            try {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    contentResolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } }
                        ?: error("Export destination unavailable.")
                }
                browser.notice = "Resource exported."
            } catch (error: Exception) { browser.notice = "Export failed: " + error.message }
        }
        exportingMedia = null
    }
    private val mediaDestination = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            try {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                MediaLibrary.get(this).apply { exportTree = uri.toString(); settings() }
            } catch (error: Exception) { browser.notice = "Could not save destination permission." }
        }
    }
    fun exportMedia(file: java.io.File) { exportingMedia = file; mediaExport.launch(file.name) }
    fun chooseMediaDestination() { mediaDestination.launch(null) }

    private var exportingScript:String?=null
    private val scriptExport=registerForActivityResult(ActivityResultContracts.CreateDocument("text/javascript")){uri->
        val source=exportingScript
        if(uri!=null&&source!=null)lifecycleScope.launch {
            try{kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO){contentResolver.openOutputStream(uri)?.use{it.write(source.toByteArray(Charsets.UTF_8))}?:error("Could not open destination.")};browser.notice="Script exported."}
            catch(failure:Exception){browser.notice="Export failed: "+failure.message}
        }
        exportingScript=null
    }
    fun exportScript(name:String,source:String,kind:String){exportingScript=source;scriptExport.launch(name.replace(Regex("[^a-zA-Z0-9_-]"),"_")+(if(kind=="userscript")".user.js" else ".js"))}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        configuration = Configuration(this)
        val editorPrefs=getSharedPreferences("bundled-plugins",0)
        if(!editorPrefs.getBoolean("css-installed",false)) {
            runCatching {
                val id="devtools-css-editor"
                if(configuration.plugins.none{it.optString("id")==id}) {
                    check(configuration.savePlugin("CSS editor",assets.open("tools/css-editor-plugin.js").bufferedReader().use{it.readText()},id))
                    check(configuration.change{root->root.getJSONArray("plugins").objects().first{it.optString("id")==id}.put("enabled",true)})
                }
                editorPrefs.edit().putBoolean("css-installed",true).apply()
            }
        }

        if (configuration.data.optJSONObject("browserProfiles")?.optJSONObject("firefox") == null) {
            configuration.changeProfile("firefox") { }
        }
        firefoxSync = FirefoxSync(this, configuration)
        firefoxSync.start()
        packages = ToolPackages(this)
        browser =
            BrowserController(
                this,
                configuration,
                packages,
                lifecycleScope,
                { callback, params ->
                    fileCallback?.onReceiveValue(null)
                    fileCallback = callback
                    try {
                        filePicker.launch(params.createIntent())
                    } catch (_: Exception) {
                        callback.onReceiveValue(null)
                        fileCallback = null
                        browser.notice = "No document picker is available."
                    }
                },
                {
                    MediaLibrary.get(this).pending = PageMedia(it, browser.current?.url.orEmpty(), browser.current?.id.orEmpty(), browser.current?.title.orEmpty())
                },
            )
        purchases =
            PlayPurchases(this, configuration) {
                browser.refresh()
                if (::ads.isInitialized) {
                    ads.start()
                }
            }
        ads = Ads(this, configuration)
        setContent { DevToolsUI(this) }
        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.RESUMED) {
                while (true) { browser.mediaProgress(); kotlinx.coroutines.delay(1000) }
            }
        }
        lifecycleScope.launch {
            browser.initialize()
            initialized = true
            handleIntent(intent)
            purchases.connect()
            ads.start()
            packages.check(configuration.data.optBoolean("automaticallyInstallUpdates", true))
        }
        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.RESUMED) {
                while (true) {
                    kotlinx.coroutines.delay(180000)
                    if (initialized) {
                        purchases.restore()
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (initialized) {
            handleIntent(intent)
        }
    }

    private fun handleIntent(intent: Intent) {
        val raw =
            if (intent.action == Intent.ACTION_SEND) intent.getStringExtra(Intent.EXTRA_TEXT)
            else intent.dataString
        val url = raw?.let(WebURLs::fromShare)
        val action = intent.component?.className?.substringAfterLast('.') ?: ""
        when (action) {
            "AddBlacklist",
            "AddAllowList" -> {
                if (url == null) {
                    browser.notice = "Share a valid HTTP or HTTPS URL to add a rule."
                } else {
                    shareRule = (if (action == "AddBlacklist") "block" else "allow") to url
                    screen = "Lists"
                }
            }
            "Enable",
            "Disable" -> {
                configuration.change { it.put("enabled", action == "Enable") }
                browser.refresh()
                screen = "Browser"
                if (url != null) {
                    browser.openShared(url)
                }
            }
            else -> {
                if (url != null) {
                    browser.openShared(
                        url,
                        when (action) {
                            "Show" -> "show"
                            "Hide" -> "hide"
                            else -> ""
                        },
                    )
                    screen = "Browser"
                } else if (raw != null) {
                    browser.notice = "Only HTTP and HTTPS links can be opened."
                }
            }
        }
    }

    fun changed() {
        browser.refresh()
    }

    fun checkUpdates() {
        lifecycleScope.launch { packages.check(true) }
    }

    override fun onResume() {
        super.onResume()
        if (::browser.isInitialized && initialized) {
            browser.resume()
            purchases.restore()
            if (backgrounded && screen != "Pro" && shareRule == null) {
                ads.foreground()
            }
            backgrounded = false
            lifecycleScope.launch {
                packages.check(configuration.data.optBoolean("automaticallyInstallUpdates", true))
            }
        }
    }

    override fun onPause() {
        if (::browser.isInitialized) {
            browser.pause()
        }
        super.onPause()
    }

    override fun onStop() {
        backgrounded = true
        super.onStop()
    }

    override fun onDestroy() {
        webMediaPermissions.destroy()
        firefoxSync.close()
        fileCallback?.onReceiveValue(null)
        browser.destroy()
        purchases.close()
        super.onDestroy()
    }
}
