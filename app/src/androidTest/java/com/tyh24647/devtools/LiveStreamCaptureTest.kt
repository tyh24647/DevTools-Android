package com.tyh24647.devtools

import android.content.Intent
import android.media.MediaMetadataRetriever
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Opt-in device smoke test: pass -e streamUrl <public HLS URL>. Keeps the recording. */
class LiveStreamCaptureTest {
    @Test fun recordsAndFinalizesPublicLiveStream() {
        val url=InstrumentationRegistry.getArguments().getString("streamUrl").orEmpty()
        assumeTrue("A live stream URL must be supplied explicitly",url.isNotBlank())
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val context=instrumentation.targetContext
        instrumentation.startActivitySync(Intent(context,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        val library=MediaLibrary.get(context)
        val media=PageMedia(url,"https://kick.com/chudlogic","live-smoke","Kick livestream proof",hls=true)
        val choices=runBlocking {library.choices(media)}
        val choice=choices.firstOrNull {
            val height=it.variant?.resolution?.substringAfter('x')?.toIntOrNull()
            height!=null && height<=720
        } ?: choices.first()
        assertTrue("Expected a currently live playlist",choice.live)
        val folder=library.defaultFolder
        val tree=library.exportTree
        var task:MediaTask?=null
        try {
            instrumentation.runOnMainSync {
                library.defaultFolder="";library.exportTree=null
                task=library.start(media,choice,"kick-chudlogic-live-proof")
            }
            runBlocking {
                withTimeout(60000) {while(task!!.bytes==0L && !task!!.done)delay(100)}
                assertFalse(task!!.error,task!!.done)
                delay(20000)
            }
            instrumentation.runOnMainSync {library.cancel(task!!)}
            runBlocking {withTimeout(60000) {while(!task!!.done)delay(100)}}
            assertNull(task!!.error,task!!.error)
            val output=requireNotNull(task!!.output)
            val metadata=MediaMetadataRetriever()
            try {
                metadata.setDataSource(output.path)
                val duration=metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)!!.toLong()
                assertTrue("Recording must have at least 10 seconds",duration>=10000)
                assertTrue("Playlist refresh must not repeat old segments",duration<40000)
                assertEquals("yes",metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO))
                assertEquals("yes",metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO))
                println("LIVE_CAPTURE file=${output.name} durationMs=$duration bytes=${output.length()}")
            } finally {metadata.release()}
        } finally {
            instrumentation.runOnMainSync {
                task?.takeIf {!it.done}?.let {library.cancel(it)}
                library.defaultFolder=folder;library.exportTree=tree
            }
        }
    }
}
