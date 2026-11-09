package top.yogiczy.mytv.core.data.entities.channel

import org.junit.Assert.assertEquals
import org.junit.Test

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
}
