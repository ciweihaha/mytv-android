package top.yogiczy.mytv.tv.ui

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 退出二次确认（天光 1858/1859 轮姊妹检查）：按一次返回后 2 秒内再按
 * 一次退出，2 秒后复位。壳线没有天光的 300 毫秒去重与 [300, 2000)
 * 窗口——每次按压都重记时间，不丢弃快速重复按压。
 */
class DoubleBackPressedExitStateTest {

    @Test
    fun `按一次后两秒内允许退出、到期自动复位`() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        val state = DoubleBackPressedExitState(2)
        val observeJob = scope.launch { state.observe() }
        try {
            state.backPress()
            delay(200)
            assertTrue(state.allowExit)

            // 默认 2 秒防抖到期后自己复位
            delay(2200)
            assertFalse(state.allowExit)
        } finally {
            observeJob.cancel()
            scope.cancel()
        }
    }

    @Test
    fun `窗口内再按一次重新计时`() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        val state = DoubleBackPressedExitState(2)
        val observeJob = scope.launch { state.observe() }
        try {
            state.backPress()
            delay(1500)
            assertTrue(state.allowExit)

            // 窗口内再按一次：防抖重记，复位时间从这次按压重算；
            // 距第一次按压已 3 秒、仍在窗口内
            state.backPress()
            delay(1500)
            assertTrue(state.allowExit)

            delay(1000)
            assertFalse(state.allowExit)
        } finally {
            observeJob.cancel()
            scope.cancel()
        }
    }
}
