package com.tyh24647.devtools

import android.content.Intent
import android.net.Uri
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.Image
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import java.net.URI
import org.json.JSONArray
import org.json.JSONObject

/** Shared shell keeps ads in native views, outside the inspected document. */
@Composable
fun DevToolsUI(activity: MainActivity) {
    val config = activity.configuration
    val appearance = config.data.optString("appearance")
    val dark = appearance == "dark" || (appearance == "system" && isSystemInDarkTheme())
    val accent =
        when (config.data.optString("accent")) {
            "pink" -> Color(0xFFF472B6)
            "mint" -> Color(0xFF34D399)
            "blue" -> Color(0xFF60A5FA)
            else -> Color(0xFFA78BFA)
        }
    val scheme =
        if (dark)
            darkColorScheme(
                primary = accent,
                background = Color(0xFF101427),
                surface = Color(0xFF171C32),
            )
        else lightColorScheme(primary = accent, background = Color(0xFFF4F2FC))
    MaterialTheme(colorScheme = scheme) {
        Scaffold(
            bottomBar = {
                Column {
                    Banner(activity)
                    NavigationBar {
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                        listOf("Browser", "Browsers", "Plugins", "Resources", "Tools", "Pro", "Settings").forEach { title ->
                            NavigationBarItem(
                                modifier = Modifier.widthIn(min = 88.dp),
                                selected = activity.screen == title,
                                onClick = {
                                    activity.screen = title
                                    if (title != "Pro") {
                                        activity.ads.opportunity()
                                    }
                                },
                                icon = {
                                    Icon(
                                        when (title) {
                                            "Browser" -> Icons.Default.Language
                                            "Browsers" -> Icons.Default.Public
                                            "Plugins" -> Icons.Default.Extension
                                            "Resources" -> Icons.Default.Folder
                                            "Tools" -> Icons.Default.Build
                                            "Pro" -> Icons.Default.Star
                                            else -> Icons.Default.Settings
                                        },
                                        contentDescription = title,
                                    )
                                },
                                label = { Text(title) },
                            )
                        }
                        }
                    }
                }
            }
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                if (!activity.browser.ready) {
                    CircularProgressIndicator(Modifier.align(Alignment.Center))
                } else {
                    when (activity.screen) {
                        "Browser" -> BrowserScreen(activity)
                        "Browsers" -> BrowserProfilesScreen(activity)
                        "Plugins" -> PluginsScreen(activity)
                        "Resources" -> ResourcesScreen(activity)
                        "Lists" -> ListsScreen(activity)
                        "Tools" -> ToolsScreen(activity)
                        "Pro" -> ProScreen(activity)
                        else -> SettingsScreen(activity)
                    }
                }
            }
        }
        MediaSaveDialog(activity)
        activity.browser.notice?.let { message ->
            AlertDialog(
                onDismissRequest = { activity.browser.notice = null },
                title = { Text("DevTools") },
                text = { Text(message) },
                confirmButton = {
                    TextButton(onClick = { activity.browser.notice = null }) { Text("OK") }
                },
            )
        }
        activity.shareRule?.let { (kind, url) ->
            RuleEditor(
                activity,
                listKind = kind,
                initialURL = url,
                dismiss = { activity.shareRule = null },
            )
        }
    }
}

@Composable
private fun Banner(activity: MainActivity) {
    if (!activity.ads.eligible) {
        return
    }
    val ad = remember {
        AdView(activity).apply {
            setAdSize(AdSize.BANNER)
            adUnitId = BuildConfig.AD_BANNER
        }
    }
    LaunchedEffect(activity.ads.bannerRevision) { ad.loadAd(activity.ads.request()) }
    DisposableEffect(ad) { onDispose { ad.destroy() } }
    AndroidView(
        factory = {
            (ad.parent as? ViewGroup)?.removeView(ad)
            ad
        },
        modifier = Modifier.fillMaxWidth().height(50.dp),
    )
}

@Composable
private fun Page(title: String, subtitle: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(title, style = MaterialTheme.typography.headlineLarge)
        Text(
            subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        content()
    }
}

@Composable
private fun Panel(content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = content,
        )
    }
}

@Composable
private fun Toggle(
    title: String,
    description: String = "",
    checked: Boolean,
    enabled: Boolean = true,
    change: (Boolean) -> Unit,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title)
            if (description.isNotEmpty()) {
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Switch(checked = checked, onCheckedChange = change, enabled = enabled)
    }
}

@Composable
private fun BrowserScreen(activity: MainActivity) {
    val browser = activity.browser
    val tab = browser.current
    var address by remember(tab?.id) { mutableStateOf(tab?.url ?: "") }
    var tabsDialog by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    LaunchedEffect(tab?.url) { address = tab?.url ?: "" }
    BackHandler(enabled = tab?.webView?.canGoBack() == true) { tab?.webView?.goBack() }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = address,
                onValueChange = { address = it },
                singleLine = true,
                label = { Text("URL") },
                modifier = Modifier.weight(1f),
                keyboardOptions =
                    androidx.compose.foundation.text.KeyboardOptions(
                        imeAction = androidx.compose.ui.text.input.ImeAction.Go,
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Uri,
                    ),
                keyboardActions =
                    androidx.compose.foundation.text.KeyboardActions(
                        onGo = { browser.navigate(address) }
                    ),
            )
            IconButton(onClick = { browser.navigate(address) }) {
                Icon(Icons.AutoMirrored.Filled.ArrowForward, "Go")
            }
            Box {
                IconButton(onClick = { menu = true }) {
                    Icon(Icons.Default.MoreVert, "Browser actions")
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    listOf(
                            "Show",
                            "Hide",
                            "Add to Blacklist",
                            "Add to AllowList",
                            "Share",
                            "Open externally",
                        )
                        .forEach { action ->
                            DropdownMenuItem(
                                text = { Text(action) },
                                onClick = {
                                    menu = false
                                    when (action) {
                                        "Show",
                                        "Hide" -> browser.command(action.lowercase())
                                        "Add to Blacklist",
                                        "Add to AllowList" -> {
                                            if (WebURLs.normalize(tab?.url ?: "") != null) {
                                                activity.shareRule =
                                                    (if (action.contains("Blacklist")) "block"
                                                    else "allow") to tab!!.url
                                            }
                                        }
                                        "Share" -> {
                                            if (tab?.url?.isNotEmpty() == true) {
                                                activity.startActivity(
                                                    Intent.createChooser(
                                                        Intent(Intent.ACTION_SEND)
                                                            .setType("text/plain")
                                                            .putExtra(Intent.EXTRA_TEXT, tab.url),
                                                        "Share page",
                                                    )
                                                )
                                            }
                                        }
                                        else -> {
                                            if (WebURLs.normalize(tab?.url ?: "") != null) {
                                                activity.startActivity(
                                                    Intent.createChooser(
                                                        Intent(
                                                            Intent.ACTION_VIEW,
                                                            Uri.parse(tab!!.url),
                                                        ),
                                                        "Open with another browser",
                                                    )
                                                )
                                            }
                                        }
                                    }
                                },
                            )
                        }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = { tab?.webView?.goBack() },
                enabled = tab?.webView?.canGoBack() == true,
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
            }
            IconButton(
                onClick = { tab?.webView?.goForward() },
                enabled = tab?.webView?.canGoForward() == true,
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowForward, "Forward")
            }
            IconButton(onClick = { tab?.webView?.reload() }) {
                Icon(Icons.Default.Refresh, "Reload")
            }
            Text(
                tab?.status ?: "Ready",
                style = MaterialTheme.typography.labelSmall,
                maxLines = 2,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { tabsDialog = true }) { Text("${browser.tabs.size} tabs") }
            IconButton(onClick = { browser.newTab() }) { Icon(Icons.Default.Add, "New tab") }
            IconButton(onClick = { tab?.let(browser::close) }, enabled = tab != null) {
                Icon(Icons.Default.Close, "Close current tab")
            }
        }
        if (tab != null && tab.progress in 1..99) {
            LinearProgressIndicator(
                progress = { tab.progress / 100f },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (tab?.url.isNullOrEmpty()) {
            Column(
                Modifier.fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Image(painterResource(R.drawable.brand_icon), "DevTools icon", Modifier.size(110.dp))
                Spacer(Modifier.height(20.dp))
                Text("Inspect from anywhere", style = MaterialTheme.typography.headlineMedium)
                Text(
                    "Open a URL above or share a link from another browser. Your selected lists decide where tools run."
                )
                Spacer(Modifier.height(16.dp))
                Toggle("DevTools enabled", checked = activity.configuration.enabled) {
                    activity.configuration.change { json -> json.put("enabled", it) }
                    activity.changed()
                }
            }
        } else if (tab != null) {
            LaunchedEffect(tab.id){browser.activate(tab)}
            key(tab.id) {
                AndroidView(
                    factory = {
                        (tab.webView.parent as? ViewGroup)?.removeView(tab.webView)
                        tab.webView
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
    if (tabsDialog) {
        AlertDialog(
            onDismissRequest = { tabsDialog = false },
            title = { Text("Open tabs") },
            text = {
                LazyColumn {
                    items(browser.tabs, key = { it.id }) { row ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextButton(
                                onClick = {
                                    browser.selected = row.id
                                    tabsDialog = false
                                },
                                modifier = Modifier.weight(1f),
                            ) {
                                Column {
                                    Text(row.title, maxLines = 1)
                                    Text(
                                        row.url,
                                        maxLines = 1,
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                            }
                            IconButton(onClick = { browser.close(row) }) {
                                Icon(Icons.Default.Close, "Close ${row.title}")
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        browser.newTab()
                        tabsDialog = false
                    }
                ) {
                    Text("New tab")
                }
            },
        )
    }
}

@Composable
private fun BrowserProfilesScreen(activity: MainActivity) {
    var selected by remember { mutableStateOf(0) }
    Column {
        TabRow(selectedTabIndex = selected) {
            listOf("DevTools", "Firefox").forEachIndexed { index, title ->
                Tab(selected = selected == index, onClick = { selected = index }, text = { Text(title) })
            }
        }
        key(selected) { ListsScreen(activity, if (selected == 0) "devtools" else "firefox", true) }
    }
}

@Composable
private fun ListsScreen(activity: MainActivity, browserID: String = "devtools", showProfile: Boolean = false) {
    val config = activity.configuration
    var selected by remember { mutableStateOf<String?>(null) }
    var create by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf("block") }
    Page(if (showProfile) "${if (browserID == "firefox") "Firefox" else "DevTools"} settings" else "Your lists", "Blacklists take priority. Select any combination of lists to apply.") {
        if (showProfile) {
            Panel {
                Toggle("DevTools enabled", checked = config.profile(browserID).optBoolean("enabled", true)) {
                    config.changeProfile(browserID) { json -> json.put("enabled", it) }
                    if (browserID == "devtools") activity.changed()
                }
                if (browserID == "firefox") {
                    Text("Pair the Firefox add-on once. Settings sync while this app is running; Firefox keeps its last settings when the app is closed.")
                    TextButton(onClick = {
                        val clipboard = activity.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("DevTools Firefox pairing", activity.firefoxSync.pairingCode))
                    }) { Text("Copy Firefox pairing code") }
                }
            }
        }
        Panel {
            Toggle(
                "Run on every webpage",
                "Turn off to run only on selected AllowLists.",
                config.profile(browserID).optBoolean("runEverywhere"),
            ) {
                config.changeProfile(browserID) { json -> json.put("runEverywhere", it) }
                if (browserID == "devtools") activity.changed()
            }
        }
        config.listsFor(browserID).forEach { list ->
            Panel {
                Toggle(
                    list.getString("name"),
                    "${if (list.getString("kind") == "block") "Blacklist" else "AllowList"} · ${list.getJSONArray("rules").length()} rules",
                    list.optBoolean("selected"),
                ) {
                    config.editList(list.getString("id"), browserID) { row -> row.put("selected", it) }
                    if (browserID == "devtools") activity.changed()
                }
                TextButton(onClick = { selected = list.getString("id") }) {
                    Text("Edit list & rules")
                }
            }
        }
        Button(onClick = { create = true }) {
            Icon(Icons.Default.Add, null)
            Text("Create list")
        }
    }
    if (create) {
        AlertDialog(
            onDismissRequest = { create = false },
            title = { Text("New list") },
            text = {
                Column {
                    OutlinedTextField(
                        name,
                        { name = it },
                        label = { Text("List name") },
                        singleLine = true,
                    )
                    Choice("Type", kind, listOf("block", "allow")) { kind = it }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = name.isNotBlank(),
                    onClick = {
                        config.newList(name, kind, browserID)
                        if (browserID == "devtools") activity.changed()
                        name = ""
                        create = false
                    },
                ) {
                    Text("Create")
                }
            },
            dismissButton = { TextButton(onClick = { create = false }) { Text("Cancel") } },
        )
    }
    selected?.let { id ->
        config.listsFor(browserID)
            .firstOrNull { it.getString("id") == id }
            ?.let { list -> ListEditor(activity, list, browserID) { selected = null } }
    }
}

@Composable
private fun ListEditor(activity: MainActivity, list: JSONObject, browserID: String, dismiss: () -> Unit) {
    val id = list.getString("id")
    var name by remember(id) { mutableStateOf(list.getString("name")) }
    var adding by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<JSONObject?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text("Edit list") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true)
                TextButton(
                    enabled = name.isNotBlank(),
                    onClick = {
                        activity.configuration.editList(id, browserID) { it.put("name", name.trim().take(80)) }
                        if (browserID == "devtools") activity.changed()
                    },
                ) {
                    Text("Save name")
                }
                Choice("Type", list.getString("kind"), listOf("block", "allow")) { value ->
                    activity.configuration.editList(id, browserID) { it.put("kind", value) }
                    if (browserID == "devtools") activity.changed()
                }
                list.getJSONArray("rules").objects().forEach { rule ->
                    HorizontalDivider()
                    Toggle(
                        rule.getString("pattern"),
                        rule.getString("kind"),
                        rule.optBoolean("enabled"),
                    ) { value ->
                        activity.configuration.editList(id, browserID) { row ->
                            row.getJSONArray("rules")
                                .objects()
                                .first { it.getString("id") == rule.getString("id") }
                                .put("enabled", value)
                        }
                        if (browserID == "devtools") activity.changed()
                    }
                    Row {
                        TextButton(onClick = { editing = rule }) { Text("Edit") }
                        TextButton(
                            onClick = {
                                activity.configuration.editList(id, browserID) { row ->
                                    row.put(
                                        "rules",
                                        JSONArray(
                                            row.getJSONArray("rules").objects().filter {
                                                it.getString("id") != rule.getString("id")
                                            }
                                        ),
                                    )
                                }
                                if (browserID == "devtools") activity.changed()
                            }
                        ) {
                            Text("Delete rule")
                        }
                    }
                }
                TextButton(onClick = { adding = true }) { Text("Add rule") }
                TextButton(onClick = { confirmDelete = true }) {
                    Text("Delete list", color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = { TextButton(onClick = dismiss) { Text("Done") } },
    )
    if (adding || editing != null) {
        RuleEditor(
            activity,
            list.getString("kind"),
            initialList = id,
            existing = editing,
            browserID = browserID,
            dismiss = {
                adding = false
                editing = null
            },
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this list?") },
            text = { Text("Its rules will also be deleted.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        activity.configuration.deleteList(id, browserID)
                        if (browserID == "devtools") activity.changed()
                        dismiss()
                    }
                ) {
                    Text("Delete")
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun RuleEditor(
    activity: MainActivity,
    listKind: String,
    initialURL: String = "",
    initialList: String = "",
    existing: JSONObject? = null,
    browserID: String = "devtools",
    dismiss: () -> Unit,
) {
    val config = activity.configuration
    val lists = config.listsFor(browserID).filter { it.getString("kind") == listKind }
    var listID by remember {
        mutableStateOf(initialList.ifEmpty { lists.firstOrNull()?.getString("id") ?: "" })
    }
    var kind by remember { mutableStateOf(existing?.getString("kind") ?: "domain") }
    var pattern by remember {
        mutableStateOf(
            existing?.getString("pattern")
                ?: runCatching { URI(initialURL).host }.getOrDefault("")
                ?: ""
        )
    }
    var newName by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = dismiss,
        title = {
            Text(
                if (existing == null)
                    "Add to ${if (listKind == "block") "Blacklist" else "AllowList"}"
                else "Edit rule"
            )
        },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                lists.forEach { list ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = listID == list.getString("id"),
                            onClick = { listID = list.getString("id") },
                        )
                        Text(
                            list.getString("name") +
                                if (list.optBoolean("selected")) "" else " (inactive)"
                        )
                    }
                }
                if (existing == null) {
                    OutlinedTextField(
                        newName,
                        { newName = it },
                        label = { Text("Or create a new list") },
                        singleLine = true,
                    )
                }
                Choice("Match", kind, listOf("domain", "url", "wildcard", "regex")) {
                    kind = it
                    if (initialURL.isNotEmpty() && existing == null) {
                        pattern = if (it == "domain") URI(initialURL).host ?: "" else initialURL
                    }
                }
                OutlinedTextField(
                    pattern,
                    {
                        pattern = it
                        error = null
                    },
                    label = { Text("Pattern") },
                    textStyle =
                        MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                )
                Text(
                    "Domain: example.com\nWildcard: *://*.example.com/*\nRegex: /^https:\\/\\/example\\.com\\//i\nRegex supports i/m/u; complex repeated groups, backreferences and lookarounds are excluded.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    "Regex matching runs in a worker with a 750 ms deadline. If a site blocks workers, it is excluded.",
                    style = MaterialTheme.typography.bodySmall,
                )
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(
                enabled =
                    !saving &&
                        pattern.isNotBlank() &&
                        (listID.isNotEmpty() || newName.isNotBlank()),
                onClick = {
                    saving = true
                    activity.browser.validate(kind, pattern) { failure ->
                        saving = false
                        if (failure != null) {
                            error = failure
                        } else {
                            val destination =
                                if (newName.isNotBlank()) config.newList(newName, listKind, browserID)
                                else listID
                            val saved =
                                if (existing == null) config.addRule(destination, kind, pattern, browserID)
                                else {
                                    config.editList(destination, browserID) { list ->
                                        list
                                            .getJSONArray("rules")
                                            .objects()
                                            .first {
                                                it.getString("id") == existing.getString("id")
                                            }
                                            .put("kind", kind)
                                            .put("pattern", pattern.trim())
                                    }
                                    config.error == null
                                }
                            if (saved) {
                                if (browserID == "devtools") activity.changed()
                                activity.ads.addedRule()
                                dismiss()
                                activity.ads.opportunity()
                            } else {
                                error = config.error
                            }
                        }
                    }
                },
            ) {
                Text(if (saving) "Validating…" else "Save")
            }
        },
        dismissButton = { TextButton(onClick = dismiss) { Text("Cancel") } },
    )
}

@Composable
private fun Choice(title: String, value: String, options: List<String>, change: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { expanded = true }) { Text("$title: $value ▾") }
        DropdownMenu(expanded, { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        change(option)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun ToolsScreen(activity: MainActivity) {
    val config = activity.configuration
    val options = config.data.getJSONObject("console")
    var panelSize by
        remember(options.optDouble("displaySize")) {
            mutableStateOf(options.optDouble("displaySize").toFloat())
        }
    var opacity by
        remember(options.optDouble("transparency")) {
            mutableStateOf(options.optDouble("transparency").toFloat())
        }
    fun set(key: String, value: Any) {
        config.change { it.getJSONObject("console").put(key, value) }
        activity.changed()
    }
    Page(
        "Developer tools",
        "Bundled offline. Vue inspection depends on the site's available debugging hooks.",
    ) {
        Panel {
            if (!config.pro) {
                Text("Unlock Pro for Vue tools, customization and additional plugins.")
                Button(onClick = { activity.screen = "Pro" }) { Text("View Pro") }
            }
            Choice(
                "Console",
                options.optString("backend"),
                if (config.pro) listOf("eruda", "vconsole") else listOf("eruda"),
            ) {
                set("backend", it)
            }
            Choice(
                "Vue adapter",
                options.optString("vueAdapter"),
                if (config.pro) listOf("modern", "legacy") else listOf("modern"),
            ) {
                set("vueAdapter", it)
            }
            listOf(
                    "vue" to "Vue DevTools",
                    "resourceTiming" to "Resource timing waterfall",
                    "code" to "Code editor",
                    "dom" to "DOM explorer",
                    "timing" to "Navigation timing",
                    "fps" to "Frame rate",
                    "features" to "Browser features",
                )
                .forEach { (key, label) ->
                    Toggle(label, checked = options.optBoolean(key), enabled = config.pro) {
                        set(key, it)
                    }
                }
        }
        Panel {
            Choice(
                "Theme",
                options.optString("theme"),
                if (config.pro) listOf("Material Palenight", "Dark", "Light", "Monokai Pro")
                else listOf("Material Palenight"),
            ) {
                set("theme", it)
            }
            Text("Panel size: ${options.optDouble("displaySize").toInt()}%")
            Slider(
                value = panelSize,
                onValueChange = { panelSize = it },
                onValueChangeFinished = { set("displaySize", panelSize.toDouble()) },
                valueRange = 20f..100f,
                enabled = config.pro,
            )
            Text("Opacity: ${"%.2f".format(options.optDouble("transparency"))}")
            Slider(
                value = opacity,
                onValueChange = { opacity = it },
                onValueChangeFinished = { set("transparency", opacity.toDouble()) },
                valueRange = 0.5f..1f,
                enabled = config.pro,
            )
            Toggle(
                "Remember icon position",
                checked = options.optBoolean("rememberPosition"),
                enabled = config.pro,
            ) {
                set("rememberPosition", it)
            }
        }
        Panel {
            Text("Package updates", style = MaterialTheme.typography.titleMedium)
            Toggle(
                "Automatically install updates",
                "Checks at app opening and foreground. New versions apply on reload.",
                config.data.optBoolean("automaticallyInstallUpdates"),
            ) {
                config.change { json -> json.put("automaticallyInstallUpdates", it) }
                activity.changed()
                if (it) {
                    activity.checkUpdates()
                }
            }
            Text(activity.packages.message)
            activity.packages.versions().forEach { (name, version) ->
                Text("$name · $version", style = MaterialTheme.typography.bodySmall)
            }
            Button(enabled = !activity.packages.checking, onClick = { activity.checkUpdates() }) {
                Text("Check now")
            }
            TextButton(
                enabled = !activity.packages.checking,
                onClick = {
                    activity.packages.clear()
                    activity.changed()
                },
            ) {
                Text("Clear downloaded packages")
            }
        }
    }
}

@Composable
private fun ProScreen(activity: MainActivity) {
    Page(
        "DevTools Pro",
        "Vue tools, resource timing, all plugins and console customization. Pro also removes ads.",
    ) {
        Panel {
            Text(
                if (activity.configuration.pro) "Full version active"
                else "Unlock your complete toolkit",
                style = MaterialTheme.typography.titleLarge,
            )
            Text(activity.purchases.message)
            activity.purchases.products.forEach { product ->
                Button(
                    onClick = { activity.purchases.buy(product) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        "${if (product.productId == PlayPurchases.MONTHLY) "Monthly" else "Lifetime"} · ${activity.purchases.price(product)}"
                    )
                }
            }
            Text(
                "Planned US pricing: $2.99/month or $19.99 lifetime. Actual localized prices appear when Google Play products are available.",
                style = MaterialTheme.typography.bodySmall,
            )
            TextButton(onClick = { activity.purchases.restore() }) {
                Text("Restore / refresh purchases")
            }
            TextButton(
                onClick = {
                    activity.startActivity(
                        Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse("https://play.google.com/store/account/subscriptions"),
                        )
                    )
                }
            ) {
                Text("Manage subscriptions")
            }
            Text(
                "Buying lifetime does not cancel an existing monthly subscription.",
                style = MaterialTheme.typography.bodySmall,
            )
            if (BuildConfig.DEBUG) {
                Toggle(
                    "Preview Pro tools",
                    "Debug builds only. This does not represent a purchase.",
                    activity.configuration.previewPro,
                ) {
                    activity.configuration.previewPro = it
                    activity.configuration.pro = it
                    activity.purchases.restore()
                    activity.changed()
                }
            }
        }
    }
}

@Composable
private fun SettingsScreen(activity: MainActivity) {
    val config = activity.configuration
    var reset by remember { mutableStateOf(false) }
    var licenses by remember { mutableStateOf(false) }
    var clearData by remember { mutableStateOf(false) }
    Page("Settings", "Your developer console, your browsing rules.") {
        config.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Panel {
            Toggle("DevTools enabled", checked = config.enabled) {
                config.change { json -> json.put("enabled", it) }
                activity.changed()
            }
            Choice(
                "Appearance",
                config.data.optString("appearance"),
                listOf("system", "dark", "light"),
            ) { value ->
                config.change { it.put("appearance", value) }
            }
            Choice(
                "Accent",
                config.data.optString("accent"),
                listOf("violet", "pink", "mint", "blue"),
            ) { value ->
                config.change { it.put("accent", value) }
            }
        }
        Panel {
            Text("Browser privacy", style = MaterialTheme.typography.titleMedium)
            Text(
                "Pages have no native JavaScript bridge. Third-party cookies and camera/microphone requests are disabled. TLS errors cancel navigation. HTTP pages are allowed for local development."
            )
            Text(
                "Sharing from Chrome opens a separate session here; Chrome's cookies and live page state are not transferred."
            )
            TextButton(onClick = { clearData = true }) { Text("Clear browser data") }
            TextButton(onClick = { reset = true }) { Text("Reset settings & lists") }
            if (BuildConfig.ADS_ENABLED) {
                TextButton(onClick = { activity.ads.privacy() }) {
                    Text("Advertising privacy choices")
                }
            }
        }
        Panel {
            Text("⚙︎ DevTools · Android 1.0.0", style = MaterialTheme.typography.titleMedium)
            Text(
                "Based on your DevTools-2-master JavaScript core. Runs inside this app's browser; it does not inject into Chrome tabs."
            )
            TextButton(
                onClick = {
                    activity.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse("https://tylero056.com"))
                    )
                }
            ) {
                Text("Developer website")
            }
            TextButton(
                onClick = {
                    activity.startActivity(
                        Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse("https://github.com/tyh24647/DevTools-2-master/issues"),
                        )
                    )
                }
            ) {
                Text("Report a bug")
            }
            TextButton(onClick = { licenses = true }) { Text("Licenses & acknowledgements") }
        }
    }
    if (reset) {
        AlertDialog(
            onDismissRequest = { reset = false },
            title = { Text("Reset settings & lists?") },
            text = {
                Text(
                    "All rules will be removed and default settings restored. Purchases and browser data are preserved."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        config.reset()
                        activity.changed()
                        reset = false
                    }
                ) {
                    Text("Reset")
                }
            },
            dismissButton = { TextButton(onClick = { reset = false }) { Text("Cancel") } },
        )
    }
    if (clearData) {
        AlertDialog(
            onDismissRequest = { clearData = false },
            title = { Text("Clear browsing data?") },
            text = { Text("Closes tabs and removes cookies, cache and website storage.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        activity.browser.tabs.toList().forEach {
                            it.webView.clearCache(true)
                            it.webView.clearHistory()
                        }
                        android.webkit.CookieManager.getInstance().removeAllCookies(null)
                        android.webkit.CookieManager.getInstance().flush()
                        android.webkit.WebStorage.getInstance().deleteAllData()
                        activity.browser.tabs.toList().forEach { activity.browser.close(it) }
                        clearData = false
                    }
                ) {
                    Text("Clear")
                }
            },
            dismissButton = { TextButton(onClick = { clearData = false }) { Text("Cancel") } },
        )
    }
    if (licenses) {
        val text = remember {
            activity.assets.open("THIRD-PARTY-NOTICES.txt").bufferedReader().use { it.readText() } + "\n\n" + activity.assets.open("editor/THIRD-PARTY-NOTICES.txt").bufferedReader().use { it.readText() }
        }
        AlertDialog(
            onDismissRequest = { licenses = false },
            title = { Text("Licenses") },
            text = {
                Text(
                    text,
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    style = MaterialTheme.typography.bodySmall,
                )
            },
            confirmButton = { TextButton(onClick = { licenses = false }) { Text("Done") } },
        )
    }
}
