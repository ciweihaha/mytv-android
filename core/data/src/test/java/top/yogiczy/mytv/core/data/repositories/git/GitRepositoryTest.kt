package top.yogiczy.mytv.core.data.repositories.git

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import top.yogiczy.mytv.core.data.network.HttpStatusException
import top.yogiczy.mytv.core.data.utils.Constants
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.ServerSocket

/**
 * 发行版拉取错误映射测试：latestRelease 只有网络调用 + 四档错误映射，
 * 解析本身是 GitReleaseParserTest 的事；这里用本地服务器走通整条路，
 * 断四档里能稳定复现的三档。
 * 地址里带 "github.com" 是为了让 GitHub 解析器接手——isSupport 只看
 * 子串，主机仍是 127.0.0.1，不走外网。
 * 空体那档（发行版接口返回空数据）测不到：parse 的返回类型是非空
 * GitRelease，?: 后面那行是死代码。
 * 取消那档也不在这里测：UpdateViewModelTest 已覆盖“检查中途取消不记失败”。
 */
class GitRepositoryTest {
    /**
     * 最小 HTTP 服务（DownloaderTest 同款写法：com.sun.net.httpserver
     * 不在 Android 单测的编译 classpath 里）。
     * /github.com/releases 回合法发行版 JSON，
     * /github.com/empty 回空数组（解析器报“没有找到任何发行版”），
     * 其余一律 404。
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
                            requestLine.startsWith("GET /github.com/releases ") -> {
                                val content = """[{"tag_name": "V2.2.0.219", "body": "notes", "assets": [{"name": "mytv-android-tv-2.2.0.219-all-sdk23-original.apk", "browser_download_url": "https://example.com/original.apk"}, {"name": "mytv-android-tv-2.2.0.219-arm64-v8a-sdk23-original.apk", "browser_download_url": "https://example.com/arm64.apk"}]}]""".toByteArray()
                                out.write(
                                    "HTTP/1.1 200 OK\r\nContent-Length: ${content.size}\r\nConnection: close\r\n\r\n"
                                        .toByteArray()
                                )
                                out.write(content)
                                out.flush()
                            }

                            requestLine.startsWith("GET /github.com/empty ") -> {
                                val content = "[]".toByteArray()
                                out.write(
                                    "HTTP/1.1 200 OK\r\nContent-Length: ${content.size}\r\nConnection: close\r\n\r\n"
                                        .toByteArray()
                                )
                                out.write(content)
                                out.flush()
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
    fun latestRelease_success_returnsParsedRelease() = runBlocking {
        val server = TestHttpServer().apply { start() }
        try {
            val release = GitRepository().latestRelease(
                "http://127.0.0.1:${server.port}/github.com/releases"
            )
            assertEquals("2.2.0.219", release.version)
            assertEquals(
                "${Constants.GITHUB_PROXY}https://example.com/arm64.apk",
                release.downloadUrl,
            )
            assertTrue(release.description.startsWith("伪装版本请到Github项目主页手动更新"))
        } finally {
            server.stop()
        }
    }

    @Test
    fun latestRelease_notFound_throwsHttpStatusException() {
        val server = TestHttpServer().apply { start() }
        try {
            val ex = assertThrows(HttpStatusException::class.java) {
                runBlocking {
                    GitRepository().latestRelease(
                        "http://127.0.0.1:${server.port}/github.com/missing"
                    )
                }
            }
            assertEquals(404, ex.code)
        } finally {
            server.stop()
        }
    }

    @Test
    fun latestRelease_emptyReleaseList_throwsWithParseMessage() = runBlocking {
        // 空数组时解析器报 IllegalStateException，仓库层要把明确原因
        // 透传给界面，不能包成“请检查网络连接”
        val server = TestHttpServer().apply { start() }
        try {
            val result = runCatching {
                GitRepository().latestRelease(
                    "http://127.0.0.1:${server.port}/github.com/empty"
                )
            }
            assertEquals(
                "发行版数据不完整：没有找到任何发行版",
                result.exceptionOrNull()?.message,
            )
        } finally {
            server.stop()
        }
    }

    @Test
    fun latestRelease_connectionRefused_throwsNetworkMessage() = runBlocking {
        // 连不上是网络问题：包成中文提示；127.0.0.1:9 拒绝连接是
        // 立刻失败、没有重试，不会拖慢测试
        val result = runCatching {
            GitRepository().latestRelease("http://127.0.0.1:9/github.com/x")
        }
        assertEquals(
            "获取最新发行版失败，请检查网络连接",
            result.exceptionOrNull()?.message,
        )
    }
}
