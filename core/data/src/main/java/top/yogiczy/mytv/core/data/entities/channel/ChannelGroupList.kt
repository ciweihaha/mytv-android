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
    /**
     * 摊平后的全量频道列表：第一次访问时拼好记下来，之后直接拿同一份。
     * 之前是扩展属性，每访问一次就 flatMap 重拼一次；搜索每敲一个字、
     * 主界面取台号、源管理每项（先取个数再算线路数，连拼两次）都在拼。
     * data class 不可变，同一份实例摊平的结果永远不变，缓存安全；
     * copy() 出新实例时才重算。
     */
    val channelList: ChannelList by lazy {
        ChannelList(value.asSequence().flatMap { it.channelList.asSequence() }.toList())
    }

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

        // 找不到时返回 null，不要直接取下标：channelGroupIdx 找不到返回 -1，
        // 收藏切换传进来的频道若已过期（源刷新/切源后线路变了、分组被隐藏），
        // 旧写法 this[-1] 直接抛 IndexOutOfBoundsException 闪退
        fun ChannelGroupList.chanelGroup(channel: Channel) =
            getOrNull(channelGroupIdx(channel))

        fun ChannelGroupList.channelIdx(channel: Channel) =
            channelList.indexOfFirst { it == channel }

        fun ChannelGroupList.channelFirstOrNull() = this.firstOrNull()?.channelList?.firstOrNull()

        fun ChannelGroupList.channelLastOrNull() = this.lastOrNull()?.channelList?.lastOrNull()
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