package com.tyh24647.devtools
import android.Manifest
import android.content.Intent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Test
import org.junit.Assert.*
class WebMediaPermissionsTest {
    @Test @androidx.test.filters.SdkSuppress(minSdkVersion=29) fun websiteConsentAllowsRealCameraAndMicrophoneTracks() {
        val i=InstrumentationRegistry.getInstrumentation()
        val packageName=i.targetContext.packageName
        // The test opens each device briefly and stops every track immediately; no recording is saved.
        i.uiAutomation.grantRuntimePermission(packageName,Manifest.permission.CAMERA)
        i.uiAutomation.grantRuntimePermission(packageName,Manifest.permission.RECORD_AUDIO)
        val activity=i.startActivitySync(Intent(i.targetContext,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
        lateinit var tab:BrowserTab
        fun buttons(v:View):List<Button> = if(v is Button)listOf(v) else if(v is ViewGroup)(0 until v.childCount).flatMap{buttons(v.getChildAt(it))} else emptyList()
        i.runOnMainSync {
            tab=activity.browser.newTab();activity.screen="Browser"
            tab.webView.loadDataWithBaseURL("https://media-test.devtools.invalid/","""<!doctype html><script>
            window.result='waiting';navigator.mediaDevices.getUserMedia({audio:true,video:true}).then(s=>{window.result=s.getTracks().map(t=>t.kind).sort().join(',');s.getTracks().forEach(t=>t.stop())}).catch(e=>window.result=e.name+':'+e.message);
            </script>""","text/html","UTF-8",null)
        }
        try {
            val deadline=System.currentTimeMillis()+30000
            var result=""
            while(System.currentTimeMillis()<deadline) {
                val latch=CountDownLatch(1)
                i.runOnMainSync {
                    android.view.inspector.WindowInspector.getGlobalWindowViews().flatMap{buttons(it)}.firstOrNull{it.text.toString().equals("Allow",true)}?.performClick()
                    tab.webView.evaluateJavascript("window.result || ''"){result=it;latch.countDown()}
                }
                latch.await(3,TimeUnit.SECONDS)
                if(result=="\"audio,video\"")break
                Thread.sleep(100)
            }
            assertEquals("Both real capture tracks should start after website consent", "\"audio,video\"",result)
        }finally{i.runOnMainSync{activity.browser.close(tab);activity.screen="Browsers"}}
    }
}
