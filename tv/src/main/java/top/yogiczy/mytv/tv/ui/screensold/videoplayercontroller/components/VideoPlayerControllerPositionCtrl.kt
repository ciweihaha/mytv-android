package top.yogiczy.mytv.tv.ui.screensold.videoplayercontroller.components

import androidx.annotation.IntRange
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.KeyboardDoubleArrowLeft
import androidx.compose.material.icons.filled.KeyboardDoubleArrowRight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.debounce
import top.yogiczy.mytv.tv.ui.material.ProgressBar
import top.yogiczy.mytv.tv.ui.theme.MyTvTheme
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

@Composable
fun VideoPlayerControllerPositionCtrl(
    modifier: Modifier = Modifier,
    currentPositionProvider: () -> Long = { 0L },
    durationProvider: () -> Pair<Long, Long> = { 0L to 0L },
    /**
     * 相对位移 seek（毫秒，可正可负）：控制条的 ±1/±10 分钟按钮只动位移。
     * 不能传“节目内偏移”：直播模式下播放器时间轴的零点是直播窗口起点，
     * 不是节目开始时间，两者差几分钟到几小时，传偏移会跳错时间；
     * 传位移则两种时间轴都对得上（见 VideoPlayer.seekBy）。
     */
    onSeekBy: (Long) -> Unit = {},
) {
    var seekToPosition by remember { mutableStateOf<Long?>(null) }

    val debounce = rememberDebounce(
        wait = 1000L,
        func = {
            seekToPosition?.let { nnSeekToPosition ->
                // 只传相对位移：目标位置 − 当前位置；播放器在自己的时间轴上
                // 加这个位移，窗口起点在哪都不影响。防抖 1 秒内当前位置最
                // 多走 1 秒，误差可忽略。
                onSeekBy(nnSeekToPosition - currentPositionProvider())
                seekToPosition = null
            }
        },
    )
    LaunchedEffect(seekToPosition) {
        if (seekToPosition != null) debounce.active()
    }

    fun seekForward(ms: Long) {
        val currentPosition = currentPositionProvider()
        val startPosition = durationProvider().first
        seekToPosition = max(startPosition, (seekToPosition ?: currentPosition) - ms)
    }

    fun seekNext(ms: Long) {
        val currentPosition = currentPositionProvider()
        val endPosition = durationProvider().second
        seekToPosition = min(
            if (endPosition <= 0L) Long.MAX_VALUE else min(endPosition, System.currentTimeMillis()),
            (seekToPosition ?: currentPosition) + ms
        )
    }

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        VideoPlayerControllerBtn(
            imageVector = Icons.Default.KeyboardDoubleArrowLeft,
            onSelect = { seekForward(1000L * 60 * 10) },
        )
        VideoPlayerControllerBtn(
            imageVector = Icons.Default.ChevronLeft,
            onSelect = { seekForward(1000L * 60 * 1) },
        )

        VideoPlayerControllerBtn(
            imageVector = Icons.Default.ChevronRight,
            onSelect = { seekNext(1000L * 60 * 1) },
        )
        VideoPlayerControllerBtn(
            imageVector = Icons.Default.KeyboardDoubleArrowRight,
            onSelect = { seekNext(1000L * 60 * 10) },
        )

        VideoPlayerControllerPositionProgress(
            modifier = Modifier.padding(start = 10.dp),
            currentPositionProvider = { seekToPosition ?: currentPositionProvider() },
            durationProvider = durationProvider,
        )
    }
}

@Composable
private fun VideoPlayerControllerPositionProgress(
    modifier: Modifier = Modifier,
    currentPositionProvider: () -> Long = { 0L },
    durationProvider: () -> Pair<Long, Long> = { 0L to 0L },
) {
    val currentPosition = currentPositionProvider()
    val duration = durationProvider()
    val timeFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = timeFormat.format(duration.first),
        )

        ProgressBar(
            // 零时长（没节目单的频道、无时长信息时起止相等）直接返回 0，
            // 当前位置在区间外时钳制到 0~1：原先直接相除，零时长会算出
            // NaN/Infinity、越界会算出负数或 >1，fillMaxWidth 要求比例
            // 在 0~1 之间，打开播放控制就会布局异常（与 EpgProgramme.progress
            // 第 26 轮同款问题，这里漏了）。
            process = run {
                val span = duration.second - duration.first
                if (span <= 0L) 0f
                else ((currentPosition - duration.first) / span.toFloat()).coerceIn(0f, 1f)
            },
            modifier = Modifier
                .weight(1f)
                .height(6.dp),
        )

        Text(
            text = "${timeFormat.format(currentPosition)} / ${timeFormat.format(duration.second)}",
        )
    }
}

@Stable
class Debounce internal constructor(
    @IntRange(from = 0) private val wait: Long,
    private val func: () -> Unit = {},
) {
    fun active() {
        channel.trySend(wait)
    }

    private val channel = Channel<Long>(Channel.CONFLATED)

    @OptIn(FlowPreview::class)
    suspend fun observe() {
        channel.consumeAsFlow().debounce { it }.collect {
            func()
        }
    }
}

@Composable
fun rememberDebounce(
    @IntRange(from = 0) wait: Long,
    func: () -> Unit = {},
) = remember { Debounce(wait = wait, func = func) }.also {
    LaunchedEffect(it) { it.observe() }
}

@Preview(device = "id:Android TV (720p)")
@Composable
private fun VideoPlayerControllerPositionCtrlPreview() {
    MyTvTheme {
        Box(modifier = Modifier.width(600.dp)) {
            VideoPlayerControllerPositionCtrl(
                currentPositionProvider = { System.currentTimeMillis() },
                durationProvider = {
                    System.currentTimeMillis() - 1000L * 60 * 60 to System.currentTimeMillis() + 1000L * 60 * 60
                },
            )
        }
    }
}
