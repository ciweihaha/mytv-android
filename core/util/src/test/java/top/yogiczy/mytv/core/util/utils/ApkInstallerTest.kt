package top.yogiczy.mytv.core.util.utils

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ApkInstallerTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun prepareCachedApk_copiesFromOtherDir() {
        val srcDir = tempFolder.newFolder("src")
        val cacheDir = tempFolder.newFolder("cache")
        val content = ByteArray(1024 * 1024) { (it % 251).toByte() }
        val src = File(srcDir, "test.apk").apply { writeBytes(content) }

        val cached = ApkInstaller.prepareCachedApk(src, cacheDir)

        assertEquals(File(cacheDir, "test.apk"), cached)
        assertArrayEquals(content, cached.readBytes())
    }

    @Test
    fun prepareCachedApk_alreadyInCacheDir_reusesFile() {
        val cacheDir = tempFolder.newFolder("cache")
        val content = "apk-content".toByteArray()
        val src = File(cacheDir, "test.apk").apply { writeBytes(content) }

        val cached = ApkInstaller.prepareCachedApk(src, cacheDir)

        assertEquals(src.absoluteFile, cached.absoluteFile)
        assertArrayEquals(content, cached.readBytes())
    }
}
