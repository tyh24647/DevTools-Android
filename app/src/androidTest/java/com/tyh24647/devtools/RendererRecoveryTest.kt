package com.tyh24647.devtools
import android.content.Intent
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.filters.SdkSuppress
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.json.JSONTokener
import org.junit.Test
import org.junit.Assert.*
class RendererRecoveryTest {
    @Test @SdkSuppress(minSdkVersion=29)
    fun editorPreservesDraftAfterSharedRendererTermination() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val activity=instrumentation.startActivitySync(Intent(instrumentation.targetContext,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
        val code="return {name:'recovery-test',init(){}};"
        fun views(view:View):List<WebView> = if(view is WebView)listOf(view) else if(view is ViewGroup)(0 until view.childCount).flatMap{views(view.getChildAt(it))} else emptyList()
        fun snapshot(view:WebView):String? {
            val latch=CountDownLatch(1);var value:String?=null
            instrumentation.runOnMainSync{view.evaluateJavascript("document.querySelector('.cm-editor')?.getBoundingClientRect().height > 100 ? globalThis.DevToolsEditor?.snapshot()?.source : null"){result->value=JSONTokener(result).nextValue() as? String;latch.countDown()}}
            latch.await(3,TimeUnit.SECONDS);return value
        }
        fun waitEditor(exclude:WebView?=null):WebView {
            val deadline=System.currentTimeMillis()+30000
            while(System.currentTimeMillis()<deadline) {
                var candidates=emptyList<WebView>()
                instrumentation.runOnMainSync{candidates=android.view.inspector.WindowInspector.getGlobalWindowViews().flatMap{views(it)}.filter{it!==exclude}}
                for(view in candidates)if(snapshot(view)==code)return view
                Thread.sleep(100)
            }
            error("Editor did not load the preserved draft.")
        }
        instrumentation.runOnMainSync{activity.pendingScript=ScriptDraft("Recovery test",code,"eruda");activity.screen="Plugins"}
        try {
            val editor=waitEditor()
            instrumentation.runOnMainSync{assertNotNull(editor.webViewRenderProcess);assertTrue(editor.webViewRenderProcess!!.terminate())}
            val recovered=waitEditor(editor)
            assertNotSame(editor,recovered)
            assertFalse(activity.isFinishing)
        }finally{instrumentation.runOnMainSync{activity.onBackPressedDispatcher.onBackPressed();activity.pendingScript=null;activity.screen="Browsers"}}
    }
}
