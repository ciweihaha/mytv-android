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
         * 是否正在直播
         */
        fun EpgProgramme.isLive() = System.currentTimeMillis() in startAt..<endAt

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