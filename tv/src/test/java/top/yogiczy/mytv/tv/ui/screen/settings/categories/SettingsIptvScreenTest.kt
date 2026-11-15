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
}
