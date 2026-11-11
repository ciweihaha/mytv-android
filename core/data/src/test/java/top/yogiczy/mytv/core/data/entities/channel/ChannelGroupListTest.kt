package top.yogiczy.mytv.core.data.entities.channel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import top.yogiczy.mytv.core.data.entities.channel.ChannelGroupList.Companion.chanelGroup

class ChannelGroupListTest {
    @Test
    fun withMetadata_assignsFlatPositions() {
        val list = ChannelGroupList(
            listOf(
                ChannelGroup(
                    name = "A",
                    channelList = ChannelList(
                        listOf(
                            Channel.EXAMPLE.copy(name = "C1"),
                            Channel.EXAMPLE.copy(name = "C2"),
                        )
                    ),
                ),
                ChannelGroup(
                    name = "B",
                    channelList = ChannelList(
                        listOf(Channel.EXAMPLE.copy(name = "C3"))
                    ),
                ),
            )
        )

        val result = list.withMetadata()
        assertEquals(0, result[0].channelList[0].index)
        assertEquals(1, result[0].channelList[1].index)
        assertEquals(2, result[1].channelList[0].index)
    }

    @Test
    fun withMetadata_duplicateChannelsGetOwnPositions() {
        // 同名同线路的重复频道：旧实现按值建 map，两者都取最后一个下标；
        // 数字选台按位置取，显示的台号应是各自的位置
        val dup = Channel.EXAMPLE.copy(name = "CCTV-1")
        val list = ChannelGroupList(
            listOf(
                ChannelGroup(name = "A", channelList = ChannelList(listOf(dup))),
                ChannelGroup(name = "B", channelList = ChannelList(listOf(dup))),
            )
        )

        val result = list.withMetadata()
        assertEquals(0, result[0].channelList[0].index)
        assertEquals(1, result[1].channelList[0].index)
    }

    @Test
    fun withMetadata_emptyList() {
        assertEquals(0, ChannelGroupList().withMetadata().size)
    }

    @Test
    fun chanelGroup_channelPresent_returnsItsGroup() {
        val channel = Channel.EXAMPLE.copy(name = "C1")
        val list = ChannelGroupList(
            listOf(
                ChannelGroup(name = "A", channelList = ChannelList(listOf(channel))),
                ChannelGroup(name = "B", channelList = ChannelList(listOf(Channel.EXAMPLE.copy(name = "C2")))),
            )
        )

        assertEquals("A", list.chanelGroup(channel)?.name)
    }

    @Test
    fun chanelGroup_channelAbsent_returnsNullInsteadOfThrowing() {
        // 源刷新/切源后线路变了、或分组被隐藏：收藏切换传进来的过期频道
        // 已不在当前列表里，旧实现 this[-1] 直接闪退
        val list = ChannelGroupList(
            listOf(
                ChannelGroup(name = "A", channelList = ChannelList(listOf(Channel.EXAMPLE.copy(name = "C1")))),
            )
        )

        assertNull(list.chanelGroup(Channel.EXAMPLE.copy(name = "不存在的频道")))
        assertNull(ChannelGroupList().chanelGroup(Channel.EXAMPLE))
    }
}
