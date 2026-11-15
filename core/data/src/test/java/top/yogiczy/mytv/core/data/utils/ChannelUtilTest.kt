package top.yogiczy.mytv.core.data.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
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
}
