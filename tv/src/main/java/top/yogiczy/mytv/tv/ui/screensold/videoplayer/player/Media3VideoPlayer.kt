package top.yogiczy.mytv.tv.ui.screensold.videoplayer.player

import android.content.Context
import android.net.Uri
import android.util.Base64
import android.view.SurfaceView
import android.view.TextureView
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

    // 无声重试计数：BEHIND_LIVE_WINDOW / DECODING_FAILED / IO_UNSPECIFIED
    // 出错时原来无条件 seek + prepare；死线（解码一直失败、IO 一直
    // 报错）会无限循环，界面永远只转圈、不报错也不换线。连续 3 次都
    // 到不了 READY 就上报错误，走界面换线逻辑
    private var silentRetryCount = 0

    private companion object {
        const val MAX_SILENT_RETRY = 3
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

        // MediaCodecVideoRenderer.skipMultipleFramesOnSameVsync =
        //     Configs.videoPlayerSkipMultipleFramesOnSameVSync
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

        if (!uri.toString().startsWith("rtmp://")){
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
            when (ex.errorCode) {
                // 如果是直播加载位置错误，尝试重新播放；连续多次都到不了
                // READY 说明这条线已死，不再无声重试，上报错误让界面换线
                androidx.media3.common.PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW,
                androidx.media3.common.PlaybackException.ERROR_CODE_DECODING_FAILED,
                androidx.media3.common.PlaybackException.ERROR_CODE_IO_UNSPECIFIED -> {
                    silentRetryCount++
                    if (silentRetryCount >= MAX_SILENT_RETRY) {
                        silentRetryCount = 0
                        triggerError(
                            PlaybackException(
                                ex.errorCodeName.replace("ERROR_CODE", "MEDIA3_ERROR"),
                                ex.errorCode
                            )
                        )
                    } else {
                        videoPlayer.seekToDefaultPosition()
                        videoPlayer.prepare()
                    }
                }

                // 当解析容器不支持时，尝试使用其他解析容器
                androidx.media3.common.PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED -> {
                    videoPlayer.currentMediaItem?.localConfiguration?.uri?.let {
                        if (contentTypeAttempts[C.CONTENT_TYPE_HLS] != true) {
                            prepare(C.CONTENT_TYPE_HLS)
                        } else if (contentTypeAttempts[C.CONTENT_TYPE_DASH] != true) {
                            prepare(C.CONTENT_TYPE_DASH)
                        } else if (contentTypeAttempts[C.CONTENT_TYPE_RTSP] != true) {
                            prepare(C.CONTENT_TYPE_RTSP)
                        } else if (contentTypeAttempts[C.CONTENT_TYPE_OTHER] != true) {
                            prepare(C.CONTENT_TYPE_OTHER)
                        } else if(contentTypeAttempts[C.CONTENT_TYPE_SS] != true){
                            prepare(C.CONTENT_TYPE_SS)
                        }else {
                            triggerError(PlaybackException.UNSUPPORTED_TYPE)
                        }
                    }
                }

                androidx.media3.common.PlaybackException.ERROR_CODE_DECODER_INIT_FAILED -> {
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
                silentRetryCount = 0
                triggerReady()
                triggerDuration(videoPlayer.duration)

                updatePositionJob?.cancel()
                updatePositionJob = coroutineScope.launch {
                    while (true) {
                        val livePosition =
                            System.currentTimeMillis() - videoPlayer.currentLiveOffset

                        triggerCurrentPosition(if (livePosition > 0) livePosition else videoPlayer.currentPosition)
                        delay(500)
                    }
                }
            }

            if (playbackState != Player.STATE_BUFFERING) {
                triggerBuffering(false)
            }

            if (playbackState == Player.STATE_ENDED) {
                videoPlayer.seekToDefaultPosition()
                videoPlayer.prepare()
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

    private fun Format.toVideoMetadata(video: Metadata.Video? = null): Metadata.Video {
        return (video ?: Metadata.Video()).copy(
            // Format 里未知值是 NO_VALUE(-1)，不转 null 界面会显示“-1x-1”
            width = width.takeIf { it > 0 },
            height = height.takeIf { it > 0 },
            color = colorInfo?.toLogString(),
            frameRate = frameRate.takeIf { it > 0 },
            // TODO 帧率、比特率目前是从tag中获取，有的返回空，后续需要实时计算
            bitrate = bitrate.takeIf { it > 0 },
            mimeType = sampleMimeType,
        )
    }

    private fun Format.toAudioMetadata(audio: Metadata.Audio? = null): Metadata.Audio {
        return (audio ?: Metadata.Audio()).copy(
            channels = channelCount.takeIf { it > 0 },
            channelsLabel = if (sampleMimeType == "audio/av3a") "菁彩声" else null,
            sampleRate = sampleRate.takeIf { it > 0 },
            bitrate = bitrate.takeIf { it > 0 },
            mimeType = sampleMimeType,
            language = language,
        )
    }

    private fun Format.toSubtitleMetadata(subtitle: Metadata.Subtitle? = null): Metadata.Subtitle {
        return (subtitle ?: Metadata.Subtitle()).copy(
            bitrate = bitrate.takeIf { it > 0 },
            mimeType = sampleMimeType,
            language = language,
        )
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
    }

    override fun release() {
        onCuesListener = null
        // 位置轮询协程只在 stop() 里取消：release() 后它仍会每 500ms
        // 访问已释放的播放器，先停掉
        updatePositionJob?.cancel()
        updatePositionJob = null
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
        currentChannelLine = line
        prepare(null)
    }

    override fun play() {
        videoPlayer.play()
    }

    override fun pause() {
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
