package top.yogiczy.mytv.core.util.utils

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * m3u8 预览取帧：入口的后缀门（非 m3u8 地址直接回 null，不联网）与
 * 找 ts 那段纯字符串逻辑（resolveFirstTsUrl）都能单测钉住；
 * 真正要网络与 Android 环境的只有取文本与取帧两步。
 */
class M3u8AnalysisUtilTest {
    @Test
    fun getFirstFrame_nonM3u8Url_returnsNullWithoutNetwork() = runBlocking {
        // 不是 .m3u8 结尾：后缀门直接拦下，10 秒超时、取帧都走不到
        assertNull(M3u8AnalysisUtil.getFirstFrame("http://example.com/video.mp4"))
        assertNull(M3u8AnalysisUtil.getFirstFrame("http://example.com/stream.m3u"))
        assertNull(M3u8AnalysisUtil.getFirstFrame(""))
    }

    @Test
    fun getFirstFrame_m3u8OnlyInQuery_returnsNull() = runBlocking {
        // .m3u8 只在问号后面：先按 "?" 切、只看前面那段，后缀门照样拦下
        assertNull(M3u8AnalysisUtil.getFirstFrame("http://example.com/video.mp4?file=x.m3u8"))
    }

    @Test
    fun resolveFirstTsUrl_relativeResolvedAgainstBase() {
        val content = "#EXTM3U\n#EXT-X-VERSION:3\n#EXTINF:10.0,\nseg001.ts\n#EXTINF:10.0,\nseg002.ts\n"
        assertEquals(
            "http://example.com/live/seg001.ts",
            resolveFirstTsUrl("http://example.com/live/list.m3u8", content)
        )
    }

    @Test
    fun resolveFirstTsUrl_absoluteReturnedAsIs() {
        val content = "#EXTM3U\n#EXTINF:10.0,\nhttp://cdn.example.com/a/seg001.ts\n#EXTINF:10.0,\nseg002.ts\n"
        assertEquals(
            "http://cdn.example.com/a/seg001.ts",
            resolveFirstTsUrl("http://example.com/live/list.m3u8", content)
        )
    }

    @Test
    fun resolveFirstTsUrl_skipsCommentLinesAndPicksFirstTs() {
        // #EXT-X-KEY 行里也有 .ts，但 # 开头的行不算；取第一条真正的分片
        val content = "#EXTM3U\n#EXT-X-KEY:METHOD=AES-128,URI=\"key.ts\"\n#EXTINF:10.0,\nseg001.ts\n#EXTINF:10.0,\nseg002.ts\n"
        assertEquals(
            "http://example.com/live/seg001.ts",
            resolveFirstTsUrl("http://example.com/live/list.m3u8", content)
        )
    }

    @Test
    fun resolveFirstTsUrl_noTsLine_returnsNull() {
        // 主列表只有变体地址、没有 ts 行：回 null，不硬造地址
        val content = "#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=800000\nlow.m3u8\n#EXT-X-STREAM-INF:BANDWIDTH=2000000\nhigh.m3u8\n"
        assertNull(resolveFirstTsUrl("http://example.com/live/list.m3u8", content))
        assertNull(resolveFirstTsUrl("http://example.com/live/list.m3u8", ""))
    }

    @Test
    fun resolveFirstTsUrl_uppercaseTsMatched() {
        val content = "#EXTM3U\n#EXTINF:10.0,\nSEG001.TS\n"
        assertEquals(
            "http://example.com/live/SEG001.TS",
            resolveFirstTsUrl("http://example.com/live/list.m3u8", content)
        )
    }
}
