package top.yogiczy.mytv.core.util.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
    fun resolveWithin_absoluteChildIsRootedUnderDir() {
        // Unix 的 File(parent, child) 会把绝对路径子项拼到 parent 之下，
        // 不会越出 base；这里锁定该行为
        val dir = tempDir()
        val resolved = FsUtil.resolveWithin(dir, "/etc/passwd")
        assertNotNull(resolved)
        assertEquals(File(dir, "etc/passwd").canonicalFile, resolved)
    }

    @Test
    fun resolveWithin_absoluteChildWithDotDotStillRejected() {
        val dir = tempDir()
        assertNull(FsUtil.resolveWithin(dir, "/../escape.txt"))
    }

    @Test
    fun isWithin_insideDir() {
        val dir = tempDir()
        assertTrue(FsUtil.isWithin(File(dir, "a.txt"), dir))
        assertTrue(FsUtil.isWithin(File(dir, "sub/b.txt"), dir))
        assertTrue(FsUtil.isWithin(dir, dir))
    }

    @Test
    fun isWithin_dotDotEscapesRejected() {
        val dir = tempDir()
        assertFalse(FsUtil.isWithin(File(dir, "../escape.txt"), dir))
        assertFalse(FsUtil.isWithin(File(dir, "sub/../../escape.txt"), dir))
    }

    @Test
    fun isWithin_siblingPrefixNotFooled() {
        // /tmp/x/files_evil 与 /tmp/x/files 前缀相同但不是子目录
        val dir = File(tempDir(), "files").apply { mkdirs() }
        val sibling = File(dir.parentFile, "files_evil/a.txt")
        assertFalse(FsUtil.isWithin(sibling, dir))
        assertTrue(FsUtil.isWithin(File(dir, "a.txt"), dir))
    }

    @Test
    fun isWithin_absoluteOutsideRejected() {
        val dir = tempDir()
        assertFalse(FsUtil.isWithin(File("/etc/passwd"), dir))
    }
}
