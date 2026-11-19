package top.yogiczy.mytv.tv.sync

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import top.yogiczy.mytv.core.data.utils.Globals
import top.yogiczy.mytv.tv.sync.repositories.CloudflareSyncRepository
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.ServerSocket
import java.util.Collections

/**
 * Cloudflare 云同步仓库测试：对着本地假 Worker 跑推拉。
 * 假 Worker 的行为和真 Worker 一致：GET/PUT /sync，请求头
 * X-Sync-Key 鉴权；没存过数据时 GET 回 404；密钥不对回 401。
 */
class CloudflareSyncRepositoryTest {
    /**
     * 最小 HTTP 服务（纯 java.net：com.sun.net.httpserver 不在 Android
     * 单测的编译 classpath 里），照 DownloaderTest 的 TestHttpServer 写法。
     */
    private class TestSyncServer {
        private val serverSocket = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
        val port: Int get() = serverSocket.localPort

        @Volatile var stored: String? = null
        @Volatile var requireKey: String = "test-key"

        data class RecordedRequest(
            val method: String,
            val path: String,
            val headers: Map<String, String>,
            val body: String,
        )

        val requests = Collections.synchronizedList(mutableListOf<RecordedRequest>())

        private val thread = Thread({
            try {
                while (!serverSocket.isClosed) {
                    serverSocket.accept().use { socket ->
                        val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
                        val requestLine = reader.readLine() ?: return@use
                        val headers = mutableMapOf<String, String>()
                        var line: String?
                        do {
                            line = reader.readLine()
                            line?.takeIf { it.isNotEmpty() }?.let {
                                val idx = it.indexOf(':')
                                if (idx > 0) headers[it.substring(0, idx).trim().lowercase()] =
                                    it.substring(idx + 1).trim()
                            }
                        } while (line != null && line.isNotEmpty())
                        val contentLength = headers["content-length"]?.toIntOrNull() ?: 0
                        val bodyChars = CharArray(contentLength)
                        var read = 0
                        while (read < contentLength) {
                            val n = reader.read(bodyChars, read, contentLength - read)
                            if (n < 0) break
                            read += n
                        }
                        val body = String(bodyChars, 0, read)
                        val parts = requestLine.split(" ")
                        val method = parts.getOrElse(0) { "" }
                        val path = parts.getOrElse(1) { "" }
                        requests.add(RecordedRequest(method, path, headers, body))

                        val out = socket.getOutputStream()
                        val (status, payload) = when {
                            headers["x-sync-key"] != requireKey ->
                                "401 Unauthorized" to ""

                            method == "PUT" && path == "/sync" -> {
                                stored = body
                                "200 OK" to ""
                            }

                            method == "GET" && path == "/sync" ->
                                stored?.let { "200 OK" to it } ?: ("404 Not Found" to "")

                            else -> "404 Not Found" to ""
                        }
                        val bytes = payload.toByteArray()
                        out.write(
                            "HTTP/1.1 $status\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n"
                                .toByteArray()
                        )
                        out.write(bytes)
                        out.flush()
                    }
                }
            } catch (_: Exception) {
                // 服务关闭
            }
        }, "test-sync-server").apply { isDaemon = true }

        fun start() = thread.start()

        fun stop() = runCatching { serverSocket.close() }
    }

    private fun repo(server: TestSyncServer, key: String = "test-key") =
        CloudflareSyncRepository("http://127.0.0.1:${server.port}/sync", key)

    private fun sampleData() = CloudSyncData(
        version = "1.1.1.99",
        syncAt = 1234567890L,
        syncFrom = "test-device",
    )

    @Test
    fun push_success_putsJsonWithSyncKey() = runBlocking {
        val server = TestSyncServer().apply { start() }
        try {
            val data = sampleData()
            assertTrue(repo(server).push(data))
            val req = server.requests.single()
            assertEquals("PUT", req.method)
            assertEquals("/sync", req.path)
            assertEquals("test-key", req.headers["x-sync-key"])
            assertEquals(data, Globals.json.decodeFromString<CloudSyncData>(req.body))
        } finally {
            server.stop()
        }
    }

    @Test
    fun pull_success_returnsStoredData() = runBlocking {
        val server = TestSyncServer().apply { start() }
        try {
            val data = sampleData()
            server.stored = Globals.json.encodeToString(data)
            val pulled = repo(server).pull()
            assertEquals(data, pulled)
            val req = server.requests.single()
            assertEquals("GET", req.method)
            assertEquals("test-key", req.headers["x-sync-key"])
        } finally {
            server.stop()
        }
    }

    @Test
    fun pull_noDataYet_returnsEmpty() = runBlocking {
        val server = TestSyncServer().apply { start() }
        try {
            // 云端还没存过（404）：不是失败，是“无云端数据”
            assertEquals(CloudSyncData.EMPTY, repo(server).pull())
        } finally {
            server.stop()
        }
    }

    @Test
    fun push_wrongKey_returnsFalse() = runBlocking {
        val server = TestSyncServer().apply { start() }
        try {
            // 401 按接口契约返回 false 而不是抛异常：调用方按返回值提示成败
            assertFalse(repo(server, key = "wrong-key").push(sampleData()))
        } finally {
            server.stop()
        }
    }

    @Test
    fun pull_wrongKey_throws() {
        val server = TestSyncServer().apply { start() }
        try {
            val ex = assertThrows(Exception::class.java) {
                runBlocking { repo(server, key = "wrong-key").pull() }
            }
            assertEquals("拉取云端失败", ex.message)
        } finally {
            server.stop()
        }
    }

    @Test
    fun roundTrip_pushThenPull_returnsSameData() = runBlocking {
        val server = TestSyncServer().apply { start() }
        try {
            val data = sampleData()
            assertTrue(repo(server).push(data))
            assertEquals(data, repo(server).pull())
        } finally {
            server.stop()
        }
    }
}
