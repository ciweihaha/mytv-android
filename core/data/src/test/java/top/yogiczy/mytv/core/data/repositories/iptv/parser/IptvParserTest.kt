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
    fun txt_trailingHash_noEmptyUrlLine() = runBlocking {
        // 尾部 `#`、连续 `##` 不应产生空地址线路
        val txtData = "央视,#genre#\nCCTV-1,http://a.com/1#\nCCTV-2,http://a.com/2##http://a.com/3"
        val channels = txtParser.parse(txtData)
        assertEquals(3, channels.size)
        assertEquals("http://a.com/1", channels[0].url)
        assertEquals("http://a.com/2", channels[1].url)
        assertEquals("http://a.com/3", channels[2].url)
    }

    @Test
    fun txt_multiUrlWithSpaces_webviewRecognized() = runBlocking {
        // `#` 两侧带空格时分段要先 trim，webview:// 才能被认出
        val txtData = "央视,#genre#\nCCTV-1,http://a.com/1 # webview://example.com/page"
        val channels = txtParser.parse(txtData)
        assertEquals(2, channels.size)
        assertEquals("http://a.com/1", channels[0].url)
        assertEquals(
            IptvParser.ChannelItem.HybridType.WebView,
            channels[1].hybridType,
        )
        assertEquals("webview://example.com/page", channels[1].url)
    }

    @Test
    fun txt_emptyUrlSkipped() = runBlocking {
        val txtData = "央视,#genre#\nCCTV-1,\nCCTV-2,http://example.com/cctv2"
        val channels = txtParser.parse(txtData)
        assertEquals(1, channels.size)
        assertEquals("CCTV-2", channels[0].name)
    }

    @Test
    fun txt_blankNameSkipped() = runBlocking {
        // 逗号前名字空白的行直接跳过，不产生无名频道
        val txtData = "央视,#genre#\n,http://example.com/garbage\nCCTV-1,http://example.com/cctv1\n ,http://example.com/garbage2"
        val channels = txtParser.parse(txtData)
        assertEquals(1, channels.size)
        assertEquals("CCTV-1", channels[0].name)
    }

    @Test
    fun txt_genreLineWithoutGroupName_keepsPreviousGroup() = runBlocking {
        // `,#genre#` 不应产生空分组名：其后的频道仍归上一个分组
        val txtData = "央视,#genre#\nCCTV-1,http://example.com/1\n,#genre#\nCCTV-2,http://example.com/2"
        val channels = txtParser.parse(txtData)
        assertEquals(2, channels.size)
        assertEquals("央视", channels[0].groupName)
        assertEquals("央视", channels[1].groupName)
    }

    @Test
    fun txt_genreLineWithoutComma_markerNotInGroupName() = runBlocking {
        // `央视 #genre#`（无逗号）：#genre# 标记本身不应带进分组名
        val txtData = "央视 #genre#\nCCTV-1,http://example.com/1"
        val channels = txtParser.parse(txtData)
        assertEquals(1, channels.size)
        assertEquals("央视", channels[0].groupName)
    }

    @Test
    fun m3u_epgUrl_firstBlankEntry_skipped() = runBlocking {
        // x-tvg-url 首项为空时应取后面的有效地址，而不是返回空串
        val data = "#EXTM3U x-tvg-url=\",http://epg2.example.com/epg.xml\"\n" +
            "#EXTINF:-1,CCTV-1\nhttp://example.com/cctv1.m3u8"
        assertEquals(
            "http://epg2.example.com/epg.xml",
            m3uParser.getEpgUrl(data),
        )
    }

    @Test
    fun m3u_epgUrl_emptyFallsBackToUrlTvg() = runBlocking {
        // x-tvg-url 为空时应兜底到 url-tvg
        val data = "#EXTM3U x-tvg-url=\"\" url-tvg=\"http://epg.example.com/epg.xml\"\n" +
            "#EXTINF:-1,CCTV-1\nhttp://example.com/cctv1.m3u8"
        assertEquals(
            "http://epg.example.com/epg.xml",
            m3uParser.getEpgUrl(data),
        )
    }

    @Test
    fun m3u_epgUrl_allBlank_returnsNull() = runBlocking {
        // 两个属性都为空时返回 null，不生成空地址的 EPG 源
        val data = "#EXTM3U x-tvg-url=\"\"\n" +
            "#EXTINF:-1,CCTV-1\nhttp://example.com/cctv1.m3u8"
        assertEquals(null, m3uParser.getEpgUrl(data))
    }

    @Test
    fun m3u_kodipropValueWithEquals_notTruncated() = runBlocking {
        // 旧实现用 split("=").last() 取值，license_key 里带 `=`（URL 参数、
        // base64 填充）会被截断为空串，DRM 频道播不了
        val data = """
            #EXTM3U
            #EXTINF:-1 tvg-name="DRM-1" group-title="测试",DRM-1
            #KODIPROP:inputstream.adaptive.manifest_type=mpd
            #KODIPROP:inputstream.adaptive.license_type=com.widevine.alpha
            #KODIPROP:inputstream.adaptive.license_key=https://lic.example.com/get?token=abc==&id=1
            http://example.com/drm1.mpd
        """.trimIndent()
        val channels = m3uParser.parse(data)
        assertEquals(1, channels.size)
        assertEquals("mpd", channels[0].manifestType)
        assertEquals("com.widevine.alpha", channels[0].licenseType)
        assertEquals(
            "https://lic.example.com/get?token=abc==&id=1",
            channels[0].licenseKey,
        )
    }

    @Test
    fun m3u_allAttributes_parsed() = runBlocking {
        // 全部属性一次到位：重构（正则只编译一次）后行为必须和原来一致
        val data = """
            #EXTM3U catchup="append" catchup-source="http://example.com/replay?date={date}"
            #EXTINF:-1 tvg-name="CCTV-1" tvg-logo="http://example.com/logo.png" group-title="央视;高清" http-user-agent="UA-1" http-referrer="https://ref.example.com/" http-origin="https://origin.example.com/",CCTV-1
            http://example.com/cctv1.m3u8
            #EXTINF:-1 tvg-name="CCTV-2" group-title="央视",CCTV-2
            http://example.com/cctv2.m3u8
        """.trimIndent()
        val channels = m3uParser.parse(data)
        assertEquals(3, channels.size)
        assertEquals("CCTV-1", channels[0].name)
        assertEquals("CCTV-1", channels[0].epgName)
        assertEquals("央视", channels[0].groupName)
        assertEquals("http://example.com/logo.png", channels[0].logo)
        assertEquals("UA-1", channels[0].httpUserAgent)
        assertEquals("https://ref.example.com/", channels[0].httpReferrer)
        assertEquals("https://origin.example.com/", channels[0].httpOrigin)
        assertEquals(1, channels[0].playbackType)
        assertEquals("http://example.com/replay?date={date}", channels[0].playbackFormat)
        assertEquals("高清", channels[1].groupName)
        assertEquals("CCTV-1", channels[1].name)
        assertEquals("CCTV-2", channels[2].name)
        assertEquals("央视", channels[2].groupName)
        assertEquals(null, channels[2].logo)
        assertEquals(1, channels[2].playbackType)
        assertEquals("http://example.com/replay?date={date}", channels[2].playbackFormat)
    }

    @Test
    fun m3u_extvlcoptValueWithEquals_notTruncated() = runBlocking {
        // #EXTVLCOPT 的 http-referrer 带查询参数时同样不能被截断
        val data = """
            #EXTM3U
            #EXTINF:-1 tvg-name="CCTV-1" group-title="央视",CCTV-1
            #EXTVLCOPT:http-referrer=https://example.com/?a=b&c=d
            #EXTVLCOPT:http-user-agent=Mozilla/5.0
            http://example.com/cctv1.m3u8
        """.trimIndent()
        val channels = m3uParser.parse(data)
        assertEquals(1, channels.size)
        assertEquals("https://example.com/?a=b&c=d", channels[0].httpReferrer)
        assertEquals("Mozilla/5.0", channels[0].httpUserAgent)
    }

    @Test
    fun m3u_extinfWithoutComma_fallsBackToTvgName() = runBlocking {
        // 残缺行：没有逗号后的显示名时退到 tvg-name，不再把整行当频道名
        val data = """
            #EXTM3U
            #EXTINF:-1 tvg-name="CCTV-1" group-title="央视"
            http://example.com/cctv1.m3u8
            #EXTINF:-1 tvg-name="CCTV-2" group-title="央视",CCTV-2
            http://example.com/cctv2.m3u8
        """.trimIndent()
        val channels = m3uParser.parse(data)
        assertEquals(2, channels.size)
        assertEquals("CCTV-1", channels[0].name)
        assertEquals("CCTV-1", channels[0].epgName)
        assertEquals("CCTV-2", channels[1].name)
    }

    @Test
    fun m3u_extinfWithoutCommaAndTvgName_skipped() = runBlocking {
        // 残缺行：既没显示名又没 tvg-name，直接跳过；且下一行 URL 不会
        // 误填到上一条频道上造成重复
        val data = """
            #EXTM3U
            #EXTINF:-1 tvg-name="CCTV-1" group-title="央视",CCTV-1
            http://example.com/cctv1.m3u8
            #EXTINF:-1 group-title="央视"
            http://example.com/garbage.m3u8
            #EXTINF:-1 tvg-name="CCTV-2" group-title="央视",CCTV-2
            http://example.com/cctv2.m3u8
        """.trimIndent()
        val channels = m3uParser.parse(data)
        assertEquals(2, channels.size)
        assertEquals("CCTV-1", channels[0].name)
        assertEquals("CCTV-2", channels[1].name)
        assertTrue(channels.none { it.url == "http://example.com/garbage.m3u8" })
    }

    @Test
    fun m3u_indentedLines_parsed() = runBlocking {
        // 行首带空格：#EXTINF 行带缩进时，旧实现认不出标记、把整行当 URL
        // 处理，频道直接丢掉；#KODIPROP 行带缩进时还会多出一路垃圾地址
        val data = "#EXTM3U\n" +
            "  #EXTINF:-1 tvg-name=\"CCTV-1\" group-title=\"央视\",CCTV-1\n" +
            "http://example.com/cctv1.m3u8\n" +
            "  #EXTINF:-1 tvg-name=\"CCTV-2\" group-title=\"央视\",CCTV-2\n" +
            "  #KODIPROP:inputstream.adaptive.manifest_type=mpd\n" +
            "http://example.com/cctv2.mpd"
        val channels = m3uParser.parse(data)
        assertEquals(2, channels.size)
        assertEquals("CCTV-1", channels[0].name)
        assertEquals("http://example.com/cctv1.m3u8", channels[0].url)
        assertEquals("CCTV-2", channels[1].name)
        assertEquals("http://example.com/cctv2.mpd", channels[1].url)
        assertEquals("mpd", channels[1].manifestType)
    }

    @Test
    fun m3u_extinfBlankName_fallsBackToTvgName() = runBlocking {
        // 逗号后显示名空白时同样退到 tvg-name
        val data = """
            #EXTM3U
            #EXTINF:-1 tvg-name="CCTV-1" group-title="央视",
            http://example.com/cctv1.m3u8
        """.trimIndent()
        val channels = m3uParser.parse(data)
        assertEquals(1, channels.size)
        assertEquals("CCTV-1", channels[0].name)
    }
}
