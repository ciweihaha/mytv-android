package top.yogiczy.mytv.tv.ui.screen.settings.categories

import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsUiScreenTest {
    @Test
    fun uiScreenAutoCloseDelayText_maxAndNormalValues() {
        assertEquals("不关闭", uiScreenAutoCloseDelayText(Long.MAX_VALUE))
        assertEquals("0秒", uiScreenAutoCloseDelayText(0L))
        assertEquals("1分钟", uiScreenAutoCloseDelayText(60_000L))
        assertEquals("5分钟", uiScreenAutoCloseDelayText(300_000L))
    }

    @Test
    fun uiDensityScaleRatioText_zeroAndNormalValues() {
        assertEquals("自适应", uiDensityScaleRatioText(0f))
        assertEquals("×1", uiDensityScaleRatioText(1.0f))
        assertEquals("×1.5", uiDensityScaleRatioText(1.5f))
        assertEquals("×2", uiDensityScaleRatioText(2.0f))
    }
}
