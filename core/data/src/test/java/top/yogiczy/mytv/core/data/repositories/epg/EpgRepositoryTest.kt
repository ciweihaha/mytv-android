package top.yogiczy.mytv.core.data.repositories.epg

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
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
    private class TestHttpServer(private val body: String, private val statusCode: Int = 200) {
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
                            if (statusCode == 204) {
                                // 无内容响应：不带 body，测 response.body 为空那档
                                out.write(
                                    "HTTP/1.1 204 No Content\r\nConnection: close\r\n\r\n"
                                        .toByteArray()
                                )
                                out.flush()
                            } else {
                                val content = body.toByteArray(Charsets.UTF_8)
                                out.write(
                                    "HTTP/1.1 200 OK\r\nContent-Type: application/xml\r\nContent-Length: ${content.size}\r\nConnection: close\r\n\r\n"
                                        .toByteArray()
                                )
                                out.write(content)
                                out.flush()
                            }
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

    private fun seedJsonCache(source: EpgSource, list: EpgList, lastModified: Long = System.currentTimeMillis()) {
        val file = File(Globals.cacheDir, source.cacheFileName("json"))
        file.parentFile?.mkdirs()
        file.writeText(Globals.json.encodeToString(list))
        file.setLastModified(lastModified)
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

    @Test
    fun getEpgList_refreshesWhenCachedFileIsFromYesterday() = runBlocking {
        // 缓存文件是昨天写盘的、但里面有正在播的节目：日期已过期，第一
        // 次读取就必须刷新——这档之前一次也没断言过：isExpired 判日期
        // 的分支全靠它钉，判错（一直按没过期算）时这里会直接返回旧缓存、
        // 一次网络也不走；反方向（今天的文件不刷新）由上一条用例钉死
        val now = System.currentTimeMillis()
        startServerWithLiveProgramme(now)
        val repo = EpgRepository(source)

        seedJsonCache(
            source,
            oneChannelEpg(
                listOf(Triple(now - 30 * 60_000, now + 30 * 60_000, "缓存里的节目"))
            ),
            lastModified = now - 25 * 3600_000,
        )

        val result = repo.getEpgList()

        assertEquals(
            "缓存是昨天的时应刷新、返回服务端新节目",
            listOf("正在播"),
            result.flatMap { it.programmeList.map { p -> p.title } },
        )
        assertEquals("日期过期刷新应只请求一次", 1, server.hits.get())
    }

    @Test
    fun getEpgList_parsesNegativeTimezoneOnMainPath() = runBlocking {
        // 主解析只认 `yyyyMMddHHmmss Z`（时区前有空格）：美洲源的负时区
        // -0800 只能走主解析——parseTime 单测（807 轮）只钉了时间戳差值，
        // 端到端一次也没断言过：XML 属性里的 -0800 必须一路进 parseTime、
        // 不能被中间环节吞掉。09:00-0800 是 UTC 17:00，只看标题断言不到
        // 时间，必须断言 startAt/endAt 绝对值
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <tv>
              <channel id="cctv1"><display-name>CCTV-1 综合</display-name></channel>
              <programme start="20261113090000 -0800" stop="20261113100000 -0800" channel="cctv1"><title>新闻联播</title></programme>
            </tv>
        """.trimIndent()
        server = TestHttpServer(xml).apply { start() }
        source = EpgSource(name = "test", url = "http://127.0.0.1:${server.port}/epg.xml")
        val repo = EpgRepository(source)

        val result = repo.getEpgList()

        assertEquals(1, result.size)
        assertEquals("新闻联播", result.first().programmeList.first().title)
        assertEquals(1794589200000L, result.first().programmeList.first().startAt)
        assertEquals(1794592800000L, result.first().programmeList.first().endAt)
    }

    @Test
    fun getEpgList_dropsProgrammesWithInvalidDates() = runBlocking {
        // 天光 1952 轮姊妹检查：830 轮把非法日期改成解析失败记 0，但 0 开头的
        // 节目还留在列表里——开始为 0、结束在未来时会被当成正在播（recentProgramme
        // 只比时间、不验有效性）；解析时直接丢掉无效节目，好节目不受影响
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <tv>
              <channel id="cctv1"><display-name>CCTV-1 综合</display-name></channel>
              <programme start="20261113080000 +0800" stop="20261113090000 +0800" channel="cctv1"><title>新闻联播</title></programme>
              <programme start="20260230080000 +0800" stop="20260230090000 +0800" channel="cctv1"><title>不存在的日期</title></programme>
              <programme start="20261113080000 +0800" stop="20261301090000 +0800" channel="cctv1"><title>不存在的月份</title></programme>
              <programme start="20261112240000 +0800" stop="20261113010000 +0800" channel="cctv1"><title>不存在的钟点</title></programme>
            </tv>
        """.trimIndent()
        server = TestHttpServer(xml).apply { start() }
        source = EpgSource(name = "test", url = "http://127.0.0.1:${server.port}/epg.xml")
        val repo = EpgRepository(source)

        val result = repo.getEpgList()

        assertEquals(1, result.size)
        assertEquals(1, result.first().programmeList.size)
        assertEquals("新闻联播", result.first().programmeList.first().title)
    }

    @Test
    fun getEpgList_stripsSingleLeadingBom() = runBlocking {
        // 天光 1954 轮姊妹检查：节目单开头带一个 BOM。KXml 喂 Reader 时
        // 不跳 BOM（只在 setInput(InputStream) 时按字节认 BOM），BOM 先
        // 占一列、带 <?xml 声明的源在 "PI must not start with xml" 上抛错，
        // 整个节目单加载失败；开头有几个 BOM 去几个
        val now = System.currentTimeMillis()
        server = TestHttpServer(
            "\uFEFF" + epgXml(
                "cctv1", "CCTV-1",
                listOf(Triple(now - 30 * 60_000, now + 30 * 60_000, "正在播"))
            )
        ).apply { start() }
        source = EpgSource(name = "test", url = "http://127.0.0.1:${server.port}/epg.xml")
        val repo = EpgRepository(source)

        val result = repo.getEpgList()

        assertEquals(1, result.size)
        assertEquals(
            "带一个 BOM 的节目单也应正常解析",
            listOf("正在播"),
            result.flatMap { it.programmeList.map { p -> p.title } },
        )
    }

    @Test
    fun getEpgList_stripsDoubleLeadingBom() = runBlocking {
        // 反复转存、拼接过的源开头可能带两个 BOM：只去一个、剩一个照样
        // 在 "PI must not start with xml" 上抛错；开头有几个去几个
        val now = System.currentTimeMillis()
        server = TestHttpServer(
            "\uFEFF\uFEFF" + epgXml(
                "cctv1", "CCTV-1",
                listOf(Triple(now - 30 * 60_000, now + 30 * 60_000, "正在播"))
            )
        ).apply { start() }
        source = EpgSource(name = "test", url = "http://127.0.0.1:${server.port}/epg.xml")
        val repo = EpgRepository(source)

        val result = repo.getEpgList()

        assertEquals(1, result.size)
        assertEquals(
            "带两个 BOM 的节目单也应正常解析",
            listOf("正在播"),
            result.flatMap { it.programmeList.map { p -> p.title } },
        )
    }

    @Test
    fun getEpgList_emptyBodyResponse_throwsEmptyProgrammeError() {
        // 服务端回 204 无内容：响应体为空时应走到“节目单为空”的诚实报错，
        // 而不是在 response.body!! 上抛空指针、被包成“请检查网络连接”
        server = TestHttpServer("", statusCode = 204).apply { start() }
        source = EpgSource(name = "test", url = "http://127.0.0.1:${server.port}/epg.xml")
        val repo = EpgRepository(source)

        val ex = assertThrows(Exception::class.java) {
            runBlocking { repo.getEpgList() }
        }
        assertEquals("获取节目单为空", ex.message)
    }

    @Test
    fun getEpgList_fallsBackToStaleCacheWhenForcedRefreshFails() = runBlocking {
        // 天光 1977 轮姊妹检查：缓存里没有正在播的节目 → 强制刷新 → 网络
        // 不通（127.0.0.1:9 丢弃端口、连接被拒）。刷新失败不应抛错、而应
        // 返回旧缓存顶上：断网时昨天的节目单还能看，总比直接报错强
        val now = System.currentTimeMillis()
        source = EpgSource(name = "test", url = "http://127.0.0.1:9/epg.xml")
        val repo = EpgRepository(source)

        seedJsonCache(
            source,
            oneChannelEpg(
                listOf(Triple(now - 25 * 3600_000, now - 24 * 3600_000, "昨天节目"))
            ),
        )

        val result = repo.getEpgList()

        assertEquals(
            "强制刷新失败时应返回旧缓存、不抛错",
            listOf("昨天节目"),
            result.flatMap { it.programmeList.map { p -> p.title } },
        )
        val cached = Globals.json.decodeFromString<EpgList>(
            File(Globals.cacheDir, source.cacheFileName("json")).readText()
        )
        assertEquals(
            "刷新失败时缓存文件不应被改动",
            listOf("昨天节目"),
            cached.flatMap { it.programmeList.map { p -> p.title } },
        )
    }

    @Test
    fun getEpgList_noCacheAndNetworkFails_throws() {
        // 没有任何缓存、网络也不通：没有旧数据可顶，必须抛网络错、不
        // 能静默返回空节目单——否则界面显示“无节目”，用户分不清是
        // 没台还是断网；上一条“刷新失败回旧缓存”只钉了有缓存那支，
        // 没缓存还抛不抛错一次也没断言过
        source = EpgSource(name = "test", url = "http://127.0.0.1:9/epg.xml")
        val repo = EpgRepository(source)

        val ex = assertThrows(Exception::class.java) {
            runBlocking { repo.getEpgList() }
        }
        assertEquals("获取节目单xml失败，请检查网络连接", ex.message)
    }
}
