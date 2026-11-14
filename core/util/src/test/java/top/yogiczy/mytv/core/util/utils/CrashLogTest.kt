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
    fun truncateForIntent_long_truncatedWithNote() {
        val long = "x".repeat(CrashLog.MAX_INTENT_STACKTRACE_LENGTH + 10)
        val result = CrashLog.truncateForIntent(long)
        assertTrue(result.length < long.length)
        assertTrue(result.contains("堆栈过长已截断"))
    }
}
