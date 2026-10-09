package com.tyh24647.devtools

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.DocumentsContract
import android.widget.VideoView
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.Image
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.Alignment
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

fun mediaUri(activity:MainActivity,file:File)=FileProvider.getUriForFile(activity,activity.packageName+".media-files",file)
fun openMedia(activity:MainActivity,file:File,share:Boolean) {
    val uri=mediaUri(activity,file)
    val intent=if(share)Intent(Intent.ACTION_SEND).setType(MediaLibrary.mime(file)).putExtra(Intent.EXTRA_STREAM,uri)
        else Intent(Intent.ACTION_VIEW).setDataAndType(uri,MediaLibrary.mime(file))
    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    intent.clipData=android.content.ClipData.newRawUri(file.name,uri)
    try {activity.startActivity(Intent.createChooser(intent,if(share)"Share resource" else "Open resource"))}
    catch(error:Exception){activity.browser.notice="No app can open this file."}
}

@Composable
fun ResourcesScreen(activity:MainActivity) {
    val library=MediaLibrary.get(activity)
    var options by remember {mutableStateOf(false)}
    var relative by remember {mutableStateOf("")}
    var preview by remember {mutableStateOf<File?>(null)}
    var folderName by remember {mutableStateOf<String?>(null)}
    var delete by remember {mutableStateOf<File?>(null)}
    val revision=library.revision
    val entries=remember(relative,revision,library.showHidden){library.path(relative).listFiles()?.filter {(library.showHidden || !it.name.startsWith(".")) && !it.name.endsWith(".part")}?.sortedWith(compareBy<File>{!it.isDirectory}.thenBy {it.name.lowercase()})?:emptyList()}
    LazyVerticalGrid(columns=GridCells.Adaptive(150.dp),modifier=Modifier.fillMaxSize(),contentPadding=PaddingValues(16.dp),horizontalArrangement=Arrangement.spacedBy(12.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
      item(span={GridItemSpan(maxLineSpan)}) {Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment=Alignment.CenterVertically) {
            Text("Saved resources",Modifier.weight(1f),style=MaterialTheme.typography.headlineMedium)
            IconButton(onClick={options=true}){Icon(Icons.Default.Settings,"Media settings")}
        }
        if(library.tasks.any{!it.done})Text("Active transfers",style=MaterialTheme.typography.titleLarge)
        library.tasks.filter{!it.done}.forEach {task->Card(Modifier.fillMaxWidth()){Column(Modifier.padding(12.dp)){
            Text(task.name);Text(task.status+" · "+(task.bytes/1024)+" KB")
            task.error?.let {Text(it,color=MaterialTheme.colorScheme.error)}
            if(!task.done) {
                if(task.progress!=null)LinearProgressIndicator(progress={task.progress!!},modifier=Modifier.fillMaxWidth())
                else LinearProgressIndicator(modifier=Modifier.fillMaxWidth())
                TextButton(onClick={library.cancel(task)}){Text(if(task.choice.live)"Stop and save recording" else "Cancel")}
            }
            task.output?.let {file->TextButton(onClick={preview=file}){Text("Preview saved file")}}
        }}}
        Text(relative.ifEmpty {"App media"},style=MaterialTheme.typography.titleLarge)
        FlowRow {
            if(relative.isNotEmpty())TextButton(onClick={relative=File(relative).parent.orEmpty()}){Text("Up")}
            TextButton(onClick={folderName=""}){Text("New folder")}
            TextButton(onClick={library.defaultFolder=relative;library.settings()}){Text("Set as default download destination")}
        }
        if(entries.isEmpty())Text("No saved files in this folder. Save resources from Eruda, long-press an image, or use a video's download button.")
      }}
        items(entries,key={it.path}) {file->Card(onClick={if(file.isDirectory)relative=file.relativeTo(library.root).path else preview=file},modifier=Modifier.fillMaxWidth()){Column(Modifier.padding(10.dp)){
            SavedThumbnail(file)
            Text(file.name);if(!file.isDirectory)Text((file.length()/1024).toString()+" KB")
            FlowRow {
                TextButton(onClick={if(file.isDirectory)relative=file.relativeTo(library.root).path else preview=file}){Text(if(file.isDirectory)"Open folder" else "Preview")}
                if(!file.isDirectory) {
                    TextButton(onClick={openMedia(activity,file,true)}){Text("Share")}
                    TextButton(onClick={activity.exportMedia(file)}){Text("Export")}
                    TextButton(onClick={openMedia(activity,file,false)}){Text("Open")}
                }
                TextButton(onClick={delete=file}){Text("Delete")}
            }
        }}}
    }
    folderName?.let {value->AlertDialog(onDismissRequest={folderName=null},title={Text("New folder")},text={OutlinedTextField(value,{folderName=it},label={Text("Folder name")})},
        confirmButton={TextButton(onClick={try{library.folder(relative,value);folderName=null}catch(error:Exception){activity.browser.notice=error.message}}){Text("Create")}},
        dismissButton={TextButton(onClick={folderName=null}){Text("Cancel")}})}
    delete?.let {file->AlertDialog(onDismissRequest={delete=null},title={Text("Delete "+file.name+"?")},text={Text(if(file.isDirectory)"This also deletes the files in this folder." else "Remove this saved file from app storage.")},
        confirmButton={TextButton(onClick={try{library.delete(file);delete=null}catch(error:Exception){activity.browser.notice=error.message}}){Text("Delete")}},
        dismissButton={TextButton(onClick={delete=null}){Text("Cancel")}})}
    if(options)AlertDialog(onDismissRequest={options=false},title={Text("Media settings")},text={Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)){
        Row {Text("Show video quality options",Modifier.weight(1f));Switch(library.showQuality,{library.showQuality=it;library.settings()})}
        Text("With quality options hidden, HLS saves the highest-resolution rendition, then highest bandwidth.")
        Row {Text("Show hidden files",Modifier.weight(1f));Switch(library.showHidden,{library.showHidden=it;library.settings()})}
        Text("Default folder: "+library.defaultFolder.ifEmpty {"App media"})
        FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick={activity.chooseMediaDestination()}){Text("Choose export destination")}
            if(library.exportTree!=null)TextButton(onClick={library.exportTree=null;library.settings()}){Text("App storage only")}
        }
        library.exportTree?.let {Text("Also export completed files to: "+it,style=MaterialTheme.typography.bodySmall)}
        OutlinedButton(onClick={
            val uri=DocumentsContract.buildRootUri(activity.packageName+".media-documents","media")
            try {activity.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri,"vnd.android.document/root"))}
            catch(error:Exception){activity.browser.notice="Open the system Files app and select DevTools media."}
        }){Text("Open in native file browser")}
    }},confirmButton={TextButton(onClick={options=false}){Text("Done")}})
    preview?.let {file->MediaPreview(activity,file){preview=null}}
}

@Composable
private fun MediaPreview(activity:MainActivity,file:File,dismiss:()->Unit) {
    val mime=MediaLibrary.mime(file)
    var bitmap by remember(file){mutableStateOf<android.graphics.Bitmap?>(null)}
    LaunchedEffect(file){if(mime.startsWith("image/"))bitmap=withContext(Dispatchers.IO){
        val bounds=BitmapFactory.Options().apply {inJustDecodeBounds=true};BitmapFactory.decodeFile(file.path,bounds)
        val options=BitmapFactory.Options().apply {inSampleSize=1;while(maxOf(bounds.outWidth,bounds.outHeight)/inSampleSize>1600)inSampleSize*=2}
        BitmapFactory.decodeFile(file.path,options)
    }}
    Dialog(dismiss,properties=DialogProperties(usePlatformDefaultWidth=false)) {
        Surface(Modifier.fillMaxWidth(.94f).heightIn(max=620.dp),shape=MaterialTheme.shapes.large) {
            Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                Text(file.name)
                if(mime.startsWith("video/")||mime.startsWith("audio/"))AndroidView(
                    factory={context->VideoView(context).apply{
                        setVideoURI(mediaUri(activity,file))
                        setMediaController(android.widget.MediaController(context).also {it.setAnchorView(this)})
                        setOnErrorListener {_,_,_->activity.browser.notice="This device cannot preview this codec. Try Open in another app.";true}
                    }},modifier=Modifier.fillMaxWidth().height(300.dp),
                    onRelease={it.stopPlayback()})
                else bitmap?.let {Image(it.asImageBitmap(),file.name,Modifier.fillMaxWidth().heightIn(max=400.dp))}
                    ?:Text(if(mime.startsWith("image/"))"Loading image…" else "Use Open to view this file.")
                FlowRow {TextButton(onClick={openMedia(activity,file,true)}){Text("Share")}
                    TextButton(onClick={activity.exportMedia(file)}){Text("Export")}
                    TextButton(onClick={openMedia(activity,file,false)}){Text("Open")}
                    TextButton(onClick=dismiss){Text("Close")}}
            }
        }
    }
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun MediaSaveDialog(activity:MainActivity) {
    val library=MediaLibrary.get(activity)
    val media=library.pending?:return
    var choices by remember(media){mutableStateOf<List<MediaChoice>?>(null)}
    var selected by remember(media){mutableStateOf(0)}
    var name by remember(media){mutableStateOf(media.title.take(80).ifBlank {"video-"+System.currentTimeMillis()})}
    var error by remember(media){mutableStateOf<String?>(null)}
    var showPreview by remember(media){mutableStateOf(false)}
    LaunchedEffect(media){try{choices=library.choices(media)}catch(failure:Exception){error=failure.message}}
    AlertDialog(onDismissRequest={library.pending=null},title={Text(if(choices?.getOrNull(selected)?.live==true)"Start recording" else "Save resource")},
        text={Column(Modifier.verticalScroll(rememberScrollState())){
            Text(media.url,maxLines=3)
            if(choices==null && error==null)CircularProgressIndicator()
            TextButton(onClick={showPreview=!showPreview}){Text(if(showPreview)"Hide preview" else "Preview media")}
            if(showPreview && (media.mime.startsWith("image/") || Regex("\\.(png|jpe?g|gif|webp|avif|svg|bmp)(?:[?#]|$)",RegexOption.IGNORE_CASE).containsMatchIn(media.url))) {
                AndroidView(factory={context->android.webkit.WebView(context).apply {
                    settings.javaScriptEnabled=false;settings.allowFileAccess=false;settings.allowContentAccess=false
                    webViewClient=object:android.webkit.WebViewClient(){
                        override fun shouldOverrideUrlLoading(view:android.webkit.WebView,request:android.webkit.WebResourceRequest)=true
                        override fun onRenderProcessGone(view:android.webkit.WebView,detail:android.webkit.RenderProcessGoneDetail):Boolean {
                            (view.parent as? android.view.ViewGroup)?.removeView(view);view.destroy()
                            library.pending=null;activity.browser.notice="Image preview renderer stopped. Open the resource again to retry.";return true
                        }
                    }
                    loadDataWithBaseURL(media.page,"<html><meta name='viewport' content='width=device-width'><body style='margin:0'><img alt='Image preview' style='width:100%;height:170px;object-fit:contain' src='"+android.text.TextUtils.htmlEncode(media.url)+"'></body></html>","text/html","UTF-8",null)
                }},modifier=Modifier.fillMaxWidth().height(180.dp),onRelease={it.destroy()})
            } else if(showPreview) key(selected) {
                AndroidView(factory={context->
                    val headers=mutableMapOf("Referer" to media.page)
                    android.webkit.CookieManager.getInstance().getCookie(media.url)?.let{headers["Cookie"]=it}
                    val source=androidx.media3.datasource.DefaultHttpDataSource.Factory().setDefaultRequestProperties(headers)
                    val player=androidx.media3.exoplayer.ExoPlayer.Builder(context)
                        .setMediaSourceFactory(androidx.media3.exoplayer.source.DefaultMediaSourceFactory(context).setDataSourceFactory(source)).build()
                    androidx.media3.ui.PlayerView(context).apply {
                        this.player=player
                        player.setMediaItem(androidx.media3.common.MediaItem.fromUri(choices?.getOrNull(selected)?.variant?.url?:media.url))
                        player.prepare()
                    }
                },modifier=Modifier.fillMaxWidth().height(180.dp),onRelease={(it.player as? androidx.media3.exoplayer.ExoPlayer)?.release()})
            }
            OutlinedTextField(name,{name=it},label={Text("File name")},singleLine=true)
            if(library.showQuality)choices?.forEachIndexed {index,choice->Row{
                RadioButton(selected==index,{selected=index})
                Text(choice.variant?.let {it.resolution+" · "+it.bandwidth/1000+" kbps"}?:"Original")
            }}
            Text("Save to "+library.defaultFolder.ifEmpty {"App media"})
            if(choices?.getOrNull(selected)?.live==true)Text("Records upcoming segments until you stop or the source page unloads.")
            error?.let{Text(it,color=MaterialTheme.colorScheme.error)}
        }},
        confirmButton={TextButton(enabled=choices?.isNotEmpty()==true,onClick={
            try {library.start(media,choices!![selected],name);library.pending=null}
            catch(failure:Exception){error=failure.message}
        }){Text(if(choices?.getOrNull(selected)?.live==true)"Start recording" else "Save")}},
        dismissButton={Row{TextButton(onClick={
            activity.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT,media.url),"Share resource"))
        }){Text("Share link")};TextButton(onClick={library.pending=null}){Text("Cancel")}}})
}

@Composable
private fun SavedThumbnail(file:File) {
    var bitmap by remember(file.path,file.lastModified()){mutableStateOf<android.graphics.Bitmap?>(null)}
    var loaded by remember(file.path,file.lastModified()){mutableStateOf(false)}
    val mime=MediaLibrary.mime(file)
    LaunchedEffect(file.path,file.lastModified()) {
        bitmap=withContext(Dispatchers.IO){runCatching {
            if(mime.startsWith("image/")) {
                val bounds=BitmapFactory.Options().apply{inJustDecodeBounds=true};BitmapFactory.decodeFile(file.path,bounds)
                val options=BitmapFactory.Options().apply{inSampleSize=1;while(maxOf(bounds.outWidth,bounds.outHeight)/inSampleSize>320)inSampleSize*=2}
                BitmapFactory.decodeFile(file.path,options)
            } else if(mime.startsWith("video/")) {
                val retriever=android.media.MediaMetadataRetriever()
                try{retriever.setDataSource(file.path);if(android.os.Build.VERSION.SDK_INT>=27)retriever.getScaledFrameAtTime(0,android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC,320,180) else android.media.ThumbnailUtils.createVideoThumbnail(file.path,android.provider.MediaStore.Video.Thumbnails.MINI_KIND)}finally{retriever.release()}
            } else null
        }.getOrNull()};loaded=true
    }
    Box(Modifier.fillMaxWidth().height(110.dp),contentAlignment=Alignment.Center) {
        bitmap?.let{Image(it.asImageBitmap(),file.name,Modifier.fillMaxSize(),contentScale=ContentScale.Crop)}?:
            if(!loaded&&(mime.startsWith("image/")||mime.startsWith("video/")))CircularProgressIndicator(Modifier.size(24.dp))
            else Icon(if(file.isDirectory)Icons.Default.Folder else Icons.Default.InsertDriveFile,if(file.isDirectory)"Folder" else "No thumbnail available",Modifier.size(54.dp))
    }
}
