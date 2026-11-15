package top.yogiczy.mytv.tv.ui.screen.about

import org.junit.Assert.assertEquals
import org.junit.Test

class AboutScreenTest {
    @Test
    fun updateCheckHintText_checkFailedTakesPriority() {
        assertEquals("检查更新失败", updateCheckHintText(true, "1.1.1", "9.0.0"))
        assertEquals("检查更新失败", updateCheckHintText(true, "1.1.1", ""))
    }

    @Test
    fun updateCheckHintText_newerVersionAvailable() {
        assertEquals("新版本: 9.0.0", updateCheckHintText(false, "1.1.1", "9.0.0"))
        assertEquals("新版本: 1.1.2", updateCheckHintText(false, "1.1.1", "1.1.2"))
    }

    @Test
    fun updateCheckHintText_noUpdate() {
        assertEquals("无更新", updateCheckHintText(false, "1.1.1", "1.1.1"))
        assertEquals("无更新", updateCheckHintText(false, "1.1.1", "1.0.0"))
        // 最新版为空时按当前版算，就是“无更新”
        assertEquals("无更新", updateCheckHintText(false, "1.1.1", ""))
    }
}
