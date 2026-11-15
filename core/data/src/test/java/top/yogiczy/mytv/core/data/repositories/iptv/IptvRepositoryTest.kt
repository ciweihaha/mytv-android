package top.yogiczy.mytv.core.data.repositories.iptv

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
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
    fun getChannelGroupList_localM3u_transformJsSuccess_returnsTransformedGroups() = runBlocking {
        // 865 轮留下的尾巴：脚本成功那档能不能在单测里钉，取决于
        // QuickJs 引擎在单测环境里能不能起来——直接试一把：脚本把
        // 卫视那组滤掉，只留央视；引擎起不来时 transform 会兜底原样
        // 返回，断言 1 组就失败，正好说明这档在单测里钉不住
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
            transformJs = "function main(list) { return list.filter(function(item) { return item.groupName === '央视'; }); }",
        )

        val result = IptvRepository(source).getChannelGroupList(60_000)

        assertEquals(1, result.size)
        assertEquals("央视", result[0].name)
        assertEquals(listOf("CCTV-1", "CCTV-2"), result[0].channelList.map { it.name })
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
