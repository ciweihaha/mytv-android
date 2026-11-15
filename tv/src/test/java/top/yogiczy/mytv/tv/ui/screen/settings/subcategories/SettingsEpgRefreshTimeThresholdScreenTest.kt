package top.yogiczy.mytv.tv.ui.screen.settings.subcategories

import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsEpgRefreshTimeThresholdScreenTest {
    @Test
    fun epgRefreshTimeThresholdText_hours() {
        assertEquals("0:00", epgRefreshTimeThresholdText(0))
        assertEquals("5:00", epgRefreshTimeThresholdText(5))
        assertEquals("12:00", epgRefreshTimeThresholdText(12))
    }

    @Test
    fun epgRefreshTimeThresholdHintText_usesSameTimeText() {
        assertEquals("时间不到5:00节目单将不会刷新", epgRefreshTimeThresholdHintText(5))
        assertEquals("时间不到0:00节目单将不会刷新", epgRefreshTimeThresholdHintText(0))
    }
}
