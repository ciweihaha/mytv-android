package top.yogiczy.mytv.tv.ui.screensold.videoplayer.player

import android.os.SystemClock
import android.view.SurfaceView
import android.view.TextureView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import top.yogiczy.mytv.core.data.entities.channel.ChannelLine
import top.yogiczy.mytv.core.util.utils.humanizeAudioChannels
import top.yogiczy.mytv.core.util.utils.humanizeBitrate
import top.yogiczy.mytv.core.util.utils.humanizeLanguage
import top.yogiczy.mytv.tv.ui.utils.Configs
import kotlin.math.roundToInt

/**
 * 加载超时看门狗该不该因“有数据在流动”而续命：从这次准备起的总耗
 * 时还没超过总上限（加载超时的 MAX_LOAD_TIMEOUT_TOTAL_MULTIPLIER 倍）
 * 就续，超过就不续——细流式卡住的源（几秒一个小包、永远播不起来）
 * 不能靠续命无限拖下去，总上限一到看门狗照样报超时换线路；正常慢
 * 启动（SCF 现取票据 + 列表 + 分片，第一帧 20–30 秒才到）都在上限
 * 内，能续到播起来。天光 v2.2.31 同款（aede8748）。
 * 顶层函数方便单元测试直接调用。
 */
internal fun shouldRestartLoadTimeout(
    elapsedSincePrepareMs: Long,
    timeoutMs: Long,
): Boolean =
    elapsedSincePrepareMs < timeoutMs * VideoPlayer.MAX_LOAD_TIMEOUT_TOTAL_MULTIPLIER

abstract class VideoPlayer(
    private val coroutineScope: CoroutineScope,
) {
    protected var metadata = Metadata()

    companion object {
        /**
         * 加载超时看门狗续命的总上限倍数：从一次 prepare 起，有进展
         * 的续命最多把总等待拉到加载超时的 3 倍，再往后看门狗照样报
         * 超时。天光 v2.2.31 同款（aede8748）：天光默认 15 秒 ×3 =
         * 45 秒窗口；壳默认 5 秒 ×3 = 15 秒窗口——并行方描述的第一
         * 帧 20–30 秒才到的慢启动，在壳默认档下仍可能撞上限，要
         * 靠调大设置里的加载超时才盖得住；倍数本身不改，只移植机制。
         */
        const val MAX_LOAD_TIMEOUT_TOTAL_MULTIPLIER = 3
    }

    open fun initialize() {
        clearAllListeners()
    }

    open fun release() {
        // 释放后两路计时都不应再跑：天光同款（基类 release() 里两路都取消）；
        // 切播放器核心时旧播放器被释放、协程作用域还活着，不取消的话挂起
        // 的加载超时／断流计时到点还会各触发一次
        loadTimeoutJob?.cancel()
        loadTimeoutJob = null
        cancelInterruptTimeout()
        clearAllListeners()
    }

    abstract fun prepare(line: ChannelLine)

    abstract fun play()

    abstract fun pause()

    abstract fun seekTo(position: Long)

    /**
     * 按相对位移 seek：往前/往后跳多少毫秒。
     * 不能拿 seekTo(节目内偏移) 实现：直播模式下控制条的“节目内偏移”是
     * 按节目单起止算的，而播放器时间轴的零点是直播窗口起点，两者差
     * （节目开始 − 窗口开始）短则几分钟、长则几小时，直接传过去会跳错
     * 时间；相对位移在两种时间轴上都是一致的。
     */
    abstract fun seekBy(deltaMs: Long)

    abstract fun setVolume(volume: Float)

    abstract fun getVolume(): Float

    open fun stop() {
        loadTimeoutJob?.cancel()
        interruptJob?.cancel()
        currentPosition = 0L
    }

    abstract fun selectVideoTrack(track: Metadata.Video?)

    abstract fun selectAudioTrack(track: Metadata.Audio?)

    abstract fun selectSubtitleTrack(track: Metadata.Subtitle?)

    abstract fun setVideoSurfaceView(surfaceView: SurfaceView)

    abstract fun setVideoTextureView(textureView: TextureView)

    private var loadTimeoutJob: Job? = null

    /**
     * 本轮加载超时计时的起点（triggerPrepared 时记，用单调时钟）：
     * 加载有进展时看门狗续命要按它算总耗时，超过总上限就不续了（见
     * shouldRestartLoadTimeout）。天光同款是 System.currentTimeMillis()，
     * 这里改用 elapsedRealtime()：算耗时该用单调时钟，墙钟跳变不会
     * 把续命算错；壳内同类耗时（过早播完时间戳）也是这个时钟。
     */
    private var loadTimeoutStartedAtMs: Long = 0L
    private var interruptJob: Job? = null
    private var currentPosition = 0L

    private val onResolutionListeners = mutableListOf<(width: Int, height: Int) -> Unit>()
    private val onErrorListeners = mutableListOf<(error: PlaybackException?) -> Unit>()
    private val onReadyListeners = mutableListOf<() -> Unit>()
    private val onBufferingListeners = mutableListOf<(buffering: Boolean) -> Unit>()
    private val onPreparedListeners = mutableListOf<() -> Unit>()
    private val onIsPlayingChanged = mutableListOf<(isPlaying: Boolean) -> Unit>()
    private val onDurationChanged = mutableListOf<(duration: Long) -> Unit>()
    private val onCurrentPositionChanged = mutableListOf<(position: Long) -> Unit>()
    private val onMetadataListeners = mutableListOf<(metadata: Metadata) -> Unit>()
    private val onInterruptListeners = mutableListOf<() -> Unit>()

    private fun clearAllListeners() {
        onResolutionListeners.clear()
        onErrorListeners.clear()
        onReadyListeners.clear()
        onBufferingListeners.clear()
        onPreparedListeners.clear()
        onIsPlayingChanged.clear()
        onDurationChanged.clear()
        onCurrentPositionChanged.clear()
        onMetadataListeners.clear()
        onInterruptListeners.clear()
    }

    protected fun triggerResolution(width: Int, height: Int) {
        onResolutionListeners.forEach { it(width, height) }
    }

    protected fun triggerError(error: PlaybackException?) {
        onErrorListeners.forEach { it(error) }
        // 只有真实错误才取消加载超时 watchdog：error 为 null 只是“开始缓冲”
        // 的信号（Media3 进 BUFFERING 时会调 triggerError(null)），原先连它
        // 也取消 watchdog，导致卡在缓冲的流永远等不到超时、永远不自动换线路；
        // 而清错误显示本就由 onBuffering 负责，不依赖这次调用
        if (error != null && error != PlaybackException.LOAD_TIMEOUT) {
            loadTimeoutJob?.cancel()
            loadTimeoutJob = null
        }
    }

    protected fun triggerReady() {
        onReadyListeners.forEach { it() }
        loadTimeoutJob?.cancel()
    }

    protected fun triggerBuffering(buffering: Boolean) {
        onBufferingListeners.forEach { it(buffering) }
    }

    protected fun triggerPrepared(timeoutMs: Long = Configs.videoPlayerLoadTimeout) {
        onPreparedListeners.forEach { it() }
        loadTimeoutJob?.cancel()
        loadTimeoutStartedAtMs = SystemClock.elapsedRealtime()
        loadTimeoutJob = coroutineScope.launch {
            delay(timeoutMs)
            triggerError(PlaybackException.LOAD_TIMEOUT)
        }
        interruptJob?.cancel()
        interruptJob = null
        metadata = Metadata()
    }

    /**
     * 加载有进展（一次加载完成）：数据在流动就不是失败，给看门狗续命。
     * 慢启动的源（SCF 现取票据 302 实测最慢 10 秒出头，再加列表和分
     * 片）按老写法到点必报 LOAD_TIMEOUT——看门狗只认“准备后多久
     * 没就绪”，不管加载有没有进展；续命后只有“超过总上限还播不起来”
     * 才报超时。续命时顺手清掉之前误报的超时显示：数据都流过来了，
     * “播放失败”再挂着就是错的。天光 v2.2.31 同款（aede8748）。
     * 只有看门狗还挂着时才续：就绪后分片加载还会一直回调，那时续命
     * 等于把已取消的看门狗重新挂起来，播稳后断流就乱套了（断流归
     * cutoff 计时管）。总上限外的进展不续：细流式卡住的源不能靠
     * 零星加载无限续命（见 shouldRestartLoadTimeout）。
     * 超时可传参、默认读设置里的加载超时，和 triggerPrepared 同一个
     * 写法，单测传个短超时就能验续命逻辑，不用碰 SP。
     */
    protected fun notifyLoadProgress(timeoutMs: Long = Configs.videoPlayerLoadTimeout) {
        if (loadTimeoutJob?.isActive != true) return
        val elapsedMs = SystemClock.elapsedRealtime() - loadTimeoutStartedAtMs
        if (!shouldRestartLoadTimeout(elapsedMs, timeoutMs)) return
        loadTimeoutJob?.cancel()
        loadTimeoutJob = coroutineScope.launch {
            delay(timeoutMs)
            triggerError(PlaybackException.LOAD_TIMEOUT)
        }
        // 有进展就不是失败：清掉之前误报的超时显示（空错误只清显示，
        // 不触发换线那路，见 triggerError）
        triggerError(null)
    }

    /**
     * 取消加载超时看门狗：延迟重试排队时旧看门狗还在跑，退避没等完
     * 它先到了就报一个假 LOAD_TIMEOUT；排队时直接取消，重试开火后
     * triggerPrepared() 会重新挂，不会漏掉超时判断。释放/真报错那路
     * 已有各自的取消，这里只给延迟重试排队用。天光 v2.2.31 同款。
     */
    protected fun cancelLoadTimeout() {
        loadTimeoutJob?.cancel()
        loadTimeoutJob = null
    }

    // 暂停时取消挂起的断流计时：暂停后位置不再前进，不取消的话位置
    // 冻住满一个加载超时就会触发中断、重备同一条流（按 Home 退后台后
    // 每隔一个超时重复准备一次）；天光同款（基类 pause() 里取消）。
    // 恢复后位置一动，triggerCurrentPosition 会照常重新挂计时。
    protected fun cancelInterruptTimeout() {
        interruptJob?.cancel()
        interruptJob = null
    }

    protected fun triggerIsPlayingChanged(isPlaying: Boolean) {
        onIsPlayingChanged.forEach { it(isPlaying) }
    }

    protected fun triggerDuration(duration: Long) {
        onDurationChanged.forEach { it(duration) }
    }

    protected fun triggerMetadata(metadata: Metadata) {
        onMetadataListeners.forEach { it(metadata) }
    }

    // 位置一动就重启断流计时：正常播放时位置每 500ms 变一次，旧计时
    // 必须取消、从新位置重新开始，否则播着播着会在超时后误报一次断流。
    // 超时做成参数（默认读配置）：天光同款签名，单测能传小超时验计时
    // 重启；两个生产调用方（Media3/Ijk）都不传参，行为不变。
    // triggerPrepared 也是同一写法（timeoutMs 默认读配置）。
    protected fun triggerCurrentPosition(position: Long, timeoutMs: Long = Configs.videoPlayerLoadTimeout) {
        if (currentPosition != position) {
            interruptJob?.cancel()
            interruptJob = coroutineScope.launch {
                delay(timeoutMs)
                onInterruptListeners.forEach { it() }
            }
        }
        currentPosition = position
        onCurrentPositionChanged.forEach { it(position) }
    }

    fun onResolution(listener: (width: Int, height: Int) -> Unit) {
        onResolutionListeners.add(listener)
    }

    fun onError(listener: (error: PlaybackException?) -> Unit) {
        onErrorListeners.add(listener)
    }

    fun onReady(listener: () -> Unit) {
        onReadyListeners.add(listener)
    }

    fun onBuffering(listener: (buffering: Boolean) -> Unit) {
        onBufferingListeners.add(listener)
    }

    fun onPrepared(listener: () -> Unit) {
        onPreparedListeners.add(listener)
    }

    fun onIsPlayingChanged(listener: (isPlaying: Boolean) -> Unit) {
        onIsPlayingChanged.add(listener)
    }

    fun onDurationChanged(listener: (duration: Long) -> Unit) {
        onDurationChanged.add(listener)
    }

    fun onCurrentPositionChanged(listener: (position: Long) -> Unit) {
        onCurrentPositionChanged.add(listener)
    }

    fun onMetadata(listener: (metadata: Metadata) -> Unit) {
        onMetadataListeners.add(listener)
    }

    fun onInterrupt(listener: () -> Unit) {
        onInterruptListeners.add(listener)
    }

    data class PlaybackException(val errorCodeName: String, val errorCode: Int) :
        Exception(errorCodeName) {
        companion object {
            val UNSUPPORTED_TYPE = PlaybackException("ERROR_UNSUPPORTED_TYPE", 10002)
            val LOAD_TIMEOUT = PlaybackException("ERROR_LOAD_TIMEOUT", 10003)
            val LIVE_STREAM_ENDED = PlaybackException("ERROR_LIVE_STREAM_ENDED", 10004)

            /**
             * 断流：位置一直不前进。主界面同一条线路短时间内反复断流时
             * 用它报错误，不再无上限重备（天光 v2.2.30 同款）
             */
            val CUTOFF = PlaybackException("ERROR_CUTOFF", 10005)
        }
    }

    /** 元数据 */
    data class Metadata(
        val video: Video? = null,
        val audio: Audio? = null,
        val subtitle: Subtitle? = null,
        val videoTracks: List<Video> = emptyList(),
        val audioTracks: List<Audio> = emptyList(),
        val subtitleTracks: List<Subtitle> = emptyList(),
    ) {
        data class Video(
            val index: Int? = null,
            val isSelected: Boolean? = null,
            val width: Int? = null,
            val height: Int? = null,
            val color: String? = null,
            val frameRate: Float? = null,
            val bitrate: Int? = null,
            val mimeType: String? = null,
            val decoder: String? = null,
        ) {
            override fun equals(other: Any?): Boolean {
                if (other !is Video) return false

                return width == other.width && height == other.height && frameRate == other.frameRate && bitrate == other.bitrate && mimeType == other.mimeType
            }

            override fun hashCode(): Int {
                var result = width ?: 0
                result = 31 * result + (height ?: 0)
                result = 31 * result + (frameRate?.hashCode() ?: 0)
                result = 31 * result + (bitrate ?: 0)
                result = 31 * result + (mimeType?.hashCode() ?: 0)
                return result
            }

            val shortLabel: String
                get() = listOfNotNull(
                    if (width != null && height != null) "${width}x${height}" else null,
                    frameRate?.takeIf { it > 0 }?.let { "${it.roundToInt()}fps" },
                    bitrate?.takeIf { nnBitrate -> nnBitrate > 0 }?.humanizeBitrate()
                )
                    .joinToString(", ")
                    .ifEmpty { mimeType?.takeIf { it.isNotBlank() } ?: "视轨 ${(index ?: 0) + 1}" }
        }

        data class Audio(
            val index: Int? = null,
            val isSelected: Boolean? = null,
            val channels: Int? = null,
            val channelsLabel: String? = null,
            val sampleRate: Int? = null,
            val bitrate: Int? = null,
            val mimeType: String? = null,
            val language: String? = null,
            val decoder: String? = null,
        ) {
            override fun equals(other: Any?): Boolean {
                if (other !is Audio) return false

                return channels == other.channels && sampleRate == other.sampleRate && bitrate == other.bitrate && mimeType == other.mimeType && language == other.language
            }

            override fun hashCode(): Int {
                var result = channels ?: 0
                result = 31 * result + (sampleRate ?: 0)
                result = 31 * result + (bitrate ?: 0)
                result = 31 * result + (mimeType?.hashCode() ?: 0)
                result = 31 * result + (language?.hashCode() ?: 0)
                return result
            }

            val shortLabel: String
                get() = listOfNotNull(
                    channelsLabel ?: channels?.humanizeAudioChannels(),
                    bitrate?.takeIf { nnBitrate -> nnBitrate > 0 }?.humanizeBitrate(),
                    language?.humanizeLanguage(),
                )
                    .joinToString(", ")
                    .ifEmpty { mimeType?.takeIf { it.isNotBlank() } ?: "音轨 ${(index ?: 0) + 1}" }
        }

        data class Subtitle(
            val index: Int? = null,
            val isSelected: Boolean? = null,
            val bitrate: Int? = null,
            val mimeType: String? = null,
            val language: String? = null,
        ) {
            override fun equals(other: Any?): Boolean {
                if (other !is Subtitle) return false

                return bitrate == other.bitrate && mimeType == other.mimeType && language == other.language
            }

            override fun hashCode(): Int {
                var result = (bitrate ?: 0)
                result = 31 * result + (mimeType?.hashCode() ?: 0)
                result = 31 * result + (language?.hashCode() ?: 0)
                return result
            }

            val shortLabel: String
                get() = listOfNotNull(
                    language?.humanizeLanguage(),
                )
                    .joinToString(", ")
                    .ifEmpty { mimeType?.takeIf { it.isNotBlank() } ?: "字幕 ${(index ?: 0) + 1}" }
        }
    }
}
