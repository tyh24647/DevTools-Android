package com.tyh24647.devtools
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.test.platform.app.InstrumentationRegistry
import androidx.webkit.WebViewCompat
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.json.JSONObject
import org.junit.Test
import org.junit.Assert.*
class ScriptEditorDeviceTest {
    @Test fun bundledEditorValidatesAndPublishesSourceInDeviceWebView() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val context=instrumentation.targetContext
        val latch=CountDownLatch(1)
        var result:JSONObject?=null
        var view:WebView?=null
        instrumentation.runOnMainSync {
            view=WebView(context).apply {
                settings.javaScriptEnabled=true;settings.allowFileAccess=false;settings.allowContentAccess=false;settings.blockNetworkLoads=true
                WebViewCompat.addWebMessageListener(this,"editorBridge",setOf("https://editor.devtools.invalid")){_,message,_,_,_->result=JSONObject(message.data!!);latch.countDown()}
                webViewClient=object:WebViewClient(){override fun onPageFinished(webView:WebView,url:String?){
                    webView.evaluateJavascript("DevToolsEditor.init('return {name: \"demo\", init(){}};', 'eruda', {theme: \"dark\"})",null)
                }}
                loadDataWithBaseURL("https://editor.devtools.invalid/",context.assets.open("editor/index.html").bufferedReader().use{it.readText()},"text/html","UTF-8",null)
            }
        }
        try {
            assertTrue("Editor must initialize and publish its parsed source",latch.await(30,TimeUnit.SECONDS))
            assertTrue(result!!.isNull("error"))
            assertTrue(result!!.getString("source").contains("demo"))
        }finally{instrumentation.runOnMainSync{view?.destroy()}}
    }
}
