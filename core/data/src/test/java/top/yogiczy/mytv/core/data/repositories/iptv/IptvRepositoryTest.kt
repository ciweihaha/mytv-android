package top.yogiczy.mytv.core.data.repositories.iptv

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import top.yogiczy.mytv.core.data.entities.channel.Channel
import top.yogiczy.mytv.core.data.entities.channel.ChannelGroup
import top.yogiczy.mytv.core.data.entities.channel.ChannelGroupList
import top.yogiczy.mytv.core.data.entities.channel.ChannelList
import top.yogiczy.mytv.core.data.entities.iptvsource.IptvSource
import top.yogiczy.mytv.core.data.network.HttpException
import top.yogiczy.mytv.core.data.utils.Globals
import java.io.File

class IptvRepositoryTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    @Before
    fun setUp() {
        Globals.cacheDir = tempFolder.newFolder("cache")
    }

    @Test
    fun getChannelGroupList_cacheHit_returnsSeededGroups() = runBlocking {
        // 缓存命中端到端：预写解析后缓存（json），地址写连不上的
        // 127.0.0.1:9——走了刷新立刻连接被拒、用例失败，正好反向证明
        // 命中缓存没走下载；之前 getChannelGroupList 一次也没端到端
        // 断言过，只有顶层 checkChannelGroupListNotEmpty 三个用例
        val source = IptvSource(name = "test", url = "http://127.0.0.1:9/iptv.m3u")
        val seeded = ChannelGroupList(
            listOf(
                ChannelGroup("央视", ChannelList(listOf(Channel(name = "CCTV-1")))),
                ChannelGroup("卫视", ChannelList(listOf(Channel(name = "湖南卫视")))),
            )
        )
        val file = File(Globals.cacheDir, source.cacheFileName("json"))
        file.parentFile?.mkdirs()
        file.writeText(Globals.json.encodeToString(seeded))
        file.setLastModified(System.currentTimeMillis())

        val result = IptvRepository(source).getChannelGroupList(60_000)

        assertEquals(2, result.size)
        assertEquals("央视", result[0].name)
        assertEquals(listOf("CCTV-1"), result[0].channelList.map { it.name })
        assertEquals("卫视", result[1].name)
        assertEquals(listOf("湖南卫视"), result[1].channelList.map { it.name })
    }

    @Test
    fun getChannelGroupList_localM3u_transformJsBlank_returnsParsedGroups() = runBlocking {
        // 天光 2027 轮姊妹检查：壳没有“精简”开关，但 transformJs 有
        // 同款三档——没写脚本时原样返回解析结果，这档一次也没端到端
        // 断言过；本地 m3u 文件走刷新那条路，不走网络
        val m3uFile = tempFolder.newFile("iptv.m3u")
        m3uFile.writeText(
            """
            #EXTM3U x-tvg-url="http://example.com/epg.xml"
            #EXTINF:-1 tvg-name="CCTV-1" group-title="央视",CCTV-1
            http://example.com/cctv1.m3u8
            #EXTINF:-1 tvg-name="CCTV-2" group-title="央视",CCTV-2
            http://example.com/cctv2.m3u8
            #EXTINF:-1 tvg-name="湖南卫视" group-title="卫视",湖南卫视
            http://example.com/hunan.m3u8
            """.trimIndent()
        )
        val source = IptvSource(
            name = "test", url = m3uFile.path, isLocal = true, transformJs = null
        )

        val result = IptvRepository(source).getChannelGroupList(60_000)

        assertEquals(2, result.size)
        assertEquals("央视", result[0].name)
        assertEquals(listOf("CCTV-1", "CCTV-2"), result[0].channelList.map { it.name })
        assertEquals("卫视", result[1].name)
        assertEquals(listOf("湖南卫视"), result[1].channelList.map { it.name })
    }

    @Test
    fun getChannelGroupList_localM3u_transformJsFails_returnsParsedGroups() = runBlocking {
        // 转换脚本跑不起来（抛错）时原样返回解析结果、不丢频道：
        // 脚本错被 runCatching 接住走兜底；就算 QuickJs 在单测环境里
        // 加载不到，建引擎那步抛的错同样被接住，结果一样是原样返回
        val m3uFile = tempFolder.newFile("iptv.m3u")
        m3uFile.writeText(
            """
            #EXTM3U x-tvg-url="http://example.com/epg.xml"
            #EXTINF:-1 tvg-name="CCTV-1" group-title="央视",CCTV-1
            http://example.com/cctv1.m3u8
            #EXTINF:-1 tvg-name="CCTV-2" group-title="央视",CCTV-2
            http://example.com/cctv2.m3u8
            #EXTINF:-1 tvg-name="湖南卫视" group-title="卫视",湖南卫视
            http://example.com/hunan.m3u8
            """.trimIndent()
        )
        val source = IptvSource(
            name = "test", url = m3uFile.path, isLocal = true,
            transformJs = "throw new Error('boom')",
        )

        val result = IptvRepository(source).getChannelGroupList(60_000)

        assertEquals(2, result.size)
        assertEquals("央视", result[0].name)
        assertEquals(listOf("CCTV-1", "CCTV-2"), result[0].channelList.map { it.name })
        assertEquals("卫视", result[1].name)
        assertEquals(listOf("湖南卫视"), result[1].channelList.map { it.name })
    }

    @Test
    fun getChannelGroupList_remoteUnreachable_throwsHttpException() = runBlocking {
        // 远程源没缓存、连不上：拉取失败要包成中文 HttpException 抛出来，
        // 不能把连接被拒的 IOException 原样抛到主界面；127.0.0.1:9
        // 拒绝连接是立刻失败、没有重试，不会拖慢测试
        val source = IptvSource(name = "test", url = "http://127.0.0.1:9/iptv.m3u")
        val result = runCatching { IptvRepository(source).getChannelGroupList(60_000) }
        val ex = result.exceptionOrNull()
        assertTrue(ex is HttpException)
        assertTrue(ex?.message?.contains("获取订阅源失败") == true)
    }

    @Test
    fun getChannelGroupList_localM3u_rawChangedAfterCache_refreshesAndReparses() = runBlocking {
        // 本地源原文改过后缓存要重刷：json 缓存是旧内容（央视），m3u
        // 原文已改成体育组；若 isExpired 的 rawChanged 那半丢了，
        // 直接回旧缓存、体育组永远出不来
        val m3uFile = tempFolder.newFile("iptv.m3u")
        m3uFile.writeText(
            """
            #EXTM3U
            #EXTINF:-1 tvg-name="CCTV-5" group-title="体育",CCTV-5
            http://example.com/cctv5.m3u8
            """.trimIndent()
        )
        val source = IptvSource(
            name = "test", url = m3uFile.path, isLocal = true, transformJs = null
        )
        val seeded = ChannelGroupList(
            listOf(ChannelGroup("央视", ChannelList(listOf(Channel(name = "CCTV-1"))))),
        )
        val cacheFile = File(Globals.cacheDir, source.cacheFileName("json"))
        cacheFile.parentFile?.mkdirs()
        cacheFile.writeText(Globals.json.encodeToString(seeded))
        cacheFile.setLastModified(System.currentTimeMillis() - 60_000)
        m3uFile.setLastModified(System.currentTimeMillis())

        val result = IptvRepository(source).getChannelGroupList(60_000)

        assertEquals(1, result.size)
        assertEquals("体育", result[0].name)
        assertEquals(listOf("CCTV-5"), result[0].channelList.map { it.name })
    }

    @Test
    fun getEpgUrl_localM3u_returnsXtvgUrl() = runBlocking {
        // 仓库层 getEpgUrl 一次也没断言过：读本地源原文 → 挑 M3U
        // 解析器 → 取 x-tvg-url，全程不走网络
        val m3uFile = tempFolder.newFile("iptv.m3u")
        m3uFile.writeText(
            """
            #EXTM3U x-tvg-url="http://example.com/epg.xml"
            #EXTINF:-1 tvg-name="CCTV-1" group-title="央视",CCTV-1
            http://example.com/cctv1.m3u8
            """.trimIndent()
        )
        val source = IptvSource(name = "test", url = m3uFile.path, isLocal = true)

        assertEquals("http://example.com/epg.xml", IptvRepository(source).getEpgUrl())
    }

    @Test
    fun getEpgUrl_localM3u_blankXtvgUrl_fallsBackToUrlTvg() = runBlocking {
        // x-tvg-url 写了空串时不能直接回空（会生成空地址的 EPG 源、
        // 节目单全空），要回落到 url-tvg 那档
        val m3uFile = tempFolder.newFile("iptv.m3u")
        m3uFile.writeText(
            """
            #EXTM3U x-tvg-url="" url-tvg="http://example.com/epg2.xml"
            #EXTINF:-1 tvg-name="CCTV-1" group-title="央视",CCTV-1
            http://example.com/cctv1.m3u8
            """.trimIndent()
        )
        val source = IptvSource(name = "test", url = m3uFile.path, isLocal = true)

        assertEquals("http://example.com/epg2.xml", IptvRepository(source).getEpgUrl())
    }

    @Test
    fun getEpgUrl_localM3u_withoutEpgUrl_returnsNull() = runBlocking {
        // 源里没写节目单地址：不抛错、回 null，主界面据此不加载节目单
        val m3uFile = tempFolder.newFile("iptv.m3u")
        m3uFile.writeText(
            """
            #EXTM3U
            #EXTINF:-1 tvg-name="CCTV-1" group-title="央视",CCTV-1
            http://example.com/cctv1.m3u8
            """.trimIndent()
        )
        val source = IptvSource(name = "test", url = m3uFile.path, isLocal = true)

        assertNull(IptvRepository(source).getEpgUrl())
    }

    @Test
    fun getEpgUrl_localFileMissing_returnsNull() = runBlocking {
        // 本地源文件不存在：读源那步抛错要被接住回 null，不能把
        // IllegalArgumentException（无 scheme 的地址）抛到主界面
        val missing = File(tempFolder.root, "missing.m3u")
        val source = IptvSource(name = "test", url = missing.path, isLocal = true)

        assertNull(IptvRepository(source).getEpgUrl())
    }

    @Test
    fun `empty group list throws`() {
        // 空源报错的文案直接显示在错误页上（MainViewModel 把 ex.message
        // 放进 MainUiState.Error），只断言类型不够：文案被改掉也没人发现
        val ex = assertThrows(IllegalStateException::class.java) {
            checkChannelGroupListNotEmpty(ChannelGroupList())
        }
        assertTrue(ex.message?.contains("没有频道") == true)
    }

    @Test
    fun `groups with no channels throw`() {
        assertThrows(IllegalStateException::class.java) {
            checkChannelGroupListNotEmpty(
                ChannelGroupList(listOf(ChannelGroup("央视", ChannelList(emptyList())))),
            )
        }
    }

    @Test
    fun `non-empty group list passes`() {
        // 不抛错即通过
        checkChannelGroupListNotEmpty(
            ChannelGroupList(
                listOf(
                    ChannelGroup(
                        "央视",
                        ChannelList(listOf(Channel(name = "CCTV-1"))),
                    )
                )
            )
        )
    }
}
