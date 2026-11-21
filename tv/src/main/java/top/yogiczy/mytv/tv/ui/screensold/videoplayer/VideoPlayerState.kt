package top.yogiczy.mytv.tv.ui.screensold.videoplayer

import android.view.SurfaceView
import android.view.TextureView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.flowOf
import top.yogiczy.mytv.core.data.entities.channel.ChannelLine
import top.yogiczy.mytv.core.data.utils.Globals
import top.yogiczy.mytv.tv.ui.screen.settings.settingsVM
import top.yogiczy.mytv.tv.ui.screensold.videoplayer.player.IjkVideoPlayer
import top.yogiczy.mytv.tv.ui.screensold.videoplayer.player.Media3VideoPlayer
import top.yogiczy.mytv.tv.ui.screensold.videoplayer.player.VideoPlayer
import top.yogiczy.mytv.tv.ui.utils.Configs
import top.yogiczy.mytv.tv.voice.AiSubtitleController
import top.yogiczy.mytv.tv.voice.AiSubtitleStatus
import top.yogiczy.mytv.tv.voice.AiTranslateProvider
import top.yogiczy.mytv.tv.voice.AsrModels
import top.yogiczy.mytv.tv.voice.AzureTranslator
import top.yogiczy.mytv.tv.voice.CloudflareTranslator
import java.io.File

@Stable
class VideoPlayerState(
    val instance: VideoPlayer,
    // AI 字幕控制器：生产环境由 rememberVideoPlayerState 传入；单元测试
    // 不传（null = 不启动 AI 字幕，字幕行/状态流给空值），旧的测试调
    // 用 VideoPlayerState(player) 不用改。
    private val aiSubtitleController: AiSubtitleController? = null,
    private var defaultDisplayModeProvider: () -> VideoPlayerDisplayMode = { VideoPlayerDisplayMode.ORIGINAL },
) {
    /** 显示模式 */
    var displayMode by mutableStateOf(defaultDisplayModeProvider())

    /** 视频宽高比 */
    var aspectRatio by mutableFloatStateOf(16f / 9f)

    /** 错误 */
    var error by mutableStateOf<String?>(null)

    /** 正在缓冲 */
    var isBuffering by mutableStateOf(false)

    /** 正在播放 */
    var isPlaying by mutableStateOf(false)

    /** 总时长 */
    var duration by mutableLongStateOf(0L)

    /** 当前播放位置 */
    var currentPosition by mutableLongStateOf(0L)

    /** 当前音量 */
    private var _volume by mutableFloatStateOf(1f)
    var volume: Float
        get() = _volume
        set(value) {
            _volume = value
            instance.setVolume(value)
        }

    /** 元数据 */
    var metadata by mutableStateOf(VideoPlayer.Metadata())

    /** AI 实时字幕当前行（空 = 不显示；没传控制器时恒为空） */
    val aiSubtitleTextFlow = aiSubtitleController?.subtitleText ?: flowOf("")

    /** AI 实时字幕状态（模型没下、引擎起不来等；没传控制器时恒为 IDLE） */
    val aiSubtitleStatusFlow = aiSubtitleController?.status ?: flowOf(AiSubtitleStatus.IDLE)

    /**
     * 按当前设置同步 AI 字幕：开关、模型、翻译服务商/Key、播放器内核。
     * 只在 Media3 内核下启动（IJK 没有音频回调）；模型没下好就不启动，
     * 状态会是 NO_MODEL，设置页会提示去下载。换台、切设置后都要调一次。
     */
    fun syncAiSubtitle() {
        val controller = aiSubtitleController ?: run {
            instance.setAudioTapEnabled(false)
            return
        }
        val enabled = Configs.aiSubtitleEnabled
        // 播放器实例本身就能说明内核（Media3VideoPlayer = Media3 内核）；
        // 这里不能用 settingsVM（它是 @Composable get，只能在组合里调）。
        val isMedia3 = instance is Media3VideoPlayer
        instance.setAudioTapEnabled(enabled && isMedia3)
        if (!enabled || !isMedia3) {
            controller.stop()
            return
        }
        val model = AsrModels.byId(Configs.aiSubtitleModelId) ?: AsrModels.defaultModel()
        controller.setTranslateMode(Configs.aiSubtitleTranslateMode)
        controller.setTranslator(
            when (Configs.aiSubtitleTranslateProvider) {
                AiTranslateProvider.AZURE -> AzureTranslator(
                    apiKey = Configs.aiSubtitleAzureApiKey,
                    region = Configs.aiSubtitleAzureRegion,
                )
                AiTranslateProvider.CLOUDFLARE -> CloudflareTranslator(
                    accountId = Configs.aiSubtitleCfAccountId,
                    apiToken = Configs.aiSubtitleCfApiToken,
                )
            },
        )
        controller.start(model)
    }

    fun prepare(line: ChannelLine) {
        error = null
        metadata = VideoPlayer.Metadata()
        // 画面比例只在新流准备时回到设置里的默认值：之前放在 onReady
        // 里，Media3 每次重缓冲恢复（BUFFERING → READY）都会再触发
        // onReady，用户中途调好的比例会被悄悄打回默认值；挪到这里后，
        // 换台/换线路/重载才回到默认值，重缓冲不再动它
        displayMode = defaultDisplayModeProvider()
        // 换台：AI 字幕清空当前行、reset 识别流，别把上一台的尾巴带过来
        aiSubtitleController?.resetForChannelChange()
        syncAiSubtitle()
        instance.prepare(line)
    }

    fun play() {
        instance.play()
    }

    fun pause() {
        instance.pause()
    }

    fun seekTo(position: Long) {
        instance.seekTo(position)
    }

    fun seekBy(deltaMs: Long) {
        instance.seekBy(deltaMs)
    }

    fun stop() {
        instance.stop()
    }

    fun selectVideoTrack(track: VideoPlayer.Metadata.Video?) {
        instance.selectVideoTrack(track)
    }

    fun selectAudioTrack(track: VideoPlayer.Metadata.Audio?) {
        instance.selectAudioTrack(track)
    }

    fun selectSubtitleTrack(track: VideoPlayer.Metadata.Subtitle?) {
        instance.selectSubtitleTrack(track)
    }

    fun setVideoSurfaceView(surfaceView: SurfaceView) {
        instance.setVideoSurfaceView(surfaceView)
    }

    fun setVideoTextureView(textureView: TextureView) {
        instance.setVideoTextureView(textureView)
    }

    private val onReadyListeners = mutableListOf<() -> Unit>()
    private val onErrorListeners = mutableListOf<() -> Unit>()
    private val onInterruptListeners = mutableListOf<() -> Unit>()
    private val onIsBufferingListeners = mutableListOf<(Boolean) -> Unit>()

    fun onReady(listener: () -> Unit) {
        onReadyListeners.add(listener)
    }

    fun onError(listener: () -> Unit) {
        onErrorListeners.add(listener)
    }

    fun onInterrupt(listener: () -> Unit) {
        onInterruptListeners.add(listener)
    }

    fun onIsBuffering(listener: (Boolean) -> Unit) {
        onIsBufferingListeners.add(listener)
    }

    init {
        // 监听桥创建时就注册一份：首播的准备发生在主界面状态的 init 里
        //（组合还没结束），那时 DisposableEffect 里的 initialize() 还没
        // 跑；桥只在 initialize() 里注册的话，准备时同步报的错（密钥格
        // 式不对、类型不支持）没人接——不显示、也不自动换台，直接黑屏
        // 卡住。initialize() 会先清空再重注册，不会重复
        registerInstanceListeners()
    }

    fun initialize() {
        instance.initialize()
        registerInstanceListeners()
    }

    private fun registerInstanceListeners() {
        instance.onResolution { width, height ->
            if (width > 0 && height > 0) aspectRatio = width.toFloat() / height
        }
        instance.onError { ex ->
            error = ex?.let { "${it.errorCodeName}(${it.errorCode})" }
                ?.apply { onErrorListeners.forEach { it.invoke() } }

        }
        instance.onReady {
            onReadyListeners.forEach { it.invoke() }
            error = null
        }
        instance.onBuffering {
            isBuffering = it
            if (it) error = null
            onIsBufferingListeners.forEach { cb -> cb(isBuffering) }
        }
        instance.onPrepared { }
        instance.onIsPlayingChanged { isPlaying = it }
        instance.onDurationChanged { duration = it }
        instance.onCurrentPositionChanged { currentPosition = it }
        instance.onMetadata { metadata = it }
        instance.onInterrupt { onInterruptListeners.forEach { it.invoke() } }
        // AI 字幕：Media3 解出来的 PCM 喂给识别引擎（IJK 没有这路回调）
        aiSubtitleController?.let { controller ->
            instance.onAudioPcm { samples, sampleRate, channels ->
                controller.acceptPcm16(samples, sampleRate, channels)
            }
            instance.onAudioPcmFloat { samples, sampleRate, channels ->
                controller.acceptFloat(samples, sampleRate, channels)
            }
        }
    }

    fun release() {
        onReadyListeners.clear()
        onErrorListeners.clear()
        onInterruptListeners.clear()
        onIsBufferingListeners.clear()
        aiSubtitleController?.stop()
        instance.setAudioTapEnabled(false)
        instance.release()
    }
}

@Composable
fun rememberVideoPlayerState(
    defaultDisplayModeProvider: () -> VideoPlayerDisplayMode = { VideoPlayerDisplayMode.ORIGINAL },
): VideoPlayerState {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()

    val videoPlayerCore = settingsVM.videoPlayerCore
    val forceSoftDecode = settingsVM.videoPlayerForceSoftDecode 
    val state = remember(videoPlayerCore, forceSoftDecode) {
        val player = when (videoPlayerCore) {
            Configs.VideoPlayerCore.MEDIA3 -> Media3VideoPlayer(context, coroutineScope)
            Configs.VideoPlayerCore.IJK -> IjkVideoPlayer(context, coroutineScope)
        }

        // AI 实时字幕控制器：和播放器同生命周期（remember 的 key 和
        // 播放器一致，切内核时一起重建）；模型目录和下载器同一个
        val aiSubtitleController = AiSubtitleController(
            modelsDir = File(Globals.cacheDir, "asr-models"),
            scope = coroutineScope,
        )
        VideoPlayerState(
            instance = player,
            aiSubtitleController = aiSubtitleController,
            defaultDisplayModeProvider = defaultDisplayModeProvider,
        )
    }

    DisposableEffect(videoPlayerCore, forceSoftDecode) {
        state.initialize()
        onDispose { state.release() }
    }

    DisposableEffect(lifecycleOwner, videoPlayerCore, forceSoftDecode) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) state.play()
            else if (event == Lifecycle.Event.ON_STOP) {
                if (!Configs.appPipEnable) state.pause()
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    return state
}

enum class VideoPlayerDisplayMode(val value: Int, val label: String) {
    /** 原始 */
    ORIGINAL(0, "原始"),

    /** 填充 */
    FILL(1, "填充"),

    /** 裁剪 */
    CROP(2, "裁剪"),

    /** 4:3 */
    FOUR_THREE(3, "4:3"),

    /** 16:9 */
    SIXTEEN_NINE(4, "16:9"),

    /** 2.35:1 */
    WIDE(5, "2.35:1");

    companion object {
        fun fromValue(value: Int): VideoPlayerDisplayMode {
            return entries.firstOrNull { it.value == value } ?: ORIGINAL
        }
    }
}