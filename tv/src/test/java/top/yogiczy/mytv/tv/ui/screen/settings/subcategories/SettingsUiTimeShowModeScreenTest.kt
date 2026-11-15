package top.yogiczy.mytv.tv.ui.screen.settings.subcategories

import org.junit.Assert.assertEquals
import org.junit.Test
import top.yogiczy.mytv.tv.ui.utils.Configs

class SettingsUiTimeShowModeScreenTest {
    @Test
    fun timeShowModeSupportingText_allModes() {
        assertEquals("不显示时间", timeShowModeSupportingText(Configs.UiTimeShowMode.HIDDEN, 30))
        assertEquals("总是显示时间", timeShowModeSupportingText(Configs.UiTimeShowMode.ALWAYS, 30))
        assertEquals("整点前后30s显示时间", timeShowModeSupportingText(Configs.UiTimeShowMode.EVERY_HOUR, 30))
        assertEquals("半点前后30s显示时间", timeShowModeSupportingText(Configs.UiTimeShowMode.HALF_HOUR, 30))
    }

    @Test
    fun timeShowModeSupportingText_rangeSecondsInterpolated() {
        assertEquals("整点前后60s显示时间", timeShowModeSupportingText(Configs.UiTimeShowMode.EVERY_HOUR, 60))
        assertEquals("半点前后60s显示时间", timeShowModeSupportingText(Configs.UiTimeShowMode.HALF_HOUR, 60))
    }
}
