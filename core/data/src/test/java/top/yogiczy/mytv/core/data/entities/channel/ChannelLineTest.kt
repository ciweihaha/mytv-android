package top.yogiczy.mytv.core.data.entities.channel

import org.junit.Assert.assertEquals
import org.junit.Test

class ChannelLineTest {
    @Test
    fun playableUrl_bareQuestionMark_getsTAppended() {
        assertEquals(
            "http://1.2.3.4/live?t",
            ChannelLine(url = "http://1.2.3.4/live?").playableUrl,
        )
    }

    @Test
    fun playableUrl_questionMarkBeforeLineNameSuffix_getsTAppended() {
        assertEquals(
            "http://1.2.3.4/live?t",
            ChannelLine(url = "http://1.2.3.4/live?\$线路1").playableUrl,
        )
    }

    @Test
    fun playableUrl_lineNameSuffix_stripped() {
        assertEquals(
            "http://1.2.3.4/live.m3u8",
            ChannelLine(url = "http://1.2.3.4/live.m3u8\$线路1").playableUrl,
        )
    }

    @Test
    fun playableUrl_queryWithParams_unchanged() {
        assertEquals(
            "http://1.2.3.4/live?token=abc",
            ChannelLine(url = "http://1.2.3.4/live?token=abc\$线路1").playableUrl,
        )
    }

    @Test
    fun playableUrl_plainUrl_unchanged() {
        assertEquals(
            "http://1.2.3.4/live.m3u8",
            ChannelLine(url = "http://1.2.3.4/live.m3u8").playableUrl,
        )
    }
}
