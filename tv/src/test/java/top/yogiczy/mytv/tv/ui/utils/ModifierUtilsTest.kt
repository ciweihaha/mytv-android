package top.yogiczy.mytv.tv.ui.utils

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 遥控按键映射表（天光 1844 轮姊妹检查）：点按表与长按表
 * 原先是 `handleKeyEvents` 里现拼的、没法单测；抽成纯函数后，
 * 哪些键进表、API 25 以上多哪些系统导航键，都能钉死。
 * 没传回调的键不进表，按下时和原来一样不消费。
 */
class ModifierUtilsTest {

    @Test
    fun `onSettings 时菜单设置帮助 H 四个键都进表`() {
        val map = keyTapMap(onSettings = {}, sdkInt = 24)
        assertEquals(
            setOf(
                KeyEvent.KEYCODE_MENU,
                KeyEvent.KEYCODE_SETTINGS,
                KeyEvent.KEYCODE_HELP,
                KeyEvent.KEYCODE_H,
            ),
            map.keys,
        )
    }

    @Test
    fun `左右方向键在 API25 以上也注册系统导航键`() {
        val leftBelow = keyTapMap(onLeft = {}, sdkInt = 24)
        assertEquals(setOf(KeyEvent.KEYCODE_DPAD_LEFT), leftBelow.keys)

        val leftAbove = keyTapMap(onLeft = {}, sdkInt = 25)
        assertEquals(
            setOf(
                KeyEvent.KEYCODE_DPAD_LEFT,
                KeyEvent.KEYCODE_SYSTEM_NAVIGATION_LEFT,
            ),
            leftAbove.keys,
        )

        val rightAbove = keyTapMap(onRight = {}, sdkInt = 25)
        assertEquals(
            setOf(
                KeyEvent.KEYCODE_DPAD_RIGHT,
                KeyEvent.KEYCODE_SYSTEM_NAVIGATION_RIGHT,
            ),
            rightAbove.keys,
        )
    }

    @Test
    fun `下方向键在 API25 以上注册频道下键与系统导航下键`() {
        val map = keyTapMap(onDown = {}, sdkInt = 25)
        assertEquals(
            setOf(
                KeyEvent.KEYCODE_DPAD_DOWN,
                KeyEvent.KEYCODE_CHANNEL_DOWN,
                KeyEvent.KEYCODE_SYSTEM_NAVIGATION_DOWN,
            ),
            map.keys,
        )
    }

    @Test
    fun `长按方向键各进长按表`() {
        assertEquals(
            setOf(KeyEvent.KEYCODE_DPAD_LEFT),
            keyLongTapMap(onLongLeft = {}).keys,
        )
        assertEquals(
            setOf(KeyEvent.KEYCODE_DPAD_RIGHT),
            keyLongTapMap(onLongRight = {}).keys,
        )
        assertEquals(
            setOf(KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_CHANNEL_UP),
            keyLongTapMap(onLongUp = {}).keys,
        )
        assertEquals(
            setOf(KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_CHANNEL_DOWN),
            keyLongTapMap(onLongDown = {}).keys,
        )
    }

    @Test
    fun `键盘 W S A D L 当长按回调进点按表`() {
        // 壳线独有的键盘备用键：W/S/A/D 当长按方向、L 当长按确定
        val map = keyTapMap(
            onLongLeft = {},
            onLongRight = {},
            onLongUp = {},
            onLongDown = {},
            onLongSelect = {},
        )
        assertEquals(
            setOf(
                KeyEvent.KEYCODE_A,
                KeyEvent.KEYCODE_D,
                KeyEvent.KEYCODE_W,
                KeyEvent.KEYCODE_S,
                KeyEvent.KEYCODE_L,
            ),
            map.keys,
        )
    }

    @Test
    fun `数字键 0 到 9 进表并按位数回调`() {
        val digits = mutableListOf<Int>()
        val map = keyTapMap(onNumber = { digits.add(it) })
        assertEquals(
            (0..9).map { KeyEvent.KEYCODE_0 + it }.toSet(),
            map.keys,
        )

        map.getValue(KeyEvent.KEYCODE_5)()
        map.getValue(KeyEvent.KEYCODE_0)()
        assertEquals(listOf(5, 0), digits)
    }
}
