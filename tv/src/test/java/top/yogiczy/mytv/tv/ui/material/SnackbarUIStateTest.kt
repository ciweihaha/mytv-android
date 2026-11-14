package top.yogiczy.mytv.tv.ui.material

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 提示条换场（天光 1855 轮姊妹检查）：显示中再发一条新提示，旧的先藏起、
 * 等 300 毫秒换场，再把新的显示出来。
 */
class SnackbarUIStateTest {

    @Test
    fun `换场后新提示正常显示`() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        val state = SnackbarUIState(scope)
        try {
            state.show("A")
            delay(200)
            assertTrue(state.visible)
            assertEquals("A", state.currentData.text)

            // B 与 A 的 id 不同：先藏起 A、等 300 毫秒换场，再把 B 显示出来。
            // 旧任务在 show 开头就被取消，不会把旧文案写回来；这条“换场后
            // 新提示照常显示”的路之前没有任何单测
            state.show("B")
            delay(700)
            assertEquals("B", state.currentData.text)
            assertTrue(state.visible)
        } finally {
            scope.cancel()
        }
    }
}
