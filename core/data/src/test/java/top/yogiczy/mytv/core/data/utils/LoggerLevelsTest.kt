package top.yogiczy.mytv.core.data.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 日志级别与原因记录（天光 1926 轮姊妹检查）：实例方法 i/w/e/wtf
 * 往历史里记的级别不能串、异常信息要记成原因、DEBUG 不进历史。
 */
class LoggerLevelsTest {
    @Test
    fun `i w e 各记自己的级别`() {
        // 实例方法调 Log 的同时往历史里记一条：级别不能串，标签与
        // 内容要原样记下来
        val stamp = System.nanoTime()
        val infoMsg = "info-$stamp"
        val warnMsg = "warn-$stamp"
        val errorMsg = "error-$stamp"
        val log = Logger.create("Test")
        log.i(infoMsg)
        log.w(warnMsg)
        log.e(errorMsg)

        val history = Logger.history
        assertEquals(
            Logger.LevelType.INFO,
            history.first { it.message == infoMsg }.level
        )
        assertEquals(
            Logger.LevelType.WARN,
            history.first { it.message == warnMsg }.level
        )
        assertEquals(
            Logger.LevelType.ERROR,
            history.first { it.message == errorMsg }.level
        )
        assertEquals("Test", history.first { it.message == infoMsg }.tag)
    }

    @Test
    fun `wtf 记成 ERROR 不是单独级别`() {
        // LevelType 里没有 WTF：wtf 记成 ERROR，别因为级别对不上
        // 在日志页里被滤掉
        val message = "wtf-${System.nanoTime()}"
        Logger.create("Test").wtf(message)

        val item = Logger.history.first { it.message == message }
        assertEquals(Logger.LevelType.ERROR, item.level)
    }

    @Test
    fun `异常信息记成原因`() {
        val message = "cause-${System.nanoTime()}"
        Logger.create("Test").e(message, RuntimeException("boom"))

        val item = Logger.history.first { it.message == message }
        assertEquals("boom", item.cause)
    }

    @Test
    fun `没信息的异常原因记 null`() {
        val message = "null-cause-${System.nanoTime()}"
        Logger.create("Test").w(message, RuntimeException())

        val item = Logger.history.first { it.message == message }
        assertNull(item.cause)
    }

    @Test
    fun `d 与 v 不进历史`() {
        // DEBUG 不进历史是刻意的（addHistoryItem 只收 INFO 及以上）：
        // d/v 虽然也调了 addHistoryItem，但级别 DEBUG 会被滤掉，
        // 日志页只留 INFO 及以上，别有人看着调用以为是漏记给“补”回去
        val stamp = System.nanoTime()
        val debugMsg = "debug-$stamp"
        val verboseMsg = "verbose-$stamp"
        val log = Logger.create("Test")
        log.d(debugMsg)
        log.v(verboseMsg)

        val history = Logger.history
        assertFalse(history.any { it.message == debugMsg })
        assertFalse(history.any { it.message == verboseMsg })
    }
}
