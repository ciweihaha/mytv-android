package top.yogiczy.mytv.tv.ui.screensold.channel

import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 输台号防抖确认（天光 1836 轮姊妹检查）：输完一位后等一等再确认，
 * 等多久按还差几位算——还差几位就等几秒，第 4 位一到立即确认。
 */
class ChannelNumberSelectStateTest {

    @Test
    fun `输入数字拼出台号`() {
        val state = ChannelNumberSelectState()
        state.input(1)
        state.input(2)
        assertEquals("12", state.channelNumber)
    }

    @Test
    fun `输不满 4 位时等防抖超时才确认并清空`() = runBlocking {
        // 2 位要等 (4 - 2) * 1000 = 2000 毫秒才确认：防抖公式若丢了、
        // 确认会立刻触发，下面“1 秒时还没确认”这句就挂
        var confirmed: String? = null
        val state = ChannelNumberSelectState(onConfirm = { confirmed = it })
        val job = launch { state.observe() }
        state.input(1)
        state.input(2)
        delay(1000)
        assertNull(confirmed)
        delay(2000)
        assertEquals("12", confirmed)
        assertEquals("", state.channelNumber)
        job.cancel()
    }

    @Test
    fun `第 4 位后立即确认并清空`() = runBlocking {
        // 第 4 位后防抖超时是 0、立即确认：若公式没按位数算，
        // 500 毫秒内拿不到确认结果
        var confirmed: String? = null
        val state = ChannelNumberSelectState(onConfirm = { confirmed = it })
        val job = launch { state.observe() }
        repeat(4) { state.input(3) }
        delay(500)
        assertEquals("3333", confirmed)
        assertEquals("", state.channelNumber)
        job.cancel()
    }

    @Test
    fun `满 4 位后再输入被忽略`() {
        // 第 5 位不追加：不封顶时防抖超时 (4 - 5) * 1000 变负数、
        // 攒到 10 位以上确认处 toInt() 也会抛异常闪退；这里不用
        // observe()，只断守卫本身——第 5 位按下去台号仍是 4 位
        val state = ChannelNumberSelectState()
        repeat(4) { state.input(3) }
        state.input(5)
        assertEquals("3333", state.channelNumber)
    }
}
