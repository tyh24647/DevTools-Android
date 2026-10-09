package com.tyh24647.devtools
import android.database.Cursor
import android.database.MatrixCursor
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract.Document
import android.provider.DocumentsContract.Root
import android.provider.DocumentsProvider
import java.io.File

class MediaDocumentsProvider:DocumentsProvider() {
    private val library get()=MediaLibrary.get(context!!)
    override fun onCreate()=true
    private fun file(id:String)=library.path(if(id=="root") "" else id.removePrefix("media:"))
    private fun id(file:File)=if(file==library.root)"root" else "media:"+file.relativeTo(library.root).path
    override fun queryRoots(projection:Array<out String>?):Cursor {
        val columns=projection?:arrayOf(Root.COLUMN_ROOT_ID,Root.COLUMN_DOCUMENT_ID,Root.COLUMN_TITLE,Root.COLUMN_FLAGS,Root.COLUMN_MIME_TYPES,Root.COLUMN_AVAILABLE_BYTES)
        val cursor=MatrixCursor(columns)
        val row=cursor.newRow()
        for(column in columns)row.add(column,when(column) {
            Root.COLUMN_ROOT_ID->"media";Root.COLUMN_DOCUMENT_ID->"root";Root.COLUMN_TITLE->"DevTools media"
            Root.COLUMN_FLAGS->Root.FLAG_SUPPORTS_CREATE or Root.FLAG_SUPPORTS_IS_CHILD
            Root.COLUMN_MIME_TYPES->"*/*";Root.COLUMN_AVAILABLE_BYTES->library.root.usableSpace;else->null
        })
        return cursor
    }
    private fun row(cursor:MatrixCursor,columns:Array<out String>,file:File) {
        val row=cursor.newRow()
        for(column in columns)row.add(column,when(column) {
            Document.COLUMN_DOCUMENT_ID->id(file);Document.COLUMN_DISPLAY_NAME->if(file==library.root)"DevTools media" else file.name
            Document.COLUMN_MIME_TYPE->if(file.isDirectory)Document.MIME_TYPE_DIR else MediaLibrary.mime(file)
            Document.COLUMN_SIZE->file.length();Document.COLUMN_LAST_MODIFIED->file.lastModified()
            Document.COLUMN_FLAGS->if(file.isDirectory)Document.FLAG_DIR_SUPPORTS_CREATE or (if(file==library.root)0 else Document.FLAG_SUPPORTS_DELETE) else Document.FLAG_SUPPORTS_WRITE or Document.FLAG_SUPPORTS_DELETE
            else->null
        })
    }
    private val defaultColumns=arrayOf(Document.COLUMN_DOCUMENT_ID,Document.COLUMN_DISPLAY_NAME,Document.COLUMN_MIME_TYPE,Document.COLUMN_FLAGS,Document.COLUMN_SIZE,Document.COLUMN_LAST_MODIFIED)
    override fun queryDocument(documentId:String,projection:Array<out String>?):Cursor {
        val columns=projection?:defaultColumns;return MatrixCursor(columns).apply {row(this,columns,file(documentId))}
    }
    override fun queryChildDocuments(parentDocumentId:String,projection:Array<out String>?,sortOrder:String?):Cursor {
        val columns=projection?:defaultColumns;return MatrixCursor(columns).apply {file(parentDocumentId).listFiles()?.filter {!it.name.endsWith(".part")}?.forEach {row(this,columns,it)}}
    }
    override fun openDocument(documentId:String,mode:String,signal:CancellationSignal?):ParcelFileDescriptor = ParcelFileDescriptor.open(file(documentId),ParcelFileDescriptor.parseMode(mode))
    override fun createDocument(parentDocumentId:String,mimeType:String,displayName:String):String {
        require(displayName.isNotBlank() && displayName !in listOf(".","..") && '/' !in displayName && '\\' !in displayName)
        val result=File(file(parentDocumentId),displayName);require(!result.exists())
        if(mimeType==Document.MIME_TYPE_DIR)check(result.mkdir())else check(result.createNewFile())
        return id(result)
    }
    override fun deleteDocument(documentId:String){require(documentId!="root");library.delete(file(documentId))}
    override fun isChildDocument(parentDocumentId:String,documentId:String)=file(documentId).canonicalPath.startsWith(file(parentDocumentId).canonicalPath+File.separator)
}
