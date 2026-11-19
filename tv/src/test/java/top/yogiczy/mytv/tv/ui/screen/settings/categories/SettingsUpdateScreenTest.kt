package top.yogiczy.mytv.tv.ui.screen.settings.categories

import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsUpdateScreenTest {
    @Test
    fun updateForceRemindSupportingText_bothStates() {
        assertEquals("检测到新版本时会全屏提醒", updateForceRemindSupportingText(true))
        assertEquals("检测到新版本时仅消息提示", updateForceRemindSupportingText(false))
    }

    @Test
    fun currentVersionText_releaseTagFirst() {
        // 发版号在前、内部版在后，用户一眼认得出是哪个版本
        assertEquals("V3.0.0.247（1.1.1.18）", currentVersionText("1.1.1.18", "V3.0.0.247"))
    }

    @Test
    fun currentVersionText_blankReleaseTag_fallsBackToVersionName() {
        // 本地/手动构建没有发版号：只写内部版
        assertEquals("v1.1.1.18", currentVersionText("1.1.1.18", ""))
    }
}
