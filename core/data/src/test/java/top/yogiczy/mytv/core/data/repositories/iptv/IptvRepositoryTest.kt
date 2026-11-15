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
