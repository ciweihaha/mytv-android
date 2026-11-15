package top.yogiczy.mytv.tv.ui.screen.main

import org.junit.Assert.assertEquals
import org.junit.Test
import top.yogiczy.mytv.core.data.entities.channel.Channel
import top.yogiczy.mytv.core.data.entities.channel.ChannelGroup
import top.yogiczy.mytv.core.data.entities.channel.ChannelGroupList
import top.yogiczy.mytv.core.data.entities.channel.ChannelLine
import top.yogiczy.mytv.core.data.entities.channel.ChannelLineList
import top.yogiczy.mytv.core.data.entities.channel.ChannelList

class MainViewModelTest {
    private fun channel(name: String, standardName: String, vararg urls: String): Channel {
        return Channel(
            name = name,
            standardName = standardName,
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
}
