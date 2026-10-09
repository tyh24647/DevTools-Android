package com.tyh24647.devtools

import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import org.json.JSONObject

@Composable
fun ScriptEditor(activity:MainActivity,initial:String,kind:String,onChange:(String,String?)->Unit) {
    val preferences=remember {activity.getSharedPreferences("script-editor",0)}
    var options by remember {mutableStateOf(runCatching{JSONObject(preferences.getString("options","{}")!!)}.getOrDefault(JSONObject()).apply {
        if(!has("theme"))put("theme","dark")
        if(!has("matchBrackets"))put("matchBrackets",true)
        if(!has("closeBrackets"))put("closeBrackets",true)
        if(!has("wrap"))put("wrap",true)
    })}
    var settings by remember {mutableStateOf(false)}
    var generation by remember {mutableStateOf(0)}
    var lastSource by remember {mutableStateOf(initial)}
    var webView by remember {mutableStateOf<WebView?>(null)}
    val callback by rememberUpdatedState(onChange)
    fun option(key:String,value:Any) {options=JSONObject(options.toString()).put(key,value);preferences.edit().putString("options",options.toString()).apply();webView?.evaluateJavascript("DevToolsEditor.configure("+options+")",null)}
    Column(Modifier.fillMaxWidth()) {
        Row {
            TextButton(onClick={webView?.evaluateJavascript("DevToolsEditor.format()",null)}){Text("Format")}
            TextButton(onClick={webView?.evaluateJavascript("DevToolsEditor.complete()",null)}){Text("Complete")}
            Spacer(Modifier.weight(1f))
            IconButton(onClick={settings=true}){Icon(Icons.Default.Settings,"Editor settings")}
        }
        key(generation){AndroidView(factory={context->WebView(context).apply {
            webView=this
            this.settings.javaScriptEnabled=true
            this.settings.allowFileAccess=false
            this.settings.allowContentAccess=false
            this.settings.blockNetworkLoads=true
            if(WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
                WebViewCompat.addWebMessageListener(this,"editorBridge",setOf("https://editor.devtools.invalid")){_,message,origin,mainFrame,_->
                    if(mainFrame && origin.host=="editor.devtools.invalid")runCatching {
                        val data=message.data?:return@runCatching
                        require(data.length<=1600000)
                        val result=JSONObject(data)
                        lastSource=result.getString("source")
                        callback(lastSource,if(result.isNull("error"))null else result.getString("error"))
                    }
                }
            } else callback(initial,"Update Android System WebView to use the script editor.")
            webViewClient=object:WebViewClient(){
                override fun onRenderProcessGone(view:WebView,detail:android.webkit.RenderProcessGoneDetail):Boolean {
                    (view.parent as? android.view.ViewGroup)?.removeView(view)
                    view.destroy()
                    callback(lastSource,"Restarting editor after a renderer failure…")
                    generation++
                    return true
                }
                override fun shouldOverrideUrlLoading(view:WebView,request:android.webkit.WebResourceRequest)=true
                override fun onPageFinished(view:WebView,url:String?){view.evaluateJavascript("DevToolsEditor.init("+JSONObject.quote(lastSource)+","+JSONObject.quote(kind)+","+options+")",null)}
            }
            loadDataWithBaseURL("https://editor.devtools.invalid/",activity.assets.open("editor/index.html").bufferedReader().use{it.readText()},"text/html","UTF-8",null)
        }},modifier=Modifier.fillMaxWidth().height(330.dp),onRelease={if(webView===it)webView=null;it.destroy()})}
    }
    if(settings)AlertDialog(onDismissRequest={settings=false},title={Text("Editor settings")},text={Column(Modifier.verticalScroll(rememberScrollState())){
        Text("Theme")
        FlowRow {listOf("light","dark").forEach {theme->FilterChip(options.optString("theme")==theme,{option("theme",theme)},label={Text(theme)})}}
        listOf("invisibles" to "Show invisible characters","matchBrackets" to "Highlight matching brackets","closeBrackets" to "Automatically close brackets","wrap" to "Wrap long lines","singleQuote" to "Format with single quotes","semicolons" to "Format with semicolons").forEach {(key,label)->Row{Text(label,Modifier.weight(1f));Switch(options.optBoolean(key,key=="semicolons"),{option(key,it)})}}
        Text("Indentation")
        FlowRow {listOf("spaces","tabs").forEach {value->FilterChip(options.optString("indent","spaces")==value,{option("indent",value)},label={Text(value)})};listOf(2,4).forEach {size->FilterChip(options.optInt("indentSize",2)==size,{option("indentSize",size)},label={Text(size.toString())})}}
        Text("Font size")
        Slider(options.optInt("fontSize",14).toFloat(),{option("fontSize",it.toInt())},valueRange=12f..24f,steps=11)
        Text("Completions include declarations in the current script above the cursor. Format applies indentation, quotes and semicolon preferences.")
    }},confirmButton={TextButton(onClick={settings=false}){Text("Done")}})
}
