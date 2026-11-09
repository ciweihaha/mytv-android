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
import java.io.File

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
}
