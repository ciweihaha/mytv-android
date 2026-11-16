package top.yogiczy.mytv.core.data.repositories.iptv.parser

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import top.yogiczy.mytv.core.data.entities.channel.ChannelLine
import top.yogiczy.mytv.core.data.repositories.iptv.parser.IptvParser.ChannelItem.Companion.toChannelGroupList
import top.yogiczy.mytv.core.data.utils.ChannelAlias

/**
 * 解析结果分组：同名频道先赢 epgName/tvgId/logo、重复地址只留一路、
 * 同名频道跨分组各留一份。
 * toChannelGroupList 是 IptvRepository.refresh 的必经之路，但之前一次
 * 也没直接断言过——解析器单测只钉到 ChannelItem 那层，分组、合并、
 * 去重这三步全是裸奔。
 */
class IptvChannelGroupTest {
    @Test
    fun toChannelGroupList_sameNameFirstWinsAndDuplicateUrlsCollapse() {
        val items = listOf(
            IptvParser.ChannelItem(
                groupName = "央视", name = "CCTV-1", epgName = "EPG-1A",
                tvgId = "id-1", url = "http://a.com/1", logo = "logo-1",
                httpUserAgent = "UA-1",
            ),
            IptvParser.ChannelItem(
                groupName = "央视", name = "CCTV-1", epgName = "EPG-1B",
                tvgId = "id-1b", url = "http://a.com/2", logo = "logo-1b",
                httpUserAgent = "UA-2",
            ),
            // 重复行：同一地址再出现一次，带不同的 UA——应只留一路，
            // 且留的是先出现那路的属性
            IptvParser.ChannelItem(
                groupName = "央视", name = "CCTV-1", epgName = "EPG-1C",
                tvgId = "id-1c", url = "http://a.com/1", logo = "logo-1c",
                httpUserAgent = "UA-dup",
            ),
            // 同名频道在另一个分组：各留一份，互不干扰
            IptvParser.ChannelItem(
                groupName = "卫视", name = "CCTV-1", epgName = "EPG-1W",
                tvgId = "id-2", url = "http://b.com/1",
            ),
        )

        val result = items.toChannelGroupList()

        assertEquals(2, result.size)
        assertEquals("央视", result[0].name)
        assertEquals("卫视", result[1].name)

        val cctv1 = result[0].channelList.single()
        assertEquals("CCTV-1", cctv1.name)
        // epgName 走标准名映射：只断言先赢哪一个，不钉映射本身的值
        assertEquals(ChannelAlias.standardChannelName("EPG-1A"), cctv1.epgName)
        assertEquals("id-1", cctv1.tvgId)
        assertEquals("logo-1", cctv1.logo)
        assertEquals(2, cctv1.lineList.size)
        assertEquals("http://a.com/1", cctv1.lineList[0].url)
        assertEquals("UA-1", cctv1.lineList[0].httpUserAgent)
        assertEquals("http://a.com/2", cctv1.lineList[1].url)
        assertEquals("UA-2", cctv1.lineList[1].httpUserAgent)

        val cctv1OtherGroup = result[1].channelList.single()
        assertEquals("CCTV-1", cctv1OtherGroup.name)
        assertEquals("id-2", cctv1OtherGroup.tvgId)
        assertEquals(1, cctv1OtherGroup.lineList.size)
        assertEquals("http://b.com/1", cctv1OtherGroup.lineList[0].url)
    }

    @Test
    fun toChannelGroupList_hybridTypeWebView_survivesGrouping() {
        // 映射里 WebView -> WebView、None -> None 各走一支：WebView 线路
        // 映射错了会悄悄走直链播放器、网页打不开；回放类型也要原样带过去
        val items = listOf(
            IptvParser.ChannelItem(
                groupName = "央视", name = "CCTV-1", url = "http://a.com/1",
                hybridType = IptvParser.ChannelItem.HybridType.WebView,
                playbackType = 1,
                playbackFormat = "?stime=\${(b)yyyyMMddHHmmss}",
            ),
            IptvParser.ChannelItem(
                groupName = "央视", name = "CCTV-1", url = "http://a.com/2",
                hybridType = IptvParser.ChannelItem.HybridType.None,
            ),
        )

        val result = items.toChannelGroupList()
        val cctv1 = result[0].channelList.single()
        assertEquals(2, cctv1.lineList.size)
        assertEquals(ChannelLine.HybridType.WebView, cctv1.lineList[0].hybridType)
        assertEquals(1, cctv1.lineList[0].playbackType)
        assertEquals(
            "?stime=\${(b)yyyyMMddHHmmss}",
            cctv1.lineList[0].playbackFormat,
        )
        assertEquals(ChannelLine.HybridType.None, cctv1.lineList[1].hybridType)
    }

    @Test
    fun parseThenGroup_sameNameDuplicateRows_firstRowWins() = runBlocking {
        // 端到端版（天光 1932 轮姊妹检查）：811 轮只钉了分组函数本身，
        // 没走解析器——同名频道两行、tvg-name 与 tvg-id 各不相同时，
        // 解析出来的两条 ChannelItem 合并后，频道名与 tvg-id 取第一条的、
        // 地址两条都留着。只断言先赢哪一个，不钉标准名映射本身的值
        val data = """
            #EXTM3U
            #EXTINF:-1 tvg-id="cctv1" tvg-name="CCTV综合" group-title="央视",CCTV-1
            http://example.com/cctv1.m3u8
            #EXTINF:-1 tvg-id="cctv1-hd" tvg-name="CCTV-1 高清" group-title="央视",CCTV-1
            http://example.com/cctv1-hd.m3u8
        """.trimIndent()

        val result = M3uIptvParser().parse(data).toChannelGroupList()

        assertEquals(1, result.size)
        assertEquals("央视", result[0].name)
        val cctv1 = result[0].channelList.single()
        assertEquals("CCTV-1", cctv1.name)
        assertEquals(ChannelAlias.standardChannelName("CCTV综合"), cctv1.epgName)
        assertEquals("cctv1", cctv1.tvgId)
        assertEquals(2, cctv1.lineList.size)
        assertEquals("http://example.com/cctv1.m3u8", cctv1.lineList[0].url)
        assertEquals("http://example.com/cctv1-hd.m3u8", cctv1.lineList[1].url)
    }

    @Test
    fun parseThenGroup_interleavedGroups_mergeIntoFirstAppearanceOrder() = runBlocking {
        // 分组交错出现：拼接过的源常把同一分组写在两处；groupBy 按首次出现
        // 顺序拼组，后出现的同名分组合并进前面那组、频道按出现顺序排——
        // 之前只测过分组整块连续的源（天光 2154 轮同款）
        val data = """
            #EXTM3U
            #EXTINF:-1 tvg-name="CCTV-1" group-title="央视",CCTV-1
            http://example.com/cctv1.m3u8
            #EXTINF:-1 tvg-name="湖南卫视" group-title="卫视",湖南卫视
            http://example.com/hunan.m3u8
            #EXTINF:-1 tvg-name="CCTV-5" group-title="央视",CCTV-5
            http://example.com/cctv5.m3u8
        """.trimIndent()

        val result = M3uIptvParser().parse(data).toChannelGroupList()

        assertEquals(2, result.size)
        assertEquals("央视", result[0].name)
        assertEquals(2, result[0].channelList.size)
        assertEquals("CCTV-1", result[0].channelList[0].name)
        assertEquals("CCTV-5", result[0].channelList[1].name)
        assertEquals("卫视", result[1].name)
        assertEquals(1, result[1].channelList.size)
        assertEquals("湖南卫视", result[1].channelList[0].name)
    }
}
