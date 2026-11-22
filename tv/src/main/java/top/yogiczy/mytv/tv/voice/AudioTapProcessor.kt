package top.yogiczy.mytv.tv.voice

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import top.yogiczy.mytv.core.data.utils.Logger
import java.nio.ByteBuffer

/**
 * 音频 tap：挂在 Media3 音频链里，把解出来的 PCM 抄一份给 AI 字幕，
 * 声音本身原样直通（不改音量、不改采样率、不改声道）。
 *
 * 只认 PCM16 和 PCM_FLOAT 两种编码（解码器出来的都是这两种）；
 * 别的编码（比如 AC3 直通）抛 UnhandledAudioFormatException，
 * Media3 会跳过这个处理器、声音照常播，只是那路没字幕。
 *
 * enabled=false 时只直通、不抄数据（AI 字幕没开时零开销）；
 * enabled=true 时抄一份调回调。回调是同步的（在播放器音频线程上调）：
 * onPcm/onFloat 收到的是刚拷出来的新数组，调用方要同步消费
 * （重采样是同步读的，安全）。
 */
class AudioTapProcessor(
    private val onPcm16: (samples: ShortArray, sampleRate: Int, channels: Int) -> Unit,
    private val onFloat: (samples: FloatArray, sampleRate: Int, channels: Int) -> Unit,
) : BaseAudioProcessor() {

    @Volatile
    var enabled: Boolean = false

    private val log = Logger.create("AudioTapProcessor")

    override fun onConfigure(
        inputAudioFormat: AudioProcessor.AudioFormat,
    ): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT &&
            inputAudioFormat.encoding != C.ENCODING_PCM_FLOAT
        ) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        // 原样直通：不改格式，只抄一份
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        if (!inputBuffer.hasRemaining()) return
        val format = inputAudioFormat
        val remaining = inputBuffer.remaining()
        // 没开 AI 字幕时不抄数据，直接直通
        // 回调跑在播放器音频线程上：字幕管线（重采样/识别）一旦抛异常，
        // Media3 会把它包成 ERROR_CODE_UNSPECIFIED、整个播放器报错换线，
        // 所以这里必须兜住——字幕失败只跳过这批音频，声音照常直通。
        if (enabled) {
            try {
                when (format.encoding) {
                    C.ENCODING_PCM_16BIT -> {
                        val count = remaining / 2
                        val samples = ShortArray(count)
                        inputBuffer.asShortBuffer().get(samples, 0, count)
                        onPcm16(samples, format.sampleRate, format.channelCount)
                    }
                    C.ENCODING_PCM_FLOAT -> {
                        val count = remaining / 4
                        val samples = FloatArray(count)
                        inputBuffer.asFloatBuffer().get(samples, 0, count)
                        onFloat(samples, format.sampleRate, format.channelCount)
                    }
                }
            } catch (ex: Exception) {
                log.w("AI 字幕音频回调失败，已跳过本批音频", ex)
            }
        }
        // 声音原样往下传：把输入字节拷到输出缓冲
        val outputBuffer = replaceOutputBuffer(remaining)
        outputBuffer.put(inputBuffer)
        outputBuffer.flip()
    }
}
