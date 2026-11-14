package top.yogiczy.mytv.core.data.repositories.epg

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import top.yogiczy.mytv.core.data.entities.epg.Epg
import top.yogiczy.mytv.core.data.entities.epg.EpgList
import top.yogiczy.mytv.core.data.entities.epg.EpgProgramme
import top.yogiczy.mytv.core.data.entities.epg.EpgProgrammeList
import top.yogiczy.mytv.core.data.entities.epgsource.EpgSource
import top.yogiczy.mytv.core.data.utils.Globals
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.ServerSocket
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger

/**
 * getEpgList 端到端测试：712 轮修法（解码后没有正在播的节目就强制再拉
 * 一次）那条路，之前只有 hasLiveProgramme 的纯函数单测、没有端到端
 * 用例——如果有人把这支判断删掉，缓存会被沿用一整天、白天全台显示无
 * 节目，端到端不会报警。
 *
 * 两个用例各钉一支：缓存里全是已播完的节目必须强制刷新；缓存里有正在
 * 播的节目直接用、不走网络。节目单 XML 走本地假服务（照 DownloaderTest
 * 的 TestHttpServer 写法），解析器走 EpgParser.fromXml 的真路。
 */
class EpgRepositoryTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var server: TestHttpServer
    private lateinit var source: EpgSource

    /**
     * 最小 HTTP 服务（纯 java.net：com.sun.net.httpserver 不在 Android
     * 单测的编译 classpath 里）。只回一份固定的节目单 XML，并数一共被
     * 请求了几次。
     */
    private class TestHttpServer(private val body: String) {
        private val serverSocket = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
        val port: Int get() = serverSocket.localPort
        val hits = AtomicInteger(0)

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
                        if (requestLine.startsWith("GET /epg.xml ")) {
                            hits.incrementAndGet()
                            val content = body.toByteArray(Charsets.UTF_8)
                            out.write(
                                "HTTP/1.1 200 OK\r\nContent-Type: application/xml\r\nContent-Length: ${content.size}\r\nConnection: close\r\n\r\n"
                                    .toByteArray()
                            )
                            out.write(content)
                            out.flush()
                        } else {
                            out.write(
                                "HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\nConnection: close\r\n\r\n"
                                    .toByteArray()
                            )
                            out.flush()
                        }
                    }
                }
            } catch (_: Exception) {
                // 服务关闭
            }
        }, "test-epg-http-server").apply { isDaemon = true }

        fun start() = thread.start()

        fun stop() = runCatching { serverSocket.close() }
    }

    private fun formatEpgTime(millis: Long): String =
        SimpleDateFormat("yyyyMMddHHmmss Z", Locale.getDefault()).format(millis)

    private fun epgXml(
        channelId: String,
        channelName: String,
        programmes: List<Triple<Long, Long, String>>,
    ): String {
        val sb = StringBuilder()
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?><tv>")
        sb.append("<channel id=\"$channelId\"><display-name>$channelName</display-name></channel>")
        for ((start, end, title) in programmes) {
            sb.append(
                "<programme channel=\"$channelId\" start=\"${formatEpgTime(start)}\" stop=\"${formatEpgTime(end)}\"><title>$title</title></programme>"
            )
        }
        sb.append("</tv>")
        return sb.toString()
    }

    private fun seedJsonCache(source: EpgSource, list: EpgList) {
        val file = File(Globals.cacheDir, source.cacheFileName("json"))
        file.parentFile?.mkdirs()
        file.writeText(Globals.json.encodeToString(list))
        file.setLastModified(System.currentTimeMillis())
    }

    private fun oneChannelEpg(programmes: List<Triple<Long, Long, String>>) = EpgList(
        listOf(
            Epg(
                id = "cctv1",
                channelList = listOf("CCTV-1"),
                programmeList = EpgProgrammeList(
                    programmes.map { (start, end, title) -> EpgProgramme(start, end, title) }
                ),
            )
        )
    )

    private fun startServerWithLiveProgramme(now: Long) {
        server = TestHttpServer(
            epgXml(
                "cctv1", "CCTV-1",
                listOf(Triple(now - 30 * 60_000, now + 30 * 60_000, "正在播"))
            )
        ).apply { start() }
        source = EpgSource(name = "test", url = "http://127.0.0.1:${server.port}/epg.xml")
    }

    @Before
    fun setUp() {
        Globals.cacheDir = tempFolder.newFolder("cache")
    }

    @After
    fun tearDown() {
        if (::server.isInitialized) server.stop()
    }

    @Test
    fun getEpgList_refreshesWhenCachedProgrammesHaveNoLiveProgramme() = runBlocking {
        val now = System.currentTimeMillis()
        startServerWithLiveProgramme(now)
        val repo = EpgRepository(source)

        // 缓存是今天写盘的、但里面只有昨天的节目（文件更新前拉的旧缓存）：
        // 日期没过期，但一个正在播的节目都没有，必须强制再拉一次
        seedJsonCache(
            source,
            oneChannelEpg(
                listOf(Triple(now - 25 * 3600_000, now - 24 * 3600_000, "昨天节目"))
            ),
        )

        val result = repo.getEpgList()

        // 结果是服务端的新节目、不是旧缓存；第一次读缓存没走网络，强制
        // 刷新只请求了一次；新内容也写回了缓存文件（按解码后的内容断言，
        // 不直接比原始文本：编码器对中文是否转义与实现有关）
        assertEquals(
            "无正在播节目时应强制刷新、返回服务端新节目",
            listOf("正在播"),
            result.flatMap { it.programmeList.map { p -> p.title } },
        )
        assertEquals("强制刷新应只请求一次", 1, server.hits.get())
        val cached = Globals.json.decodeFromString<EpgList>(
            File(Globals.cacheDir, source.cacheFileName("json")).readText()
        )
        assertEquals(
            "缓存文件应被新节目单覆盖",
            listOf("正在播"),
            cached.flatMap { it.programmeList.map { p -> p.title } },
        )
    }

    @Test
    fun getEpgList_usesCacheWhenItHasLiveProgramme() = runBlocking {
        val now = System.currentTimeMillis()
        startServerWithLiveProgramme(now)
        val repo = EpgRepository(source)

        // 缓存是今天写盘的、里面有正在播的节目：直接用缓存，不走网络
        seedJsonCache(
            source,
            oneChannelEpg(
                listOf(Triple(now - 30 * 60_000, now + 30 * 60_000, "缓存里的节目"))
            ),
        )

        val result = repo.getEpgList()

        assertEquals(
            "缓存里有正在播的节目时应直接用缓存",
            listOf("缓存里的节目"),
            result.flatMap { it.programmeList.map { p -> p.title } },
        )
        assertEquals("用缓存时不应走网络", 0, server.hits.get())
    }
}
