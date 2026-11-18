package top.yogiczy.mytv.tv.ui.screensold.videoplayer.player

import android.content.Context
import android.net.Uri
import android.os.SystemClock
import android.util.Base64
import android.view.SurfaceView
import android.view.TextureView
import java.util.concurrent.atomic.AtomicInteger
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.TrackGroup
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.common.text.Cue
import androidx.media3.common.text.CueGroup
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.rtmp.RtmpDataSource
import androidx.media3.exoplayer.DecoderReuseEvaluation
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.dash.DashMediaSource
import androidx.media3.exoplayer.drm.DefaultDrmSessionManager
import androidx.media3.exoplayer.drm.FrameworkMediaDrm
import androidx.media3.exoplayer.drm.LocalMediaDrmCallback
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.rtsp.RtspMediaSource
import androidx.media3.exoplayer.smoothstreaming.SsMediaSource
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.util.EventLogger
import androidx.media3.exoplayer.video.MediaCodecVideoRenderer
import com.google.common.collect.ImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.max

import top.yogiczy.mytv.core.data.entities.channel.ChannelLine
import top.yogiczy.mytv.core.util.utils.toHeaders
import top.yogiczy.mytv.tv.ui.utils.Configs
import top.yogiczy.mytv.core.data.utils.Logger

/**
 * 无声重试的错误码：BEHIND_LIVE_WINDOW / DECODING_FAILED /
 * IO_UNSPECIFIED（原有）+ 瞬时网络错 IO_BAD_HTTP_STATUS（2004：拉列表
 * 或分片时上游回了错误状态，如 502/503/403）、
 * IO_NETWORK_CONNECTION_FAILED（2001）、
 * IO_NETWORK_CONNECTION_TIMEOUT（2002）。ExoPlayer 内部已重试过
 * 几次才上报，但直接判死刑会让只有一条线路的台永久卡死（CCTV7
 * 的 2004 就是这么报播放失败的）；同一条线路上再试几次。
 * 顶层函数方便单元测试直接调用。
 */
internal fun isSilentRetryError(errorCode: Int): Boolean =
    errorCode == androidx.media3.common.PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW ||
        errorCode == androidx.media3.common.PlaybackException.ERROR_CODE_DECODING_FAILED ||
        errorCode == androidx.media3.common.PlaybackException.ERROR_CODE_IO_UNSPECIFIED ||
        errorCode == androidx.media3.common.PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS ||
        errorCode == androidx.media3.common.PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED ||
        errorCode == androidx.media3.common.PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT

/**
 * 无声重试第几次前等多久：第 1 次 1 秒、第 2 次 2 秒、第 3 次
 * 3 秒。调用处先把 silentRetryCount 加一再算，入参就是第几次重试；
 * 等多久是纯算式，抽出来方便单元测试直接调用、锁死档位，免得
 * 以后改了倍数或先加后算的顺序、重试节奏悄悄变了没人知道。
 */
internal fun silentRetryDelayMs(retryNumber: Int): Long = 1000L * retryNumber

/**
 * 还能不能无声重试：是无声重试类错误、且已重试次数没到上限
 * （MAX_SILENT_RETRY = 3，最多重试 3 次）。调用处先判后加，
 * 入参是“已重试次数”。抽出来方便单元测试直接调用、锁死上限。
 */
internal fun shouldSilentRetry(errorCode: Int, retriesSoFar: Int): Boolean =
    isSilentRetryError(errorCode) && retriesSoFar < Media3VideoPlayer.MAX_SILENT_RETRY

/**
 * 播完（STATE_ENDED）后该不该重备同一条流：只认点播——
 * 直播流时长未知（C.TIME_UNSET），播完说明源站关了连接，重备同一条
 * 死线会无限循环（凤凰 FLV 源站每条连接 2–5 秒就断，天光 v2.2.25
 * 同款：就绪 10 秒内播完立即报断流走换线，2719 轮）；点播播完是
 * 正常播完，从头重备是现有行为、保持。时长判定与天光 `readyAsLive`
 *（就绪时时长未知）同一口径。顶层函数方便单元测试直接调用。
 */
internal fun shouldReplayOnEnded(durationMs: Long): Boolean = durationMs != C.TIME_UNSET

/**
 * 直播流播完该不该在同一条线路上再试一次：60 秒窗口内累计
 * 的播完还没到上限就再试，超过上限就按断流报、走换线/报错。
 * 窗口外的旧记录先清掉——播稳一阵子后偶尔一次源抖不该和几小时前
 * 的记录叠加计数，否则播得越久越容易被一次小抖动判死刑；窗口内
 * 连着断才说明这条线现在就是死的。
 * 凤凰 FLV 每条连接几秒就关：单次播完直接报断流会让只有一条线路
 * 的台立刻冻住；先重拉几次（每次都是 SCF 现取新票据的新连接），
 * 扛住偶发断流，超限才报。
 * v2.2.29 之前只认“就绪 10 秒内播完”：播稳超 10 秒才断的落回原有
 * 断流计时那条慢路，v2.2.28 平板实测 72 秒照样报断流——上限提到
 * 100 次也没用，瓶颈在那条慢路。直播流没有“播完”这一说，播多久
 * 断了都是源死了：现在不分 10 秒内外，直播流播完一律走这条快路
 * 重拉（1–3 秒退避、上限 100 次）。
 * 上限 100 次不是拍脑袋：实测凤凰每条连接只活 2–3 秒、加 1–3 秒
 * 退避，60 秒窗口里稳态就有约 20 次；20 次正好卡在稳态上、65 秒
 * 就烧完上限、照样报断流（v2.2.27 平板实测：64 秒还在播，72 秒
 * 已报）。100 次是稳态的 5 倍余量，正常抖动永远碰不到，只防
 * 0.6 秒内连断这种真死源。真正死掉的源不用怕上限太高：播不起来
 * 根本到不了就绪，播完这支不触发，加载超时那路会先报；上限只管
 * “播几秒又断”这种半死不活的线。
 * 顶层函数方便单元测试直接调用。
 */
internal fun nextPrematureEndRetry(
    previous: List<Long>,
    now: Long,
    windowMs: Long,
    maxRetries: Int,
): Pair<List<Long>, Boolean> {
    val kept = previous.filter { now - it <= windowMs } + now
    return kept to (kept.size > maxRetries)
}

/**
 * 直播流播完第几次重拉前等多久：第 1、2 次 1 秒，第 3、4 次 2 秒，
 * 第 5 次起 3 秒封顶。不共用 silentRetryDelayMs 那档（1/2/3 秒递增）：
 * 播完是“播起来了、连接被源站掐了”，不是上游一时抖动——
 * 等太久只会让缓冲态占掉大半时间；重拉本身就是新连接，等 1–3 秒
 * 够了。调用处先把本次播完计入再算，入参就是第几次重试；纯算式，
 * 抽出来方便单元测试锁死档位。
 */
internal fun prematureEndRetryDelayMs(retryNumber: Int): Long = when {
    retryNumber <= 0 -> 0L
    retryNumber <= 2 -> 1000L
    retryNumber <= 4 -> 2000L
    else -> 3000L
}

/**
 * 容器不支持时下一个没试过的容器类型：HLS → DASH → RTSP → OTHER → SS，
 * 全试过回 null。顺序是契约——先试最常见的 HLS，再试其他；顺序写反了，
 * 兜底的先后就变了。顶层函数方便单元测试直接调用（监听器里的接线本身
 * JVM 单测够不着，判定抽出来才能钉死）。
 */
internal fun nextUntriedContentType(attempts: Map<Int, Boolean>): Int? =
    when {
        attempts[C.CONTENT_TYPE_HLS] != true -> C.CONTENT_TYPE_HLS
        attempts[C.CONTENT_TYPE_DASH] != true -> C.CONTENT_TYPE_DASH
        attempts[C.CONTENT_TYPE_RTSP] != true -> C.CONTENT_TYPE_RTSP
        attempts[C.CONTENT_TYPE_OTHER] != true -> C.CONTENT_TYPE_OTHER
        attempts[C.CONTENT_TYPE_SS] != true -> C.CONTENT_TYPE_SS
        else -> null
    }

/**
 * Media3 Format 转界面元数据：三个纯转换，之前是 Media3VideoPlayer 的
 * 私有成员函数、单测够不着；抽到顶层方便单元测试直接调用，行为逐字不变。
 * Format 里未知值是 NO_VALUE(-1)，不转 null 界面会显示“-1x-1”；
 * 现有元数据传进来时只覆盖转出来的字段，序号/是否选中/解码器原样保留
 * （copy）；颜色是例外——没 colorInfo 时会被写成 null，因为 copy
 * 里 color 字段是无条件覆盖的。
 */
internal fun Format.toVideoMetadata(video: VideoPlayer.Metadata.Video? = null): VideoPlayer.Metadata.Video {
    return (video ?: VideoPlayer.Metadata.Video()).copy(
        // Format 里未知值是 NO_VALUE(-1)，不转 null 界面会显示“-1x-1”
        width = width.takeIf { it > 0 },
        height = height.takeIf { it > 0 },
        color = colorInfo?.toLogString(),
        frameRate = frameRate.takeIf { it > 0 },
        // TODO 比特率目前是从 tag 中获取，有的返回空，后续需要实时计算
        // （无干净接口，990 轮记）；帧率 tag 缺失时已在位置轮询里用
        // 实测渲染帧数补，见 supplementFrameRate
        bitrate = bitrate.takeIf { it > 0 },
        mimeType = sampleMimeType,
    )
}

/**
 * tag 没给帧率时用实测渲染帧数补：1 秒采样窗口内渲染的帧数就是帧率
 * 的近似值（天光 2289 轮姊妹检查的反向回镜：天光那边的帧率就是这么
 * 补的，壳这边原来只从 tag 取、缺了就一直显示空）。
 * 壳这边帧率是可空（null 即未知），与天光的 0f 同义；卡顿一帧没渲
 * 时留着旧值，别把帧率清成未知。顶层函数方便单元测试直接调用。
 */
internal fun supplementFrameRate(currentFrameRate: Float?, framesInWindow: Int): Float? =
    if (currentFrameRate == null && framesInWindow > 0) framesInWindow.toFloat() else currentFrameRate

internal fun Format.toAudioMetadata(audio: VideoPlayer.Metadata.Audio? = null): VideoPlayer.Metadata.Audio {
    return (audio ?: VideoPlayer.Metadata.Audio()).copy(
        channels = channelCount.takeIf { it > 0 },
        channelsLabel = if (sampleMimeType == "audio/av3a") "菁彩声" else null,
        sampleRate = sampleRate.takeIf { it > 0 },
        bitrate = bitrate.takeIf { it > 0 },
        mimeType = sampleMimeType,
        language = language,
    )
}

internal fun Format.toSubtitleMetadata(subtitle: VideoPlayer.Metadata.Subtitle? = null): VideoPlayer.Metadata.Subtitle {
    return (subtitle ?: VideoPlayer.Metadata.Subtitle()).copy(
        bitrate = bitrate.takeIf { it > 0 },
        mimeType = sampleMimeType,
        language = language,
    )
}

@OptIn(UnstableApi::class)
class Media3VideoPlayer(
    private val context: Context,
    private val coroutineScope: CoroutineScope,
) : VideoPlayer(coroutineScope) {

    private val logger = Logger.create("Media3VideoPlayer")
    private var videoPlayer = getPlayer()

    private var softDecode: Boolean? = null
    private var surfaceView: SurfaceView? = null
    private var textureView: TextureView? = null

    private var currentChannelLine = ChannelLine()
    private val contentTypeAttempts = mutableMapOf<Int, Boolean>()
    private var updatePositionJob: Job? = null

    // 1 秒内渲染的帧数：setVideoFrameMetadataListener 每渲染一帧加一，
    // 位置轮询里每秒取一次、拿去补 tag 没给的帧率（见 supplementFrameRate）
    private val renderedFrameCount = AtomicInteger(0)

    // 无声重试计数：BEHIND_LIVE_WINDOW / DECODING_FAILED / IO_UNSPECIFIED
    // + 瞬时网络错 IO_BAD_HTTP_STATUS(2004) / IO_NETWORK_CONNECTION_FAILED
    // (2001) / IO_NETWORK_CONNECTION_TIMEOUT(2002)，见 isSilentRetryError。
    // 出错时原来无条件 seek + prepare；死线（解码一直失败、IO 一直
    // 报错）会无限循环，界面永远只转圈、不报错也不换线。连续 3 次都
    // 到不了 READY 就上报错误，走界面换线逻辑
    private var silentRetryCount = 0

    // 无声重试的退避任务：上游抖动是时间性的，立即重拉可能撞上同一
    // 次故障，按重试次数递增延迟（第 1 次 1s、第 2 次 2s）。换台或
    // 释放时取消，过期重试不干扰新频道
    private var silentRetryJob: Job? = null

    // 就绪时是不是直播流：直播流播完的判断用（时长未知即直播流；
    // 点播有总时长，不报）
    private var readyAsLive: Boolean = false

    // 60 秒窗口内直播流播完的时间戳：超限才报断流；换台时清零
    private var prematureEndTimestamps: List<Long> = emptyList()

    // 直播流播完的延迟重拉任务：退避后同一条线路再试一次。换台、停播
    // 或释放时取消，过期重拉不干扰新频道（无声重试同款 hazard）
    private var prematureEndRetryJob: Job? = null

    companion object {
        const val MAX_SILENT_RETRY = 3

        // 直播流播完在同一条线路上最多重拉几次：100 次（见
        // nextPrematureEndRetry）。凤凰每条连接只活 2–3 秒、加 1–3 秒
        // 退避，60 秒窗口里稳态就有约 20 次；20 次正好卡在稳态上，
        // 65 秒就烧完、照样报断流（v2.2.27 平板实测：64 秒还在播，
        // 72 秒已报）。100 次是稳态的 5 倍余量，正常抖动永远碰不到，
        // 只防 0.6 秒内连断这种真死源
        const val MAX_PREMATURE_END_RETRIES = 100

        // 播完重试的计数窗口：60 秒内的播完才累计，窗口外
        // 的旧记录不清会误判
        const val PREMATURE_END_RETRY_WINDOW_MS = 60_000L
    }

    // 字幕只接一个视图：一个播放器实例只对应一个 VideoPlayerScreen，
    // 注册写在 AndroidView 的 update 里、每次重组都会再跑一次；原来用
    // 列表只追加不去重，重组一次就多一个重复监听，字幕每次更新都要对
    // 同一个视图重复 setCues、列表越攒越长；改成单槽，后注册直接覆盖
    private var onCuesListener: ((ImmutableList<Cue>) -> Unit)? = null

    private fun triggerCues(cues: ImmutableList<Cue>) {
        onCuesListener?.invoke(cues)
    }

    fun onCues(listener: (ImmutableList<Cue>) -> Unit) {
        onCuesListener = listener
    }

    private fun getPlayer(): ExoPlayer {
        val renderersFactory =
            DefaultRenderersFactory(context)
                .setExtensionRendererMode(
                    if (Configs.videoPlayerForceSoftDecode || softDecode ?: false)
                        DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER
                    else DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON
                )
                .setEnableDecoderFallback(true)
                .setMediaCodecSelector(
                    if (Configs.videoPlayerForceSoftDecode || softDecode ?: false)
                        MediaCodecSelector.PREFER_SOFTWARE
                    else MediaCodecSelector.DEFAULT
                )

        val trackSelector = DefaultTrackSelector(context).apply {
            parameters = buildUponParameters()
                .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, false)
                .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, false)
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                .setMaxVideoSize(Integer.MAX_VALUE, Integer.MAX_VALUE)
                .setForceHighestSupportedBitrate(true)
                .setPreferredTextLanguage("zh")
                .build()
        }

        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                Configs.videoPlayerBufferTime.toInt(), // 最小缓冲时间（毫秒） minBufferMs
                max(Configs.videoPlayerBufferTime.toInt() * 5, 60 * 1000), // maxBufferMs
                Configs.videoPlayerBufferTime.toInt(), // bufferForPlaybackMs
                Configs.videoPlayerBufferTime.toInt(), // bufferForPlaybackAfterRebufferMs
            )
            .build()

        return ExoPlayer.Builder(context)
            .setRenderersFactory(renderersFactory)
            .setTrackSelector(trackSelector)
            .setLoadControl(loadControl)
            .build()
            .apply { playWhenReady = true }
    }

    private fun reInitPlayer() {
        // 只重建内部的 ExoPlayer，包装类和字幕视图都还在：监听不能清，
        // 清了软解兜底后字幕就断了、要等下次重组才偶然重新注册回来
        videoPlayer.removeListener(playerListener)
        videoPlayer.removeAnalyticsListener(metadataListener)
        videoPlayer.removeAnalyticsListener(eventLogger)
        videoPlayer.stop()
        videoPlayer.release()

        videoPlayer = getPlayer()

        videoPlayer.addListener(playerListener)
        videoPlayer.addAnalyticsListener(metadataListener)
        videoPlayer.addAnalyticsListener(eventLogger)
        videoPlayer.setVideoFrameMetadataListener { _, _, _, _ ->
            renderedFrameCount.incrementAndGet()
        }

        surfaceView?.let { setVideoSurfaceView(it) }
        textureView?.let { setVideoTextureView(it) }
        prepare()
    }

    private fun getDataSourceFactory(): DefaultDataSource.Factory {
        val headers = Configs.videoPlayerHeaders.toHeaders() + mapOf(
            "Referer" to (currentChannelLine.httpReferrer ?: "")
            ).filterValues { it.isNotEmpty() } + mapOf(
                "Origin" to (currentChannelLine.httpOrigin ?: "")
                ).filterValues { it.isNotEmpty() }
        
        // 使用应用内日志系统
        logger.i("播放地址: ${currentChannelLine.playableUrl}")
        logger.i("请求头: $headers")
        logger.i("User-Agent: ${currentChannelLine.httpUserAgent ?: Configs.videoPlayerUserAgent}")
        
        return DefaultDataSource.Factory(
            context,
            DefaultHttpDataSource.Factory().apply {
                setUserAgent(currentChannelLine.httpUserAgent ?: Configs.videoPlayerUserAgent)
                setDefaultRequestProperties(headers)
                setConnectTimeoutMs(Configs.videoPlayerLoadTimeout.toInt())
                setReadTimeoutMs(Configs.videoPlayerLoadTimeout.toInt())
                setKeepPostFor302Redirects(true)
                setAllowCrossProtocolRedirects(true)
            },
        )
    }


    private fun getMediaSource(contentType: Int? = null): MediaSource? {
        val uri = Uri.parse(currentChannelLine.playableUrl)
        val mediaItem = MediaItem.fromUri(uri)
        val dataSourceFactory = getDataSourceFactory()

        // 显式指定了内容类型（解析容器不支持时的重试）就别走这条按地址推断
        // 的捷径：否则每次重试都会回到同一个解析器，5 种类型白白轮一遍、
        // 每次建的都是同一个源，真正换解析器的目的落空
        if (contentType == null && !uri.toString().startsWith("rtmp://")){
            var mimeType = if (uri.toString().startsWith("rtp://") || uri.toString().startsWith("rtsp://")) {
                MimeTypes.APPLICATION_RTSP
            } else {
                null
            }
            if(mimeType == null){
                mimeType = when(Util.inferContentType(uri)){
                    C.CONTENT_TYPE_HLS -> MimeTypes.APPLICATION_M3U8
                    C.CONTENT_TYPE_SS -> MimeTypes.APPLICATION_SS
                    C.CONTENT_TYPE_RTSP -> MimeTypes.APPLICATION_RTSP
                    else -> null
                }
            }
            if (mimeType != null) {
                val mediaItem = MediaItem.Builder().setUri(uri)
                                        .setMimeType(mimeType)
                                        .build()
                val mediaSource = DefaultMediaSourceFactory(context)
                            .setDataSourceFactory(dataSourceFactory)
                            .createMediaSource(mediaItem)
                if (mediaSource != null) {
                    return mediaSource
                }
            }
        }
        
        var contentTypeForce = contentType

        if (contentTypeForce == null){
            if (uri.toString().startsWith("rtp://") || uri.toString().startsWith("rtsp://")) {
                contentTypeForce = C.CONTENT_TYPE_RTSP
            }

            if (currentChannelLine.manifestType == "mpd") {
                contentTypeForce = C.CONTENT_TYPE_DASH
            }

            if (uri.toString().startsWith("rtmp://")){
                contentTypeForce = C.CONTENT_TYPE_OTHER
            }
        }
        

        

        return when (contentTypeForce ?: Util.inferContentType(uri)) {
            C.CONTENT_TYPE_HLS -> {
                HlsMediaSource.Factory(dataSourceFactory)
                    .apply{
                        setAllowChunklessPreparation(true)
                    }
                    .createMediaSource(mediaItem)
            }

            C.CONTENT_TYPE_DASH -> {
                // 密钥出错（格式不对、或本机配不出 clearkey 会话）时只报错、
                // 不建源：建个没配 DRM 的源返回，外层 prepare 还会继续把
                // 它接上播；而报错是同步触发的、会立刻切下一条线，下一条
                // 线刚接上又被这个坏源盖回去，白白跳过一条能播的线。返
                // 回 null 让外层跳过这次准备，切线才落得实
                var drmError = false
                val factory = DashMediaSource.Factory(dataSourceFactory)
                factory.apply {
                        if (!(currentChannelLine.manifestType == "mpd" && currentChannelLine.licenseType == "clearkey" && currentChannelLine.licenseKey != null))
                            return@apply

                        // 密钥必须是 keyId:key、两段都是偶数位十六进制：格式不对直接
                        // 报密钥格式错误，别走到 DRM 那步再报“授权已过期”——过期和
                        // 写错是两回事，报过期会让人去重新取票，实际是源里的 key 写错了
                        val licenseKeyParts = currentChannelLine.licenseKey!!.split(":")
                        val isLicenseKeyValid = licenseKeyParts.size == 2 &&
                            licenseKeyParts.all { part ->
                                part.isNotEmpty() && part.length % 2 == 0 &&
                                    part.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }
                            }
                        if (!isLicenseKeyValid) {
                            triggerError(
                                PlaybackException(
                                    "MEDIA3_ERROR_DRM_CONTENT_ERROR",
                                    androidx.media3.common.PlaybackException.ERROR_CODE_DRM_CONTENT_ERROR
                                )
                            )
                            drmError = true
                            return@apply
                        }

                        runCatching {
                            val (drmKeyId, drmKey) = currentChannelLine.licenseKey!!.split(":")
                            val encodedDrmKey = Base64.encodeToString(
                                drmKey.chunked(2).map { it.toInt(16).toByte() }.toByteArray(),
                                Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP
                            )
                            val encodedDrmKeyId = Base64.encodeToString(
                                drmKeyId.chunked(2).map { it.toInt(16).toByte() }.toByteArray(),
                                Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP
                            )
                            val drmBody =
                                "{\"keys\":[{\"kty\":\"oct\",\"k\":\"${encodedDrmKey}\",\"kid\":\"${encodedDrmKeyId}\"}],\"type\":\"temporary\"}"

                            val drmCallback = LocalMediaDrmCallback(drmBody.toByteArray())
                            val drmSessionManager = DefaultDrmSessionManager.Builder()
                                .setMultiSession(true)
                                .setUuidAndExoMediaDrmProvider(
                                    C.CLEARKEY_UUID,
                                    FrameworkMediaDrm.DEFAULT_PROVIDER
                                )
                                .build(drmCallback)

                            setDrmSessionManagerProvider { drmSessionManager }
                        }
                            .onFailure {
                                triggerError(
                                    PlaybackException(
                                        "MEDIA3_ERROR_DRM_LICENSE_EXPIRED",
                                        androidx.media3.common.PlaybackException.ERROR_CODE_DRM_LICENSE_EXPIRED
                                    )
                                )
                                drmError = true
                            }
                }
                if (drmError) null else factory.createMediaSource(mediaItem)
            }

            C.CONTENT_TYPE_RTSP -> {
                RtspMediaSource.Factory().createMediaSource(mediaItem)
            }

            C.CONTENT_TYPE_OTHER -> {
                if (uri.toString().startsWith("rtmp://")) {
                    ProgressiveMediaSource.Factory(RtmpDataSource.Factory()).createMediaSource(mediaItem)
                }
                else{
                    ProgressiveMediaSource.Factory(dataSourceFactory).createMediaSource(mediaItem)
                }
            }

            C.CONTENT_TYPE_SS -> {
                SsMediaSource.Factory(dataSourceFactory).createMediaSource(mediaItem)
            }
            else -> {
                triggerError(PlaybackException.UNSUPPORTED_TYPE)
                null
            }
        }
    }

    private fun prepare(contentType: Int? = null) {
        // 重新备流（换台、换容器、软解重建都走这里）：挂起的直播流播完
        // 重拉任务取消，别让旧任务到点把刚备好的流又重拉一遍
        prematureEndRetryJob?.cancel()
        prematureEndRetryJob = null
        val uri = Uri.parse(currentChannelLine.playableUrl)
        val mediaSource = getMediaSource(contentType)
        if (mediaSource != null) {
            contentTypeAttempts[contentType ?: Util.inferContentType(uri)] = true
            videoPlayer.setMediaSource(mediaSource)
            videoPlayer.prepare()
            videoPlayer.play()
            triggerPrepared()
        }
        updatePositionJob?.cancel()
        updatePositionJob = null
    }

    private val playerListener = object : Player.Listener {
        override fun onVideoSizeChanged(videoSize: VideoSize) {
            triggerResolution(videoSize.width, videoSize.height)
        }

        override fun onPlayerError(ex: androidx.media3.common.PlaybackException) {
            // 有错就走报错那条路：挂起的直播流播完重拉任务取消，别让它
            // 到点又把流拉起来、和报错换线撞车（天光同款：延迟重试
            // 任务在各报错分支里先取消）
            prematureEndRetryJob?.cancel()
            prematureEndRetryJob = null
            when {
                // 无声重试的错误（见 isSilentRetryError）：如果是直播加载
                // 位置错误或瞬时网络错，尝试重新播放；连续多次都到不了
                // READY 说明这条线已死，不再无声重试，上报错误让界面换线
                isSilentRetryError(ex.errorCode) -> {
                    // 先判后加：silentRetryCount 是“已重试次数”，没到
                    // 上限（3 次）才再试一次；到上限才上报原始错误走换线
                    if (shouldSilentRetry(ex.errorCode, silentRetryCount)) {
                        silentRetryCount++
                        // 退避后同一条线路再试一次：上游抖动是时间性的，
                        // 立即重拉可能撞上同一次故障（票据刷新、CDN 瞬断）；
                        // 按重试次数递增延迟（第 1 次 1s、第 2 次 2s、第 3 次 3s）
                        silentRetryJob?.cancel()
                        silentRetryJob = coroutineScope.launch {
                            delay(silentRetryDelayMs(silentRetryCount))
                            videoPlayer.seekToDefaultPosition()
                            videoPlayer.prepare()
                            // 重试后也要重启加载超时 watchdog：triggerPrepared() 只在
                            // prepare() 里调，重试直接调 ExoPlayer 的 prepare() 会绕过
                            // 它，所以下面显式再调一次。但重启发生在退避等完之后——等
                            // 待期间旧 watchdog 还在跑，若它剩下的时间比本次退避短，就
                            // 先报一个假 LOAD_TIMEOUT（超时 1 秒时第 1 次退避几乎必撞；
                            // 默认 5 秒时报错晚于 4 秒才撞上；877 轮 P2-2 是旧机理）。
                            triggerPrepared()
                        }
                    } else {
                        silentRetryCount = 0
                        triggerError(
                            PlaybackException(
                                ex.errorCodeName.replace("ERROR_CODE", "MEDIA3_ERROR"),
                                ex.errorCode
                            )
                        )
                    }
                }

                // 当解析容器不支持时，尝试使用其他解析容器
                ex.errorCode == androidx.media3.common.PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED -> {
                    videoPlayer.currentMediaItem?.localConfiguration?.uri?.let {
                        nextUntriedContentType(contentTypeAttempts)?.let { nextType ->
                            prepare(nextType)
                        } ?: triggerError(PlaybackException.UNSUPPORTED_TYPE)
                    }
                }

                ex.errorCode == androidx.media3.common.PlaybackException.ERROR_CODE_DECODER_INIT_FAILED -> {
                    if (softDecode == true) {
                        triggerError(
                            PlaybackException(
                                ex.errorCodeName.replace("ERROR_CODE", "MEDIA3_ERROR"),
                                ex.errorCode
                            )
                        )
                    } else {
                        softDecode = true
                        reInitPlayer()
                    }
                }

                else -> {
                    triggerError(
                        PlaybackException(
                            ex.errorCodeName.replace("ERROR_CODE", "MEDIA3_ERROR"),
                            ex.errorCode
                        )
                    )
                }
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_BUFFERING) {
                triggerError(null)
                triggerBuffering(true)
            } else if (playbackState == Player.STATE_READY) {
                // 播起来了：重试计数清零；挂起的延迟重试任务也取消——
                // 比如解码失败走软解重建（reInitPlayer）先一步把新播放器
                // 拉起来了，旧的延迟任务再触发会把刚恢复的流从头再播一遍
                silentRetryCount = 0
                silentRetryJob?.cancel()
                silentRetryJob = null
                // 记是不是直播流：直播流播完的判断用
                // （时长未知即直播流；点播有总时长，不报）。播起来了，
                // 挂起的直播流播完重拉任务也不需要了
                readyAsLive = videoPlayer.duration == C.TIME_UNSET
                prematureEndRetryJob?.cancel()
                prematureEndRetryJob = null
                triggerReady()
                triggerDuration(videoPlayer.duration)

                updatePositionJob?.cancel()
                updatePositionJob = coroutineScope.launch {
                    var lastFrameRateSampleAtMs = System.currentTimeMillis()
                    while (true) {
                        val livePosition =
                            System.currentTimeMillis() - videoPlayer.currentLiveOffset

                        triggerCurrentPosition(if (livePosition > 0) livePosition else videoPlayer.currentPosition)
                        // tag 没给帧率时用实测渲染帧数补：每 1 秒取一次
                        // 计数（500ms 轮询里约每两拍一次）；卡顿一帧没渲
                        // 时留着旧值。窗口是近似值，调试信息够用
                        val nowMs = System.currentTimeMillis()
                        if (nowMs - lastFrameRateSampleAtMs >= 1000) {
                            lastFrameRateSampleAtMs = nowMs
                            val frames = renderedFrameCount.getAndSet(0)
                            val supplemented =
                                supplementFrameRate(metadata.video?.frameRate, frames)
                            if (supplemented != metadata.video?.frameRate) {
                                metadata = metadata.copy(
                                    video = (metadata.video ?: Metadata.Video())
                                        .copy(frameRate = supplemented)
                                )
                                triggerMetadata(metadata)
                            }
                        }
                        delay(500)
                    }
                }
            }

            if (playbackState != Player.STATE_BUFFERING) {
                triggerBuffering(false)
            }

            if (playbackState == Player.STATE_ENDED) {
                if (shouldReplayOnEnded(videoPlayer.duration)) {
                    videoPlayer.seekToDefaultPosition()
                    videoPlayer.prepare()
                    // 重备后也要重启加载超时 watchdog：triggerPrepared()
                    // 只在 prepare() 里调，重备直接调 ExoPlayer 的
                    // prepare() 会绕过它，所以下面显式再调一次
                    // （无声重试／过早播完重拉同款写法）。不重启的话，
                    // 重备后卡在缓冲就永远等不到超时、一直转圈。
                    triggerPrepared()
                } else if (readyAsLive) {
                    // 直播流播完：源站断了流（如凤凰 FLV 每条连接几秒就关），
                    // 不是正常播完——直播流没有“播完”这一说，播多久断了都
                    // 是源死了。先在同一条线路上重拉（每次都是 SCF 现取新票据
                    // 的新连接，带 1/1/2/2/3 秒短退避），扛住偶发断流；60 秒
                    // 窗口内累计 100 次还稳不住，才按断流报、走换线/报错流程。
                    // v2.2.29 之前只认就绪 10 秒内播完，播稳超 10 秒的落回原有
                    // 断流计时那条慢路，v2.2.28 平板实测 72 秒照样报断流——上限
                    // 提到 100 次也没用，瓶颈在那条慢路；现在不分播了多久，
                    // 直播流播完一律走这条快路。点播有总时长（readyAsLive
                    // 为 false），正常播完根本不进这支。
                    val nowMs = SystemClock.elapsedRealtime()
                    val (streak, giveUp) = nextPrematureEndRetry(
                        prematureEndTimestamps,
                        nowMs,
                        PREMATURE_END_RETRY_WINDOW_MS,
                        MAX_PREMATURE_END_RETRIES,
                    )
                    prematureEndTimestamps = streak
                    if (!giveUp) {
                        // 壳没有天光的 triggerReconnecting（“正在重连…”
                        // 指示）：重拉等待期间用已有的 triggerBuffering(true)
                        // 顶一下，界面会显示缓冲态、不至于冻在最后一帧
                        // 无反馈；重拉时清掉，之后 BUFFERING/READY 会按
                        // 正常流程再置。
                        triggerBuffering(true)
                        prematureEndRetryJob?.cancel()
                        prematureEndRetryJob = coroutineScope.launch {
                            delay(prematureEndRetryDelayMs(streak.size))
                            triggerBuffering(false)
                            videoPlayer.seekToDefaultPosition()
                            videoPlayer.prepare()
                            // 重试后也要重启加载超时 watchdog：triggerPrepared()
                            // 只在 prepare() 里调，重试直接调 ExoPlayer 的
                            // prepare() 会绕过它，所以下面显式再调一次
                            // （无声重试同款写法）。就绪后 watchdog 已取消，
                            // 等待期间没有旧 watchdog 在跑，不会先报假超时。
                            // 上次位置变化挂起的断流计时到点还会再补一次
                            // onInterrupt，上层靠换台守卫挡住了（同台同线
                            // 同节目直接 return），重拉成功后 triggerPrepared()
                            // 也会把它取消——不用给基类加取消方法。
                            triggerPrepared()
                        }
                    } else {
                        prematureEndTimestamps = emptyList()
                        triggerError(PlaybackException.LIVE_STREAM_ENDED)
                        updatePositionJob?.cancel()
                        updatePositionJob = null
                    }
                } else {
                    // 直播流播完：源站关了连接，重备同一条死线会无限循环；
                    // 报断流，上层把这条线从可播列表里拿掉、换下一条线
                    // （天光 v2.2.25 同款：过早播完报 CUTOFF 走换线，2719 轮）
                    triggerError(PlaybackException.LIVE_STREAM_ENDED)
                    // 播完后位置再也不会动：把 500ms 位置轮询也停掉，
                    // 否则它在死播放器上永远空转（每 500ms 推一次冻住
                    // 的位置），上次位置变化挂起的断流计时到点还会再补
                    // 一次 onInterrupt——上层靠换台守卫挡住了（同台同线
                    // 同节目直接 return），但空转本身不该留着。
                    // 点播重备那档不用停：prepare() 里已经停了，READY
                    // 后会重拉。
                    updatePositionJob?.cancel()
                    updatePositionJob = null
                }
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            triggerIsPlayingChanged(isPlaying)
        }

        override fun onTracksChanged(tracks: Tracks) {
            metadata = metadata.copy(
                videoTracks = emptyList(),
                audioTracks = emptyList(),
                subtitleTracks = emptyList()
            )
            triggerMetadata(metadata)

            val videoFormats = videoPlayer.currentTracks.groups
                .filter { it.mediaTrackGroup.type == C.TRACK_TYPE_VIDEO }
                .flatMap { group ->
                    List(group.mediaTrackGroup.length) { trackIndex ->
                        group.mediaTrackGroup
                            .getFormat(trackIndex)
                            .toVideoMetadata()
                            .copy(isSelected = group.isTrackSelected(trackIndex))
                    }
                }
                .mapIndexed { index, metadata ->
                    metadata.copy(index = index)
                }

            metadata = metadata.copy(videoTracks = videoFormats)

            val audioFormats = videoPlayer.currentTracks.groups
                .filter { it.mediaTrackGroup.type == C.TRACK_TYPE_AUDIO }
                .flatMap { group ->
                    List(group.mediaTrackGroup.length) { trackIndex ->
                        group.mediaTrackGroup
                            .getFormat(trackIndex)
                            .toAudioMetadata()
                            .copy(isSelected = group.isTrackSelected(trackIndex))
                    }
                }
                .mapIndexed { index, metadata ->
                    metadata.copy(index = index)
                }

            metadata = metadata.copy(audioTracks = audioFormats)

            val subtitleFormats = videoPlayer.currentTracks.groups
                .filter { it.mediaTrackGroup.type == C.TRACK_TYPE_TEXT }
                .flatMap { group ->
                    List(group.mediaTrackGroup.length) { trackIndex ->
                        group.mediaTrackGroup
                            .getFormat(trackIndex)
                            ?.toSubtitleMetadata()
                            ?.copy(isSelected = group.isTrackSelected(trackIndex))
                    }
                }
                .mapNotNull { it }
                .mapIndexed { index, metadata ->
                    metadata.copy(index = index)
                }

            metadata = metadata.copy(
                subtitleTracks = subtitleFormats,
                subtitle = subtitleFormats.firstOrNull { it.isSelected == true },
            )
            

            triggerMetadata(metadata)
        }

        override fun onCues(cueGroup: CueGroup) {
            triggerCues(cueGroup.cues)
        }
    }

    /**
     * 按展平后的轨道下标找轨道组。下标越界（轨道列表还是旧频道
     * 的、切台后新流轨道变少，或重建播放器时当前轨道为空）时
     * 返回 null，调用方直接忽略这次选择：原先用 first()，
     * 越界直接抛 NoSuchElementException 闪退。
     */
    private fun Int.fromIndexFindTrack(type: @C.TrackType Int): Pair<TrackGroup, Int>? {
        val groups = videoPlayer.currentTracks.groups
            .filter { group -> group.mediaTrackGroup.type == type }
            .map { it.mediaTrackGroup }

        var trackCount = 0
        val group = groups.firstOrNull { group ->
            trackCount += group.length
            this < trackCount
        } ?: return null

        val trackIndex = this - (trackCount - group.length)

        return Pair(group, trackIndex)
    }

    private val metadataListener = object : AnalyticsListener {
        override fun onVideoInputFormatChanged(
            eventTime: AnalyticsListener.EventTime,
            format: Format,
            decoderReuseEvaluation: DecoderReuseEvaluation?,
        ) {
            metadata = metadata.copy(video = format.toVideoMetadata(metadata.video))
            triggerMetadata(metadata)
        }

        override fun onVideoDecoderInitialized(
            eventTime: AnalyticsListener.EventTime,
            decoderName: String,
            initializedTimestampMs: Long,
            initializationDurationMs: Long,
        ) {
            metadata = metadata.copy(
                video = (metadata.video ?: Metadata.Video()).copy(decoder = decoderName)
            )

            triggerMetadata(metadata)
        }

        override fun onAudioInputFormatChanged(
            eventTime: AnalyticsListener.EventTime,
            format: Format,
            decoderReuseEvaluation: DecoderReuseEvaluation?,
        ) {
            metadata = metadata.copy(audio = format.toAudioMetadata(metadata.audio))
            triggerMetadata(metadata)
        }

        override fun onAudioDecoderInitialized(
            eventTime: AnalyticsListener.EventTime,
            decoderName: String,
            initializedTimestampMs: Long,
            initializationDurationMs: Long,
        ) {
            metadata = metadata.copy(
                audio = (metadata.audio ?: Metadata.Audio()).copy(decoder = decoderName)
            )

            triggerMetadata(metadata)
        }
    }

    private val eventLogger = EventLogger()

    override fun initialize() {
        super.initialize()
        videoPlayer.addListener(playerListener)
        videoPlayer.addAnalyticsListener(metadataListener)
        videoPlayer.addAnalyticsListener(eventLogger)
        // 不用清监听器：setVideoFrameMetadataListener 只接受非空，
        // 且 release() 里 videoPlayer.release() 后它再也不会回调；
        // 软解重建（reInitPlayer）换了新播放器，那边也要再注册一次
        videoPlayer.setVideoFrameMetadataListener { _, _, _, _ ->
            renderedFrameCount.incrementAndGet()
        }
    }

    override fun release() {
        onCuesListener = null
        // 位置轮询协程只在 stop() 里取消：release() 后它仍会每 500ms
        // 访问已释放的播放器，先停掉
        updatePositionJob?.cancel()
        updatePositionJob = null
        silentRetryJob?.cancel()
        silentRetryJob = null
        prematureEndRetryJob?.cancel()
        prematureEndRetryJob = null
        videoPlayer.removeListener(playerListener)
        videoPlayer.removeAnalyticsListener(metadataListener)
        videoPlayer.removeAnalyticsListener(eventLogger)
        videoPlayer.stop()
        videoPlayer.release()
        super.release()
    }

    override fun prepare(line: ChannelLine) {
        if (Configs.videoPlayerStopPreviousMediaItem)
            videoPlayer.stop()

        contentTypeAttempts.clear()
        silentRetryCount = 0
        silentRetryJob?.cancel()
        silentRetryJob = null
        prematureEndTimestamps = emptyList()
        prematureEndRetryJob?.cancel()
        prematureEndRetryJob = null
        currentChannelLine = line
        prepare(null)
    }

    override fun play() {
        videoPlayer.play()
    }

    override fun pause() {
        // 暂停时位置不再前进：先把挂起的断流计时停掉，否则冻住的
        // 位置满一个加载超时就会误报中断、重备同一条流（天光同款）
        cancelInterruptTimeout()
        videoPlayer.pause()
    }

    override fun seekTo(position: Long) {
        videoPlayer.seekTo(position)
    }

    override fun seekBy(deltaMs: Long) {
        videoPlayer.seekTo(videoPlayer.currentPosition + deltaMs)
    }

    override fun setVolume(volume: Float) {
        videoPlayer.volume = volume
    }

    override fun getVolume(): Float {
        return videoPlayer.volume
    }

    override fun stop() {
        videoPlayer.stop()
        updatePositionJob?.cancel()
        // 停掉的播放器别再被挂起的延迟重试拉起来：比如瞬时网络错后
        // 1~2 秒内切到 WebView 台（只调 stop、不释放），旧任务到点
        // 会把已停的流 seek + prepare，在 WebView 后面悄悄复播
        silentRetryJob?.cancel()
        silentRetryJob = null
        prematureEndRetryJob?.cancel()
        prematureEndRetryJob = null
        super.stop()
    }

    override fun selectVideoTrack(track: Metadata.Video?) {
        if (track?.index == null) {
            videoPlayer.trackSelectionParameters = videoPlayer.trackSelectionParameters
                .buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, true)
                .build()

            return
        }

        val (group, trackIndex) = track.index.fromIndexFindTrack(C.TRACK_TYPE_VIDEO) ?: return

        videoPlayer.trackSelectionParameters = videoPlayer.trackSelectionParameters
            .buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, false)
            .setOverrideForType(TrackSelectionOverride(group, trackIndex))
            .build()
    }

    override fun selectAudioTrack(track: Metadata.Audio?) {
        if (track?.index == null) {
            videoPlayer.trackSelectionParameters = videoPlayer.trackSelectionParameters
                .buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, true)
                .build()

            return
        }

        val (group, trackIndex) = track.index.fromIndexFindTrack(C.TRACK_TYPE_AUDIO) ?: return

        videoPlayer.trackSelectionParameters = videoPlayer.trackSelectionParameters
            .buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, false)
            .setOverrideForType(TrackSelectionOverride(group, trackIndex))
            .build()
    }
    
    override fun selectSubtitleTrack(track: Metadata.Subtitle?) {
        // 和视频/音频轨一样按展平后的轨道下标选：原来按 language 选，
        // 容器没标语言的字幕轨 language 是 null，点它等于关字幕、永远选不上；
        // 两条同语言的字幕轨也分不清点的到底是哪条。index 为空（点“关闭”）
        // 才是关字幕。
        if (track?.index == null) {
            videoPlayer.trackSelectionParameters = videoPlayer.trackSelectionParameters
                .buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                .build()
            return
        }

        val (group, trackIndex) = track.index.fromIndexFindTrack(C.TRACK_TYPE_TEXT) ?: return

        videoPlayer.trackSelectionParameters = videoPlayer.trackSelectionParameters
            .buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
            .setOverrideForType(TrackSelectionOverride(group, trackIndex))
            .build()
    }

    override fun setVideoSurfaceView(surfaceView: SurfaceView) {
        this.surfaceView = surfaceView
        videoPlayer.setVideoSurfaceView(surfaceView)
    }

    override fun setVideoTextureView(textureView: TextureView) {
        this.textureView = textureView
        videoPlayer.setVideoTextureView(textureView)
    }
}
