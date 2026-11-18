package top.yogiczy.mytv.tv.ui.screensold.videoplayer.player

import androidx.media3.common.C
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 播完重备分流（天光 2719 轮姊妹检查）：直播流播完不能悄悄重备
 * 同一条死线——凤凰 FLV 源站每条连接 2–5 秒就关连接，重备会无限
 * 循环（天光 v2.2.25 同款：过早播完报断流走换线）；点播播完是
 * 正常播完，从头重备是现有行为、保持。时长判定与天光 `readyAsLive`
 *（就绪时时长未知）同一口径：只有 C.TIME_UNSET 算直播。
 */
class Media3EndedReplayTest {

    @Test
    fun `直播流时长未知播完不重备`() {
        assertEquals(false, shouldReplayOnEnded(C.TIME_UNSET))
    }

    @Test
    fun `点播时长已知播完重备`() {
        assertEquals(true, shouldReplayOnEnded(60_000L))
    }

    @Test
    fun `时长为零也算已知播完重备`() {
        // 边界只认 TIME_UNSET：0 是已知时长（退化值），不是直播
        assertEquals(true, shouldReplayOnEnded(0L))
    }
}
