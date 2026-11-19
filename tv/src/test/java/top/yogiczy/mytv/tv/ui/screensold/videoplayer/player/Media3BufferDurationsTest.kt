package top.yogiczy.mytv.tv.ui.screensold.videoplayer.player

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 播放器缓冲时长：设置里的缓冲时间取 0 时，直接拿来当 minBuffer / 起播
 * 缓冲会让播放器零缓冲起播，分片稍慢（中继取列表 1–3 秒、凤凰流速
 * 只有 1Mbps 左右）就冻住，冻住后一有数据就立刻续播、又立刻冻住，
 * 画面一卡一卡，还不触发“正在重连”。三个值都要兜底。
 */
class Media3BufferDurationsTest {
    @Test
    fun `缓冲时间 0 时兜底到 15 秒最小缓冲 1 秒起播 5 秒重缓冲`() {
        val d = loadControlBufferDurationsMs(0L)
        assertEquals(15_000, d.minBufferMs)
        assertEquals(60_000, d.maxBufferMs)
        assertEquals(1_000, d.bufferForPlaybackMs)
        assertEquals(5_000, d.bufferForPlaybackAfterRebufferMs)
    }

    @Test
    fun `缓冲时间 5 秒时用用户的值但最小缓冲仍不低于 15 秒`() {
        val d = loadControlBufferDurationsMs(5_000L)
        assertEquals(15_000, d.minBufferMs)
        assertEquals(60_000, d.maxBufferMs)
        assertEquals(5_000, d.bufferForPlaybackMs)
        assertEquals(5_000, d.bufferForPlaybackAfterRebufferMs)
    }

    @Test
    fun `缓冲时间 30 秒时全用用户的值`() {
        val d = loadControlBufferDurationsMs(30_000L)
        assertEquals(30_000, d.minBufferMs)
        assertEquals(150_000, d.maxBufferMs)
        assertEquals(30_000, d.bufferForPlaybackMs)
        assertEquals(30_000, d.bufferForPlaybackAfterRebufferMs)
    }
}
