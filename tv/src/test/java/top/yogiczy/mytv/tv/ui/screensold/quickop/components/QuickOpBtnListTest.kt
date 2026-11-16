package top.yogiczy.mytv.tv.ui.screensold.quickop.components

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 快捷操作面板里解码方式那颗按钮的标题：强制软解开 → "强制软解"，
 * 关 → "自动解码"。顶层纯函数，两档一次也没断言过。
 */
class QuickOpBtnListTest {
    @Test
    fun forceDecodeLabel_on_showsForceSoftDecode() {
        assertEquals("强制软解", ForceDecodeLabel(true))
    }

    @Test
    fun forceDecodeLabel_off_showsAutoDecode() {
        assertEquals("自动解码", ForceDecodeLabel(false))
    }
}
