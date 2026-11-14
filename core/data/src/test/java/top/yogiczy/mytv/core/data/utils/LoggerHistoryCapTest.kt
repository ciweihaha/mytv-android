package top.yogiczy.mytv.core.data.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 日志历史上限截断（天光 1843 轮姊妹检查）：历史最多留
 * [Constants.LOG_HISTORY_MAX_SIZE] 条（壳线 100 条，天光 50 条），
 * 超了删最旧的、只留最后 100 条。
 *
 * 注意：历史是全局共享的，但一次连加 105 条后，不管之前
 * 别的测试留了什么，最早的 5 条（连同旧内容）都会被截掉，
 * 列表里正好只剩自己最后 100 条，断言不受别的测试干扰。
 */
class LoggerHistoryCapTest {
    @Test
    fun `超过上限时删最旧的只留 100 条`() {
        // 上限 100 条：连加 105 条后只剩最后 100 条，最早 5 条被删掉；
        // 截断若丢了，列表会一直长下去
        val stamp = System.nanoTime()
        val messages = (0 until 105).map { "截断-$stamp-$it" }
        messages.forEach {
            Logger.addHistoryItem(Logger.HistoryItem(Logger.LevelType.INFO, "Test", it))
        }

        val history = Logger.history
        assertEquals(Constants.LOG_HISTORY_MAX_SIZE, history.size)
        assertEquals(messages[5], history.first().message)
        messages.take(5).forEach { assertFalse(history.any { item -> item.message == it }) }
        assertTrue(history.any { it.message == messages.last() })
    }
}
