package com.tyh24647.devtools

import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.test.platform.app.InstrumentationRegistry
import androidx.webkit.WebViewCompat
import org.json.JSONObject
import org.junit.Test
import org.junit.Assert.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class DOMExplorerDeviceTest {
    @Test fun expandsTreeAndSelectsPageNodeWithErudaThree() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val context=instrumentation.targetContext
        val source=listOf("tools/vendor/eruda.js","tools/vendor/dom.js","tools/source-formatting.js","tools/page-runtime.js").joinToString("\n") { context.assets.open(it).bufferedReader().use { it.readText() } }
        val latch=CountDownLatch(1)
        var result:JSONObject?=null
        var view:WebView?=null
        instrumentation.runOnMainSync {
            view=WebView(context).apply {
                settings.javaScriptEnabled=true;settings.blockNetworkLoads=true
                WebViewCompat.addWebMessageListener(this,"testBridge",setOf("https://dom.fixture.test")){_,message,_,_,_->result=JSONObject(message.data!!);latch.countDown()}
                webViewClient=object:WebViewClient(){override fun onPageFinished(webView:WebView,url:String?){
                    webView.evaluateJavascript("globalThis.__DTExpectedURL=location.href;\n"+source+"\n"+"""
                        (async()=>{try {
                            await __DevToolsRuntime.apply({pro:true,console:{dom:true,displaySize:55,transparency:0.98,theme:'Dark'}},{run:true},'show');
                            const eruda=__DTAssets.eruda;eruda.show('dom');eruda.show();
                            eruda.get('dom').select(document.querySelector('#target'));
                            testBridge.postMessage(JSON.stringify({selected:eruda.get('dom')._selectedEl.id,rows:eruda._shadowRoot.querySelectorAll('.eruda-dom-tree .eruda-tree-item').length}));
                        }catch(error){testBridge.postMessage(JSON.stringify({error:error.message}));}})();
                    """.trimIndent(),null)
                }}
                loadDataWithBaseURL("https://dom.fixture.test/","<!doctype html><main><h1 id='target'>DOM test</h1><p>Content</p></main>","text/html","UTF-8",null)
            }
        }
        try {
            assertTrue("DOM explorer must finish rendering",latch.await(30,TimeUnit.SECONDS))
            assertFalse(result.toString(),result!!.has("error"))
            assertEquals("target",result!!.getString("selected"))
            assertTrue(result!!.getInt("rows")>3)
        }finally {instrumentation.runOnMainSync{view?.destroy()}}
    }
}
