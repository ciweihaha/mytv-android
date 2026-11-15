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
    fun compareVersion_preReleaseNumericVsAlpha() {
        // 预发布段同一位置一段是数字、一段是字母时，数字段更小（semver 规则）：
        // 符号写反了，更新检查会把预发布版的先后判错
        assertEquals(-1, "1.4.4-1".compareVersion("1.4.4-alpha"))
        assertEquals(1, "1.4.4-alpha".compareVersion("1.4.4-1"))
        assertEquals(-1, "1.4.4-rc.1".compareVersion("1.4.4-rc.x"))
        assertEquals(1, "1.4.4-rc.x".compareVersion("1.4.4-rc.1"))
    }

    @Test
    fun compareVersion_preReleaseAlphaLexicographicAndLongerLabel() {
        // 预发布段两边都是字母时按字典比（semver）：alpha < beta < rc。
        // 这条是 comparePreRelease 最后的 return part1.compareTo(part2)，
        // 之前只测过数字段与数字对字母，纯字母对字母一次也没断言过
        assertEquals(-1, "1.0.0-alpha".compareVersion("1.0.0-beta"))
        assertEquals(1, "1.0.0-rc".compareVersion("1.0.0-beta"))
        assertEquals(-1, "1.0.0-alpha.1".compareVersion("1.0.0-beta.1"))
        // 前缀相同、段多的更大：段少的更小只测过一个方向，反方向也没断言过
        assertEquals(1, "1.0.0-alpha.1".compareVersion("1.0.0-alpha"))
        assertEquals(1, "1.0.0-rc.1.2".compareVersion("1.0.0-rc.1"))
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

    @Test
    fun isIPv6_cases() {
        assertTrue("http://[2409:8087:5e01:34::20]:6610/ZTE_CMS/00000001000000060000000000000131/index.m3u8?IAS".isIPv6())
        assertTrue("[::1]".isIPv6())
        assertTrue("[2001:db8::1]:8080".isIPv6())
        assertTrue("http://[::1]/".isIPv6())
        assertFalse("http://1.2.3.4/live".isIPv6())
        assertFalse("https://example.com/live".isIPv6())
        assertFalse("example.com".isIPv6())
        assertFalse("".isIPv6())
    }

    @Test
    fun urlHost_cases() {
        assertEquals("example.com", "http://example.com/live".urlHost())
        assertEquals("example.com", "http://example.com".urlHost())
        assertEquals("example.com:8080", "https://example.com:8080/live".urlHost())
        assertEquals("[2409:8087::20]:6610", "http://[2409:8087::20]:6610/x".urlHost())
        assertEquals("", "example.com/live".urlHost())
        assertEquals("", "".urlHost())
    }

    @Test
    fun humanizeBitrate_cases() {
        // 码率显示：播放器元数据里视频/音频码率都走这里；负数先夹到 0
        assertEquals("0bps", 0.humanizeBitrate())
        assertEquals("0bps", (-5).humanizeBitrate())
        assertEquals("999bps", 999.humanizeBitrate())
        assertEquals("1Kbps", 1000.humanizeBitrate())
        assertEquals("999Kbps", 999999.humanizeBitrate())
        assertEquals("1.00Mbps", 1000000.humanizeBitrate())
        assertEquals("2.50Mbps", 2500000.humanizeBitrate())
    }

    @Test
    fun humanizeBitrate_base1024() {
        assertEquals("1Kbps", 1024.humanizeBitrate(base = 1024))
        assertEquals("1.00Mbps", 1048576.humanizeBitrate(base = 1024))
    }

    @Test
    fun humanizeAudioChannels_cases() {
        // 声道数显示：直播频道信息与播放器都走这里；表里没有的数直接拼
        assertEquals("单声道", 1.humanizeAudioChannels())
        assertEquals("立体声", 2.humanizeAudioChannels())
        assertEquals("2.1 声道", 3.humanizeAudioChannels())
        assertEquals("4.0 四声道", 4.humanizeAudioChannels())
        assertEquals("5.0 环绕声", 5.humanizeAudioChannels())
        assertEquals("5.1 环绕声", 6.humanizeAudioChannels())
        assertEquals("6.1 环绕声", 7.humanizeAudioChannels())
        assertEquals("7.1 环绕声", 8.humanizeAudioChannels())
        assertEquals("7.1.2 杜比全景声", 10.humanizeAudioChannels())
        assertEquals("7.1.4 杜比全景声", 12.humanizeAudioChannels())
        assertEquals("0声道", 0.humanizeAudioChannels())
        assertEquals("9声道", 9.humanizeAudioChannels())
    }

    @Test
    fun humanizeLanguage_cases() {
        assertEquals("中文", "zh".humanizeLanguage())
        assertEquals("简体中文", "chs".humanizeLanguage())
        assertEquals("英语", "en".humanizeLanguage())
        // 先转小写再比：源里写大写也不怕
        assertEquals("中文", "ZH".humanizeLanguage())
        assertEquals("英语", "EN".humanizeLanguage())
        // 表里没有的原样返回
        assertEquals("fr", "fr".humanizeLanguage())
        assertEquals("", "".humanizeLanguage())
    }

    @Test
    fun ensureSuffix_cases() {
        // 同步仓库拼路径：WebDAV 与本地文件都在 url/路径后补 "/" 再拼
        // all_configs.json；已有后缀不重复加
        assertEquals("abc.m3u8", "abc".ensureSuffix(".m3u8"))
        assertEquals("abc.m3u8", "abc.m3u8".ensureSuffix(".m3u8"))
        assertEquals("http://x/", "http://x".ensureSuffix("/"))
        assertEquals("http://x/", "http://x/".ensureSuffix("/"))
        assertEquals("/", "".ensureSuffix("/"))
    }
}
