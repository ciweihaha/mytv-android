package top.yogiczy.mytv.core.data.entities.epg

import kotlinx.serialization.Serializable
import top.yogiczy.mytv.core.data.entities.channel.Channel
import top.yogiczy.mytv.core.data.utils.Logger
import kotlin.time.measureTimedValue

/**
 * 频道节目单
 */
@Serializable
data class Epg(
    /**
     * 节目单频道 id（XMLTV channel 的 id 属性）：与直播源 tvg-id 是
     * 同一套；频道名写法和节目单不一样时，节目单按名查不到，靠它兜底
     * 匹配；解析时不带 id 的节目单记空串，不参与按 id 匹配
     */
    val id: String = "",

    /**
     * 频道名称
     */
    val channelList: List<String> = emptyList(),

    /**
     * 图标
     */
    val logo: String? = null,

    /**
     * 节目列表
     */
    val programmeList: EpgProgrammeList = EpgProgrammeList(),
) {
    companion object {
        private val log = Logger.create("Epg")

        /**
         * 按开始时间排好节目：源文件不保证按时间顺序，而 recentProgramme
         * 的二分查找和节目单界面都依赖有序列表；sortedBy 是稳定排序，
         * 同一开始时间的节目保持原文顺序
         */
        fun Epg.sortedProgrammes(): Epg {
            return copy(programmeList = EpgProgrammeList(programmeList.sortedBy { it.startAt }))
        }

        /**
         * 当前与下一个节目。时间可传参、默认当前时间：跟同文件 isLive /
         * progress 同一个写法，单测才能钉住边界点。
         *
         * 边界是左闭右开、与 isLive() 一致：开始那一刻算正在播，结束那一刻
         * 算已播完。原来是二分查找加 `>` 判结束，相邻节目共用边界点时两
         * 个节目都算“命中”、随手返回哪一个都不一定；重叠的脏数据还会让
         * 二分查找直接错过正在播的节目。改成顺序找加 `>=` 判结束。
         */
        fun Epg.recentProgramme(now: Long = System.currentTimeMillis()): EpgProgrammeRecent {
            if (this == Epg()) return EpgProgrammeRecent()

            val t = measureTimedValue {
                // 顺序找，不用二分查找：节目单源头偶尔会给出互相重叠的节目，
                // 那时“正在播”在开始时间顺序里不是单调的，二分查找可能直接
                // 错过正在播的节目（返回正在播为空）；一天的节目只有几十条，
                // 顺序找的开销可以忽略
                val liveProgramIndex =
                    programmeList.indexOfFirst { now >= it.startAt && now < it.endAt }

                if (liveProgramIndex > -1) EpgProgrammeRecent(
                    now = programmeList[liveProgramIndex],
                    // 下一个节目跳过已经播完的重叠脏数据：直接取下一条会把
                    // 已经播完的节目显示成“稍后播放”；正常首尾相接的数据里
                    // 下一条本来就没播完，这条判断不改变行为
                    next = programmeList.drop(liveProgramIndex + 1)
                        .firstOrNull { it.endAt > now }
                )
                // 当前时间落在两个节目之间的空档（或当天第一个节目还没
                // 开始）：没有正在播的节目，但下一个节目是已知的；取开始
                // 时间在现在的第一个节目
                else EpgProgrammeRecent(
                    next = programmeList.firstOrNull { it.startAt > now }
                )
            }
            log.v("recentProgramme: ${channelList.firstOrNull()}", null, t.duration)

            return t.value
        }

        fun example(channel: Channel): Epg {
            return Epg(
                channelList = listOf(channel.epgName),
                programmeList = EpgProgrammeList(
                    List(100) { index ->
                        val startAt =
                            System.currentTimeMillis() - 3500 * 1000 * 24 * 2 + index * 3600 * 1000
                        EpgProgramme(
                            title = "${channel.epgName}节目${index + 1}",
                            startAt = startAt,
                            endAt = startAt + 3600 * 1000
                        )
                    }
                )
            )
        }

        fun empty(channel: Channel): Epg {
            return Epg(
                channelList = listOf(channel.epgName),
                programmeList = EpgProgrammeList(listOf(EpgProgramme.EMPTY))
            )
        }
    }
}