package top.yogiczy.mytv.core.data.entities.channel

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

/**
 * 频道分组列表
 */
@Serializable
@Immutable
data class ChannelGroupList(
    val value: List<ChannelGroup> = emptyList(),
) : List<ChannelGroup> by value {
    companion object {
        val EXAMPLE = ChannelGroupList(List(20) { groupIdx ->
            ChannelGroup(
                name = "频道分组${groupIdx + 1}",
                channelList = ChannelList(
                    List(20) { idx ->
                        Channel.EXAMPLE.copy(
                            name = "频道${groupIdx + 1}-${idx + 1}",
                            epgName = "频道${groupIdx + 1}-${idx + 1}",
                        )
                    },
                )
            )
        })

        fun ChannelGroupList.channelGroupIdx(channel: Channel) =
            this.indexOfFirst { group -> group.channelList.any { it == channel } }

        fun ChannelGroupList.chanelGroup(channel: Channel) = this[channelGroupIdx(channel)]

        fun ChannelGroupList.channelIdx(channel: Channel) =
            channelList.indexOfFirst { it == channel }

        fun ChannelGroupList.channelFirstOrNull() = this.firstOrNull()?.channelList?.firstOrNull()

        fun ChannelGroupList.channelLastOrNull() = this.lastOrNull()?.channelList?.lastOrNull()

        val ChannelGroupList.channelList: ChannelList
            get() = ChannelList(this.asSequence().flatMap { it.channelList.asSequence() }.toList())
    }

    fun withMetadata(): ChannelGroupList {
        // 旧实现每组都重算一次全表（flatMap + associate），O(G×N)；
        // 且按值建 map 时同名同线路的重复频道都取最后一个下标，显示的
        // 台号与数字选台（按位置取）对不上。改成一次遍历按位置编号。
        var index = 0
        return ChannelGroupList(map { group ->
            group.copy(channelList = ChannelList(group.channelList.map { channel ->
                channel.copy(index = index++)
            }))
        })
    }
}