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

        // 按列表现查当前台号（两位补零）：当前台不在列表里（切源后上次播的
        // 台不在新源里、或所在分组被隐藏）时下标是 -1，显示“–”，不用频道
        // 对象自带的序号（切源后是旧号）；选台页右上角、底部频道信息、
        // 经典选台页三处共用，之前是三份一模一样的写法
        fun ChannelGroupList.channelNumber(channel: Channel): String {
            val idx = channelIdx(channel)
            return if (idx < 0) "–" else (idx + 1).toString().padStart(2, '0')
        }

        // 首个分组为空（组内频道被过滤/删光）时不能直接返回 null：
        // 后面分组还有台，旧写法只看第一组、开机无上次播放时就兜底成
        // Channel.EMPTY，切台守卫也拿不到台；跳过空分组再取
        fun ChannelGroupList.channelFirstOrNull() =
            this.firstOrNull { it.channelList.isNotEmpty() }?.channelList?.firstOrNull()

        // 同上：末尾分组为空时取倒数第一个非空分组的末台
        fun ChannelGroupList.channelLastOrNull() =
            this.lastOrNull { it.channelList.isNotEmpty() }?.channelList?.lastOrNull()

        // 记住的分组序号可能越界，钳到合法范围内再取，别显示空白：
        // 换订阅源后分组变少（全部频道页）、当前分组被删光（收藏页在
        // B 源分组里把最后一个收藏取消掉，分组列表缩回只剩“全部”和 A）；
        // 大了取最后一个、负了取第一个；空列表回落一个空分组
        fun ChannelGroupList.getOrClamped(index: Int): ChannelGroup {
            val safeIdx = index.coerceIn(0, (size - 1).coerceAtLeast(0))
            return getOrElse(safeIdx) { ChannelGroup() }
        }
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