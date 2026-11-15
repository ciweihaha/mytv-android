package top.yogiczy.mytv.tv.ui.screen.settings.categories

import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsControlScreenTest {
    @Test
    fun channelChangeFlipSupportingText_bothDirections() {
        assertEquals(
            "方向键上：下一个频道；方向键下：上一个频道",
            channelChangeFlipSupportingText(true),
        )
        assertEquals(
            "方向键上：上一个频道；方向键下：下一个频道",
            channelChangeFlipSupportingText(false),
        )
    }
}
