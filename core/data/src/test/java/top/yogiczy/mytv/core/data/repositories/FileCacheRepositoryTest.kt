package top.yogiczy.mytv.core.data.repositories

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import top.yogiczy.mytv.core.data.utils.Globals
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream

class FileCacheRepositoryTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    private class TestRepo(fileName: String) : FileCacheRepository(fileName) {
        var refreshCount = 0

        suspend fun get(cacheTime: Long, value: String): String =
            getOrRefresh(cacheTime) {
                refreshCount++
                value
            }

        suspend fun getStream(cacheTime: Long, value: String): String =
            getOrRefreshInputStream(cacheTime) {
                refreshCount++
                value.byteInputStream()
            }.readBytes().decodeToString()

        suspend fun getStreamFrom(stream: InputStream): String =
            getOrRefreshInputStream(0) { stream }.use { it.readBytes().decodeToString() }

        suspend fun getFailing(cacheTime: Long): String =
            getOrRefresh(cacheTime) { throw RuntimeException("network down") }

        suspend fun getStreamFailing(cacheTime: Long): String =
            getOrRefreshInputStream(cacheTime) { throw RuntimeException("network down") }
                .readBytes().decodeToString()
    }

    @Before
    fun setUp() {
        Globals.cacheDir = tempFolder.newFolder("cache")
    }

    @Test
    fun getOrRefresh_cachesValueWithinCacheTime() = runBlocking {
        val repo = TestRepo("test.json")
        assertEquals("v1", repo.get(Long.MAX_VALUE, "v1"))
        assertEquals("v1", repo.get(Long.MAX_VALUE, "v2"))
        assertEquals(1, repo.refreshCount)
    }

    @Test
    fun getOrRefresh_refreshesAfterExpiry() = runBlocking {
        val repo = TestRepo("test.json")
        assertEquals("v1", repo.get(0, "v1"))
        assertEquals("v2", repo.get(0, "v2"))
        assertEquals(2, repo.refreshCount)
    }

    @Test
    fun getOrRefresh_leavesNoTempFile() = runBlocking {
        val repo = TestRepo("test.json")
        repo.get(Long.MAX_VALUE, "v1")
        val files = Globals.cacheDir.listFiles()?.map { it.name } ?: emptyList()
        assertEquals(listOf("test.json"), files)
        assertFalse(files.any { it.endsWith(".tmp") })
        assertEquals("v1", File(Globals.cacheDir, "test.json").readText())
    }

    @Test
    fun getOrRefreshInputStream_roundTrip() = runBlocking {
        val repo = TestRepo("test.bin")
        assertEquals("hello", repo.getStream(Long.MAX_VALUE, "hello"))
        assertEquals("hello", repo.getStream(Long.MAX_VALUE, "world"))
        assertEquals(1, repo.refreshCount)
        assertTrue(File(Globals.cacheDir, "test.bin").exists())
        assertFalse(File(Globals.cacheDir, "test.bin.tmp").exists())
    }

    @Test
    fun getOrRefreshInputStream_closesRefreshStream() = runBlocking {
        val repo = TestRepo("test.bin")
        var closed = false
        // 模拟网络来的刷新流：旧代码读完不关，连接回不了共用池
        val tracking = object : ByteArrayInputStream("hello".toByteArray()) {
            override fun close() {
                closed = true
                super.close()
            }
        }
        assertEquals("hello", repo.getStreamFrom(tracking))
        assertTrue(closed)
        assertEquals("hello", File(Globals.cacheDir, "test.bin").readText())
    }

    @Test
    fun getOrRefresh_fallsBackToStaleDataWhenRefreshFails() = runBlocking {
        val repo = TestRepo("test.json")
        assertEquals("v1", repo.get(Long.MAX_VALUE, "v1"))
        // 过期后刷新失败：不断网时本来会拿到 v2，断网时旧的 v1 顶上
        assertEquals("v1", repo.getFailing(0))
        assertEquals("v1", File(Globals.cacheDir, "test.json").readText())
    }

    @Test(expected = RuntimeException::class)
    fun getOrRefresh_throwsWhenNoStaleDataAndRefreshFails() = runBlocking {
        val repo = TestRepo("test.json")
        repo.getFailing(0)
    }

    @Test
    fun getOrRefreshInputStream_fallsBackToStaleDataWhenRefreshFails() = runBlocking {
        val repo = TestRepo("test.bin")
        assertEquals("hello", repo.getStream(Long.MAX_VALUE, "hello"))
        assertEquals("hello", repo.getStreamFailing(0))
        assertEquals("hello", File(Globals.cacheDir, "test.bin").readText())
    }

    @Test(expected = RuntimeException::class)
    fun getOrRefreshInputStream_throwsWhenNoStaleDataAndRefreshFails() = runBlocking {
        val repo = TestRepo("test.bin")
        repo.getStreamFailing(0)
    }

    @Test
    fun clearCache_alsoDeletesOrphanedTempFile() = runBlocking {
        val repo = TestRepo("test.json")
        repo.get(Long.MAX_VALUE, "v1")
        // 模拟原子写中途崩溃残留的 .tmp
        File(Globals.cacheDir, "test.json.tmp").writeText("partial")
        repo.clearCache()
        assertFalse(File(Globals.cacheDir, "test.json").exists())
        assertFalse(File(Globals.cacheDir, "test.json.tmp").exists())
    }
}
