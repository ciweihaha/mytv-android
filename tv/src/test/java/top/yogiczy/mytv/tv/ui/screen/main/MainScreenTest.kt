package top.yogiczy.mytv.tv.ui.screen.main

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MainScreenTest {
    @Test
    fun shouldRemindUpdate_onlyForNewVersion() {
        // 没更新不提醒
        assertFalse(shouldRemindUpdate(false, "1.1.2", ""))
        assertFalse(shouldRemindUpdate(false, "1.1.2", "1.1.1"))
        // 有更新、但这个版本上次已经提醒过：不再重复提醒
        assertFalse(shouldRemindUpdate(true, "1.1.2", "1.1.2"))
        // 有更新、且是没提醒过的新版本：提醒
        assertTrue(shouldRemindUpdate(true, "1.1.2", "1.1.1"))
        // 从没提醒过（上次版本为空）：提醒
        assertTrue(shouldRemindUpdate(true, "1.1.2", ""))
    }
}
