package top.yogiczy.mytv.tv.ui.screensold.videoplayer.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 过早播完重拉（天光 v2.2.26/v2.2.27 姊妹检查）：直播流就绪 10 秒内
 * 播完不是正常播完，是源站掐了连接——先在同一条线路上重拉几次
 * 再报断流；凤凰 FLV 每条连接 2–5 秒就关，单次播完直接报断流会让
 * 只有一条线路的台立刻冻住。判定、计数、退避三档都是纯算式，
 * 抽到顶层方便单元测试直接调用、锁死档位（接线本身 JVM 单测
 * 够不着，1421 轮先例）。
 */
class Media3PrematureLiveEndRetryTest {
    @Test
    fun `上限钉死 20 次、窗口 60 秒、过早判定 10 秒`() {
        assertEquals(20, Media3VideoPlayer.MAX_PREMATURE_END_RETRIES)
        assertEquals(60_000L, Media3VideoPlayer.PREMATURE_END_RETRY_WINDOW_MS)
        assertEquals(10_000L, Media3VideoPlayer.MAX_PREMATURE_LIVE_END_MS)
    }

    @Test
    fun `直播就绪 5 秒播完要报过早`() {
        assertTrue(shouldReportPrematureLiveEnd(true, 1000L, 6000L))
    }

    @Test
    fun `点播播完不报过早`() {
        assertFalse(shouldReportPrematureLiveEnd(false, 1000L, 6000L))
    }

    @Test
    fun `播稳超过 10 秒才播完不报过早`() {
        assertTrue(shouldReportPrematureLiveEnd(true, 1000L, 11000L))
        assertFalse(shouldReportPrematureLiveEnd(true, 1000L, 11001L))
    }

    @Test
    fun `播完时刻早于就绪时刻不报过早`() {
        assertFalse(shouldReportPrematureLiveEnd(true, 5000L, 3000L))
        assertFalse(shouldReportPrematureLiveEnd(true, 1000L, -1L))
    }

    @Test
    fun `就绪时刻无效不报过早`() {
        assertFalse(shouldReportPrematureLiveEnd(true, -1L, 1000L))
    }

    @Test
    fun `窗口内第一次过早播完要重试`() {
        val (streak, giveUp) = nextPrematureEndRetry(
            emptyList(),
            1000L,
            Media3VideoPlayer.PREMATURE_END_RETRY_WINDOW_MS,
            Media3VideoPlayer.MAX_PREMATURE_END_RETRIES,
        )
        assertEquals(listOf(1000L), streak)
        assertFalse(giveUp)
    }

    @Test
    fun `窗口内连着二十次都重试、第二十一次才报断流`() {
        var streak = emptyList<Long>()
        var now = 1000L
        repeat(20) {
            val (kept, giveUp) = nextPrematureEndRetry(
                streak,
                now,
                Media3VideoPlayer.PREMATURE_END_RETRY_WINDOW_MS,
                Media3VideoPlayer.MAX_PREMATURE_END_RETRIES,
            )
            streak = kept
            assertFalse(giveUp)
            now += 2000L
        }
        val (_, giveUp) = nextPrematureEndRetry(
            streak,
            now,
            Media3VideoPlayer.PREMATURE_END_RETRY_WINDOW_MS,
            Media3VideoPlayer.MAX_PREMATURE_END_RETRIES,
        )
        assertTrue(giveUp)
    }

    @Test
    fun `窗口外的旧记录不清掉会误判、隔久了要重新数`() {
        val windowMs = Media3VideoPlayer.PREMATURE_END_RETRY_WINDOW_MS
        val maxRetries = Media3VideoPlayer.MAX_PREMATURE_END_RETRIES
        val (streak, giveUp) = nextPrematureEndRetry(
            listOf(1000L),
            1000L + windowMs + 1L,
            windowMs,
            maxRetries,
        )
        assertEquals(listOf(1000L + windowMs + 1L), streak)
        assertFalse(giveUp)
    }

    @Test
    fun `窗口边界上的旧记录还算数`() {
        val windowMs = Media3VideoPlayer.PREMATURE_END_RETRY_WINDOW_MS
        val maxRetries = Media3VideoPlayer.MAX_PREMATURE_END_RETRIES
        val (streak, giveUp) = nextPrematureEndRetry(
            listOf(1000L),
            1000L + windowMs,
            windowMs,
            maxRetries,
        )
        assertEquals(listOf(1000L, 1000L + windowMs), streak)
        assertFalse(giveUp)
    }

    @Test
    fun `重试退避短档位、第 1 2 次 1 秒、第 3 4 次 2 秒、之后 3 秒封顶`() {
        assertEquals(0L, prematureEndRetryDelayMs(0))
        assertEquals(1000L, prematureEndRetryDelayMs(1))
        assertEquals(1000L, prematureEndRetryDelayMs(2))
        assertEquals(2000L, prematureEndRetryDelayMs(3))
        assertEquals(2000L, prematureEndRetryDelayMs(4))
        assertEquals(3000L, prematureEndRetryDelayMs(5))
        assertEquals(3000L, prematureEndRetryDelayMs(20))
    }
}
