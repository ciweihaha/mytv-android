package top.yogiczy.mytv.tv.voice

/**
 * PCM 转 16kHz 单声道 float32：sherpa-onnx 流式识别要的输入格式。
 * 输入是播放器解出来的 PCM（常见 48kHz 立体声 16-bit，也可能是
 * 44.1k/32k/16k、单声道或 float），输出一律 16kHz 单声道 float。
 *
 * 有状态：连续调用 process* 时，采样点位置跨 chunk 连续，边界处
 * 线性插值不断，避免 chunk 接缝的咔哒声影响识别。换台/换流时调
 * reset()，别把上一条流的尾巴带进下一条流。
 */
class Pcm16kResampler {
    // 待消费的单声道采样（float）；offset 是下一个输出采样点在它里面的位置
    private val pending = mutableListOf<Float>()
    private var offset = 0.0

    /** 输入一批 PCM16 采样（声道交错），返回 16kHz 单声道 float。 */
    fun processPcm16(samples: ShortArray, sampleRate: Int, channels: Int): FloatArray {
        if (samples.isEmpty() || sampleRate <= 0 || channels <= 0) return FloatArray(0)
        val frames = samples.size / channels
        val mono = FloatArray(frames)
        for (i in 0 until frames) {
            var sum = 0f
            for (c in 0 until channels) {
                sum += samples[i * channels + c] / 32768f
            }
            mono[i] = sum / channels
        }
        return resample(mono, sampleRate)
    }

    /** 输入一批 float 采样（声道交错，-1..1），返回 16kHz 单声道 float。 */
    fun processFloat(samples: FloatArray, sampleRate: Int, channels: Int): FloatArray {
        if (samples.isEmpty() || sampleRate <= 0 || channels <= 0) return FloatArray(0)
        val frames = samples.size / channels
        val mono = FloatArray(frames)
        for (i in 0 until frames) {
            var sum = 0f
            for (c in 0 until channels) {
                sum += samples[i * channels + c]
            }
            mono[i] = sum / channels
        }
        return resample(mono, sampleRate)
    }

    private fun resample(mono: FloatArray, sampleRate: Int): FloatArray {
        pending.addAll(mono.asList())
        val step = sampleRate.toDouble() / 16000.0
        val out = mutableListOf<Float>()
        // 要留一个采样做插值：offset+1 必须在范围内，否则最后一
        // 个输出点没法插值；留下的那点下次接着用，只晚 1/16000 秒。
        while (offset + 1 < pending.size) {
            val idx = offset.toInt()
            val frac = (offset - idx).toFloat()
            out.add(pending[idx] * (1 - frac) + pending[idx + 1] * frac)
            offset += step
        }
        // 丢掉已消费的采样，但留 offset 前面一个采样供下次插值
        val drop = offset.toInt()
        if (drop > 0) {
            repeat(drop) { pending.removeAt(0) }
            offset -= drop
        }
        return out.toFloatArray()
    }

    fun reset() {
        pending.clear()
        offset = 0.0
    }
}
