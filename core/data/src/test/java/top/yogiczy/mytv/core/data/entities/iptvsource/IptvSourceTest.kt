package top.yogiczy.mytv.core.data.entities.iptvsource

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import top.yogiczy.mytv.core.data.entities.iptvsource.IptvSource.Companion.needExternalStoragePermission
import top.yogiczy.mytv.core.data.utils.Globals

/**
 * 直播源缓存文件名与外存权限判断：
 *
 * 缓存文件名用 url + transformJs 的 SHA-256（64 bit），原先用 data class
 * 的 32 位 hashCode，两个不同订阅源可能撞名、共用一个缓存文件互相覆盖；
 * 同 url 不同 transformJs 也必须分开（解析后缓存是转换后的结果）。
 * 改名后旧缓存作废一次——这几条是文件名不撞名的凭据，改回去会立刻红。
 */
class IptvSourceTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    @Before
    fun setUp() {
        Globals.cacheDir = tempFolder.newFolder("cache")
        Globals.fileDir = tempFolder.newFolder("files")
    }

    @Test
    fun `同链接同转换脚本缓存文件名相同`() {
        val a = IptvSource(url = "http://1.2.3.4/tv.txt", transformJs = "js1")
        val b = IptvSource(url = "http://1.2.3.4/tv.txt", transformJs = "js1")
        assertEquals(a.cacheFileName("json"), b.cacheFileName("json"))
    }

    @Test
    fun `同链接不同转换脚本缓存文件名不同`() {
        val a = IptvSource(url = "http://1.2.3.4/tv.txt", transformJs = "js1")
        val b = IptvSource(url = "http://1.2.3.4/tv.txt", transformJs = "js2")
        assertNotEquals(a.cacheFileName("json"), b.cacheFileName("json"))
    }

    @Test
    fun `不同链接缓存文件名不同`() {
        val a = IptvSource(url = "http://1.2.3.4/tv.txt")
        val b = IptvSource(url = "http://5.6.7.8/tv.txt")
        assertNotEquals(a.cacheFileName("json"), b.cacheFileName("json"))
    }

    @Test
    fun `缓存文件名带目录前缀与扩展名`() {
        val name = IptvSource(url = "http://1.2.3.4/tv.txt").cacheFileName("json")
        assertTrue(name.startsWith("iptv_source_cache/iptv_source_"))
        assertTrue(name.endsWith(".json"))
    }

    @Test
    fun `本地源在应用目录外要外存权限`() {
        val source = IptvSource(
            url = "/sdcard/Download/tv.txt",
            isLocal = true,
        )
        assertTrue(source.needExternalStoragePermission())
    }

    @Test
    fun `本地源在应用目录内不要外存权限`() {
        val source = IptvSource(
            url = "${Globals.fileDir.path}/tv.txt",
            isLocal = true,
        )
        assertFalse(source.needExternalStoragePermission())
    }

    @Test
    fun `远程源不要外存权限`() {
        val source = IptvSource(url = "http://1.2.3.4/tv.txt", isLocal = false)
        assertFalse(source.needExternalStoragePermission())
    }
}
