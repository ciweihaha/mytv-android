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

        fun Epg.recentProgramme(): EpgProgrammeRecent {
            if (this == Epg()) return EpgProgrammeRecent()

            val t = measureTimedValue {
                val currentTime = System.currentTimeMillis()
                val liveProgramIndex = programmeList.binarySearch {
                    when {
                        currentTime < it.startAt -> 1
                        currentTime > it.endAt -> -1
                        else -> 0
                    }
                }

                if (liveProgramIndex > -1) EpgProgrammeRecent(
                    now = programmeList[liveProgramIndex],
                    next = programmeList.getOrNull(liveProgramIndex + 1)
                )
                // 当前时间落在两个节目之间的空档（或当天第一个节目还没
                // 开始）：没有正在播的节目，但下一个节目是已知的；原来
                // next 也返回空，选台页“稍后播放”只能显示“精彩节目”
                // 占位；二分查找返回的负值里藏着插入点，第一个开始时间
                // 在现在的节目就是下一个
                else EpgProgrammeRecent(
                    next = programmeList.getOrNull(-liveProgramIndex - 1)
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