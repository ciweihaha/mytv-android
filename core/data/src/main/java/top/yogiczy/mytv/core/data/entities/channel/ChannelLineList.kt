package top.yogiczy.mytv.core.data.entities.channel

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable
import kotlin.math.max
import kotlin.math.min

/**
 * 频道线路列表
 */
@Serializable
@Immutable
data class ChannelLineList(
    val value: List<ChannelLine> = emptyList(),
) : List<ChannelLine> by value {
    /**
     * 把线路下标环绕到合法范围：超出从头/尾环绕；空列表返回 0。
     * 原先调用方写的是 `(lineIdx + size) % size`，size 为 0 时除零崩溃。
     */
    fun resolveLineIdx(lineIdx: Int): Int {
        if (isEmpty()) return 0
        return max(0, min((lineIdx + size) % size, size - 1))
    }

    companion object {
        val EXAMPLE = ChannelLineList(List(10) { i ->
            ChannelLine.EXAMPLE.copy(
                url = "http://1.2.3.$i",
                httpUserAgent = "okhttp $i",
            )
        })
    }
}