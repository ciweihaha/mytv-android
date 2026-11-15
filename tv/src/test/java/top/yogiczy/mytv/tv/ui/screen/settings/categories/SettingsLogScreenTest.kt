package top.yogiczy.mytv.tv.ui.screen.settings.categories

import org.junit.Assert.assertEquals
import org.junit.Test
import top.yogiczy.mytv.core.data.utils.Logger

class SettingsLogScreenTest {
    @Test
    fun logLevelLetter_allLevels() {
        assertEquals("D", logLevelLetter(Logger.LevelType.DEBUG))
        assertEquals("I", logLevelLetter(Logger.LevelType.INFO))
        assertEquals("W", logLevelLetter(Logger.LevelType.WARN))
        assertEquals("E", logLevelLetter(Logger.LevelType.ERROR))
    }

    @Test
    fun logSupportingText_withAndWithoutCause() {
        assertEquals("出错了 java.lang.Exception: 401", logSupportingText("出错了", "java.lang.Exception: 401"))
        // 没有原因时现行写法在消息后留一个空格，原样钉死
        assertEquals("出错了 ", logSupportingText("出错了", null))
    }
}
