package top.yogiczy.mytv.tv.ui.screen.live.channels.components

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 网速折算（天光 1834 轮姊妹检查）：算式是“差值 × 1000 ÷ 耗时”，
 * 先乘后除；先除会把每毫秒的零头截掉再乘 1000，一秒流量不到
 * 1KB 时直接算成 0，平时也固定少算将近 1KB/s。
 */
class LiveChannelsChannelInfoNetSpeedTest {

    @Test
    fun `一秒整耗时算对`() {
        assertEquals(10_240L, nextNetSpeed(0, 0, 10_240, 1000))
    }

    @Test
    fun `按实际耗时折算成每秒、不是只按一秒算`() {
        // 只测 1000 毫秒的耗时时，算式里 * 1000 / elapsedMs
        // 的折算若丢了、写死了，分母永远是 1000 也能过；
        // 换两个不是 1000 的耗时钉住它；旧算式先除后乘时
        // 这两例会算成 0 和 20000，正好把毛病露出来
        assertEquals(250L, nextNetSpeed(0, 0, 500, 2000))
        assertEquals(20_480L, nextNetSpeed(0, 0, 10_240, 500))
    }

    @Test
    fun `流量计数中途倒退时按 0 算、不显示负网速`() {
        assertEquals(0L, nextNetSpeed(10_240, 20_000, 10_000, 1000))
    }

    @Test
    fun `两次采样时刻相同或倒挂时沿用上一次的值`() {
        assertEquals(100L, nextNetSpeed(100, 0, 500, 0))
        assertEquals(100L, nextNetSpeed(100, 0, 500, -100))
    }

    @Test
    fun `流量没变时网速为 0`() {
        assertEquals(0L, nextNetSpeed(0, 1000, 1000, 1000))
    }
}
