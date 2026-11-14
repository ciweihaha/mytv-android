package top.yogiczy.mytv.tv.ui.utils

import android.os.Build
import android.view.KeyEvent
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import top.yogiczy.mytv.tv.ui.theme.LAYOUT_GRID_SPACING
import top.yogiczy.mytv.tv.ui.theme.LAYOUT_GRID_WIDTH
import kotlin.math.absoluteValue

fun Modifier.ifElse(
    condition: () -> Boolean, ifTrueModifier: Modifier, ifFalseModifier: Modifier = Modifier
): Modifier = then(if (condition()) ifTrueModifier else ifFalseModifier)

fun Modifier.ifElse(
    condition: Boolean, ifTrueModifier: Modifier, ifFalseModifier: Modifier = Modifier
): Modifier = ifElse({ condition }, ifTrueModifier, ifFalseModifier)

fun Modifier.focusOnLaunched(key: Any = Unit): Modifier = composed {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(key) {
        focusRequester.saveRequestFocus()
    }
    focusRequester(focusRequester)
}

fun Modifier.focusOnLaunchedSaveable(key: Any = Unit): Modifier = composed {
    val focusRequester = remember { FocusRequester() }
    var hasFocused by rememberSaveable(key) { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!hasFocused) {
            focusRequester.saveRequestFocus()
            hasFocused = true
        }
    }
    focusRequester(focusRequester)
}

fun Modifier.handleKeyEvents(
    onKeyTap: Map<Int, (() -> Unit)?> = emptyMap(),
    onKeyLongTap: Map<Int, (() -> Unit)?> = emptyMap(),
): Modifier = composed {
    val keyDownMap = remember { mutableMapOf<Int, Boolean>() }

    onPreviewKeyEvent {
        when (it.nativeKeyEvent.action) {
            KeyEvent.ACTION_DOWN -> {
                if (it.nativeKeyEvent.repeatCount == 0) {
                    keyDownMap[it.nativeKeyEvent.keyCode] = true
                } else if (it.nativeKeyEvent.repeatCount == 1) {
                    keyDownMap.remove(it.nativeKeyEvent.keyCode)
                    onKeyLongTap[it.nativeKeyEvent.keyCode]?.invoke()

                    onKeyLongTap[it.nativeKeyEvent.keyCode] != null
                }

                false
            }

            KeyEvent.ACTION_UP -> {
                if (keyDownMap[it.nativeKeyEvent.keyCode] == true) {
                    keyDownMap.remove(it.nativeKeyEvent.keyCode)
                    onKeyTap[it.nativeKeyEvent.keyCode]?.invoke()

                    if (it.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_MENU) {
                        return@onPreviewKeyEvent true // Consume the event to prevent losing focus
                    }

                    onKeyTap[it.nativeKeyEvent.keyCode] != null
                }

                false
            }

            else -> false
        }
    }
}

fun Modifier.handleDragGestures(
    onSwipeUp: () -> Unit = {},
    onSwipeDown: () -> Unit = {},
    onSwipeLeft: () -> Unit = {},
    onSwipeRight: () -> Unit = {},
) = composed {
    val currentOnSwipeUp by rememberUpdatedState(onSwipeUp)
    val currentOnSwipeDown by rememberUpdatedState(onSwipeDown)
    val currentOnSwipeLeft by rememberUpdatedState(onSwipeLeft)
    val currentOnSwipeRight by rememberUpdatedState(onSwipeRight)

    val speedThreshold = 100.dp
    val distanceThreshold = 10.dp

    val verticalTracker = VelocityTracker()
    var verticalDragOffset = 0f
    val horizontalTracker = VelocityTracker()
    var horizontalDragOffset = 0f


    pointerInput(Unit) {
        detectVerticalDragGestures(
            onDragEnd = {
                if (verticalDragOffset.absoluteValue > distanceThreshold.toPx()) {
                    val velocityY = verticalTracker.calculateVelocity().y
                    val speedPx = speedThreshold.toPx()
                    if (velocityY > speedPx) {
                        currentOnSwipeDown()
                    } else if (velocityY < -speedPx) {
                        currentOnSwipeUp()
                    }
                }
                // 一次滑动结束就清零：不然偏移会攒到下一次滑动里，攒够距离后
                // 随便轻轻一划就触发，等于距离门槛只对第一次滑动有效
                verticalDragOffset = 0f
            },
            // 滑动被取消（另一方向的手势抢赢、或手指中途被系统接管）时
            // 不走 onDragEnd：不这里清零，偏移同样会攒到下一次滑动里
            onDragCancel = { verticalDragOffset = 0f },
        ) { change, dragAmount ->
            verticalDragOffset += dragAmount
            verticalTracker.addPosition(change.uptimeMillis, change.position)
        }
    }
        .pointerInput(Unit) {
            detectHorizontalDragGestures(
                onDragEnd = {
                    if (horizontalDragOffset.absoluteValue > distanceThreshold.toPx()) {
                        val velocityX = horizontalTracker.calculateVelocity().x
                        val speedPx = speedThreshold.toPx()
                        if (velocityX > speedPx) {
                            currentOnSwipeRight()
                        } else if (velocityX < -speedPx) {
                            currentOnSwipeLeft()
                        }
                    }
                    // 同上：横向偏移一次滑动结束就清零，别攒到下一次
                    horizontalDragOffset = 0f
                },
                // 同上：取消时也不走 onDragEnd，这里同样清零
                onDragCancel = { horizontalDragOffset = 0f },
            ) { change, dragAmount ->
                horizontalDragOffset += dragAmount
                horizontalTracker.addPosition(change.uptimeMillis, change.position)
            }
        }
}

fun Modifier.clickableNoIndication(
    onLongClick: (() -> Unit)? = null,
    onDoubleClick: (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) = composed {
    val currentOnClick by rememberUpdatedState(onClick)
    val currentOnLongClick by rememberUpdatedState(onLongClick)
    val currentOnDoubleClick by rememberUpdatedState(onDoubleClick)

    pointerInput(Unit) {
        detectTapGestures(
            onDoubleTap = currentOnDoubleClick?.let { { _ -> it() } },
            onLongPress = currentOnLongClick?.let { { _ -> it() } },
            onTap = currentOnClick?.let { { _ -> it() } },
        )
    }
}

/**
 * 遥控按键“点按”映射表（纯函数，方便单元测试）：只收录真正传了
 * 回调的键，没传的不进表；按下没进表的键时和原来一样不消费。
 * 键盘 W/S/A/D 当长按方向用、L 当长按确定用（原有行为保留）；
 * 数字键 0-9 按位数回调。
 */
internal fun keyTapMap(
    onLeft: (() -> Unit)? = null,
    onRight: (() -> Unit)? = null,
    onUp: (() -> Unit)? = null,
    onDown: (() -> Unit)? = null,
    onSelect: (() -> Unit)? = null,
    onLongLeft: (() -> Unit)? = null,
    onLongRight: (() -> Unit)? = null,
    onLongUp: (() -> Unit)? = null,
    onLongDown: (() -> Unit)? = null,
    onLongSelect: (() -> Unit)? = null,
    onSettings: (() -> Unit)? = null,
    onNumber: ((Int) -> Unit)? = null,
    sdkInt: Int = Build.VERSION.SDK_INT,
): Map<Int, () -> Unit> = buildMap {
    onLeft?.let {
        put(KeyEvent.KEYCODE_DPAD_LEFT, it)
        if (sdkInt >= Build.VERSION_CODES.N_MR1) {
            put(KeyEvent.KEYCODE_SYSTEM_NAVIGATION_LEFT, it)
        }
    }
    onRight?.let {
        put(KeyEvent.KEYCODE_DPAD_RIGHT, it)
        if (sdkInt >= Build.VERSION_CODES.N_MR1) {
            put(KeyEvent.KEYCODE_SYSTEM_NAVIGATION_RIGHT, it)
        }
    }
    onUp?.let {
        put(KeyEvent.KEYCODE_DPAD_UP, it)
        put(KeyEvent.KEYCODE_CHANNEL_UP, it)
        if (sdkInt >= Build.VERSION_CODES.N_MR1) {
            put(KeyEvent.KEYCODE_SYSTEM_NAVIGATION_UP, it)
        }
    }
    onDown?.let {
        put(KeyEvent.KEYCODE_DPAD_DOWN, it)
        put(KeyEvent.KEYCODE_CHANNEL_DOWN, it)
        if (sdkInt >= Build.VERSION_CODES.N_MR1) {
            put(KeyEvent.KEYCODE_SYSTEM_NAVIGATION_DOWN, it)
        }
    }
    onSelect?.let {
        put(KeyEvent.KEYCODE_DPAD_CENTER, it)
        put(KeyEvent.KEYCODE_ENTER, it)
        put(KeyEvent.KEYCODE_NUMPAD_ENTER, it)
    }
    onSettings?.let {
        put(KeyEvent.KEYCODE_MENU, it)
        put(KeyEvent.KEYCODE_SETTINGS, it)
        put(KeyEvent.KEYCODE_HELP, it)
        put(KeyEvent.KEYCODE_H, it)
    }
    // 键盘 W/S/A/D 当长按方向用、L 当长按确定用（原有行为保留）
    onLongLeft?.let { put(KeyEvent.KEYCODE_A, it) }
    onLongRight?.let { put(KeyEvent.KEYCODE_D, it) }
    onLongUp?.let { put(KeyEvent.KEYCODE_W, it) }
    onLongDown?.let { put(KeyEvent.KEYCODE_S, it) }
    onLongSelect?.let { put(KeyEvent.KEYCODE_L, it) }
    onNumber?.let { number ->
        for (i in 0..9) {
            put(KeyEvent.KEYCODE_0 + i) { number(i) }
        }
    }
}

/**
 * 遥控按键“长按”映射表（纯函数，方便单元测试）：同样只收录真正传了
 * 回调的键。
 */
internal fun keyLongTapMap(
    onLongLeft: (() -> Unit)? = null,
    onLongRight: (() -> Unit)? = null,
    onLongUp: (() -> Unit)? = null,
    onLongDown: (() -> Unit)? = null,
    onLongSelect: (() -> Unit)? = null,
): Map<Int, () -> Unit> = buildMap {
    onLongLeft?.let { put(KeyEvent.KEYCODE_DPAD_LEFT, it) }
    onLongRight?.let { put(KeyEvent.KEYCODE_DPAD_RIGHT, it) }
    onLongUp?.let {
        put(KeyEvent.KEYCODE_DPAD_UP, it)
        put(KeyEvent.KEYCODE_CHANNEL_UP, it)
    }
    onLongDown?.let {
        put(KeyEvent.KEYCODE_DPAD_DOWN, it)
        put(KeyEvent.KEYCODE_CHANNEL_DOWN, it)
    }
    onLongSelect?.let {
        put(KeyEvent.KEYCODE_ENTER, it)
        put(KeyEvent.KEYCODE_NUMPAD_ENTER, it)
        put(KeyEvent.KEYCODE_DPAD_CENTER, it)
    }
}

fun Modifier.handleKeyEvents(
    onLeft: (() -> Unit)? = null,
    onLongLeft: (() -> Unit)? = null,
    onRight: (() -> Unit)? = null,
    onLongRight: (() -> Unit)? = null,
    onUp: (() -> Unit)? = null,
    onLongUp: (() -> Unit)? = null,
    onDown: (() -> Unit)? = null,
    onLongDown: (() -> Unit)? = null,
    onSelect: (() -> Unit)? = null,
    onLongSelect: (() -> Unit)? = null,
    onSettings: (() -> Unit)? = null,
    onNumber: ((Int) -> Unit)? = null,
) = handleKeyEvents(
    onKeyTap = keyTapMap(
        onLeft = onLeft,
        onRight = onRight,
        onUp = onUp,
        onDown = onDown,
        onSelect = onSelect,
        onLongLeft = onLongLeft,
        onLongRight = onLongRight,
        onLongUp = onLongUp,
        onLongDown = onLongDown,
        onLongSelect = onLongSelect,
        onSettings = onSettings,
        onNumber = onNumber,
    ),
    onKeyLongTap = keyLongTapMap(
        onLongLeft = onLongLeft,
        onLongRight = onLongRight,
        onLongUp = onLongUp,
        onLongDown = onLongDown,
        onLongSelect = onLongSelect,
    ),
)
    .clickableNoIndication(
        onClick = onSelect,
        onLongClick = onLongSelect,
        onDoubleClick = onSettings,
    )

fun Modifier.handleKeyEvents(
    isFocused: () -> Boolean,
    focusRequester: FocusRequester,
    onLeft: (() -> Unit)? = null,
    onLongLeft: (() -> Unit)? = null,
    onRight: (() -> Unit)? = null,
    onLongRight: (() -> Unit)? = null,
    onUp: (() -> Unit)? = null,
    onLongUp: (() -> Unit)? = null,
    onDown: (() -> Unit)? = null,
    onLongDown: (() -> Unit)? = null,
    onSelect: (() -> Unit)? = null,
    onLongSelect: (() -> Unit)? = null,
    onSettings: (() -> Unit)? = null,
    onNumber: ((Int) -> Unit)? = null,
) = handleKeyEvents(
    onLeft = onLeft?.let { { if (isFocused()) it() else focusRequester.saveRequestFocus() } },
    onLongLeft = onLongLeft?.let { { if (isFocused()) it() else focusRequester.saveRequestFocus() } },
    onRight = onRight?.let { { if (isFocused()) it() else focusRequester.saveRequestFocus() } },
    onLongRight = onLongRight?.let { { if (isFocused()) it() else focusRequester.saveRequestFocus() } },
    onUp = onUp?.let { { if (isFocused()) it() else focusRequester.saveRequestFocus() } },
    onLongUp = onLongUp?.let { { if (isFocused()) it() else focusRequester.saveRequestFocus() } },
    onDown = onDown?.let { { if (isFocused()) it() else focusRequester.saveRequestFocus() } },
    onLongDown = onLongDown?.let { { if (isFocused()) it() else focusRequester.saveRequestFocus() } },
    onSelect = onSelect?.let { { if (isFocused()) it() else focusRequester.saveRequestFocus() } },
    onLongSelect = onLongSelect?.let { { if (isFocused()) it() else focusRequester.saveRequestFocus() } },
    onSettings = onSettings?.let { { if (isFocused()) it() else focusRequester.saveRequestFocus() } },
    onNumber = onNumber?.let { { num -> if (isFocused()) it(num) else focusRequester.saveRequestFocus() } },
)

fun Modifier.backHandler(onBackPressed: () -> Unit) = this.onPreviewKeyEvent {
    if (it.key == Key.Back && it.type == KeyEventType.KeyUp) {
        onBackPressed()
        true
    } else {
        false
    }
}

fun Modifier.backHandler(
    condition: () -> Boolean,
    onBackPressed: () -> Unit,
) = this.onPreviewKeyEvent {
    if (it.key == Key.Back && it.type == KeyEventType.KeyUp && condition()) {
        onBackPressed()
        true
    } else {
        false
    }
}

fun Modifier.visible(visible: Boolean) = alpha(if (visible) 1f else 0f)

fun Int.gridColumns() = (LAYOUT_GRID_WIDTH * this + LAYOUT_GRID_SPACING * (this - 1)).dp
fun Float.gridColumns() = (LAYOUT_GRID_WIDTH * this + LAYOUT_GRID_SPACING * (this - 1)).dp

fun NavHostController.navigateSingleTop(route: String) {
    navigate(route) {
        popUpTo(route) { inclusive = true }
        launchSingleTop = true
    }
}

@Composable
@OptIn(ExperimentalComposeUiApi::class)
fun Modifier.saveFocusRestorer(onRestoreFailed: (() -> FocusRequester)? = null): Modifier {
    if (!Configs.uiFocusOptimize) return this

    return focusRestorer {
        if (onRestoreFailed == null) return@focusRestorer FocusRequester.Default

        val result = onRestoreFailed()
        runCatching {
            result.requestFocus()
            result
        }.getOrElse { FocusRequester.Default }
    }
}

fun FocusRequester.saveRequestFocus() = runCatching { requestFocus() }