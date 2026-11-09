package top.yogiczy.mytv.core.util.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExtensionUtilsTest {
    @Test
    fun toHeaders_normal() {
        assertEquals(
            mapOf("Accept" to "application/json", "Referer" to "https://example.com/"),
            "Accept: application/json\nReferer: https://example.com/".toHeaders()
        )
    }

    @Test
    fun toHeaders_trailingNewline_keepsValidLines() {
        // 粘贴时常见的末尾空行：原先整段 headers 全被丢掉
        assertEquals(
            mapOf("Accept" to "application/json"),
            "Accept: application/json\n".toHeaders()
        )
    }

    @Test
    fun toHeaders_malformedLineSkipped_othersKept() {
        assertEquals(
            mapOf("Accept" to "application/json", "X-Token" to "abc"),
            "Accept: application/json\nnot-a-header\nX-Token: abc".toHeaders()
        )
    }

    @Test
    fun toHeaders_emptyOrBlank_returnsEmpty() {
        assertEquals(emptyMap<String, String>(), "".toHeaders())
        assertEquals(emptyMap<String, String>(), "   \n  ".toHeaders())
    }

    @Test
    fun headersValid_cases() {
        assertTrue("Accept: application/json".headersValid())
        assertTrue("Accept: application/json\n".headersValid())
        assertTrue("".headersValid())
        assertFalse("Accept: application/json\nnot-a-header".headersValid())
        assertFalse("Accept: ".headersValid())
    }

    @Test
    fun compareVersion_normal() {
        assertEquals(-1, "1.2.3".compareVersion("1.2.4"))
        assertEquals(1, "2.0.0".compareVersion("1.9.9"))
        assertEquals(0, "1.2.3".compareVersion("1.2.3"))
        assertEquals(0, "1.2".compareVersion("1.2.0"))
        assertEquals(-1, "1.0.0-rc.1".compareVersion("1.0.0"))
        assertEquals(1, "1.0.0".compareVersion("1.0.0-rc.1"))
    }

    @Test
    fun compareVersion_weirdTags_neverThrows() {
        // 自定义更新渠道的奇怪 tag：原先 toInt() 抛异常，AboutScreen 里比较时
        // 没有任何保护，打开关于页就闪退
        assertEquals(0, "v1.2.3".compareVersion("1.2.3"))
        assertEquals(0, "V1.2.3".compareVersion("1.2.3"))
        assertEquals(0, "1.0.0+build".compareVersion("1.0.0"))
        assertEquals(-1, "release-2024.11.09".compareVersion("1.1.1"))
        assertEquals(0, "".compareVersion(""))
    }
}
