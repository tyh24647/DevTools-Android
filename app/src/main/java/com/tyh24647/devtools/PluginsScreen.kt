package com.tyh24647.devtools

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.util.UUID
import kotlinx.coroutines.launch
import org.json.JSONArray

private val examplePlugin="""
return {
  name: "hello",
  init(element) { this.element = element; element.text("Hello from your Eruda panel!"); },
  show() { this.element.show(); },
  hide() { this.element.hide(); },
  destroy() { this.element.empty(); }
};
""".trimIndent()
private val userscriptTemplate="""
// ==UserScript==
// @name         My userscript
// @namespace    devtools.local
// @version      1.0.0
// @description  Describe what your script does
// @match        https://example.com/*
// @run-at       document-end
// @grant        none
// ==/UserScript==

(function () {
  'use strict';
  // Your code here. Update @match to choose your pages.
})();
""".trimIndent()
private fun tagIDs(plugin:org.json.JSONObject):List<String> = (plugin.optJSONArray("tags")?:JSONArray()).let{array->(0 until array.length()).map{array.getString(it)}}
private fun tagColor(raw:String)=runCatching{Color(android.graphics.Color.parseColor(raw))}.getOrDefault(Color.Gray)

@Composable
fun PluginsScreen(activity:MainActivity) {
    val config=activity.configuration
    val scope=rememberCoroutineScope()
    var kind by remember {mutableStateOf("eruda")}
    var folder by remember {mutableStateOf("")}
    var filterTag by remember {mutableStateOf<String?>(null)}
    var editing by remember {mutableStateOf(false)}
    var pluginID by remember {mutableStateOf("")}
    var name by remember {mutableStateOf("")}
    var source by remember {mutableStateOf("")}
    var initial by remember {mutableStateOf("")}
    var editFolder by remember {mutableStateOf("")}
    var editTags by remember {mutableStateOf<List<String>>(emptyList())}
    var syntaxError by remember {mutableStateOf<String?>("Loading editor…")}
    var error by remember {mutableStateOf<String?>(null)}
    var url by remember {mutableStateOf<String?>(null)}
    var importing by remember {mutableStateOf(false)}
    var folderName by remember {mutableStateOf<String?>(null)}
    var removeFolder by remember {mutableStateOf(false)}
    var deleting by remember {mutableStateOf<String?>(null)}
    var tagsOpen by remember {mutableStateOf(false)}
    var tagName by remember {mutableStateOf("")}
    var tagID by remember {mutableStateOf("")}
    var color by remember {mutableStateOf("#8B5CF6")}
    fun edit(id:String,title:String,code:String,target:String,labels:List<String>) {
        pluginID=id;name=title;source=code;initial=code;editFolder=target;editTags=labels
        error=null;syntaxError="Loading editor…";editing=true
    }
    fun action(block:()->Unit){try{block();activity.changed()}catch(failure:Exception){activity.browser.notice=failure.message}}
    LaunchedEffect(activity.pendingScript){activity.pendingScript?.let {draft->
        kind=draft.kind;folder="";edit(UUID.randomUUID().toString(),draft.name,draft.source,"",emptyList());activity.pendingScript=null
    }}
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
        Text("Plugins & userscripts",style=MaterialTheme.typography.headlineMedium)
        TabRow(if(kind=="eruda")0 else 1){
            Tab(kind=="eruda",{kind="eruda";folder=""},text={Text("Eruda panels")})
            Tab(kind=="userscript",{kind="userscript";folder=""},text={Text("Userscripts")})
        }
        Text("Create or import scripts for the native browser. New and edited scripts stay disabled until you enable them. Reload pages after changes.")
        FlowRow {
            OutlinedButton(onClick={activity.importPlugin()}){Text("Import file")}
            OutlinedButton(onClick={url="";error=null}){Text("Import URL")}
            OutlinedButton(onClick={edit(UUID.randomUUID().toString(),if(kind=="userscript")"My userscript" else "",if(kind=="userscript")userscriptTemplate else examplePlugin,folder,emptyList())}){Text(if(kind=="userscript")"Create userscript" else "Create plugin")}
            TextButton(onClick={tagsOpen=true}){Text("Manage tags")}
        }
        if(kind=="eruda")OutlinedButton(onClick={
            val existing=config.plugins.firstOrNull{it.optString("id")=="devtools-css-editor"}
            if(existing!=null)edit(existing.optString("id"),existing.optString("name"),existing.optString("source"),existing.optString("folder"),tagIDs(existing))
            else edit("devtools-css-editor","CSS editor",activity.assets.open("tools/css-editor-plugin.js").bufferedReader().use{it.readText()},folder,emptyList())
        }){Text("CSS editor plugin")}
        Text(if(folder.isEmpty())"Root" else config.scriptFolders.firstOrNull{it.optString("id")==folder}?.optString("name")?:"Root",style=MaterialTheme.typography.titleLarge)
        FlowRow {
            if(folder.isNotEmpty())TextButton(onClick={folder=config.scriptFolders.firstOrNull{it.optString("id")==folder}?.optString("parent").orEmpty()}){Text("Up")}
            TextButton(onClick={folderName=""}){Text("New folder")}
            if(folder.isNotEmpty())TextButton(onClick={removeFolder=true}){Text("Remove folder")}
        }
        config.scriptFolders.filter{it.optString("kind")==kind&&it.optString("parent")==folder}.forEach {entry->
            OutlinedButton(onClick={folder=entry.optString("id")}){Text("📁 "+entry.optString("name"))}
        }
        if(config.scriptTags.isNotEmpty())FlowRow {
            FilterChip(filterTag==null,{filterTag=null},label={Text("All tags")})
            config.scriptTags.forEach {tag->FilterChip(filterTag==tag.optString("id"),{filterTag=tag.optString("id")},label={Text("● "+tag.optString("name"),color=tagColor(tag.optString("color")))})}
        }
        val visible=config.plugins.filter{it.optString("kind","eruda")==kind&&it.optString("folder")==folder&&(filterTag==null||filterTag in tagIDs(it))}
        visible.forEach {plugin->val id=plugin.optString("id")
            Card(Modifier.fillMaxWidth()){Column(Modifier.padding(12.dp)){
                Text(plugin.optString("name"),style=MaterialTheme.typography.titleMedium)
                FlowRow{config.scriptTags.filter{it.optString("id") in tagIDs(plugin)}.forEach{tag->Text("● "+tag.optString("name")+"  ",color=tagColor(tag.optString("color")))}}
                Row {Text("Enabled",Modifier.weight(1f));Switch(plugin.optBoolean("enabled"),{enabled->action{
                    if(enabled&&kind=="userscript")UserscriptMetadata.validate(plugin.optString("source"))
                    config.change {root->root.getJSONArray("plugins").objects().first{it.optString("id")==id}.put("enabled",enabled)}
                }})}
                FlowRow {
                    TextButton(onClick={edit(id,plugin.optString("name"),plugin.optString("source"),plugin.optString("folder"),tagIDs(plugin))}){Text("Edit / organize")}
                    TextButton(onClick={activity.exportScript(plugin.optString("name"),plugin.optString("source"),kind)}){Text("Export")}
                    TextButton(onClick={deleting=id}){Text("Delete")}
                }
            }}
        }
        if(visible.isEmpty())Text("No scripts in this folder.")
        Text("Help",style=MaterialTheme.typography.titleLarge)
        Text("URL imports must point directly to JavaScript files, not installation pages. Source is parsed before Save; code is never run during import. Eruda plugins must return a tool or export a factory. Files are limited to 256 KB, with 20 installed scripts total.")
        Text("Userscripts support match/include/exclude rules, document start/end/idle, styles, logging and per-script/per-origin GM storage. Storage is page-visible. External dependencies and privileged networking are unsupported.")
        listOf("Eruda plugin guide" to "https://eruda.liriliri.io/docs/plugin.html","Userscript metadata and GM APIs" to "https://www.tampermonkey.net/documentation.php").forEach {(label,address)->TextButton(onClick={activity.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(address)))}){Text(label)}}
    }
    if(editing)Dialog({editing=false},properties=DialogProperties(usePlatformDefaultWidth=false)){
        Surface(Modifier.fillMaxWidth(.96f).heightIn(max=820.dp),shape=MaterialTheme.shapes.large){Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)){
            Text(if(kind=="userscript")"Userscript editor" else "Eruda plugin editor",style=MaterialTheme.typography.titleLarge)
            OutlinedTextField(name,{name=it},label={Text("Name")},singleLine=true,modifier=Modifier.fillMaxWidth())
            key(pluginID){ScriptEditor(activity,initial,kind){code,invalid->source=code;syntaxError=invalid}}
            Text("Folder")
            FlowRow {
                FilterChip(editFolder.isEmpty(),{editFolder=""},label={Text("Root")})
                config.scriptFolders.filter{it.optString("kind")==kind}.forEach {entry->FilterChip(editFolder==entry.optString("id"),{editFolder=entry.optString("id")},label={Text(entry.optString("name"))})}
            }
            Text("Tags")
            FlowRow {config.scriptTags.forEach {tag->val id=tag.optString("id");FilterChip(id in editTags,{editTags=if(id in editTags)editTags-id else editTags+id},label={Text("● "+tag.optString("name"),color=tagColor(tag.optString("color")))})};TextButton(onClick={tagsOpen=true}){Text("Add / edit tags")}}
            (error?:syntaxError)?.let{Text(it,color=MaterialTheme.colorScheme.error)}
            FlowRow {
                Button(enabled=syntaxError==null,onClick={try{
                    if(config.savePlugin(name,source,pluginID,kind)) {
                        check(config.scriptOrganization(pluginID,editFolder,editTags)){"Could not save organization."}
                        activity.changed();editing=false
                    } else error=config.error
                }catch(failure:Exception){error=failure.message}}){Text("Save disabled")}
                TextButton(onClick={editing=false}){Text("Cancel")}
            }
        }}
    }
    url?.let {value->AlertDialog(onDismissRequest={if(!importing)url=null},title={Text("Import "+if(kind=="userscript")"userscript URL" else "plugin URL")},text={Column{
        OutlinedTextField(value,{url=it},label={Text("Direct JavaScript URL")},singleLine=true,enabled=!importing)
        if(importing)LinearProgressIndicator(Modifier.fillMaxWidth())
        error?.let{Text(it,color=MaterialTheme.colorScheme.error)}
    }},confirmButton={TextButton(enabled=!importing,onClick={scope.launch{
        importing=true;error=null
        try{val draft=ScriptImport.url(value,kind);url=null;edit(UUID.randomUUID().toString(),draft.name,draft.source,folder,emptyList())}
        catch(failure:Exception){error=failure.message}
        finally{importing=false}
    }}){Text("Load for review")}},dismissButton={TextButton(enabled=!importing,onClick={url=null}){Text("Cancel")}})}
    folderName?.let{value->AlertDialog(onDismissRequest={folderName=null},title={Text("New folder")},text={OutlinedTextField(value,{folderName=it},label={Text("Name")})},confirmButton={TextButton(onClick={action{check(config.addScriptFolder(value,folder,kind));folderName=null}}){Text("Create")}},dismissButton={TextButton(onClick={folderName=null}){Text("Cancel")}})}
    if(removeFolder)AlertDialog(onDismissRequest={removeFolder=false},title={Text("Remove this folder?")},text={Text("Nested folders are removed. Their scripts are kept and moved to the root.")},confirmButton={TextButton(onClick={action{check(config.removeScriptFolder(folder));folder="";removeFolder=false}}){Text("Remove")}},dismissButton={TextButton(onClick={removeFolder=false}){Text("Cancel")}})
    deleting?.let{id->AlertDialog(onDismissRequest={deleting=null},title={Text("Delete script?")},confirmButton={TextButton(onClick={action{check(config.removePlugin(id));deleting=null}}){Text("Delete")}},dismissButton={TextButton(onClick={deleting=null}){Text("Cancel")}})}
    if(tagsOpen)AlertDialog(onDismissRequest={tagsOpen=false},title={Text("Colored tags")},text={Column(Modifier.verticalScroll(rememberScrollState())){
        config.scriptTags.forEach{tag->Row {
            TextButton(onClick={tagID=tag.optString("id");tagName=tag.optString("name");color=tag.optString("color")}){Text("● "+tag.optString("name"),color=tagColor(tag.optString("color")))}
            TextButton(onClick={action{check(config.removeScriptTag(tag.optString("id")));editTags=editTags-tag.optString("id");if(filterTag==tag.optString("id"))filterTag=null}}){Text("Remove")}
        }}
        OutlinedTextField(tagName,{tagName=it},label={Text("Tag name")})
        FlowRow{listOf("#EF4444","#F97316","#EAB308","#22C55E","#3B82F6","#8B5CF6","#EC4899","#808080").forEach{value->FilterChip(color==value,{color=value},label={Text("●",color=tagColor(value))})}}
        TextButton(onClick={action{check(config.saveScriptTag(tagName,color,tagID.ifEmpty{UUID.randomUUID().toString()}));tagID="";tagName=""}}){Text(if(tagID.isEmpty())"Create tag" else "Update tag")}
        if(tagID.isNotEmpty())TextButton(onClick={tagID="";tagName=""}){Text("New tag")}
    }},confirmButton={TextButton(onClick={tagsOpen=false}){Text("Done")}})
}
