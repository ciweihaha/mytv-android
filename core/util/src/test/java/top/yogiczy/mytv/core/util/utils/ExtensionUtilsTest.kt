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
}
