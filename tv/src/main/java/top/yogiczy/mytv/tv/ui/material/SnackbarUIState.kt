package top.yogiczy.mytv.tv.ui.material

import androidx.compose.animation.core.AnimationConstants
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import java.util.UUID

@Stable
class SnackbarUIState(
    private val coroutineScope: CoroutineScope,
) {
    private var _visible by mutableStateOf(false)
    val visible get() = _visible

    private var _currentData by mutableStateOf(SnackbarData(text = ""))
    val currentData get() = _currentData

    private var showJob: Job? = null

    fun show(text: String, data: SnackbarData = SnackbarData()) {
        // 连发时旧的那次可能还卡在等旧条目收起的延迟里：不取消的话它会
        // 在新消息已经显示出来之后再把旧文案写回来（比如收藏键 300 毫秒
        // 内连按三次，最后显示的会是中间那次、和实际状态反过来）；
        // 先取消旧任务，最后一次 show 一定赢
        showJob?.cancel()
        showJob = coroutineScope.launch {
            if (_visible && _currentData.id != data.id) {
                _visible = false
                delay(AnimationConstants.DefaultDurationMillis.toLong())
            }

            _currentData = data.copy(text = text)
            _visible = true
            channel.trySend(data.duration)
        }
    }

    private val channel = Channel<Long>(Channel.CONFLATED)

    @OptIn(FlowPreview::class)
    suspend fun observe() {
        channel.consumeAsFlow().debounce { it }.collect { _visible = false }
    }

    companion object {
        // 全局单例：由 rememberSnackbarUIState() 在 SideEffect 里登记，
        // 只在组合成功提交后写，见该函数注释
        lateinit var I: SnackbarUIState

        fun isInitialized() = ::I.isInitialized
    }
}

@Composable
fun rememberSnackbarUIState(): SnackbarUIState {
    val coroutineScope = rememberCoroutineScope()

    val state = remember { SnackbarUIState(coroutineScope) }
    // 全局单例只在组合提交后登记：直接在组合里写外部状态违反 Compose
    // 规则——组合可能被丢弃重跑，写进去的可能是没提交的那次组合的实例；
    // SideEffect 只在组合成功提交后执行，登记的一定是生效的实例
    SideEffect { SnackbarUIState.I = state }
    LaunchedEffect(state) { state.observe() }
    return state
}

data class SnackbarData(
    val text: String = "",
    val showLeadingIcon: Boolean = true,
    val leadingIcon: ImageVector = Icons.Outlined.Info,
    val leadingLoading: Boolean = false,
    val showTrailingIcon: Boolean = false,
    val trailingIcon: ImageVector = Icons.Outlined.Info,
    val trailingLoading: Boolean = false,
    val type: SnackbarType = SnackbarType.DEFAULT,
    val duration: Long = DEFAULT_DURATION,
    val id: String = UUID.randomUUID().toString(),
) {
    companion object {
        const val DEFAULT_DURATION = 2300L
    }
}

enum class SnackbarType {
    DEFAULT, PRIMARY, SECONDARY, TERTIARY, ERROR
}

data class SnackbarColorData(
    val containerColors: Color,
    val contentColor: Color,
    val iconContainerColors: Color,
)

object Snackbar {
    fun show(text: String, data: SnackbarData) {
        if (!SnackbarUIState.isInitialized()) return

        SnackbarUIState.I.show(text, data)
    }

    fun show(
        text: String,
        showLeadingIcon: Boolean = true,
        leadingIcon: ImageVector = Icons.Outlined.Info,
        leadingLoading: Boolean = false,
        showTrailingIcon: Boolean = false,
        trailingIcon: ImageVector = Icons.Outlined.Info,
        trailingLoading: Boolean = false,
        type: SnackbarType = SnackbarType.DEFAULT,
        duration: Long = SnackbarData.DEFAULT_DURATION,
        id: String = UUID.randomUUID().toString(),
    ) {
        if (!SnackbarUIState.isInitialized()) return

        SnackbarUIState.I.show(
            text = text,
            data = SnackbarData(
                showLeadingIcon = showLeadingIcon,
                leadingIcon = leadingIcon,
                leadingLoading = leadingLoading,
                showTrailingIcon = showTrailingIcon,
                trailingIcon = trailingIcon,
                trailingLoading = trailingLoading,
                type = type,
                duration = duration,
                id = id,
            )
        )
    }
}