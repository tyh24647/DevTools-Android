package com.tyh24647.devtools

import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.os.Message
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.PermissionRequest
import android.webkit.SslErrorHandler
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.webkit.ScriptHandler
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject

class BrowserTab(val webView: WebView) {
    val id = UUID.randomUUID().toString()
    var title by mutableStateOf("New tab")
    var url by mutableStateOf("")
    var progress by mutableStateOf(0)
    var status by mutableStateOf("Ready")
    var script: ScriptHandler? = null
    var pendingCommand = ""
    var restoreURL = ""
}

/** Owns WebViews on the UI thread. Media requests use a bounded message listener; saving requires native confirmation. */
class BrowserController(
    private val activity: MainActivity,
    private val configuration: Configuration,
    private val packages: ToolPackages,
    private val scope: CoroutineScope,
    private val chooseFile: (ValueCallback<Array<Uri>>, WebChromeClient.FileChooserParams) -> Unit,
    private val download: (String) -> Unit,
) {
    val tabs = mutableStateListOf<BrowserTab>()
    var selected by mutableStateOf("")
    val current: BrowserTab?
        get() = tabs.firstOrNull { it.id == selected }

    var ready by mutableStateOf(false)
        private set

    var notice by mutableStateOf<String?>(null)
    private var script = ""
    private var refreshJob: Job? = null
    private val scriptBuildMutex = Mutex()
    private val early = WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)
    private val ruleSource =
        activity.assets.open("tools/rule-engine.js").bufferedReader().use { it.readText() }
    private var disposed=false
    private var validator = createValidator()
    private fun createValidator():WebView=WebView(activity).apply {
        settings.javaScriptEnabled=true
        webViewClient=object:WebViewClient(){
            override fun onRenderProcessGone(view:WebView,detail:android.webkit.RenderProcessGoneDetail):Boolean {
                view.destroy()
                android.os.Handler(android.os.Looper.getMainLooper()).post{if(!disposed)validator=createValidator()}
                return true
            }
        }
    }

    suspend fun initialize() {
        script = withContext(Dispatchers.IO) { scriptBuildMutex.withLock { buildScript() } }
        ready = true
        if (!early) {
            notice =
                "Update Android System WebView for document-start injection. This device currently injects after page load."
        }
        val saved = activity.getPreferences(0).getString("tabs", null)
        val urls =
            runCatching {
                    org.json.JSONArray(saved).let { array ->
                        (0 until array.length()).map { array.getString(it) }
                    }
                }
                .getOrDefault(emptyList())
        urls.take(12).filter { WebURLs.normalize(it) != null }.forEach { newTab(it, loadImmediately = false) }
        if (tabs.isEmpty()) {
            newTab()
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    fun newTab(address: String = "", provided: WebView? = null, loadImmediately: Boolean = true): BrowserTab {
        if (tabs.size >= 12) {
            notice = "Close a tab before opening another (12-tab limit)."
            return current ?: tabs.first()
        }
        val webView = provided ?: WebView(activity)
        val tab = BrowserTab(webView)
        with(webView.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
            javaScriptCanOpenWindowsAutomatically = false
            setSupportMultipleWindows(true)
            builtInZoomControls = true
            displayZoomControls = false
        }
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, false)
        val library = MediaLibrary.get(activity)
        if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
            WebViewCompat.addWebMessageListener(webView, "devtoolsMedia", setOf("*")) { _, message, sourceOrigin, mainFrame, _ ->
                if (mainFrame && sourceOrigin.scheme in listOf("http", "https")) {
                    runCatching {
                        val body = message.data ?: ""
                        require(body.length <= 8192)
                        val data = JSONObject(body)
                        val url = WebURLs.normalize(data.optString("url")) ?: return@runCatching
                        when (data.optString("type")) {
                            "save" -> library.pending = PageMedia(url, tab.url, tab.id, data.optString("title").take(160), data.optBoolean("hls"), data.optString("mime").take(80))
                            "stop", "unload" -> library.unloaded(tab.id, url)
                            "share" -> activity.startActivity(android.content.Intent.createChooser(
                                android.content.Intent(android.content.Intent.ACTION_SEND).setType("text/plain").putExtra(android.content.Intent.EXTRA_TEXT, url), "Share resource"))
                        }
                    }
                }
            }
        }
        webView.webViewClient =
            object : WebViewClient() {
                override fun onRenderProcessGone(view:WebView,detail:android.webkit.RenderProcessGoneDetail):Boolean {
                    activity.webMediaPermissions.leave(view)
                    val address=tab.url
                    val wasSelected=selected==tab.id
                    library.leave(tab.id)
                    tab.script=null
                    (view.parent as? ViewGroup)?.removeView(view)
                    tabs.remove(tab)
                    view.destroy()
                    if(wasSelected)selected=tabs.lastOrNull()?.id.orEmpty()
                    notice="The browser renderer stopped. Saved tabs can be reopened."
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        if(!disposed) {
                            val currentID=selected
                            newTab(WebURLs.normalize(address).orEmpty(),loadImmediately=false)
                            if(!wasSelected && tabs.any{it.id==currentID})selected=currentID
                            persistTabs()
                        }
                    }
                    return true
                }

                override fun shouldOverrideUrlLoading(
                    view: WebView,
                    request: WebResourceRequest,
                ): Boolean {
                    if (request.url.scheme !in listOf("http", "https", "about")) {
                        notice =
                            "External app link blocked. Use the browser menu to open links externally."
                        return true
                    }
                    return false
                }

                override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                    library.leave(tab.id)
                    tab.url = url ?: ""
                    activity.webMediaPermissions.leave(view)
                    tab.status = "Loading…"
                }

                override fun onPageFinished(view: WebView, url: String?) {
                    tab.url = url ?: ""
                    if (!early && WebURLs.normalize(tab.url) != null) {
                        view.evaluateJavascript(script, null)
                    }
                    if (tab.pendingCommand.isNotEmpty()) {
                        command(tab.pendingCommand, tab)
                        tab.pendingCommand = ""
                    }
                    scope.launch {
                        kotlinx.coroutines.delay(1000)
                        refreshStatus(tab)
                    }
                    persistTabs()
                }

                override fun doUpdateVisitedHistory(
                    view: WebView,
                    url: String?,
                    isReload: Boolean,
                ) {
                    tab.url = url ?: ""
                    refreshStatus(tab)
                }

                override fun onReceivedSslError(
                    view: WebView,
                    handler: SslErrorHandler,
                    error: SslError,
                ) {
                    handler.cancel()
                    tab.status = "TLS certificate error. Navigation cancelled."
                }

                override fun onReceivedError(
                    view: WebView,
                    request: WebResourceRequest,
                    error: android.webkit.WebResourceError,
                ) {
                    if (request.isForMainFrame) {
                        tab.status = error.description.toString()
                    }
                }
            }
        webView.webChromeClient =
            object : WebChromeClient() {
                override fun onProgressChanged(view: WebView, progress: Int) {
                    tab.progress = progress
                }

                override fun onReceivedTitle(view: WebView, title: String?) {
                    tab.title = title?.take(120) ?: "Untitled"
                }

                override fun onPermissionRequest(request: PermissionRequest) {
                    activity.webMediaPermissions.request(webView, request)
                }

                override fun onPermissionRequestCanceled(request: PermissionRequest) { activity.webMediaPermissions.canceled(request) }

                override fun onShowFileChooser(
                    view: WebView,
                    callback: ValueCallback<Array<Uri>>,
                    params: FileChooserParams,
                ): Boolean {
                    chooseFile(callback, params)
                    return true
                }

                override fun onCreateWindow(
                    view: WebView,
                    isDialog: Boolean,
                    isUserGesture: Boolean,
                    resultMsg: Message,
                ): Boolean {
                    if (!isUserGesture || tabs.size >= 12) {
                        return false
                    }
                    val popup = newTab()
                    (resultMsg.obj as WebView.WebViewTransport).webView = popup.webView
                    resultMsg.sendToTarget()
                    return true
                }

                override fun onCloseWindow(window: WebView) {
                    tabs.firstOrNull { it.webView == window }?.let { close(it) }
                }
            }
        webView.setDownloadListener { url, _, _, _, _ -> download(url) }
        webView.setOnLongClickListener {
            val hit=webView.hitTestResult
            if(hit.type in listOf(WebView.HitTestResult.IMAGE_TYPE,WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE)) {
                val url=WebURLs.normalize(hit.extra.orEmpty())
                if(url!=null){library.pending=PageMedia(url,tab.url,tab.id,"image-"+System.currentTimeMillis(),mime="image/unknown");true}else false
            }else false
        }
        if(loadImmediately)install(tab)
        tabs += tab
        selected = tab.id
        if (address.isNotEmpty()) {
            if(loadImmediately)navigate(address,tab)
            else {tab.restoreURL=address;tab.url=address;tab.title=Uri.parse(address).host.orEmpty();tab.status="Saved tab"}
        }
        return tab
    }

    fun navigate(input: String, tab: BrowserTab? = current) {
        val url = WebURLs.normalize(input)
        if (url == null) {
            notice = "Enter a valid HTTP or HTTPS URL without credentials."
            return
        }
        tab?.let {
            if(it.restoreURL.isNotEmpty() || (early && it.script==null)){it.restoreURL="";install(it)}
            it.webView.loadUrl(url)
        }
    }

    fun activate(tab:BrowserTab) {
        if(tab.restoreURL.isNotEmpty())navigate(tab.restoreURL,tab)
    }

    fun openShared(url: String, action: String = "") {
        val existing = tabs.firstOrNull { it.url == url }
        val tab = existing ?: newTab()
        selected = tab.id
        if (existing == null || tab.restoreURL.isNotEmpty()) {
            tab.pendingCommand = action
            navigate(url, tab)
        } else if (action.isNotEmpty()) {
            command(action, tab)
        }
    }

    fun command(action: String, tab: BrowserTab? = current) {
        tab?.webView?.evaluateJavascript(
            "globalThis.__DTAndroidCommand?.(${JSONObject.quote(action)});",
            null,
        )
        tab?.let { refreshStatus(it) }
    }

    fun refresh() {
        refreshJob?.cancel()
        // Disable immediately before asynchronous script generation.
        if (!configuration.enabled) {
            tabs.forEach {
                it.webView.evaluateJavascript("globalThis.__DevToolsRuntime?.stop();", null)
            }
        }
        refreshJob =
            scope.launch {
                script = withContext(Dispatchers.IO) { scriptBuildMutex.withLock { buildScript() } }
                tabs.filter{it.restoreURL.isEmpty()}.forEach {
                    install(it)
                    if (WebURLs.normalize(it.url) != null) {
                        it.webView.evaluateJavascript(script, null)
                    }
                }
            }
    }

    fun validate(kind: String, pattern: String, completion: (String?) -> Unit) {
        val rule = JSONObject().put("kind", kind).put("pattern", pattern)
        val code =
            "$ruleSource\n(function(){try{return {pattern:DevToolsRules.validate($rule)}}catch(e){return {error:e.message}}})();"
        validator.evaluateJavascript(code) { raw ->
            val error =
                runCatching { JSONObject(raw).optString("error").ifEmpty { null } }
                    .getOrElse { "Validation failed." }
            completion(error)
        }
    }

    fun close(tab: BrowserTab) {
        activity.webMediaPermissions.leave(tab.webView)
        MediaLibrary.get(activity).leave(tab.id)
        tab.script?.remove()
        tabs.remove(tab)
        (tab.webView.parent as? ViewGroup)?.removeView(tab.webView)
        tab.webView.stopLoading()
        tab.webView.destroy()
        if (selected == tab.id) {
            selected = tabs.lastOrNull()?.id ?: ""
        }
        if (tabs.isEmpty()) {
            newTab()
        }
        persistTabs()
    }

    fun pause() {
        tabs.forEach { it.webView.onPause() }
        CookieManager.getInstance().flush()
        persistTabs()
    }

    fun resume() {
        current?.webView?.onResume()
        current?.takeIf{it.restoreURL.isEmpty()}?.let { refreshStatus(it) }
    }

    fun destroy() {
        disposed=true
        refreshJob?.cancel()
        tabs.forEach {
            MediaLibrary.get(activity).leave(it.id)
            it.script?.remove()
            (it.webView.parent as? ViewGroup)?.removeView(it.webView)
            it.webView.destroy()
        }
        tabs.clear()
        validator.destroy()
    }

    private fun install(tab: BrowserTab) {
        if (early) {
            tab.script?.remove()
            tab.script =
                WebViewCompat.addDocumentStartJavaScript(
                    tab.webView,
                    script,
                    // WebView accepts "*" for all origins, not scheme-only wildcards.
                    // android-bootstrap.js restricts execution to top-level HTTP(S) pages.
                    setOf("*"),
                )
        }
    }

    private fun refreshStatus(tab: BrowserTab) {
        if(tab !in tabs || tab.restoreURL.isNotEmpty() || disposed)return
        tab.webView.evaluateJavascript("JSON.stringify(globalThis.__DTAndroidStatus || {})") { raw
            ->
            runCatching {
                val string = org.json.JSONTokener(raw).nextValue() as? String ?: return@runCatching
                val status = JSONObject(string)
                tab.status = status.optString("reason", "DevTools ready")
                if (status.optBoolean("active")) {
                    tab.status =
                        if (status.optBoolean("hidden")) "Console hidden" else "Console active"
                }
                if (status.optBoolean("error")) {
                    tab.status =
                        "Tool error: ${status.optString("reason")} — clear package cache to restore bundled tools."
                }
            }
        }
    }

    private fun persistTabs() {
        activity
            .getPreferences(0)
            .edit()
            .putString(
                "tabs",
                org.json
                    .JSONArray(tabs.map { it.url }.filter { WebURLs.normalize(it) != null })
                    .toString(),
            )
            .apply()
    }

    fun mediaProgress() {
        val library = MediaLibrary.get(activity)
        for (task in library.tasks) {
            tabs.firstOrNull { it.id == task.media.tab }?.webView?.evaluateJavascript(
                "globalThis.__DTMedia?.status(" + JSONObject.quote(task.media.url) + "," +
                    JSONObject.quote(task.status) + "," + (task.progress?.toString() ?: "null") + "," +
                    (!task.done) + "," + task.choice.live + ")", null)
        }
    }

    private fun buildScript(): String {
        val config = configuration.bridge()
        val options = config.getJSONObject("console")
        val ids = mutableListOf("eruda")
        if (config.optBoolean("pro")) {
            if (options.optString("backend") == "vconsole") {
                ids += "vconsole"
                if (options.optBoolean("vue")) {
                    ids += "vueVConsole"
                }
            } else {
                if (options.optBoolean("vue")) {
                    ids += if (options.optString("vueAdapter") == "legacy") "vueLegacy" else "vue"
                }
                listOf("code", "dom", "timing", "fps", "features")
                    .filter { options.optBoolean(it) }
                    .forEach { ids += it }
            }
        }
        val mediaSource = activity.assets.open("tools/media-resources.js").bufferedReader().use { it.readText() }
        val userscriptSource = activity.assets.open("tools/userscripts.js").bufferedReader().use { it.readText() }
        val userscripts = configuration.data.optJSONArray("plugins")?.objects()?.filter { it.optString("kind") == "userscript" } ?: emptyList()
        val userPrefix = "if(window.top===window && /^https?:$/.test(location.protocol)) {\n" + userscriptSource +
            "\n__DTUserscripts.start(" + org.json.JSONArray(userscripts).toString() + ");\n}\n"
        val assets =
            activity.assets.open("tools/source-formatting.js").bufferedReader().use { it.readText() } + "\n" +
            mediaSource + "\n" + ids.joinToString("\n") { packages.source(it) } +
                "\n" +
                activity.assets.open("tools/resource-timing.js").bufferedReader().use {
                    it.readText()
                }
        val runtime =
            activity.assets.open("tools/page-runtime.js").bufferedReader().use { it.readText() }
        return userPrefix + activity.assets
            .open("tools/android-bootstrap.js")
            .bufferedReader()
            .use { it.readText() }
            .replace("__CONFIG__", config.toString())
            .replace("__RULE_SOURCE__", JSONObject.quote(ruleSource))
            .replace("__RULE_CODE__", ruleSource)
            .replace("__RUNTIME_CODE__", runtime)
            .replace("__COMMAND__", "\"\"")
            // Insert the large bundles last so later substitutions cannot copy them again.
            .replace("__ASSET_CODE__", assets)
    }
}
