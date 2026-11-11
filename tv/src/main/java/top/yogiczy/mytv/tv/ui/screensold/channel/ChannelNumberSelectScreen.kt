package top.yogiczy.mytv.tv.ui.screensold.channel

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.debounce
import top.yogiczy.mytv.tv.ui.rememberChildPadding
import top.yogiczy.mytv.tv.ui.screensold.channel.components.ChannelNumber
import top.yogiczy.mytv.tv.ui.theme.MyTvTheme

@Composable
fun ChannelNumberSelectScreen(
    modifier: Modifier = Modifier,
    channelNumberProvider: () -> String = { "" },
) {
    val childPadding = rememberChildPadding()

    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(top = childPadding.top, end = childPadding.end)
    ) {
        ChannelNumber(
            modifier = Modifier.align(Alignment.TopEnd),
            channelNumberProvider = channelNumberProvider,
        )
    }
}

@Preview(device = "id:Android TV (720p)")
@Composable
private fun ChannelNumberSelectScreenPreview() {
    MyTvTheme {
        ChannelNumberSelectScreen(
            channelNumberProvider = { "01" },
        )
    }
}

@Stable
class ChannelNumberSelectState(
    initialChannelNumber: String = "",
    private val onConfirm: (String) -> Unit = {},
) {
    private var _channelNumber by mutableStateOf(initialChannelNumber)
    val channelNumber get() = _channelNumber

    fun input(no: Int) {
        // 最多 4 位：debounce 按 4 位设计（第 4 位后立即确认）；不封顶时
        // 按键连发可攒出 5 位以上数字，debounce 超时变负数抛异常，攒到
        // 10 位以上确认处的 toInt() 也会抛异常闪退
        if (_channelNumber.length >= 4) return
        _channelNumber += no.toString()
        channel.trySend(_channelNumber)
    }

    private val channel = Channel<String>(Channel.CONFLATED)

    @OptIn(FlowPreview::class)
    suspend fun observe() {
        channel.consumeAsFlow().debounce { maxOf(0L, (4 - it.length) * 1000L) }.collect {
            onConfirm(it)
            _channelNumber = ""
        }
    }
}

@Composable
fun rememberChannelNumberSelectState(
    initialChannelNumber: String = "",
    onConfirm: (String) -> Unit = {},
) = remember {
    ChannelNumberSelectState(initialChannelNumber, onConfirm)
}.also { LaunchedEffect(it) { it.observe() } }