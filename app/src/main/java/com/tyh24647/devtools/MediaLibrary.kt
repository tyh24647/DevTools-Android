package com.tyh24647.devtools

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.webkit.CookieManager
import androidx.compose.runtime.*
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.nio.ByteBuffer
import java.util.UUID
import kotlinx.coroutines.*
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

data class PageMedia(val url: String, val page: String, val tab: String, val title: String, val hls: Boolean = false, val mime: String = "")
data class MediaChoice(val variant: HlsVariant?, val audio: String?, val live: Boolean)
class MediaTask(val media: PageMedia, val choice: MediaChoice, val folder: String, val name: String) {
    val id=UUID.randomUUID().toString()
    var status by mutableStateOf("Queued")
    var progress by mutableStateOf<Float?>(null)
    var bytes by mutableStateOf(0L)
    var output by mutableStateOf<File?>(null)
    var error by mutableStateOf<String?>(null)
    var done by mutableStateOf(false)
    @Volatile var stopRequested=false
    var job: Job?=null
}
class MediaLibrary private constructor(val context: Context) {
    val root=File(context.filesDir,"media").apply {mkdirs()}
    val tasks=mutableStateListOf<MediaTask>()
    val detected=mutableStateListOf<PageMedia>()
    var revision by mutableStateOf(0)
    val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
    val preferences=context.getSharedPreferences("media-library",0)
    var showQuality by mutableStateOf(preferences.getBoolean("quality",false))
    var showHidden by mutableStateOf(preferences.getBoolean("hidden",false))
    var defaultFolder by mutableStateOf(preferences.getString("folder","").orEmpty())
    var exportTree by mutableStateOf(preferences.getString("tree",null))
    var pending by mutableStateOf<PageMedia?>(null)
    fun settings() { preferences.edit().putBoolean("quality",showQuality).putBoolean("hidden",showHidden).putString("folder",defaultFolder).putString("tree",exportTree).apply() }
    fun path(relative:String):File {
        val file=File(root,relative).canonicalFile
        require(file==root.canonicalFile || file.path.startsWith(root.canonicalPath+File.separator)) { "Invalid library path." }
        return file
    }
    fun folder(relative:String,name:String) { require(name.isNotBlank() && name !in listOf(".","..") && '/' !in name && '\\' !in name);check(File(path(relative),name).mkdir());revision++ }
    fun createFile(relative:String,name:String) {
        require(name.isNotBlank() && name !in listOf(".","..") && '/' !in name && name.none {it.code==92}) {"Enter a file name without path separators."}
        check(File(path(relative),name).createNewFile()) {"A file with this name already exists."}
        revision++
    }
    fun delete(file:File) {
        val relative=file.relativeTo(root).path
        require(tasks.none {!it.done && (it.folder==relative || it.folder.startsWith(relative+"/"))}) {"A download is using this folder."}
        check(path(relative).deleteRecursively());if(!path(defaultFolder).exists()){defaultFolder="";settings()};revision++
    }
    fun discover(media:PageMedia) {if(WebURLs.normalize(media.url)!=null && detected.none {it.url==media.url && it.tab==media.tab}) {if(detected.size>=200)detected.removeAt(0);detected.add(media)}}
    fun leave(tab:String) {tasks.filter {it.media.tab==tab && it.choice.live && !it.done}.forEach {it.stopRequested=true};detected.removeAll {it.tab==tab}}
    fun unloaded(tab:String,url:String) {tasks.filter {it.media.tab==tab && it.media.url==url && it.choice.live}.forEach {it.stopRequested=true}}
    suspend fun choices(media:PageMedia):List<MediaChoice> = withContext(Dispatchers.IO) {
        if(!media.hls && !media.url.substringBefore('?').endsWith(".m3u8",true)) return@withContext listOf(MediaChoice(null,null,false))
        val first=playlist(media.url,media)
        if(first.variants.isEmpty()) return@withContext listOf(MediaChoice(null,null,!first.ended))
        first.variants.sortedWith(compareByDescending<HlsVariant> { val parts=it.resolution.split("x"); (parts.getOrNull(0)?.toLongOrNull() ?: 0) * (parts.getOrNull(1)?.toLongOrNull() ?: 0) }.thenByDescending {it.bandwidth}).map {variant->MediaChoice(variant,variant.audioGroup?.let(first.audio::get),!playlist(variant.url,media).ended)}
    }
    fun start(media:PageMedia,choice:MediaChoice,name:String):MediaTask {
        require(tasks.count {!it.done}<2) {"Finish or stop an active transfer first (two-transfer limit)."}
        check(path(defaultFolder).isDirectory)
        val task=MediaTask(media,choice,defaultFolder,safeName(name)); tasks.add(0,task)
        context.startForegroundService(android.content.Intent(context,MediaDownloadService::class.java).putExtra("task",task.id))
        return task
    }
    fun launch(task:MediaTask) {
        if(task.job!=null)return
        task.job=scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val isHls=task.media.hls || task.media.url.substringBefore('?').endsWith(".m3u8",true)
                    val ext=if(isHls) "mp4" else android.webkit.MimeTypeMap.getSingleton().getExtensionFromMimeType(task.media.mime)?.takeIf{it.isNotEmpty()} ?: Uri.parse(task.media.url).lastPathSegment?.substringAfterLast('.', "bin")?.lowercase()?.takeIf {it.matches(Regex("[a-z0-9]{1,8}"))} ?: "bin"
                    var output=File(path(task.folder),task.name.substringBeforeLast('.',task.name)+"."+ext);var index=1
                    while(output.exists()) {output=File(path(task.folder),task.name.substringBeforeLast('.',task.name)+"-"+index+++"."+ext)}
                    val part=File(output.parentFile,"."+output.name+"."+task.id+".part")
                    try {
                        if(isHls) hls(task,part)
                        else {
                            val mime=fetchTo(task.media.url,task.media,part,null) {bytes,total->scope.launch {task.bytes=bytes;task.progress=if(total>0)bytes.toFloat()/total else null;task.status="Downloading"}}
                            if(ext=="bin")android.webkit.MimeTypeMap.getSingleton().getExtensionFromMimeType(mime)?.let {extension->
                                output=File(output.parentFile,output.nameWithoutExtension+"."+extension)
                                while(output.exists())output=File(output.parentFile,task.name+"-"+index+++"."+extension)
                            }
                        }
                        check(part.length()>0) {"The resource was empty."};check(part.renameTo(output)) {"Could not save the completed file."}
                        withContext(Dispatchers.Main) {task.output=output;revision++;task.status="Saved";task.progress=1f}
                        exportTree?.let {tree->try {copyToTree(output,Uri.parse(tree))}catch(error:Exception){withContext(Dispatchers.Main){task.status="Saved in app; export failed: "+error.message}}}
                    } finally {part.delete()}
                }
            } catch(error:CancellationException) {task.status="Cancelled"}
            catch(error:Exception) {task.error=error.message;task.status="Failed"}
            finally {task.done=true;if(tasks.none {!it.done})context.stopService(android.content.Intent(context,MediaDownloadService::class.java))}
        }
    }
    fun cancel(task:MediaTask) {if(task.choice.live) {task.stopRequested=true;task.status="Finalizing recording"} else task.job?.cancel()}
    private fun safeName(name:String)=name.trim().replace(Regex("[/\\\\\\p{Cntrl}]"),"_").take(120).ifBlank {"media-"+System.currentTimeMillis()}
    private fun connection(url:String,media:PageMedia,range:HlsRange?):HttpURLConnection {
        require(WebURLs.normalize(url)!=null) {"Unsupported resource URL."}
        val connection=URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout=15000;connection.readTimeout=15000;connection.instanceFollowRedirects=false
        connection.setRequestProperty("User-Agent","Mozilla/5.0 (Android) DevTools/1.0")
        connection.setRequestProperty("Referer",media.page)
        CookieManager.getInstance().getCookie(url)?.let {connection.setRequestProperty("Cookie",it)}
        range?.let {connection.setRequestProperty("Range","bytes="+it.offset+"-"+(it.offset+it.length-1))}
        return connection
    }
    private val resolvedUrls=java.util.concurrent.ConcurrentHashMap<String,String>()
    private fun response(url:String,media:PageMedia,range:HlsRange?,redirect:Int=0):HttpURLConnection {
        require(redirect<6) {"Too many media redirects."}
        val conn=connection(url,media,range)
        if(conn.responseCode in 300..399) {
            val target=java.net.URI(url).resolve(conn.getHeaderField("Location") ?: error("Missing redirect URL.")).toString()
            conn.disconnect();val resolved=response(target,media,range,redirect+1);resolvedUrls[url]=resolved.url.toString();return resolved
        }
        if(conn.responseCode !in 200..299) {val status=conn.responseCode;conn.disconnect();error("Media server returned HTTP "+status)}
        if(range!=null) require(conn.responseCode==206) {"Server ignored the requested HLS byte range."}
        resolvedUrls[url]=conn.url.toString()
        return conn
    }
    private fun read(url:String,media:PageMedia,limit:Int=2*1024*1024,range:HlsRange?=null):ByteArray {
        val conn=response(url,media,range)
        try {return conn.inputStream.use {input->
            val output=java.io.ByteArrayOutputStream();val buffer=ByteArray(8192)
            while(true) {val count=input.read(buffer);if(count<0)break;require(output.size()+count<=limit) {"Media metadata is too large."};output.write(buffer,0,count)}
            output.toByteArray()
        }} finally {conn.disconnect()}
    }
    private fun playlist(url:String,media:PageMedia):HlsPlaylist {
        val text=read(url,media).toString(Charsets.UTF_8)
        return HlsPlaylist.parse(text,resolvedUrls[url] ?: url)
    }
    private suspend fun fetchTo(url:String,media:PageMedia,file:File,range:HlsRange?,progress:(Long,Long)->Unit={_,_->}):String {
        val conn=response(url,media,range)
        try {conn.inputStream.use {input->file.outputStream().use {output->
            val buffer=ByteArray(64*1024);var count=0L;var last=0L
            while(true) {currentCoroutineContext().ensureActive();val size=input.read(buffer);if(size<0)break;output.write(buffer,0,size);count+=size
                if(count-last>512*1024){progress(count,conn.contentLengthLong);last=count}}
            progress(count,conn.contentLengthLong)
        }};return conn.contentType.orEmpty().substringBefore(';').trim().lowercase()}finally {conn.disconnect()}
    }
    private suspend fun segment(segment:HlsSegment,media:PageMedia,file:File) {
        fetchTo(segment.url,media,file,segment.range)
        segment.key?.let {key->
            val bytes=read(key.url,media,32);require(bytes.size==16) {"Invalid AES-128 key."}
            val iv=if(key.iv!=null) {
                val hex=key.iv.lowercase().removePrefix("0x").padStart(32,'0');require(hex.length==32)
                ByteArray(16){hex.substring(it*2,it*2+2).toInt(16).toByte()}
            } else ByteArray(16).also {java.nio.ByteBuffer.wrap(it).putLong(8,segment.sequence)}
            val cipher=Cipher.getInstance("AES/CBC/PKCS5Padding");cipher.init(Cipher.DECRYPT_MODE,SecretKeySpec(bytes,"AES"),IvParameterSpec(iv))
            val encrypted=File(file.parentFile,file.name+".encrypted");check(file.renameTo(encrypted))
            try {javax.crypto.CipherInputStream(encrypted.inputStream(),cipher).use {input->file.outputStream().use {input.copyTo(it)}}}finally {encrypted.delete()}
        }
        segment.init?.let {(url,range)->
            require(segment.key==null) {"Encrypted fragmented MP4 is unsupported."}
            val temp=File(file.parentFile,file.name+".fragment");check(file.renameTo(temp))
            try {file.outputStream().use {out->out.write(read(url,media,4*1024*1024,range));temp.inputStream().use {it.copyTo(out)}}}finally{temp.delete()}
        }
    }
    private suspend fun hls(task:MediaTask,output:File) {
        val urls=listOfNotNull(task.choice.variant?.url?:task.media.url,task.choice.audio)
        val temp=File(context.cacheDir,"media-"+task.id).apply {mkdirs()}
        val muxer=MediaMuxer(output.path,MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var started=false;var sampleCount=0L
        val tracks=mutableMapOf<Pair<Int,Int>,Int>();val mimes=mutableMapOf<Pair<Int,Int>,String>();val dimensions=mutableMapOf<Pair<Int,Int>,Pair<Int,Int>>()
        val seen=urls.map {mutableSetOf<Long>()};val offsets=LongArray(urls.size);val lastTimes=mutableMapOf<Int,Long>()
        val buffer=ByteBuffer.allocateDirect(8*1024*1024);var captured=0.0
        fun extractor(file:File)=MediaExtractor().apply {setDataSource(file.path)}
        fun addTracks(stream:Int,file:File) {
            val ex=extractor(file)
            try {for(i in 0 until ex.trackCount) {
                val format=ex.getTrackFormat(i);val mime=format.getString(MediaFormat.KEY_MIME).orEmpty()
                if(mime.startsWith("audio/")||mime.startsWith("video/")){tracks[stream to i]=muxer.addTrack(format);mimes[stream to i]=mime;if(mime.startsWith("video/"))dimensions[stream to i]=format.getInteger(MediaFormat.KEY_WIDTH) to format.getInteger(MediaFormat.KEY_HEIGHT)}
            }}finally {ex.release()}
        }
        fun append(stream:Int,file:File,duration:Double) {
            val ex=extractor(file)
            try {
                val keys=tracks.keys.filter {it.first==stream};keys.forEach {(s,index)->
                    require(ex.getTrackFormat(index).getString(MediaFormat.KEY_MIME)==mimes[s to index]) {"The stream changed codec during recording."}
                    dimensions[s to index]?.let {expected->
                        val format=ex.getTrackFormat(index)
                        require(expected==(format.getInteger(MediaFormat.KEY_WIDTH) to format.getInteger(MediaFormat.KEY_HEIGHT))) {"The stream changed video dimensions."}
                    }
                    ex.selectTrack(index)}
                var first=-1L;var maxTime=0L
                while(ex.sampleTrackIndex>=0) {
                    val outTrack=tracks[stream to ex.sampleTrackIndex] ?: error("Unexpected media track.")
                    val size=ex.readSampleData(buffer,0);if(size<0)break
                    require(ex.sampleFlags and MediaExtractor.SAMPLE_FLAG_ENCRYPTED==0) {"Encrypted samples are unsupported."}
                    if(first<0)first=ex.sampleTime
                    val time=(ex.sampleTime-first+offsets[stream]).coerceAtLeast((lastTimes[outTrack]?:-1L)+1)
                    val info=MediaCodec.BufferInfo().apply {set(0,size,time,if(ex.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0)MediaCodec.BUFFER_FLAG_KEY_FRAME else 0)}
                    muxer.writeSampleData(outTrack,buffer,info);lastTimes[outTrack]=time;maxTime=maxOf(maxTime,time);sampleCount++
                    if(!ex.advance())break
                }
                offsets[stream]=maxOf(offsets[stream]+(duration*1_000_000).toLong(),maxTime+1)
            }finally {ex.release()}
        }
        try {
            val initial=urls.map {playlist(it,task.media)}
            require(initial.all {it.segments.isNotEmpty()}) {"No media segments are available yet."}
            val firstFiles=initial.mapIndexed {stream,list->
                val seg=if(task.choice.live)list.segments.takeLast(3).first() else list.segments.first()
                val file=File(temp,"first-"+stream);segment(seg,task.media,file);addTracks(stream,file);Triple(seg,file,stream)}
            require(tracks.isNotEmpty()) {"Android cannot extract this stream's codecs."}
            muxer.start();started=true
            for((seg,file,stream) in firstFiles){
                append(stream,file,seg.duration);seen[stream].add(seg.sequence)
                val size=file.length();file.delete();if(stream==0)captured+=seg.duration
                withContext(Dispatchers.Main){task.bytes+=size;task.status=if(task.choice.live)"Recording · "+captured.toInt()+" seconds" else "Downloading segments"}
            }
            var lists=initial;var idle=0
            while(true) {
                currentCoroutineContext().ensureActive();var added=false
                for((stream,list) in lists.withIndex()) {
                    val candidates=if(task.choice.live && seen[stream].size==1)list.segments.takeLast(3) else list.segments
                    for(seg in candidates.filter {!seen[stream].contains(it.sequence) || (task.choice.live && it.sequence <= (seen[stream].maxOrNull() ?: -1L))}) {
                        if(task.stopRequested && task.choice.live)break
                        if(task.choice.live) require(seg.sequence <= (seen[stream].maxOrNull() ?: seg.sequence)+1) {"A live segment expired before it could be recorded."}
                        val file=File(temp,"segment-"+stream)
                        try {segment(seg,task.media,file)} catch(error:Exception) {if(task.stopRequested && task.choice.live)break;throw error}
                        append(stream,file,seg.duration)
                        seen[stream].add(seg.sequence);val size=file.length();file.delete();added=true
                        if(stream==0)captured+=seg.duration
                        withContext(Dispatchers.Main) {task.bytes+=size;task.status=if(task.choice.live)"Recording · "+captured.toInt()+" seconds" else "Downloading segments"
                            task.progress=if(task.choice.live)null else seen[0].size.toFloat()/lists[0].segments.size}
                    }
                }
                if(!task.choice.live || task.stopRequested || lists.all {it.ended})break
                idle=if(added)0 else idle+1;require(idle<30) {"The live stream stopped publishing segments."}
                delay((lists[0].targetDuration*500).toLong().coerceIn(1000,10000));if(task.stopRequested)break;lists=urls.map {playlist(it,task.media)}
            }
            require(sampleCount>0) {"No supported media samples were found."}
            withContext(Dispatchers.Main){task.status="Finalizing MP4";task.progress=null}
            muxer.stop();started=false
        }finally {if(started)runCatching {muxer.stop()};muxer.release();temp.deleteRecursively()}
    }
    fun copyToTree(file:File,tree:Uri):Uri {
        val rootDoc=android.provider.DocumentsContract.buildDocumentUriUsingTree(tree,android.provider.DocumentsContract.getTreeDocumentId(tree))
        val uri=android.provider.DocumentsContract.createDocument(context.contentResolver,rootDoc,mime(file),file.name) ?: error("Destination unavailable.")
        try {context.contentResolver.openOutputStream(uri)?.use {out->file.inputStream().use {it.copyTo(out)}} ?: error("Destination could not be opened.")}
        catch(error:Exception){runCatching {android.provider.DocumentsContract.deleteDocument(context.contentResolver,uri)};throw error}
        return uri
    }
    companion object {
        @Volatile private var instance:MediaLibrary?=null
        fun get(context:Context):MediaLibrary=instance?:synchronized(this){instance?:MediaLibrary(context.applicationContext).also {instance=it}}
        fun mime(file:File)=android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension.lowercase()) ?: "application/octet-stream"
    }
}
