package top.yogiczy.mytv.tv.ui.screensold.videoplayer.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 直播流播完重拉（天光 v2.2.28/v2.2.29 姊妹检查）：直播流播完不是
 * 正常播完，是源站掐了连接——先在同一条线路上重拉几次再报断流；
 * 凤凰 FLV 每条连接几秒就关，单次播完直接报断流会让只有一条线路
 * 的台立刻冻住。v2.2.29 之前只认“就绪 10 秒内播完”，播稳超 10 秒
 * 的落回原有断流计时那条慢路（v2.2.28 平板实测 72 秒照样报断流），
 * 现在不分播了多久，直播流播完一律走这条快路重拉；上限 100 次是
 * 稳态（60 秒窗口里约 20 次）的 5 倍余量。计数、退避两档都是纯算式，
 * 抽到顶层方便单元测试直接调用、锁死档位（接线本身 JVM 单测
 * 够不着，1421 轮先例）。
 */
class Media3PrematureLiveEndRetryTest {
    @Test
    fun `上限钉死 100 次、窗口 60 秒`() {
        assertEquals(100, Media3VideoPlayer.MAX_PREMATURE_END_RETRIES)
        assertEquals(60_000L, Media3VideoPlayer.PREMATURE_END_RETRY_WINDOW_MS)
    }

    @Test
    fun `窗口内第一次过早播完要重试`() {
        val (kept, giveUp) = nextPrematureEndRetry(
            emptyList(),
            now = 1000L,
            windowMs = 60_000L,
            maxRetries = 20,
        )
        assertFalse(giveUp)
        assertEquals(listOf(1000L), kept)
    }

    @Test
    fun `窗口内连着过早播完一百次都重试、第一百零一次才报断流`() {
        // 凤凰 FLV 每条连接 2–5 秒就关：单次过早播完直接报断流会让
        // 只有一条线路的台立刻冻住；先重拉，扛住偶发断流。上限 100 次
        // 是实测值：60 秒窗口里稳态就有约 20 次，20 次正好卡在稳态上、
        // 65 秒就烧完、照样报断流（v2.2.27 平板实测：64 秒还在播，72 秒
        // 已报）；100 次是稳态的 5 倍余量，正常抖动永远碰不到
        val maxRetries = Media3VideoPlayer.MAX_PREMATURE_END_RETRIES
        var timestamps: List<Long> = emptyList()
        var now = 1000L
        repeat(maxRetries) {
            val (kept, giveUp) = nextPrematureEndRetry(
                timestamps,
                now,
                windowMs = 60_000L,
                maxRetries = maxRetries,
            )
            assertFalse(giveUp)
            timestamps = kept
            // 间隔 500 毫秒：100 次要挤在 60 秒窗口里才数得到 100 次；
            // 真实节奏是 3–8 秒一次，窗口会先清掉旧的、永远数不到上限，
            // 那正是想要的（一直重连不断播），上限只防窗口内密集连断
            now += 500L
        }
        assertEquals(maxRetries, timestamps.size)
        val (_, giveUp) = nextPrematureEndRetry(
            timestamps,
            now,
            windowMs = 60_000L,
            maxRetries = maxRetries,
        )
        assertTrue(giveUp)
    }

    @Test
    fun `窗口外的旧记录不清掉会误判、隔久了要重新数`() {
        // 播稳一阵子后偶尔一次源抖，不该和几小时前的记录叠加计数，
        // 否则播得越久越容易被一次小抖动判死刑
        // 100 条旧记录都要落在窗口外（now - 60 秒 = 40000 之前），
        // 间隔 100 毫秒：1000..10900 全是旧的，now=100000 时全清掉
        val old = List(100) { 1000L + it * 100L }
        val (kept, giveUp) = nextPrematureEndRetry(
            old,
            now = 100_000L,
            windowMs = 60_000L,
            maxRetries = 100,
        )
        assertFalse(giveUp)
        assertEquals(listOf(100_000L), kept)
    }

    @Test
    fun `窗口边界上的旧记录还算数`() {
        // filter 是 <=：正好 60 秒前的那次还在窗口内，第 101 次要报断流
        // 间隔 600 毫秒：1000..60400，最老那条正好 60 秒前（61000-1000）
        val old = List(100) { 1000L + it * 600L }
        val (_, giveUp) = nextPrematureEndRetry(
            old,
            now = 61_000L,
            windowMs = 60_000L,
            maxRetries = 100,
        )
        assertTrue(giveUp)
    }

    @Test
    fun `重试退避短档位、第 1 2 次 1 秒、第 3 4 次 2 秒、之后 3 秒封顶`() {
        // 直播流播完不共用 silentRetryDelayMs 那档（1/2/3 秒递增）：
        // 连接是被源站掐的，等太久只会让缓冲态占掉大半时间；重拉本身
        // 就是新连接，等 1–3 秒够了
        assertEquals(0L, prematureEndRetryDelayMs(0))
        assertEquals(1000L, prematureEndRetryDelayMs(1))
        assertEquals(1000L, prematureEndRetryDelayMs(2))
        assertEquals(2000L, prematureEndRetryDelayMs(3))
        assertEquals(2000L, prematureEndRetryDelayMs(4))
        assertEquals(3000L, prematureEndRetryDelayMs(5))
        assertEquals(3000L, prematureEndRetryDelayMs(100))
    }
}
