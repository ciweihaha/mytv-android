package top.yogiczy.mytv.tv.ui.screen.update

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import top.yogiczy.mytv.core.data.entities.git.GitRelease
import java.io.File

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

    @Test
    fun `没更新可下时直接跳过下载`() = runBlocking {
        // 没查过更新（isUpdateAvailable 为 false）：第一行守卫直接返回，
        // 一次也不下载、也不删文件。这档之前没有任何单测——没这行守卫时
        // 这里会直接下 _latestRelease 里的默认空地址，还会先把旧文件删掉
        val viewModel = UpdateViewModel()
        val file = File.createTempFile("update-test", ".apk").apply { writeText("旧包") }
        try {
            viewModel.downloadAndUpdate(file)

            assertFalse(viewModel.updateDownloaded)
            assertTrue(file.exists())
        } finally {
            file.delete()
        }
    }

    @Test
    fun `下载进行中再次下载直接返回不重复下载`() = runBlocking {
        val viewModel = UpdateViewModel()
        // 先查出有更新（走 768 轮的可注入拉取函数，不联网）
        viewModel.checkUpdate("1.0.0", "stable") {
            GitRelease(version = "9.9.9", downloadUrl = "https://example.com/app.apk")
        }
        assertTrue(viewModel.isUpdateAvailable)

        val started = CompletableDeferred<Unit>()
        val releaseGate = CompletableDeferred<Unit>()
        var secondDownloadCalls = 0
        val file = File.createTempFile("update-test", ".apk")

        try {
            // 第一轮下载卡在下载上：_isUpdating 已置 true
            val first = async {
                viewModel.downloadAndUpdate(file) { _, _, _ ->
                    started.complete(Unit)
                    releaseGate.await()
                }
            }
            started.await()

            // 正在下时再调一次：应直接返回，第二次下载一次也不调——
            // 没这行守卫时两次下载会并行写同一个文件
            viewModel.downloadAndUpdate(file) { _, _, _ ->
                secondDownloadCalls++
            }
            assertEquals(0, secondDownloadCalls)

            releaseGate.complete(Unit)
            first.await()
            assertTrue(viewModel.updateDownloaded)
            assertFalse(viewModel.isUpdating)
        } finally {
            file.delete()
        }
    }

    @Test
    fun `拉取失败记一次失败、不当成已是最新`() = runBlocking {
        val viewModel = UpdateViewModel()
        viewModel.checkUpdate("1.0.0", "stable") {
            throw RuntimeException("网络断了")
        }

        // 失败时 isUpdateAvailable 仍是 false，不靠 checkFailed 区分
        // 会把“没查到”说成“已是最新”；报错原因写进版本说明里
        assertTrue(viewModel.checkFailed)
        assertFalse(viewModel.isUpdateAvailable)
        assertEquals("网络断了", viewModel.latestRelease.description)

        // finally 里 _isChecking 已复位：失败后还能再查，查到了就是有更新
        viewModel.checkUpdate("1.0.0", "stable") {
            GitRelease(version = "9.9.9", downloadUrl = "https://example.com/app.apk")
        }
        assertFalse(viewModel.checkFailed)
        assertTrue(viewModel.isUpdateAvailable)
    }

    @Test
    fun `检查中途取消不记失败`() = runBlocking {
        val viewModel = UpdateViewModel()
        val gate = CompletableDeferred<GitRelease>()
        val job = async {
            viewModel.checkUpdate("1.0.0", "stable") { gate.await() }
        }
        delay(200)
        job.cancel()

        // 取消信号原样抛出来，不记成一次失败、不写进版本说明
        try {
            job.await()
            fail("应抛出取消异常")
        } catch (ex: CancellationException) {
            // 预期
        }
        assertFalse(viewModel.checkFailed)
        assertFalse(viewModel.isUpdateAvailable)
    }

    @Test
    fun `下载失败不记已下载、状态复位`() = runBlocking {
        val viewModel = UpdateViewModel()
        viewModel.checkUpdate("1.0.0", "stable") {
            GitRelease(version = "9.9.9", downloadUrl = "https://example.com/app.apk")
        }
        val file = File.createTempFile("update-test", ".apk")
        try {
            viewModel.downloadAndUpdate(file) { _, _, _ ->
                throw RuntimeException("下载断了")
            }

            // 下载失败不是下载完成：updateDownloaded 仍是 false，
            // _isUpdating 在 finally 里复位，之后还能再下
            assertFalse(viewModel.updateDownloaded)
            assertFalse(viewModel.isUpdating)
        } finally {
            file.delete()
        }
    }

    @Test
    fun `下载中途取消不记已下载、状态复位`() = runBlocking {
        val viewModel = UpdateViewModel()
        viewModel.checkUpdate("1.0.0", "stable") {
            GitRelease(version = "9.9.9", downloadUrl = "https://example.com/app.apk")
        }
        val gate = CompletableDeferred<Unit>()
        val file = File.createTempFile("update-test", ".apk")
        try {
            val job = async {
                viewModel.downloadAndUpdate(file) { _, _, _ -> gate.await() }
            }
            delay(200)
            job.cancel()

            // 取消信号原样抛出来，不弹“下载更新失败”的假报错
            // （690 轮同款问题）；状态复位，之后还能再下
            try {
                job.await()
                fail("应抛出取消异常")
            } catch (ex: CancellationException) {
                // 预期
            }
            assertFalse(viewModel.updateDownloaded)
            assertFalse(viewModel.isUpdating)
        } finally {
            file.delete()
        }
    }
}
