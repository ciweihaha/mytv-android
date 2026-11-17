package top.yogiczy.mytv.core.data.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 日志历史快照是拷贝（天光 2277 轮姊妹检查）：history 每次现拷
 * 快照（`_history.toList()`），调用方改返回的列表不能动到内部那份。
 * 改成直接返回 `_history` 时，设置页一次误操作就会把日志清空，
 * 而现有的上限/级别用例只断内容、一个都不会红。
 */
class LoggerHistorySnapshotTest {
    @Test
    fun `history returns a copy mutating it does not affect stored history`() {
        val message = "快照是拷贝-${System.nanoTime()}"
        Logger.addHistoryItem(Logger.HistoryItem(Logger.LevelType.INFO, "Test", message))

        val snapshot = Logger.history
        val sizeBefore = snapshot.size
        (snapshot as MutableList).clear()

        val again = Logger.history
        assertEquals(sizeBefore, again.size)
        assertTrue(again.any { it.message == message })
    }
}
