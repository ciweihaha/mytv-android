package top.yogiczy.mytv.tv.ui.screen.settings.categories

import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsIptvScreenTest {
    @Test
    fun iptvSourceCacheTimeText_specialValues() {
        assertEquals("不缓存", iptvSourceCacheTimeText(0L))
        assertEquals("永久", iptvSourceCacheTimeText(Long.MAX_VALUE))
    }

    @Test
    fun iptvSourceCacheTimeText_normalValuesUseHumanizeMs() {
        assertEquals("1分钟", iptvSourceCacheTimeText(60_000L))
        assertEquals("1小时", iptvSourceCacheTimeText(3_600_000L))
    }

    @Test
    fun truncateCookieForDisplay_boundaries() {
        assertEquals("", truncateCookieForDisplay(""))
        assertEquals("abc", truncateCookieForDisplay("abc"))
        assertEquals("a".repeat(50), truncateCookieForDisplay("a".repeat(50)))
        assertEquals("a".repeat(50) + "...", truncateCookieForDisplay("a".repeat(51)))
        assertEquals("a".repeat(50) + "...", truncateCookieForDisplay("a".repeat(100)))
    }

    @Test
    fun channelGroupManagementText_hiddenCounts() {
        assertEquals("共0个分组", channelGroupManagementText(0, 0))
        assertEquals("共10个分组", channelGroupManagementText(10, 0))
        assertEquals("共10个分组，已隐藏3个分组", channelGroupManagementText(10, 3))
        assertEquals("共10个分组，已隐藏10个分组", channelGroupManagementText(10, 10))
    }
}
