package top.yogiczy.mytv.core.data.repositories.iptv.parser

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 缺省解析器：源既不是 M3U 也不是 TXT 时的兜底——不抛错、不返回空列表，
 * 而是在选台页里放两条占位频道，告诉用户支持哪两种格式。
 * 占位文案与地址是用户看得见的，一旦被改错，格式不对的源就只剩一条
 * 看不懂的空白页，值得钉死。
 */
class DefaultIptvParserTest {
    private val parser = DefaultIptvParser()

    @Test
    fun defaultParser_alwaysSupports() {
        // 兜底解析器永远认：解析链走到它这里就不再往下找
        assertTrue(parser.isSupport("", "garbage"))
        assertTrue(parser.isSupport("", ""))
    }

    @Test
    fun defaultParser_parse_returnsTwoPlaceholderChannels() = runBlocking {
        val channels = parser.parse("garbage")

        assertEquals(2, channels.size)
        assertEquals("未知直播源格式", channels[0].groupName)
        assertEquals("支持m3u（以#EXTM3U开头）", channels[0].name)
        assertEquals("http://1.2.3.4", channels[0].url)
        assertEquals("未知直播源格式", channels[1].groupName)
        assertEquals("支持txt（包含#genre#）", channels[1].name)
        assertEquals("http://1.2.3.4", channels[1].url)
    }
}
