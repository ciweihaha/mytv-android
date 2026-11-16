package top.yogiczy.mytv.tv.ui.material

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    @Test
    fun `到期后自动藏起`() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        val state = SnackbarUIState(scope)
        // observe() 之前没有任何单测：它只在 rememberSnackbarUIState
        // 里 LaunchedEffect 调，防抖到期自动藏起这条路一次也没走通过——
        // 如果 debounce 那行丢了，提示会一直挂在屏幕上，旧单测全过、
        // 发现不了
        val observeJob = scope.launch { state.observe() }
        try {
            state.show("A")
            delay(200)
            assertTrue(state.visible)

            // 默认时长 2300 毫秒：等防抖到期，提示自己藏起
            delay(2800)
            assertFalse(state.visible)
        } finally {
            observeJob.cancel()
            scope.cancel()
        }
    }

    @Test
    fun `自定义时长按自定义毫秒藏起`() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        val state = SnackbarUIState(scope)
        val observeJob = scope.launch { state.observe() }
        try {
            // 传 500 毫秒：防抖必须按 500 走、不是默认 2300——
            // 设置页等处在用自定义时长，这档一次也没测过
            state.show("A", SnackbarData(duration = 500))
            delay(200)
            assertTrue(state.visible)

            // 500 毫秒防抖到期就藏起；若错回默认 2300，
            // 这里还会是可见，单测会失败
            delay(800)
            assertFalse(state.visible)
        } finally {
            observeJob.cancel()
            scope.cancel()
        }
    }

    @Test
    fun `到期藏起后再发新提示仍能显示并到期藏起`() = runBlocking {
        // 天光 2102 轮姊妹检查：第一条到期藏起后，再发一条新提示，
        // 应正常显示、到期再藏起；observe 若只取一次就停，第二条
        // 到期也不会藏起，提示会一直挂在屏幕上
        val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        val state = SnackbarUIState(scope)
        val observeJob = scope.launch { state.observe() }
        try {
            state.show("A", SnackbarData(duration = 500))
            delay(200)
            assertTrue(state.visible)

            delay(800)
            assertFalse(state.visible)

            state.show("B", SnackbarData(duration = 500))
            delay(200)
            assertTrue(state.visible)
            assertEquals("B", state.currentData.text)

            delay(800)
            assertFalse(state.visible)
        } finally {
            observeJob.cancel()
            scope.cancel()
        }
    }
}
