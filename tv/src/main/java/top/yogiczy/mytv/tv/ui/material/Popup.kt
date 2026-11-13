package top.yogiczy.mytv.tv.ui.material

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.tv.material3.MaterialTheme
import top.yogiczy.mytv.core.data.utils.Logger
import top.yogiczy.mytv.tv.ui.utils.backHandler
import top.yogiczy.mytv.tv.ui.utils.clickableNoIndication
import top.yogiczy.mytv.tv.ui.utils.ifElse
import java.util.UUID

class PopupManager {
    private val stack = mutableListOf<StackItem>()
    private val log = Logger.create("PopupManager")

    fun push(focusRequester: FocusRequester, emitter: Boolean = false) {
        stack.add(StackItem(focusRequester, emitter))
    }

    fun pop() {
        if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex)
        val last = stack.lastOrNull()
        try {
            last?.focusRequester?.requestFocus()
            if (last?.emitter == true) stack.remove(last)
        } catch (ex: Exception) {
            log.e("弹窗焦点恢复失败", ex)
            try {
                stack.remove(last)
                stack.lastOrNull()?.focusRequester?.requestFocus()
            } catch (_: Exception) {
            }
        }
    }

    class StackItem(
        val focusRequester: FocusRequester,
        val emitter: Boolean = false,
    )
}

val LocalPopupManager = compositionLocalOf { PopupManager() }

fun Modifier.popupable() = composed {
    val popupManager = LocalPopupManager.current
    val focusRequester = remember { FocusRequester() }

    DisposableEffect(Unit) {
        focusRequester.requestFocus()
        popupManager.push(focusRequester)
        onDispose { popupManager.pop() }
    }

    focusRequester(focusRequester).focusable()
}

@Composable
fun PopupContent(
    modifier: Modifier = Modifier,
    visibleProvider: () -> Boolean,
    onDismissRequest: (() -> Unit)? = null,
    withBackground: Boolean = false,
    content: @Composable BoxScope.() -> Unit,
) {
    if (!visibleProvider()) return

    Box(
        modifier
            .fillMaxSize()
            .popupable()
            .clickableNoIndication { onDismissRequest?.invoke() }
            .backHandler { onDismissRequest?.invoke() }
            .ifElse(
                withBackground,
                Modifier.background(MaterialTheme.colorScheme.background.copy(0.5f)),
            ),
    ) {
        content()
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun SimplePopup(
    modifier: Modifier = Modifier,
    visibleProvider: () -> Boolean,
    onDismissRequest: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val visible = visibleProvider()
    val key = remember { UUID.randomUUID().toString() }
    val popupState = LocalPopupState.current

    if (visible) {
        // 同一个弹窗每次重画都会重新执行这里：不按 key 去重，同一份
        // 弹窗会被反复加进列表，背景越叠越暗、每份还各推一个焦点请求；
        // 关闭时下面又只删第一份，攒下的重复项会一直留着、弹窗关不掉
        if (popupState.none { it.key == key }) {
            popupState.add(
                PopupState(
                    key = key,
                    composableReference = {
                        PopupContent(
                            modifier = modifier
                                .focusProperties { exit = { FocusRequester.Cancel } },
                            visibleProvider = visibleProvider,
                            onDismissRequest = onDismissRequest,
                            withBackground = true,
                            content = content,
                        )
                    },
                ),
            )
        }
    } else {
        // 按 key 全删：只删第一份的话，前面攒下的重复项还会留在列表里
        popupState.removeAll { it.key == key }
    }
}

data class PopupState(
    val key: String = UUID.randomUUID().toString(),
    val composableReference: @Composable () -> Unit = {},
)

val LocalPopupState = compositionLocalOf { mutableStateListOf<PopupState>() }

@Composable
fun PopupHandleableApplication(
    applicationContent: @Composable () -> Unit
) {
    val popupState = LocalPopupState.current

    Box(modifier = Modifier.fillMaxSize()) {
        applicationContent()

        popupState.map {
            it.composableReference()
        }
    }
}