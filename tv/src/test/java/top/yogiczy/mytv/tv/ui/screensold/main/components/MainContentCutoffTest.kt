package top.yogiczy.mytv.tv.ui.screensold.main.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 断流重备计数（天光 v2.2.30 同款机制的壳侧移植）：
 * 位置一直不前进就算一次断流，同一条线路短时间内反复断流就放弃
 * 重备、走换线/报错；窗口外的旧断流先清掉。
 * 注意：这里的 90 秒窗口是直接传给纯函数的参数（照抄天光的用例数，
 * 只验函数本身）；生产里窗口按加载超时的 6 倍算，壳默认 5 秒、窗口 30 秒。
 */
class MainContentCutoffTest {
    @Test
    fun `上限钉死 20 次`() {
        assertEquals(20, MainContentState.MAX_CUTOFFS_BEFORE_GIVE_UP)
    }

    @Test
    fun `第一次断流不放弃`() {
        val maxCutoffs = MainContentState.MAX_CUTOFFS_BEFORE_GIVE_UP
        val (streak, giveUp) = nextCutoffStreak(emptyList(), 1000, 90_000, maxCutoffs)
        assertEquals(listOf(1000L), streak)
        assertFalse(giveUp)
    }

    @Test
    fun `窗口内第 20 次断流还不放弃`() {
        // 凤凰那种几秒就断的源，窗口里稳态就有几次；上限 3 次的话
        // 一个窗口没撑满就烧完、照样报断流。20 次是稳态的几倍余量，
        // 正常抖动永远碰不到，只防几秒内连断这种真死源
        val maxCutoffs = MainContentState.MAX_CUTOFFS_BEFORE_GIVE_UP
        val old = List(19) { it * 4000L }
        val (streak, giveUp) = nextCutoffStreak(old, 76_000, 90_000, maxCutoffs)
        assertEquals(old + 76_000L, streak)
        assertFalse(giveUp)
    }

    @Test
    fun `窗口内第 21 次断流放弃重备`() {
        val maxCutoffs = MainContentState.MAX_CUTOFFS_BEFORE_GIVE_UP
        val old = List(20) { it * 4000L }
        val (_, giveUp) = nextCutoffStreak(old, 80_000, 90_000, maxCutoffs)
        assertTrue(giveUp)
    }

    @Test
    fun `窗口外的旧断流先清掉、不误判`() {
        val maxCutoffs = MainContentState.MAX_CUTOFFS_BEFORE_GIVE_UP
        val old = List(20) { it * 1000L }
        val (streak, giveUp) = nextCutoffStreak(old, 200_000, 90_000, maxCutoffs)
        assertEquals(listOf(200_000L), streak)
        assertFalse(giveUp)
    }

    @Test
    fun `窗口边界上的旧断流还算数`() {
        // filter 是 <=：正好 90 秒前的那次还在窗口内，第 21 次要报断流
        val maxCutoffs = MainContentState.MAX_CUTOFFS_BEFORE_GIVE_UP
        val old = List(20) { it * 4000L }
        val (streak, giveUp) = nextCutoffStreak(old, 90_000, 90_000, maxCutoffs)
        assertEquals(old + 90_000L, streak)
        assertTrue(giveUp)
    }
}
