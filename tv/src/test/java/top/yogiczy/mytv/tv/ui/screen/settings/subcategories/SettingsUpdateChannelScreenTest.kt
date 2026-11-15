package top.yogiczy.mytv.tv.ui.screen.settings.subcategories

import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsUpdateChannelScreenTest {
    @Test
    fun updateChannelLabel_knownChannels() {
        assertEquals("稳定版", updateChannelLabel("stable"))
        assertEquals("测试版", updateChannelLabel("beta"))
        assertEquals("开发版", updateChannelLabel("dev"))
    }

    @Test
    fun updateChannelLabel_unknownChannelShownAsIs() {
        assertEquals("nightly", updateChannelLabel("nightly"))
        assertEquals("", updateChannelLabel(""))
    }
}
