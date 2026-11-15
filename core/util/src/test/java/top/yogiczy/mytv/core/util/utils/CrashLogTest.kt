package top.yogiczy.mytv.core.util.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class CrashLogTest {
    @Test
    fun buildText_containsAllParts() {
        val text = CrashLog.buildText(
            timestampMillis = 1792000000000L,
            appVersion = "1.1.1.11(11)",
            deviceInfo = "HUAWEI MatePad / Android 10 (API 29)",
            message = "boom",
            stacktrace = "java.lang.RuntimeException: boom\n\tat Foo.bar(Foo.kt:1)",
        )

        assertTrue(text.contains("崩溃时间："))
        assertTrue(text.contains("应用版本：1.1.1.11(11)"))
        assertTrue(text.contains("设备：HUAWEI MatePad / Android 10 (API 29)"))
        assertTrue(text.contains("错误信息：\nboom"))
        assertTrue(text.contains("堆栈跟踪：\njava.lang.RuntimeException: boom"))
    }

    @Test
    fun buildText_blankVersionAndDevice_omitted() {
        val text = CrashLog.buildText(message = null, stacktrace = "trace")

        assertTrue(text.contains("错误信息：\n未知错误"))
        assertTrue(!text.contains("应用版本："))
        assertTrue(!text.contains("设备："))
    }

    @Test
    fun writeAndRead_roundTrip() {
        val dir = Files.createTempDirectory("crashlog-test").toFile()
        try {
            val file = CrashLog.writeToFile(dir, "hello crash")
            assertEquals(File(dir, CrashLog.FILE_NAME), file)
            assertEquals("hello crash", CrashLog.readFromFile(dir))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun readFromFile_missing_returnsNull() {
        val dir = Files.createTempDirectory("crashlog-test").toFile()
        try {
            assertNull(CrashLog.readFromFile(dir))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun readFromFile_emptyFile_returnsNull() {
        val dir = Files.createTempDirectory("crashlog-test").toFile()
        try {
            File(dir, CrashLog.FILE_NAME).writeText("")
            assertNull(CrashLog.readFromFile(dir))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun writeToFile_notADirectory_returnsNull() {
        val file = Files.createTempFile("crashlog-test", ".tmp").toFile()
        try {
            assertNull(CrashLog.writeToFile(file, "hello crash"))
        } finally {
            file.delete()
        }
    }

    @Test
    fun readFromFile_reportPathIsDirectory_returnsNull() {
        // 同名位置被目录占住时 readText 抛错：catch 那条路一次也没
        // 断言过——目录的 length() 在 Linux 上大于 0，会进 readText
        // 抛错、而不是被 length 守卫拦住，所以这档测的是真异常分支
        val dir = Files.createTempDirectory("crashlog-test").toFile()
        try {
            File(dir, CrashLog.FILE_NAME).mkdir()

            assertNull(CrashLog.readFromFile(dir))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun readFromFile_reportPathIsNonEmptyDirectory_returnsNullAndLeavesItAlone() {
        // 非空目录也一样回 null、不抛错；读回不删东西，目录原样留着
        val dir = Files.createTempDirectory("crashlog-test").toFile()
        try {
            val reportDir = File(dir, CrashLog.FILE_NAME)
            reportDir.mkdir()
            File(reportDir, "stale.txt").writeText("stale")

            assertNull(CrashLog.readFromFile(dir))
            assertTrue(reportDir.exists())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun extractStacktrace_returnsOnlyStacktrace() {
        val text = CrashLog.buildText(message = "m", stacktrace = "TRACE-BODY")
        assertEquals("TRACE-BODY", CrashLog.extractStacktrace(text))
    }

    @Test
    fun extractStacktrace_noMarker_returnsWholeText() {
        assertEquals("plain", CrashLog.extractStacktrace("plain"))
    }

    @Test
    fun truncateForIntent_short_unchanged() {
        assertEquals("short", CrashLog.truncateForIntent("short"))
    }

    @Test
    fun truncateForIntent_exactLength_unchanged() {
        val exact = "x".repeat(CrashLog.MAX_INTENT_STACKTRACE_LENGTH)
        assertEquals(exact, CrashLog.truncateForIntent(exact))
    }

    @Test
    fun truncateForIntent_long_truncatedWithNote() {
        val long = "x".repeat(CrashLog.MAX_INTENT_STACKTRACE_LENGTH + 10)
        val result = CrashLog.truncateForIntent(long)
        // 截断后只留前 MAX 个字符再加提示语：原串只超 10 字，提示语比这长，
        // 总长度反而会变长，所以不断言变短，只断言内容被截断 + 有提示语
        assertEquals(
            "x".repeat(CrashLog.MAX_INTENT_STACKTRACE_LENGTH),
            result.substring(0, CrashLog.MAX_INTENT_STACKTRACE_LENGTH)
        )
        assertTrue(result.endsWith("完整内容见崩溃日志文件）"))
        assertTrue(result.contains("堆栈过长已截断"))
    }
}
