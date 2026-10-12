package com.tyh24647.devtools

import android.graphics.Typeface
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import org.json.JSONObject
import java.io.File
import java.util.UUID

object CodeFonts {
    private fun directory(activity: MainActivity) = File(activity.filesDir, "code-fonts").apply { mkdirs() }
    fun files(activity: MainActivity) = directory(activity).listFiles()?.filter { it.extension == "ttf" || it.extension == "otf" }?.sortedBy { it.name } ?: emptyList()
    fun options(activity: MainActivity): JSONObject {
        val id=activity.configuration.data.optString("codeFont", "monospace")
        val font=files(activity).firstOrNull { it.name == id }
        val asset=when(id){"roboto-mono"->"RobotoMono.ttf";"jetbrains-mono"->"JetBrainsMono.ttf";else->null}
        val bytes=if(asset!=null)activity.assets.open("fonts/$asset").use{it.readBytes()} else font?.readBytes()
        return JSONObject().put("codeFont", if(bytes!=null) "DevToolsCustom" else when(id){"courier"->"'Courier New', Courier, monospace";"mono"->"'Roboto Mono', 'Droid Sans Mono', monospace";else->"monospace"}).apply {
            if(bytes!=null)put("codeFontData", "data:font/${if(font?.extension=="otf")"otf" else "ttf"};base64,"+Base64.encodeToString(bytes,Base64.NO_WRAP))
        }
    }
    fun import(activity: MainActivity, uri: android.net.Uri): File {
        val bytes=activity.contentResolver.openInputStream(uri)?.use { val output=java.io.ByteArrayOutputStream();val buffer=ByteArray(8192);while(output.size()<=524288){val count=it.read(buffer,0,minOf(buffer.size,524289-output.size()));if(count<0)break;output.write(buffer,0,count)};output.toByteArray() } ?: error("Cannot open font.")
        require(bytes.size in 12..524288){"Fonts must be smaller than 512 KB."}
        val signature=bytes.take(4).toByteArray()
        val otf=signature.contentEquals("OTTO".toByteArray())
        require(otf || signature.contentEquals(byteArrayOf(0,1,0,0)) || signature.contentEquals("true".toByteArray())){"Choose a valid TTF or OTF font."}
        val file=File(directory(activity),UUID.randomUUID().toString()+if(otf)".otf" else ".ttf")
        try {file.writeBytes(bytes);Typeface.createFromFile(file)}catch(e:Exception){file.delete();throw e}
        return file
    }
}

@Composable
fun CodeFontSettings(activity: MainActivity) {
    var revision by remember {mutableIntStateOf(0)}
    var message by remember {mutableStateOf<String?>(null)}
    val fonts=remember(revision){CodeFonts.files(activity)}
    val selected=activity.configuration.data.optString("codeFont", "monospace")
    fun select(id:String){activity.configuration.change {it.put("codeFont",id)};activity.changed()}
    val importer=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){uri->if(uri!=null)runCatching {val font=CodeFonts.import(activity,uri);revision++;select(font.name)}.onFailure {message=it.message}}
    Column(Modifier.fillMaxWidth()) {
        Text("Code fonts",style=MaterialTheme.typography.titleMedium)
        FlowRow {listOf("monospace" to "System monospace","roboto-mono" to "Roboto Mono","jetbrains-mono" to "JetBrains Mono").forEach {(id,label)->FilterChip(selected==id,{select(id)},label={Text(label)})}}
        fonts.forEachIndexed {index,font->Row {
            FilterChip(selected==font.name,{select(font.name)},label={Text("Imported font ${index+1}")})
            TextButton(onClick={if(selected==font.name)select("monospace");font.delete();revision++;activity.changed()}){Text("Delete")}
        }}
        TextButton(onClick={importer.launch(arrayOf("font/*","application/x-font-ttf","application/octet-stream"))}){Text("Import TTF or OTF font")}
        Text("Applied to console code views and script editors. Imported fonts retain their own spacing; choose a monospaced font for aligned code.")
        message?.let {Text(it,color=MaterialTheme.colorScheme.error)}
    }
}
