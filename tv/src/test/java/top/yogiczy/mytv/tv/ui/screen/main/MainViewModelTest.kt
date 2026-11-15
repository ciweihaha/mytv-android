package top.yogiczy.mytv.tv.ui.screen.main

import org.junit.Assert.assertEquals
import org.junit.Test
import top.yogiczy.mytv.core.data.entities.channel.Channel
import top.yogiczy.mytv.core.data.entities.channel.ChannelFavorite
import top.yogiczy.mytv.core.data.entities.channel.ChannelFavoriteList
import top.yogiczy.mytv.core.data.entities.channel.ChannelGroup
import top.yogiczy.mytv.core.data.entities.channel.ChannelGroupList
import top.yogiczy.mytv.core.data.entities.channel.ChannelLine
import top.yogiczy.mytv.core.data.entities.channel.ChannelLineList
import top.yogiczy.mytv.core.data.entities.channel.ChannelList
import top.yogiczy.mytv.tv.ui.utils.Configs

class MainViewModelTest {
    private fun channel(
        name: String,
        standardName: String,
        vararg urls: String,
        // epgName 默认跟名字走：网页混合线路按 epgName 查表，留空就查不到
        epgName: String = name,
    ): Channel {
        return Channel(
            name = name,
            standardName = standardName,
            epgName = epgName,
            lineList = ChannelLineList(urls.map { ChannelLine(url = it) }),
        )
    }

    @Test
    fun mergeSimilarChannels_singleChannelPerStandardName_unchanged() {
        val input = ChannelGroupList(
            listOf(
                ChannelGroup(
                    "央视",
                    ChannelList(
                        listOf(
                            channel("CCTV-1", "CCTV-1", "http://a.com/1"),
                            channel("CCTV-2", "CCTV-2", "http://a.com/2"),
                        )
                    ),
                )
            )
        )

        assertEquals(input, mergeSimilarChannels(input))
    }

    @Test
    fun mergeSimilarChannels_sameStandardName_mergesLines() {
        val input = ChannelGroupList(
            listOf(
                ChannelGroup(
                    "央视",
                    ChannelList(
                        listOf(
                            channel("CCTV-1 高清", "CCTV-1", "http://a.com/1"),
                            channel("CCTV-1 标清", "CCTV-1", "http://b.com/1"),
                        )
                    ),
                )
            )
        )

        val result = mergeSimilarChannels(input)

        assertEquals(1, result[0].channelList.size)
        val merged = result[0].channelList[0]
        assertEquals("CCTV-1", merged.name)
        assertEquals(
            listOf("http://a.com/1", "http://b.com/1"),
            merged.lineList.map { it.url },
        )
        // 合并后线路名取原频道名，选台页切线路时还能认出是哪路
        assertEquals(
            listOf("CCTV-1 高清", "CCTV-1 标清"),
            merged.lineList.map { it.name },
        )
    }

    @Test
    fun mergeSimilarChannels_sharedUrl_deduplicated() {
        val input = ChannelGroupList(
            listOf(
                ChannelGroup(
                    "央视",
                    ChannelList(
                        listOf(
                            channel("CCTV-1 高清", "CCTV-1", "http://a.com/1"),
                            channel("CCTV-1 标清", "CCTV-1", "http://a.com/1"),
                        )
                    ),
                )
            )
        )

        val result = mergeSimilarChannels(input)

        assertEquals(1, result[0].channelList.size)
        val merged = result[0].channelList[0]
        assertEquals(1, merged.lineList.size)
        assertEquals("http://a.com/1", merged.lineList[0].url)
        // 去重留先到的那条，线路名就是先到那路的频道名
        assertEquals("CCTV-1 高清", merged.lineList[0].name)
    }

    @Test
    fun mergeSimilarChannels_otherGroups_untouched() {
        val input = ChannelGroupList(
            listOf(
                ChannelGroup(
                    "央视",
                    ChannelList(
                        listOf(
                            channel("CCTV-1 高清", "CCTV-1", "http://a.com/1"),
                            channel("CCTV-1 标清", "CCTV-1", "http://b.com/1"),
                        )
                    ),
                ),
                ChannelGroup(
                    "卫视",
                    ChannelList(
                        listOf(
                            channel("湖南卫视", "湖南卫视", "http://a.com/hunan"),
                        )
                    ),
                ),
            )
        )

        val result = mergeSimilarChannels(input)

        assertEquals(2, result.size)
        assertEquals(1, result[0].channelList.size)
        assertEquals("CCTV-1", result[0].channelList[0].name)
        assertEquals(1, result[1].channelList.size)
        assertEquals("湖南卫视", result[1].channelList[0].name)
        assertEquals("http://a.com/hunan", result[1].channelList[0].lineList[0].url)
    }

    @Test
    fun refreshFavoriteChannels_otherSource_unchanged() {
        val favorites = ChannelFavoriteList(
            listOf(
                ChannelFavorite(
                    channel = channel("CCTV-1", "CCTV-1", "http://old.com/1"),
                    iptvSourceName = "源B",
                    groupName = "央视",
                )
            )
        )
        val groups = ChannelGroupList(
            listOf(
                ChannelGroup(
                    "央视",
                    ChannelList(listOf(channel("CCTV-1", "CCTV-1", "http://new.com/1"))),
                )
            )
        )

        // 刷的是源A的单，别家源的收藏一根指头都别动
        assertEquals(favorites, refreshFavoriteChannels(favorites, "源A", groups))
    }

    @Test
    fun refreshFavoriteChannels_found_swapsToNewChannel() {
        val favorites = ChannelFavoriteList(
            listOf(
                ChannelFavorite(
                    channel = channel("CCTV-1", "CCTV-1", "http://old.com/1"),
                    iptvSourceName = "源A",
                    groupName = "央视",
                )
            )
        )
        val groups = ChannelGroupList(
            listOf(
                ChannelGroup(
                    "央视",
                    ChannelList(listOf(channel("CCTV-1", "CCTV-1", "http://new.com/1"))),
                )
            )
        )

        val result = refreshFavoriteChannels(favorites, "源A", groups)

        // 找得到就换成新源里的频道对象，线路跟着新源走
        assertEquals("http://new.com/1", result[0].channel.lineList[0].url)
        assertEquals("源A", result[0].iptvSourceName)
        assertEquals("央视", result[0].groupName)
    }

    @Test
    fun refreshFavoriteChannels_groupMissing_keepsOldChannel() {
        val favorites = ChannelFavoriteList(
            listOf(
                ChannelFavorite(
                    channel = channel("CCTV-1", "CCTV-1", "http://old.com/1"),
                    iptvSourceName = "源A",
                    groupName = "央视",
                )
            )
        )
        val groups = ChannelGroupList(
            listOf(
                ChannelGroup(
                    "卫视",
                    ChannelList(listOf(channel("湖南卫视", "湖南卫视", "http://new.com/hunan"))),
                )
            )
        )

        // 新源里没这个分组：留旧频道、不丢收藏
        val result = refreshFavoriteChannels(favorites, "源A", groups)
        assertEquals("http://old.com/1", result[0].channel.lineList[0].url)
    }

    @Test
    fun refreshFavoriteChannels_channelMissing_keepsOldChannel() {
        val favorites = ChannelFavoriteList(
            listOf(
                ChannelFavorite(
                    channel = channel("CCTV-1", "CCTV-1", "http://old.com/1"),
                    iptvSourceName = "源A",
                    groupName = "央视",
                )
            )
        )
        val groups = ChannelGroupList(
            listOf(
                ChannelGroup(
                    "央视",
                    ChannelList(listOf(channel("CCTV-2", "CCTV-2", "http://new.com/2"))),
                )
            )
        )

        // 分组在、台不在：同样留旧频道、不丢收藏
        val result = refreshFavoriteChannels(favorites, "源A", groups)
        assertEquals("http://old.com/1", result[0].channel.lineList[0].url)
    }

    @Test
    fun hybridChannels_disable_returnsInput() {
        val input = ChannelGroupList(
            listOf(
                ChannelGroup(
                    "卫视",
                    ChannelList(listOf(channel("江西卫视", "江西卫视", "http://a.com/jx"))),
                )
            )
        )

        assertEquals(input, hybridChannels(input, Configs.IptvHybridMode.DISABLE))
    }

    @Test
    fun hybridChannels_iptvFirst_putsWebViewLinesAfter() {
        val input = ChannelGroupList(
            listOf(
                ChannelGroup(
                    "卫视",
                    ChannelList(listOf(channel("江西卫视", "江西卫视", "http://a.com/jx"))),
                )
            )
        )

        val result = hybridChannels(input, Configs.IptvHybridMode.IPTV_FIRST)

        // 直播线在前、两条网页线在后（江西卫视在网页混合表里有两条）
        assertEquals(
            listOf(
                "http://a.com/jx",
                "https://www.jxntv.cn/live/#/jxtv1",
                "https://yangshipin.cn/tv/home?pid=600002503",
            ),
            result[0].channelList[0].lineList.map { it.url },
        )
    }

    @Test
    fun hybridChannels_hybridFirst_putsWebViewLinesBefore() {
        val input = ChannelGroupList(
            listOf(
                ChannelGroup(
                    "卫视",
                    ChannelList(listOf(channel("江西卫视", "江西卫视", "http://a.com/jx"))),
                )
            )
        )

        val result = hybridChannels(input, Configs.IptvHybridMode.HYBRID_FIRST)

        assertEquals(
            listOf(
                "https://www.jxntv.cn/live/#/jxtv1",
                "https://yangshipin.cn/tv/home?pid=600002503",
                "http://a.com/jx",
            ),
            result[0].channelList[0].lineList.map { it.url },
        )
    }

    @Test
    fun hybridChannels_channelWithoutWebViewLines_unchanged() {
        val input = ChannelGroupList(
            listOf(
                ChannelGroup(
                    "央视",
                    ChannelList(listOf(channel("CCTV-1", "CCTV-1", "http://a.com/1"))),
                )
            )
        )

        // CCTV-1 不在网页混合表里：两种非禁用模式下线路都不变
        val iptvFirst = hybridChannels(input, Configs.IptvHybridMode.IPTV_FIRST)
        assertEquals(listOf("http://a.com/1"), iptvFirst[0].channelList[0].lineList.map { it.url })
        val hybridFirst = hybridChannels(input, Configs.IptvHybridMode.HYBRID_FIRST)
        assertEquals(listOf("http://a.com/1"), hybridFirst[0].channelList[0].lineList.map { it.url })
    }
}
