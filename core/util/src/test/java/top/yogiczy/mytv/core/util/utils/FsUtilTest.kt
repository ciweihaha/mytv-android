package top.yogiczy.mytv.core.util.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * FsUtil.resolveWithin 测试：正常子路径放行，目录穿越拒绝。
 * 旧实现直接 File(dir, filename)，局域网调用方传 ../../ 即可越出
 * cache/file 目录写任意位置的文件。
 */
class FsUtilTest {
    private fun tempDir(): File {
        return Files.createTempDirectory("fsutil-test").toFile()
    }

    @Test
    fun resolveWithin_plainChild() {
        val dir = tempDir()
        val resolved = FsUtil.resolveWithin(dir, "iptv_source_local_1.txt")
        assertNotNull(resolved)
        assertEquals(File(dir, "iptv_source_local_1.txt").canonicalFile, resolved)
    }

    @Test
    fun resolveWithin_nestedChild() {
        val dir = tempDir()
        val resolved = FsUtil.resolveWithin(dir, "sub/file.txt")
        assertNotNull(resolved)
        assertEquals(File(dir, "sub/file.txt").canonicalFile, resolved)
    }

    @Test
    fun resolveWithin_dotDotEscapes() {
        val dir = tempDir()
        assertNull(FsUtil.resolveWithin(dir, "../escape.txt"))
        assertNull(FsUtil.resolveWithin(dir, "sub/../../escape.txt"))
        assertNull(FsUtil.resolveWithin(dir, ".."))
    }

    @Test
    fun resolveWithin_absolutePathOutsideDir() {
        val dir = tempDir()
        assertNull(FsUtil.resolveWithin(dir, "/etc/passwd"))
        assertNull(FsUtil.resolveWithin(dir, "/tmp/escape.txt"))
    }

    @Test
    fun resolveWithin_absolutePathInsideDir() {
        val dir = tempDir()
        val inside = File(dir, "inner.txt").canonicalPath
        assertEquals(File(inside), FsUtil.resolveWithin(dir, inside))
    }
}
