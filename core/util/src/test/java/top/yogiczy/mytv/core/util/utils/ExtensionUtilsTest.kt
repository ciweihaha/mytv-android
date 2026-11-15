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
    fun compareVersion_preReleaseNumericSegments() {
        // 预发布版的数字段要按数值比，不能整串按字典比：
        // 旧实现 "rc.2" > "rc.10"，会把旧版当成更新
        assertEquals(-1, "1.0.0-rc.2".compareVersion("1.0.0-rc.10"))
        assertEquals(1, "1.0.0-rc.10".compareVersion("1.0.0-rc.2"))
        assertEquals(-1, "1.0.0-alpha.1".compareVersion("1.0.0-alpha.2"))
        assertEquals(-1, "1.0.0-alpha".compareVersion("1.0.0-alpha.1"))
        assertEquals(0, "1.0.0-rc.2".compareVersion("1.0.0-rc.2"))
    }

    @Test
    fun compareVersion_multiDigitSegments() {
        // 本 fork 的版本号是 1.1.1.<构建号>，构建号已过百：多位数段必须按数值比，
        // 不能按字典比（字典比会把 1.1.1.135 判成大于 1.1.1.99，更新检查漏掉新版）
        assertEquals(-1, "1.1.1.99".compareVersion("1.1.1.135"))
        assertEquals(1, "1.1.1.380".compareVersion("1.1.1.135"))
        assertEquals(0, "1.1.1.135".compareVersion("1.1.1.135"))
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

    @Test
    fun humanizeMs_boundaries() {
        assertEquals("0秒", 0L.humanizeMs())
        assertEquals("1秒", 1000L.humanizeMs())
        assertEquals("59秒", 59999L.humanizeMs())
        assertEquals("1分钟", 60000L.humanizeMs())
        assertEquals("59分钟", 3599999L.humanizeMs())
        assertEquals("1小时", 3600000L.humanizeMs())
        assertEquals("23小时", 86399999L.humanizeMs())
        assertEquals("1天", 86400000L.humanizeMs())
        assertEquals("2天", 172800000L.humanizeMs())
    }

    @Test
    fun humanizeBytes_boundaries() {
        assertEquals("0B", 0L.humanizeBytes())
        assertEquals("1023B", 1023L.humanizeBytes())
        assertEquals("1KB", 1024L.humanizeBytes())
        assertEquals("1023KB", 1048575L.humanizeBytes())
        assertEquals("1MB", 1048576L.humanizeBytes())
        assertEquals("1023MB", 1073741823L.humanizeBytes())
        assertEquals("1GB", 1073741824L.humanizeBytes())
    }

    @Test
    fun humanizeBufferNum_cases() {
        assertEquals("0秒 | 0f", 0L.humanizeBufferNum())
        assertEquals("1秒 | 30f", 1000L.humanizeBufferNum())
        assertEquals("1分钟 | 1800f", 60000L.humanizeBufferNum())
    }
}
