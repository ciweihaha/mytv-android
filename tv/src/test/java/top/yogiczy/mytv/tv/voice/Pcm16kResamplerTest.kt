package top.yogiczy.mytv.tv.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class Pcm16kResamplerTest {
    private fun assertFloatArrayEquals(expected: FloatArray, actual: FloatArray, delta: Float = 1e-4f) {
        assertEquals("长度不同", expected.size, actual.size)
        for (i in expected.indices) {
            assertTrue("下标 $i：期望 ${expected[i]}，实际 ${actual[i]}", abs(expected[i] - actual[i]) <= delta)
        }
    }

    @Test
    fun pcm16_16kMono_passthrough() {
        // 16kHz 单声道 PCM16：不用重采样，值只除以 32768。
        // 最后一个采样留着给下一批插值（流式连续性的代价），
        // 所以一次喂 4 个只出 3 个；再喂一批时会接着出。
        val r = Pcm16kResampler()
        val out = r.processPcm16(shortArrayOf(0, 16384, -16384, 32767), 16000, 1)
        assertFloatArrayEquals(floatArrayOf(0f, 0.5f, -0.5f), out)
        val out2 = r.processPcm16(shortArrayOf(32767), 16000, 1)
        assertFloatArrayEquals(floatArrayOf(32767 / 32768f), out2)
    }

    @Test
    fun pcm16_stereo_mixedToMono() {
        // 立体声混单声道：左右声道取平均；同上，最后一个采样留着插值
        val r = Pcm16kResampler()
        val out = r.processPcm16(shortArrayOf(16384, -16384, 32767, 32767), 16000, 2)
        assertFloatArrayEquals(floatArrayOf(0f), out)
    }

    @Test
    fun pcm16_48kDownsample_lengthAndValues() {
        // 48kHz → 16kHz 是 3:1：恒定信号重采样后还是恒定信号，
        // 长度约是输入帧数的 1/3（留 1 个采样做插值）
        val r = Pcm16kResampler()
        val input = ShortArray(4800) { 16384 } // 0.1 秒恒定 0.5
        val out = r.processPcm16(input, 48000, 1)
        assertTrue("输出长度应在 1599~1600，实际 ${out.size}", out.size in 1599..1600)
        for (v in out) assertTrue("恒定信号重采样后应仍是 0.5，实际 $v", abs(v - 0.5f) < 1e-4f)
    }

    @Test
    fun float_48kStereo_matchesPcm16() {
        // float 输入与 PCM16 输入同一信号，重采样结果应一致
        val r1 = Pcm16kResampler()
        val r2 = Pcm16kResampler()
        val pcm = ShortArray(4800) { 16384 }
        val flt = FloatArray(9600) { i -> if (i % 2 == 0) 0.5f else 0.5f }
        val out1 = r1.processPcm16(pcm, 48000, 1)
        val out2 = r2.processFloat(flt, 48000, 2)
        assertFloatArrayEquals(out1, out2)
    }

    @Test
    fun streaming_chunks_continuous() {
        // 分两次喂 48kHz 恒定信号：输出应连续不断、长度约等于一次喂完，
        // 不能在 chunk 边界处丢采样或跳变
        val whole = Pcm16kResampler()
        val input = ShortArray(4800) { 16384 }
        val outWhole = whole.processPcm16(input, 48000, 1)

        val chunked = Pcm16kResampler()
        val out1 = chunked.processPcm16(input.copyOfRange(0, 2400), 48000, 1)
        val out2 = chunked.processPcm16(input.copyOfRange(2400, 4800), 48000, 1)
        val outChunked = out1 + out2
        assertTrue(
            "分块输出长度 ${outChunked.size} 应与整块 ${outWhole.size} 相差不超过 1",
            abs(outChunked.size - outWhole.size) <= 1,
        )
        for (v in outChunked) assertTrue("恒定信号分块后应仍是 0.5，实际 $v", abs(v - 0.5f) < 1e-4f)
    }

    @Test
    fun invalidInput_returnsEmpty() {
        val r = Pcm16kResampler()
        assertEquals(0, r.processPcm16(ShortArray(0), 48000, 1).size)
        assertEquals(0, r.processPcm16(shortArrayOf(1, 2), 0, 1).size)
        assertEquals(0, r.processPcm16(shortArrayOf(1, 2), 48000, 0).size)
        assertEquals(0, r.processFloat(FloatArray(0), 48000, 1).size)
    }

    @Test
    fun reset_clearsState() {
        val r = Pcm16kResampler()
        r.processPcm16(ShortArray(4800) { 16384 }, 48000, 1)
        r.reset()
        // reset 后再喂同一信号，应和全新实例输出一致
        val fresh = Pcm16kResampler()
        val out1 = r.processPcm16(ShortArray(4800) { 16384 }, 48000, 1)
        val out2 = fresh.processPcm16(ShortArray(4800) { 16384 }, 48000, 1)
        assertFloatArrayEquals(out2, out1)
    }
}
