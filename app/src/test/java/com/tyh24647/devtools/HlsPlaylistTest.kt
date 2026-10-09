package com.tyh24647.devtools
import org.junit.Assert.*
import org.junit.Test
class HlsPlaylistTest {
    @Test fun masterResolvesAudioAndSortMetadata() {
        val p=HlsPlaylist.parse("""
#EXTM3U
#EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID="a",DEFAULT=YES,URI="audio/list.m3u8"
#EXT-X-STREAM-INF:BANDWIDTH=900000,RESOLUTION=1280x720,AUDIO="a"
video/list.m3u8
""".trimIndent(),"https://example.com/master.m3u8")
        assertEquals("https://example.com/video/list.m3u8",p.variants[0].url)
        assertEquals("https://example.com/audio/list.m3u8",p.audio["a"])
        assertEquals(900000L,p.variants[0].bandwidth)
    }
    @Test fun vodTracksSequenceEncryptionRangeAndEnd() {
        val p=HlsPlaylist.parse("""
#EXTM3U
#EXT-X-MEDIA-SEQUENCE:8
#EXT-X-KEY:METHOD=AES-128,URI="key",IV=0x01
#EXTINF:6,
#EXT-X-BYTERANGE:100@0
media.ts
#EXT-X-DISCONTINUITY
#EXTINF:6,
#EXT-X-BYTERANGE:100
media.ts
#EXT-X-ENDLIST
""".trimIndent(),"https://example.com/vod.m3u8")
        assertTrue(p.ended);assertEquals(9L,p.segments[1].sequence)
        assertEquals(100L,p.segments[1].range!!.offset)
        assertTrue(p.segments[1].discontinuity)
        assertEquals("https://example.com/key",p.segments[0].key!!.url)
    }
    @Test fun rejectsProtectedMethods() {
        try{HlsPlaylist.parse("#EXTM3U\n#EXT-X-KEY:METHOD=SAMPLE-AES,URI=\"key\"","https://example.com/a");fail()}catch(expected:IllegalStateException){}
    }
    @Test fun rejectsNonHttpSegments() {
        try{HlsPlaylist.parse("#EXTM3U\n#EXTINF:1,\nfile:///private/a","https://example.com/a");fail()}catch(expected:IllegalArgumentException){}
    }
    @Test fun liveHasNoEndTag() {
        assertFalse(HlsPlaylist.parse("#EXTM3U\n#EXTINF:2,\na.ts","https://example.com/a").ended)
    }
}
