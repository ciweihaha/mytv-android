package top.yogiczy.mytv.core.data.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import top.yogiczy.mytv.core.data.entities.channel.ChannelLine
import java.text.SimpleDateFormat
import java.util.Locale

class ChannelUtilTest {
    @Test
    fun replacePlaybackFormat_cctvPlaybackFormat() {
        val start = 1750000000000L
        val end = 1750003600000L
        val dateFormat = SimpleDateFormat("yyyyMMddHHmmss", Locale.getDefault())
        val expected =
            "?stime=${dateFormat.format(start)}&etime=${dateFormat.format(end)}&type=lbacks"

        assertEquals(
            expected,
            ChannelUtil.replacePlaybackFormat(
                "?stime=\${(b)yyyyMMddHHmmss}&etime=\${(e)yyyyMMddHHmmss}&type=lbacks",
                start, System.currentTimeMillis(), end
            )
        )
    }

    @Test
    fun replacePlaybackFormat_unknownKey_replacedWithEmpty() {
        assertEquals(
            "ab",
            ChannelUtil.replacePlaybackFormat("a\${(x)yyyy}b", 0L, 0L, 0L)
        )
    }

    @Test
    fun replacePlaybackFormat_nullOrEmpty_returnsNull() {
        assertNull(ChannelUtil.replacePlaybackFormat(null, 0L, 0L, 0L))
        assertNull(ChannelUtil.replacePlaybackFormat("", 0L, 0L, 0L))
    }

    @Test
    fun urlSupportPlayback_rtspWithPltv_returnsTrue() {
        assertTrue(ChannelUtil.urlSupportPlayback("rtsp://example.com/pltv/1"))
    }

    @Test
    fun urlSupportPlayback_rtspWithTvod_returnsTrue() {
        assertTrue(ChannelUtil.urlSupportPlayback("rtsp://example.com/tvod/1"))
    }

    @Test
    fun urlSupportPlayback_rtspWithoutKeyword_returnsFalse() {
        assertTrue(!ChannelUtil.urlSupportPlayback("rtsp://example.com/live/1"))
    }

    @Test
    fun urlSupportPlayback_nonRtsp_returnsFalse() {
        assertTrue(!ChannelUtil.urlSupportPlayback("http://example.com/pltv/1"))
    }

    @Test
    fun urlSupportPlayback_keywordCaseInsensitive() {
        assertTrue(ChannelUtil.urlSupportPlayback("rtsp://example.com/PLTV/1"))
        assertTrue(ChannelUtil.urlSupportPlayback("rtsp://example.com/TVOD/1"))
    }

    @Test
    fun urlToCanPlayback_replacesPltvWithTvod() {
        assertEquals(
            "rtsp://example.com/tvod/1",
            ChannelUtil.urlToCanPlayback("rtsp://example.com/pltv/1"),
        )
    }

    @Test
    fun urlToCanPlayback_keywordCaseInsensitive() {
        assertEquals(
            "rtsp://example.com/tvod/1",
            ChannelUtil.urlToCanPlayback("rtsp://example.com/PLTV/1"),
        )
    }

    @Test
    fun urlToCanPlayback_withoutPltv_unchanged() {
        assertEquals(
            "rtsp://example.com/live/1",
            ChannelUtil.urlToCanPlayback("rtsp://example.com/live/1"),
        )
    }

    @Test
    fun getHybridWebViewUrlProvider_cctv_returnsOfficial() {
        assertEquals("官网", ChannelUtil.getHybridWebViewUrlProvider("https://tv.cctv.com/live/cctv1/"))
    }

    @Test
    fun getHybridWebViewUrlProvider_yangshipin_returnsYangshipin() {
        assertEquals(
            "央视频",
            ChannelUtil.getHybridWebViewUrlProvider("https://yangshipin.cn/tv/home?pid=600002503"),
        )
    }

    @Test
    fun getHybridWebViewUrlProvider_providerDomain_returnsOfficial() {
        assertEquals("官网", ChannelUtil.getHybridWebViewUrlProvider("https://www.jstv.com/live"))
    }

    @Test
    fun getHybridWebViewUrlProvider_otherDomain_returnsOther() {
        assertEquals("其它", ChannelUtil.getHybridWebViewUrlProvider("https://example.com/live/1"))
    }

    @Test
    fun getHybridWebViewUrlProvider_webviewPrefix_resolvesProvider() {
        // webview:// 线路（解析器会原样保留这种线路）：去掉前缀后补协议再解析，
        // 不然 URL() 报 no protocol、标签只能显示"未知"
        assertEquals(
            "官网",
            ChannelUtil.getHybridWebViewUrlProvider("webview://www.jstv.com/live"),
        )
    }

    @Test
    fun getHybridWebViewUrlProvider_invalidUrl_returnsUnknown() {
        assertEquals("未知", ChannelUtil.getHybridWebViewUrlProvider("not a url"))
    }

    @Test
    fun getHybridWebViewLines_cctv1_returnsOfficialAndYangshipin() {
        // 网页混合线路查表：CCTV-1 两路——官网（带回放格式）+ 央视频；
        // 回来的线路必须是 WebView 型、回放地址清空，否则会走直链播放器
        val lines = ChannelUtil.getHybridWebViewLines("cctv-1")
        assertEquals(2, lines.size)
        assertEquals("https://tv.cctv.com/live/cctv1/", lines[0].url)
        assertEquals(1, lines[0].playbackType)
        assertEquals(
            "?stime=\${(b)yyyyMMddHHmmss}&etime=\${(e)yyyyMMddHHmmss}&type=lbacks",
            lines[0].playbackFormat,
        )
        assertEquals("https://yangshipin.cn/tv/home?pid=600001859", lines[1].url)
        assertTrue(lines.all { it.hybridType == ChannelLine.HybridType.WebView })
        assertTrue(lines.all { it.playbackUrl == null })
    }

    @Test
    fun getHybridWebViewLines_singleEntryChannel_returnsOneLine() {
        // 只有一路的台：CCTV-4K 只有央视频一路
        val lines = ChannelUtil.getHybridWebViewLines("cctv-4k")
        assertEquals(1, lines.size)
        assertEquals("https://yangshipin.cn/tv/home?pid=600002264", lines[0].url)
        assertEquals(ChannelLine.HybridType.WebView, lines[0].hybridType)
    }

    @Test
    fun getHybridWebViewLines_guangdongKeepsCustomUserAgent() {
        // 广东卫视官网那路带定制的 iPhone UA：查表映射时 httpUserAgent
        // 要原样带过去，丢了官网会拦
        val lines = ChannelUtil.getHybridWebViewLines("广东卫视")
        assertEquals(2, lines.size)
        assertEquals("https://www.gdtv.cn/tvChannelDetail/43", lines[0].url)
        assertTrue(lines[0].httpUserAgent?.contains("iPhone") == true)
        assertEquals("https://yangshipin.cn/tv/home?pid=600002485", lines[1].url)
    }

    @Test
    fun getHybridWebViewLines_unknownChannel_returnsEmpty() {
        assertEquals(0, ChannelUtil.getHybridWebViewLines("不存在的频道").size)
    }
}
