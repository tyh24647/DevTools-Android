package com.tyh24647.devtools

import java.net.HttpURLConnection
import java.net.URL
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ScriptDraft(val name:String,val source:String,val kind:String)
object ScriptImport {
    fun decode(bytes:ByteArray):String {
        require(bytes.size<=262144){"Script files must be 256 KB or smaller."}
        val source=Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString().removePrefix("\uFEFF")
        require(!source.trimStart().startsWith("<") && '\u0000' !in source){"Choose a JavaScript file, not a webpage or binary file."}
        return source
    }
    fun draft(source:String,kind:String?=null):ScriptDraft {
        val actual=if(source.contains("==UserScript=="))"userscript" else "eruda"
        require(kind==null || actual==kind){"This file belongs in the "+if(actual=="userscript")"Userscripts tab." else "Eruda panels tab."}
        val name=if(actual=="userscript")UserscriptMetadata.validate(source)["name"]?.firstOrNull()?:"Imported userscript" else "Imported plugin"
        return ScriptDraft(name,source,actual)
    }
    suspend fun url(raw:String,kind:String):ScriptDraft=withContext(Dispatchers.IO) {
        var address=raw.trim()
        repeat(6) {attempt->
            val url=URL(address)
            require(url.protocol in listOf("https","http") && url.userInfo==null){"Enter a direct HTTP(S) JavaScript URL."}
            val connection=(url.openConnection() as HttpURLConnection).apply {
                connectTimeout=15000;readTimeout=15000;instanceFollowRedirects=false
                setRequestProperty("Accept","text/javascript, application/javascript, text/plain")
            }
            try {
                val status=connection.responseCode
                if(status in 300..399) {
                    require(attempt<5){"Too many redirects."}
                    address=URL(url,connection.getHeaderField("Location")?:error("Missing redirect URL.")).toString()
                } else {
                    require(status in 200..299){"Download returned HTTP "+status}
                    val type=connection.contentType.orEmpty().substringBefore(';').trim().lowercase()
                    require(type in listOf("text/javascript","application/javascript","application/x-javascript","text/plain","application/octet-stream")){"URL must return a JavaScript file, not a webpage."}
                    require(connection.contentLengthLong<=262144){"Script files must be 256 KB or smaller."}
                    val bytes=connection.inputStream.use{input->
                        val output=java.io.ByteArrayOutputStream();val buffer=ByteArray(8192)
                        while(output.size()<=262144){val count=input.read(buffer);if(count<0)break;output.write(buffer,0,count)}
                        output.toByteArray()
                    }
                    return@withContext draft(decode(bytes),kind)
                }
            } finally {connection.disconnect()}
        }
        error("Could not download the script.")
    }
}
