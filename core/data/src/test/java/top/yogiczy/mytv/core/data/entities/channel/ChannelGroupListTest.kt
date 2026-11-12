package top.yogiczy.mytv.core.data.entities.channel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import top.yogiczy.mytv.core.data.entities.channel.ChannelGroupList.Companion.chanelGroup
import top.yogiczy.mytv.core.data.entities.channel.ChannelGroupList.Companion.channelFirstOrNull
import top.yogiczy.mytv.core.data.entities.channel.ChannelGroupList.Companion.channelLastOrNull
import top.yogiczy.mytv.core.data.entities.channel.ChannelGroupList.Companion.getOrClamped

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
    fun channelFirstOrNull_skipsEmptyFirstGroup() {
        // 首个分组为空、后面分组有台：旧实现只看第一组返回 null，
        // 开机无上次播放时就兜底成 Channel.EMPTY
        val c2 = Channel.EXAMPLE.copy(name = "C2")
        val list = ChannelGroupList(
            listOf(
                ChannelGroup(name = "空组", channelList = ChannelList()),
                ChannelGroup(name = "B", channelList = ChannelList(listOf(c2))),
            )
        )

        assertEquals("C2", list.channelFirstOrNull()?.name)
    }

    @Test
    fun channelLastOrNull_skipsEmptyLastGroup() {
        val c1 = Channel.EXAMPLE.copy(name = "C1")
        val list = ChannelGroupList(
            listOf(
                ChannelGroup(name = "A", channelList = ChannelList(listOf(c1))),
                ChannelGroup(name = "空组", channelList = ChannelList()),
            )
        )

        assertEquals("C1", list.channelLastOrNull()?.name)
    }

    @Test
    fun channelFirstOrNull_allGroupsEmpty_returnsNull() {
        val list = ChannelGroupList(
            listOf(
                ChannelGroup(name = "空组1", channelList = ChannelList()),
                ChannelGroup(name = "空组2", channelList = ChannelList()),
            )
        )

        assertNull(list.channelFirstOrNull())
        assertNull(list.channelLastOrNull())
    }

    @Test
    fun getOrClamped_inRange_returnsThatGroup() {
        val list = ChannelGroupList(
            listOf(
                ChannelGroup(name = "A"),
                ChannelGroup(name = "B"),
            )
        )

        assertEquals("A", list.getOrClamped(0).name)
        assertEquals("B", list.getOrClamped(1).name)
    }

    @Test
    fun getOrClamped_outOfRange_clampsIntoRange() {
        // 换订阅源后分组变少、收藏分组被删光：记住的序号越界就钳到
        // 合法范围内再取，别显示空白；大了取最后一个、负了取第一个
        val list = ChannelGroupList(
            listOf(
                ChannelGroup(name = "A"),
                ChannelGroup(name = "B"),
            )
        )

        assertEquals("B", list.getOrClamped(5).name)
        assertEquals("A", list.getOrClamped(-1).name)
    }

    @Test
    fun getOrClamped_emptyList_returnsEmptyGroup() {
        assertEquals(ChannelGroup(), ChannelGroupList().getOrClamped(0))
        assertEquals(ChannelGroup(), ChannelGroupList().getOrClamped(3))
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
