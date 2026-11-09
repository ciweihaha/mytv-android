package top.yogiczy.mytv.core.util.utils

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.ServerSocket

/**
 * 下载进度计算测试：总长度未知或为 0 时不得除零崩溃。
 */
class DownloaderTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    /**
     * 最小 HTTP 服务（纯 java.net：com.sun.net.httpserver 不在 Android
     * 单测的编译 classpath 里）。/ok 正常返回，/partial 宣称 100 字节、
     * 实际只发 7 字节就断开，模拟中途失败。
     */
    private class TestHttpServer {
        private val serverSocket = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
        val port: Int get() = serverSocket.localPort

        private val thread = Thread({
            try {
                while (!serverSocket.isClosed) {
                    serverSocket.accept().use { socket ->
                        val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
                        val requestLine = reader.readLine() ?: return@use
                        var line: String?
                        do {
                            line = reader.readLine()
                        } while (line != null && line.isNotEmpty())
                        val out = socket.getOutputStream()
                        when {
                            requestLine.startsWith("GET /ok ") -> {
                                val content = "hello-apk".toByteArray()
                                out.write(
                                    "HTTP/1.1 200 OK\r\nContent-Length: ${content.size}\r\nConnection: close\r\n\r\n"
                                        .toByteArray()
                                )
                                out.write(content)
                                out.flush()
                            }

                            requestLine.startsWith("GET /partial ") -> {
                                out.write(
                                    "HTTP/1.1 200 OK\r\nContent-Length: 100\r\nConnection: close\r\n\r\n"
                                        .toByteArray()
                                )
                                out.write("partial".toByteArray())
                                out.flush()
                                // 不发完就断开：socket.use 关闭连接
                            }

                            else -> {
                                out.write(
                                    "HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\nConnection: close\r\n\r\n"
                                        .toByteArray()
                                )
                                out.flush()
                            }
                        }
                    }
                }
            } catch (_: Exception) {
                // 服务关闭
            }
        }, "test-http-server").apply { isDaemon = true }

        fun start() = thread.start()

        fun stop() = runCatching { serverSocket.close() }
    }

    @Test
    fun downloadTo_success_writesTargetAndLeavesNoTemp() {
        val server = TestHttpServer().apply { start() }
        try {
            val dir = tempFolder.newFolder("dl")
            val target = File(dir, "latest.apk")
            runBlocking {
                Downloader.downloadTo(
                    "http://127.0.0.1:${server.port}/ok", target.path, null
                )
            }
            assertArrayEquals("hello-apk".toByteArray(), target.readBytes())
            assertFalse(File(dir, "latest.apk.tmp").exists())
        } finally {
            server.stop()
        }
    }

    @Test
    fun downloadTo_failedDownload_leavesNoPartialFile() {
        val server = TestHttpServer().apply { start() }
        try {
            val dir = tempFolder.newFolder("dl")
            val target = File(dir, "latest.apk")
            assertThrows(Exception::class.java) {
                runBlocking {
                    Downloader.downloadTo(
                        "http://127.0.0.1:${server.port}/partial", target.path, null
                    )
                }
            }
            // 失败时目标路径不得留下残缺文件，否则更新页会拿它去安装
            assertFalse(target.exists())
            assertFalse(File(dir, "latest.apk.tmp").exists())
        } finally {
            server.stop()
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
