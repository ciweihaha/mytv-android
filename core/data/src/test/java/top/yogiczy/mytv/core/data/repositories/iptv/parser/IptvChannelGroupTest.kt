package top.yogiczy.mytv.core.data.repositories.iptv.parser

import org.junit.Assert.assertEquals
import org.junit.Test
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
}
