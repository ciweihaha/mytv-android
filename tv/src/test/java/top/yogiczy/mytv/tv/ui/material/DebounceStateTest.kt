package top.yogiczy.mytv.tv.ui.material

import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 防抖状态：频道焦点来回切时只在停稳后触发一次（多画面、经典频道列表、
 * 频道列表三处在用，wait 都是 100 毫秒）。之前一次也没测过——
 * 防抖那行丢了的话，每按一次方向键就触发一次焦点回调，旧单测全过、
 * 发现不了。
 */
class DebounceStateTest {

    @Test
    fun `连续发送只触发一次`() = runBlocking {
        var runs = 0
        val state = DebounceState(wait = 200) { runs++ }
        val job = launch { state.observe() }
        try {
            // 200 毫秒内连发三次：防抖应把前两次吞掉，只跑最后一次
            state.send()
            state.send()
            state.send()
            delay(500)
            assertEquals(1, runs)
        } finally {
            job.cancel()
        }
    }

    @Test
    fun `隔开两次发送各触发一次`() = runBlocking {
        var runs = 0
        val state = DebounceState(wait = 200) { runs++ }
        val job = launch { state.observe() }
        try {
            state.send()
            delay(400)
            assertEquals(1, runs)

            // 停稳后再发一次：防抖窗口已过，应再跑一次
            state.send()
            delay(400)
            assertEquals(2, runs)
        } finally {
            job.cancel()
        }
    }
}
