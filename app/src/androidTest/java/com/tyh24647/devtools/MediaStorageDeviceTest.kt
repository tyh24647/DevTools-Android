package com.tyh24647.devtools

import android.content.Intent
import android.provider.DocumentsContract
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.net.ServerSocket
import kotlinx.coroutines.*

class MediaStorageDeviceTest {
    @Test fun foldersAndFilesAreAccessibleThroughDocumentsProvider() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val context=instrumentation.targetContext
        val library=MediaLibrary.get(context)
        val name="storage-test-"+System.currentTimeMillis()
        val folder=File(library.root,name)
        folder.mkdirs()
        File(folder,"saved.txt").writeText("Saved media fixture")
        try {
            // A directory listing must round-trip through the same path used by the UI.
            val listed=library.path("").listFiles()!!.first {it.name==name}
            val relative=listed.relativeTo(library.root).path
            assertEquals(name,relative)
            assertEquals(folder.canonicalFile,library.path(relative))
            val authority=context.packageName+".media-documents"
            val resolver=context.contentResolver
            val roots=DocumentsContract.buildRootsUri(authority)
            resolver.query(roots,null,null,null,null)!!.use {
                assertTrue(it.moveToFirst())
                assertEquals("root",it.getString(it.getColumnIndexOrThrow(DocumentsContract.Root.COLUMN_DOCUMENT_ID)))
            }
            val children=DocumentsContract.buildChildDocumentsUri(authority,"media:$name")
            resolver.query(children,null,null,null,null)!!.use {
                assertTrue(it.moveToFirst())
                val id=it.getString(it.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID))
                assertEquals("media:$name/saved.txt",id)
                val uri=DocumentsContract.buildDocumentUri(authority,id)
                assertEquals("Saved media fixture",resolver.openInputStream(uri)!!.bufferedReader().use {reader->reader.readText()})
            }
        } finally {folder.deleteRecursively()}
    }

    @Test fun htmlResponseFailsInsteadOfSavingAWebpage() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val context=instrumentation.targetContext
        instrumentation.startActivitySync(Intent(context,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        val library=MediaLibrary.get(context)
        val server=ServerSocket(0)
        val body="<!doctype html><title>Login required</title>".toByteArray()
        val serverJob=CoroutineScope(Dispatchers.IO).launch {
            server.accept().use {socket->
                val input=socket.getInputStream().bufferedReader()
                while(!input.readLine().isNullOrEmpty()) {}
                socket.getOutputStream().apply {
                    write("HTTP/1.1 200 OK\r\nContent-Type: text/html\r\nContent-Length: ${body.size}\r\nConnection: close\r\n\r\n".toByteArray())
                    write(body);flush()
                }
            }
        }
        var task:MediaTask?=null
        val previousFolder=library.defaultFolder
        try {
            val url="http://127.0.0.1:${server.localPort}/video"
            instrumentation.runOnMainSync {
                library.defaultFolder=""
                task=library.start(PageMedia(url,url,"fixture","Video"),MediaChoice(null,null,false),"html-response-test")
            }
            runBlocking {withTimeout(20000){while(!task!!.done)delay(100)}}
            assertEquals("Failed",task!!.status)
            assertTrue(task!!.error.orEmpty().contains("webpage instead of media"))
            assertNull(task!!.output)
        } finally {
            server.close();serverJob.cancel()
            instrumentation.runOnMainSync {library.defaultFolder=previousFolder;task?.let {library.tasks.remove(it)}}
        }
    }
}
