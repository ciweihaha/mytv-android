package top.yogiczy.mytv.tv.ui.screen.settings.categories

import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsUpdateScreenTest {
    @Test
    fun updateForceRemindSupportingText_bothStates() {
        assertEquals("检测到新版本时会全屏提醒", updateForceRemindSupportingText(true))
        assertEquals("检测到新版本时仅消息提示", updateForceRemindSupportingText(false))
    }
}
