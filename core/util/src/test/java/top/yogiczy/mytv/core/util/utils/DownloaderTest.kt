package top.yogiczy.mytv.core.util.utils

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 下载进度计算测试：总长度未知或为 0 时不得除零崩溃。
 */
class DownloaderTest {
    @Test
    fun calcDownloadProgress_normal() {
        assertEquals(0, calcDownloadProgress(0, 100))
        assertEquals(50, calcDownloadProgress(50, 100))
        assertEquals(100, calcDownloadProgress(100, 100))
    }

    @Test
    fun calcDownloadProgress_over100ClampedTo99() {
        assertEquals(99, calcDownloadProgress(150, 100))
    }

    @Test
    fun calcDownloadProgress_unknownLengthReturnsZero() {
        // chunked 传输总长度 -1、空响应 0：旧代码直接除零抛 ArithmeticException
        assertEquals(0, calcDownloadProgress(10, -1))
        assertEquals(0, calcDownloadProgress(10, 0))
        assertEquals(0, calcDownloadProgress(0, -1))
    }
}
