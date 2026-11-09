package top.yogiczy.mytv.core.util.utils

import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.net.InetSocketAddress

/**
 * 下载进度计算测试：总长度未知或为 0 时不得除零崩溃。
 */
class DownloaderTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun startServer(): HttpServer {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/ok") { ex ->
            val content = "hello-apk".toByteArray()
            ex.sendResponseHeaders(200, content.size.toLong())
            ex.responseBody.use { it.write(content) }
        }
        server.createContext("/partial") { ex ->
            // 宣称 100 字节，实际只发 7 字节就断开，模拟中途失败
            ex.responseHeaders.set("Content-Length", "100")
            ex.sendResponseHeaders(200, 100)
            ex.responseBody.write("partial".toByteArray())
            ex.responseBody.flush()
            ex.close()
        }
        server.start()
        return server
    }

    @Test
    fun downloadTo_success_writesTargetAndLeavesNoTemp() {
        val server = startServer()
        try {
            val dir = tempFolder.newFolder("dl")
            val target = File(dir, "latest.apk")
            runBlocking {
                Downloader.downloadTo(
                    "http://127.0.0.1:${server.address.port}/ok", target.path, null
                )
            }
            assertArrayEquals("hello-apk".toByteArray(), target.readBytes())
            assertFalse(File(dir, "latest.apk.tmp").exists())
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun downloadTo_failedDownload_leavesNoPartialFile() {
        val server = startServer()
        try {
            val dir = tempFolder.newFolder("dl")
            val target = File(dir, "latest.apk")
            assertThrows(Exception::class.java) {
                runBlocking {
                    Downloader.downloadTo(
                        "http://127.0.0.1:${server.address.port}/partial", target.path, null
                    )
                }
            }
            // 失败时目标路径不得留下残缺文件，否则更新页会拿它去安装
            assertFalse(target.exists())
            assertFalse(File(dir, "latest.apk.tmp").exists())
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun calcDownloadProgress_normal() {
        assertEquals(0, calcDownloadProgress(0, 100))
        assertEquals(50, calcDownloadProgress(50, 100))
        assertEquals(100, calcDownloadProgress(100, 100))
    }

    @Test
    fun calcDownloadProgress_over100ClampedTo99() {
        assertEquals(99, calcDownloadProgress(150, 100))
    }

    @Test
    fun calcDownloadProgress_unknownLengthReturnsZero() {
        // chunked 传输总长度 -1、空响应 0：旧代码直接除零抛 ArithmeticException
        assertEquals(0, calcDownloadProgress(10, -1))
        assertEquals(0, calcDownloadProgress(10, 0))
        assertEquals(0, calcDownloadProgress(0, -1))
    }
}
