package top.yogiczy.mytv.core.data.repositories.iptv.parser

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 直播源解析器 BOM 容错测试。
 * 旧实现用 data.startsWith("#EXTM3U") 识别 M3U，带 UTF-8 BOM（Windows 记事本
 * 默认保存格式）或开头空行的源会被当成未知格式，整条源解析不出频道；
 * TXT 源首个分组名会带上 BOM 字符。
 */
class IptvParserTest {
    private val m3uParser = M3uIptvParser()
    private val txtParser = TxtIptvParser()

    private val m3uData = """
        #EXTM3U x-tvg-url="http://example.com/epg.xml"
        #EXTINF:-1 tvg-name="CCTV-1" group-title="央视",CCTV-1
        http://example.com/cctv1.m3u8
        #EXTINF:-1 tvg-name="CCTV-2" group-title="央视",CCTV-2
        http://example.com/cctv2.m3u8
    """.trimIndent()

    @Test
    fun m3u_withBom_isSupported() {
        assertTrue(m3uParser.isSupport("", "\uFEFF$m3uData"))
    }

    @Test
    fun m3u_withBom_parsesChannels() = runBlocking {
        val channels = m3uParser.parse("\uFEFF$m3uData")
        assertEquals(2, channels.size)
        assertEquals("CCTV-1", channels[0].name)
        assertEquals("央视", channels[0].groupName)
        assertEquals("http://example.com/cctv1.m3u8", channels[0].url)
        assertEquals("CCTV-2", channels[1].name)
        assertEquals("http://example.com/cctv2.m3u8", channels[1].url)
    }

    @Test
    fun m3u_withBom_getEpgUrl() = runBlocking {
        assertEquals("http://example.com/epg.xml", m3uParser.getEpgUrl("\uFEFF$m3uData"))
    }

    @Test
    fun m3u_leadingBlankLines_isSupported() {
        assertTrue(m3uParser.isSupport("", "\n\n$m3uData"))
    }

    @Test
    fun m3u_plain_stillWorks() = runBlocking {
        assertTrue(m3uParser.isSupport("", m3uData))
        val channels = m3uParser.parse(m3uData)
        assertEquals(2, channels.size)
        assertEquals("CCTV-1", channels[0].name)
    }

    @Test
    fun txt_withBom_groupNameClean() = runBlocking {
        val txtData = "\uFEFF央视,#genre#\nCCTV-1,http://example.com/cctv1\nCCTV-2,http://example.com/cctv2"
        assertTrue(txtParser.isSupport("", txtData))
        val channels = txtParser.parse(txtData)
        assertEquals(2, channels.size)
        assertEquals("央视", channels[0].groupName)
        assertEquals("CCTV-1", channels[0].name)
    }

    @Test
    fun m3u_channelNameWithComma() = runBlocking {
        // 频道名本身含逗号：旧代码 split(",").last() 只剩尾段
        val data = """
            #EXTM3U
            #EXTINF:-1 group-title="央视",CCTV-1,高清
            http://example.com/cctv1.m3u8
        """.trimIndent()
        val channels = m3uParser.parse(data)
        assertEquals(1, channels.size)
        assertEquals("CCTV-1,高清", channels[0].name)
    }

    @Test
    fun m3u_attributeValueWithComma() = runBlocking {
        // 属性值含逗号时，显示名仍是属性区之后第一个逗号后的全部内容
        val data = """
            #EXTM3U
            #EXTINF:-1 tvg-name="A,B" group-title="央视",CCTV-1
            http://example.com/cctv1.m3u8
        """.trimIndent()
        val channels = m3uParser.parse(data)
        assertEquals(1, channels.size)
        assertEquals("CCTV-1", channels[0].name)
        assertEquals("A,B", channels[0].epgName)
    }

    @Test
    fun m3u_channelNameAndAttributeBothWithComma() = runBlocking {
        val data = """
            #EXTM3U
            #EXTINF:-1 tvg-name="A,B",CCTV-1,高清
            http://example.com/cctv1.m3u8
        """.trimIndent()
        val channels = m3uParser.parse(data)
        assertEquals(1, channels.size)
        assertEquals("CCTV-1,高清", channels[0].name)
    }

    @Test
    fun txt_urlWithComma() = runBlocking {
        // 地址查询串含逗号：旧代码 split 后取 res[1]，逗号后的部分被丢掉
        val txtData = "央视,#genre#\nCCTV-1,http://example.com/play?token=a,b"
        val channels = txtParser.parse(txtData)
        assertEquals(1, channels.size)
        assertEquals("CCTV-1", channels[0].name)
        assertEquals("http://example.com/play?token=a,b", channels[0].url)
    }

    @Test
    fun txt_multiLineUrlsAndChineseComma() = runBlocking {
        val txtData = "央视,#genre#\nCCTV-1,http://a.com/1#http://a.com/2\nCCTV-2，http://example.com/cctv2"
        val channels = txtParser.parse(txtData)
        assertEquals(3, channels.size)
        assertEquals("http://a.com/1", channels[0].url)
        assertEquals("http://a.com/2", channels[1].url)
        assertEquals("CCTV-2", channels[2].name)
        assertEquals("http://example.com/cctv2", channels[2].url)
    }

    @Test
    fun txt_emptyUrlSkipped() = runBlocking {
        val txtData = "央视,#genre#\nCCTV-1,\nCCTV-2,http://example.com/cctv2"
        val channels = txtParser.parse(txtData)
        assertEquals(1, channels.size)
        assertEquals("CCTV-2", channels[0].name)
    }
}
