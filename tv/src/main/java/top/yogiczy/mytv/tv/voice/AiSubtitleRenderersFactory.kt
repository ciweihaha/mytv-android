package top.yogiczy.mytv.tv.voice

import android.content.Context
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink

/**
 * 给 Media3 挂音频 tap 的 RenderersFactory：只改音频 sink（把
 * AudioTapProcessor 塞进音频链），别的渲染器一律走默认。
 * tap 默认是关的（AudioTapProcessor.enabled=false），只抄声音、不改声音；
 * AI 字幕打开时 VideoPlayerState 会把它打开。
 */
class AiSubtitleRenderersFactory(
    context: Context,
    private val audioTap: AudioTapProcessor,
) : DefaultRenderersFactory(context) {

    override fun buildAudioSink(
        context: Context,
        enableFloatOutput: Boolean,
        enableAudioTrackPlaybackParams: Boolean,
    ): AudioSink {
        return DefaultAudioSink.Builder(context)
            .setAudioProcessors(arrayOf(audioTap))
            .setEnableFloatOutput(enableFloatOutput)
            .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
            .build()
    }
}
