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
}
