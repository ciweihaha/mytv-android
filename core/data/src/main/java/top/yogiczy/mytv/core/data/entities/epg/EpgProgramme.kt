package top.yogiczy.mytv.core.data.entities.epg

import kotlinx.serialization.Serializable
import java.util.Calendar
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * 频道节目
 */
@Serializable
data class EpgProgramme(
    /**
     * 开始时间（时间戳）
     */
    val startAt: Long = 0,

    /**
     * 结束时间（时间戳）
     */
    val endAt: Long = 0,

    /**
     * 节目名称
     */
    val title: String = "",
) {
    companion object {
        /**
         * 是否正在直播：左闭右开（开始 <= 现在 < 结束），与
         * EpgList.hasLiveProgramme 一致。时间可传参：默认用当前
         * 时间，单测传固定时间才能钉死开始/结束正好等于现在的
         * 边界（否则 wall clock 在构造节目与调用之间走动，边界
         * 永远钉不住）
         */
        fun EpgProgramme.isLive(now: Long = System.currentTimeMillis()) = now in startAt..<endAt

        /**
         * 节目进度（0~1）。时长 <= 0（EPG 里缺 start/stop、解析失败会得 0）
         * 时返回 0；当前时间在节目范围外时钳制到 0/1：原先直接相除，
         * 零时长会算出 NaN/Infinity、越界会算出负数或 >1，调用方拿它当
         * fillMaxWidth 的比例会布局异常。
         */
        fun EpgProgramme.progress(current: Long = System.currentTimeMillis()): Float {
            val duration = endAt - startAt
            if (duration <= 0) return 0f
            return ((current - startAt).toFloat() / duration).coerceIn(0f, 1f)
        }

        fun EpgProgramme.remainingMinutes(current: Long = System.currentTimeMillis()) =
            ceil((endAt - current) / 60_000f).roundToInt().coerceAtLeast(0)

        /**
         * 时间是否有效：EPG 里缺 start/stop、解析失败时 parseTime 得 0，
         * 会产出开始/结束为 0 或结束 <= 开始的节目；这类节目进节目单面板
         * 会单独成一个 1970-01-01 的日期分组、还挂着“回放”让人点，点了
         * 是拿 1970 年的时间戳去拼回放地址、播不了。节目单面板只显示有效的。
         */
        fun EpgProgramme.isValid() = startAt > 0 && endAt > startAt

        val EXAMPLE = EpgProgramme(
            startAt = System.currentTimeMillis() - 3600 * 1000,
            endAt = System.currentTimeMillis() + 3600 * 1000,
            title = "节目标题",
        )

        val EMPTY by lazy {
            val calendar = Calendar.getInstance()
            calendar.set(Calendar.HOUR_OF_DAY, 0)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)

            EpgProgramme(
                startAt = calendar.timeInMillis,
                endAt = calendar.timeInMillis + (24 * 3600 - 1) * 1000,
                title = "精彩节目",
            )
        }
    }
}