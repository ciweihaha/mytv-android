package top.yogiczy.mytv.tv.ui.screen.update

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import top.yogiczy.mytv.core.data.entities.git.GitRelease

/**
 * 更新检查（天光 1860 轮姊妹检查）：检查进行中再次检查直接返回，
 * 不重复拉取。
 */
class UpdateViewModelTest {

    @Test
    fun `检查进行中再次检查直接返回不重复拉取`() = runBlocking {
        val viewModel = UpdateViewModel()
        val started = CompletableDeferred<Unit>()
        val releaseGate = CompletableDeferred<GitRelease>()
        var secondFetchCalls = 0

        // 第一轮检查卡在拉取上（没回包）：_isChecking 已置 true
        val first = async {
            viewModel.checkUpdate("1.0.0", "stable") {
                started.complete(Unit)
                releaseGate.await()
            }
        }
        started.await()

        // 正在查时再调一次：应直接返回，第二次拉取一次也不调——
        // 没这行守卫时两次拉取会并行跑，后回来的那次会把先回来的结果盖掉
        viewModel.checkUpdate("1.0.0", "stable") {
            secondFetchCalls++
            GitRelease(version = "9.9.9")
        }
        assertEquals(0, secondFetchCalls)

        releaseGate.complete(GitRelease(version = "9.9.9"))
        first.await()
        assertTrue(viewModel.isUpdateAvailable)
    }
}
