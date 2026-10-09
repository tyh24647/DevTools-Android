package com.tyh24647.devtools

import java.net.URI

data class HlsVariant(val url: String, val bandwidth: Long, val resolution: String, val audioGroup: String?)
data class HlsRange(val offset: Long, val length: Long)
data class HlsKey(val url: String, val iv: String?)
data class HlsSegment(val url: String, val sequence: Long, val duration: Double, val range: HlsRange?, val init: Pair<String, HlsRange?>?, val key: HlsKey?, val discontinuity: Boolean)
data class HlsPlaylist(val variants: List<HlsVariant>, val audio: Map<String, String>, val segments: List<HlsSegment>, val ended: Boolean, val targetDuration: Double) {
    companion object {
        fun parse(source: String, base: String): HlsPlaylist {
            require(source.trimStart().startsWith("#EXTM3U")) { "Not an HLS playlist." }
            fun url(value: String): String {
                val result = URI(base).resolve(value.trim()).toString()
                require(URI(result).scheme in listOf("http", "https")) { "Unsupported media URL." }
                require(URI(result).userInfo == null) { "URLs with embedded credentials are unsupported." }
                return result
            }
            fun attrs(line: String): Map<String,String> = Regex("""([A-Z0-9-]+)=("[^"]*"|[^,]*)""").findAll(line.substringAfter(':')).associate { it.groupValues[1] to it.groupValues[2].trim('"') }
            val variants=mutableListOf<HlsVariant>(); val audio=mutableMapOf<String,String>(); val segments=mutableListOf<HlsSegment>()
            var variant: Map<String,String>?=null; var sequence=0L; var duration=0.0; var target=6.0; var ended=false
            var rangeText: String?=null; var nextOffset=0L; var previousURL=""
            var init: Pair<String,HlsRange?>?=null; var key: HlsKey?=null; var discontinuity=false
            fun range(value:String?, offset:Long): HlsRange? {
                if(value==null) return null
                val bits=value.split('@'); val length=bits[0].toLong()
                require(length>0) { "Invalid HLS byte range." }
                return HlsRange(bits.getOrNull(1)?.toLong() ?: offset,length)
            }
            for(raw in source.lineSequence()) {
                val line=raw.trim()
                when {
                    line.startsWith("#EXT-X-STREAM-INF:")-> variant=attrs(line)
                    line.startsWith("#EXT-X-MEDIA:")-> {
                        val a=attrs(line)
                        if(a["TYPE"]=="AUDIO" && a["URI"]!=null && (a["DEFAULT"]=="YES" || !audio.containsKey(a["GROUP-ID"])))
                            audio[a["GROUP-ID"].orEmpty()]=url(a.getValue("URI"))
                    }
                    line.startsWith("#EXT-X-MEDIA-SEQUENCE:")->sequence=line.substringAfter(':').toLong()
                    line.startsWith("#EXT-X-TARGETDURATION:")->target=line.substringAfter(':').toDouble()
                    line.startsWith("#EXTINF:")->duration=line.substringAfter(':').substringBefore(',').toDouble()
                    line.startsWith("#EXT-X-BYTERANGE:")->rangeText=line.substringAfter(':')
                    line.startsWith("#EXT-X-MAP:")-> {val a=attrs(line);init=url(a.getValue("URI")) to range(a["BYTERANGE"],0)}
                    line.startsWith("#EXT-X-KEY:")-> {
                        val a=attrs(line)
                        key=when(a["METHOD"]) {
                            "NONE"->null
                            "AES-128"->{require(a["KEYFORMAT"]==null || a["KEYFORMAT"]=="identity") { "Protected key formats are unsupported." };HlsKey(url(a.getValue("URI")),a["IV"])}
                            else->error("This protected HLS stream cannot be saved.")
                        }
                    }
                    line=="#EXT-X-DISCONTINUITY"->discontinuity=true
                    line=="#EXT-X-ENDLIST"->ended=true
                    line.isNotEmpty() && !line.startsWith("#")-> {
                        val resolved=url(line)
                        val v=variant
                        if(v!=null) {variants+=HlsVariant(resolved,(v["AVERAGE-BANDWIDTH"]?:v["BANDWIDTH"]?:"0").toLong(),v["RESOLUTION"]?:"Auto",v["AUDIO"]);variant=null}
                        else {
                            require(rangeText==null || '@' in rangeText!! || previousURL==resolved) { "Missing HLS byte range offset." }
                            val r=range(rangeText,nextOffset);nextOffset=r?.let {it.offset+it.length}?:0
                            segments+=HlsSegment(resolved,sequence++,duration,r,init,key,discontinuity)
                            previousURL=resolved;duration=0.0;rangeText=null;discontinuity=false
                        }
                    }
                }
            }
            return HlsPlaylist(variants,audio,segments,ended,target)
        }
    }
}
