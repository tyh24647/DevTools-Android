package com.tyh24647.devtools
import android.content.Intent
import android.media.MediaExtractor
import android.media.MediaFormat
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import org.junit.Test
import org.junit.Assert.*
class MediaWorkflowTest {
    @Test fun savesVodAndFinalizesLiveRecording() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val context=instrumentation.targetContext
        instrumentation.startActivitySync(Intent(context,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        val library=MediaLibrary.get(context)
        val previousFolder=library.defaultFolder
        val previousTree=library.exportTree
        val testFolder=".media-workflow-test-"+System.currentTimeMillis()
        instrumentation.runOnMainSync {library.folder("",testFolder);library.defaultFolder=testFolder;library.exportTree=null}
        fun waitFor(task:MediaTask) {
            runBlocking {withTimeout(90000){while(!task.done)delay(100)}}
            assertNull(task.error,task.error)
            assertNotNull(task.output)
            val extractor=MediaExtractor()
            try {
                extractor.setDataSource(task.output!!.path)
                assertTrue("MP4 must contain media tracks",extractor.trackCount>0)
                assertTrue((0 until extractor.trackCount).any {extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("video/")==true})
                assertTrue(task.output!!.length()>1000)
                val metadata=android.media.MediaMetadataRetriever()
                try {
                    metadata.setDataSource(task.output!!.path)
                    assertTrue("Final MP4 duration must contain recorded samples",metadata.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)!!.toLong()>5000)
                } finally {metadata.release()}
            } finally {extractor.release()}
        }
        try {
            val vod=PageMedia("http://127.0.0.1:18880/vod.m3u8","http://127.0.0.1:18880/","fixture","VOD")
            val choices=runBlocking {library.choices(vod)}
            assertFalse(choices.first().live)
            var task:MediaTask?=null
            instrumentation.runOnMainSync {task=library.start(vod,choices.first(),"vod")}
            waitFor(task!!)
            val live=vod.copy(url="http://127.0.0.1:18880/live.m3u8",title="Live")
            val liveChoice=runBlocking {library.choices(live)}.first()
            assertTrue(liveChoice.live)
            instrumentation.runOnMainSync {task=library.start(live,liveChoice,"live")}
            runBlocking {withTimeout(45000){while(task!!.bytes==0L && !task!!.done)delay(100)}}
            instrumentation.runOnMainSync {library.leave("fixture")}
            waitFor(task!!)
        } finally {
            instrumentation.runOnMainSync {
                library.tasks.filter {it.folder==testFolder}.forEach {it.job?.cancel()}
                library.defaultFolder=previousFolder;library.exportTree=previousTree
                library.path(testFolder).deleteRecursively();library.revision++
                library.tasks.removeAll {it.folder==testFolder}
            }
        }
    }
}
