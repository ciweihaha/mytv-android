package top.yogiczy.mytv.core.data.entities.epgsource

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import top.yogiczy.mytv.core.data.utils.Globals

/**
 * 节目单源缓存文件名：用 url 的 SHA-256（64 bit），原先用 data class
 * 的 32 位 hashCode，两个不同节目单源可能撞名、共用一个缓存文件互相
 * 覆盖——这两条是文件名不撞名的凭据。
 */
class EpgSourceTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    @Before
    fun setUp() {
        Globals.cacheDir = tempFolder.newFolder("cache")
    }

    @Test
    fun `同链接缓存文件名相同`() {
        val a = EpgSource(url = "http://1.2.3.4/all.xml")
        val b = EpgSource(url = "http://1.2.3.4/all.xml")
        assertEquals(a.cacheFileName("xml"), b.cacheFileName("xml"))
    }

    @Test
    fun `不同链接缓存文件名不同`() {
        val a = EpgSource(url = "http://1.2.3.4/all.xml")
        val b = EpgSource(url = "http://5.6.7.8/all.xml")
        assertNotEquals(a.cacheFileName("xml"), b.cacheFileName("xml"))
    }

    @Test
    fun `缓存文件名带目录前缀与扩展名`() {
        val name = EpgSource(url = "http://1.2.3.4/all.xml").cacheFileName("xml")
        assertTrue(name.startsWith("epg_source_cache/epg_source_"))
        assertTrue(name.endsWith(".xml"))
    }
}
