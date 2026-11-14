package top.yogiczy.mytv.core.util.utils

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.ServerSocket
import java.util.Collections

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

                            requestLine.startsWith("GET /big ") -> {
                                // 1MB 定长：每 8KB 读一次，整数百分比大量重复，
                                // 进度回调去重后应严格递增、无重复值
                                val size = 1_000_000
                                out.write(
                                    "HTTP/1.1 200 OK\r\nContent-Length: $size\r\nConnection: close\r\n\r\n"
                                        .toByteArray()
                                )
                                val chunk = ByteArray(8192) { 0x41 }
                                var remaining = size
                                while (remaining > 0) {
                                    val n = minOf(chunk.size, remaining)
                                    out.write(chunk, 0, n)
                                    remaining -= n
                                }
                                out.flush()
                            }

                            requestLine.startsWith("GET /slow ") -> {
                                // 宣称 100MB、只发 8KB 就一直拖着不发完：
                                // 模拟慢速下载，取消时必须立刻停、不能等到发完
                                out.write(
                                    "HTTP/1.1 200 OK\r\nContent-Length: 100000000\r\nConnection: close\r\n\r\n"
                                        .toByteArray()
                                )
                                out.write(ByteArray(8192) { 0x41 })
                                out.flush()
                                Thread.sleep(60_000)
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
    fun downloadTo_progressCallback_onlyOnChange() {
        val server = TestHttpServer().apply { start() }
        try {
            val dir = tempFolder.newFolder("dl")
            val target = File(dir, "big.bin")
            val seen = Collections.synchronizedList(mutableListOf<Int>())
            runBlocking {
                Downloader.downloadTo(
                    "http://127.0.0.1:${server.port}/big", target.path
                ) { seen.add(it) }
            }
            // 回调是异步发的：等 100（读满时的最后一个值）到了再断言，
            // 另留一点时间给可能乱序到达的尾部回调；calcDownloadProgress
            // 只把 >100 钳到 99，读满正好 100 时会回调 100
            val deadline = System.currentTimeMillis() + 5000
            while (System.currentTimeMillis() < deadline && !seen.contains(100)) {
                Thread.sleep(50)
            }
            Thread.sleep(200)
            assertTrue(seen.isNotEmpty())
            assertEquals(100, seen.maxOrNull())
            // 无重复值（去重是关键性质；回调异步发，不断言顺序）
            assertEquals(seen.toSet().size, seen.size)
            assertTrue(seen.size <= 101)
        } finally {
            server.stop()
        }
    }

    @Test(timeout = 20_000)
    fun downloadTo_cancelled_stopsImmediately() {
        val server = TestHttpServer().apply { start() }
        try {
            val dir = tempFolder.newFolder("dl")
            val target = File(dir, "latest.apk")
            val start = System.currentTimeMillis()
            assertThrows(CancellationException::class.java) {
                runBlocking {
                    val job = launch {
                        Downloader.downloadTo(
                            "http://127.0.0.1:${server.port}/slow", target.path, null
                        )
                    }
                    delay(500)
                    job.cancel()
                    job.join()
                }
            }
            val elapsed = System.currentTimeMillis() - start
            // 没修之前：阻塞的 execute()/copyTo() 根本收不到取消，下载会
            // 一直拖到服务端发完（本用例里 60 秒都不发完），job.join() 永远
            // 等不到回来，靠 @Test(timeout) 判失败；修好后 1 秒内就停
            assertTrue("取消后应在 5 秒内停下，实际 ${elapsed}ms", elapsed < 5_000)
            // 取消的下载不得留下残缺文件，否则更新页会拿它去安装
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
