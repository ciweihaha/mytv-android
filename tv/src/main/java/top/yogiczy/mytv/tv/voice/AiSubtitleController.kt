package top.yogiczy.mytv.tv.voice

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import top.yogiczy.mytv.core.data.utils.Logger
import java.io.File

/** AI 字幕状态：给设置页/播放页显示（模型没下、引擎起不来等） */
private val log = Logger.create("AiSubtitleController")

enum class AiSubtitleStatus {
    IDLE,           // 没开
    NO_MODEL,       // 选的模型还没下载
    STARTING,       // 引擎启动中
    READY,          // 正在识别
    ENGINE_ERROR,   // 引擎起不来/解码崩了
}

/** 翻译模式（和 TiviMateWin 一致）：默认只翻英语 */
enum class AiTranslateMode { OFF, ENGLISH_ONLY, ALL }

/**
 * AI 实时字幕协调器：把播放器音频 → 重采样 → 识别引擎 → 翻译 → 显示行串起来。
 *
 * 显示规则：
 * - 中文识别出来直接显示中文。
 * - 英文识别出来先显示英文（一句话说完后）再调免费接口翻中文，
 *   翻到了就把这行换成中文，翻不到就留着英文（接口失败只跳过那行）。
 * - 一句话说完 5 秒没新内容，字幕行自动清空（别一直挂在屏幕上）。
 *
 * 音频从播放器来：Media3 核心经 AudioProcessor 回调喂 PCM；
 * IJK 核心没有音频回调，所以 IJK 下 AI 字幕不可用（设置里会提示）。
 */
class AiSubtitleController(
    private val modelsDir: File,
    private val scope: CoroutineScope,
) {
    private val _subtitleText = MutableStateFlow("")
    val subtitleText: StateFlow<String> = _subtitleText.asStateFlow()

    private val _status = MutableStateFlow(AiSubtitleStatus.IDLE)
    val status: StateFlow<AiSubtitleStatus> = _status.asStateFlow()

    private var engine: AsrEngine? = null
    private val resampler = Pcm16kResampler()
    private var translator: FreeTranslator? = null
    private var translateMode: AiTranslateMode = AiTranslateMode.ENGLISH_ONLY
    private var clearJob: Job? = null
    private var lastFinalText = ""

    fun setTranslator(translator: FreeTranslator?) {
        this.translator = translator
    }

    fun setTranslateMode(mode: AiTranslateMode) {
        translateMode = mode
    }

    /** 启动：模型没下好回 false（调用方引导用户去下载） */
    fun start(model: AsrModel): Boolean {
        if (engine != null) return true
        val modelDir = File(modelsDir, model.id)
        if (!File(modelDir, "tokens.txt").exists()) {
            _status.value = AiSubtitleStatus.NO_MODEL
            log.i("AI 字幕：模型 ${model.displayName} 还没下载")
            return false
        }
        _status.value = AiSubtitleStatus.STARTING
        val eng = AsrEngine(
            modelDir = modelDir,
            onPartial = { text -> onRecognized(text, isFinal = false) },
            onFinal = { text -> onRecognized(text, isFinal = true) },
            onError = { _status.value = AiSubtitleStatus.ENGINE_ERROR },
        )
        return if (eng.start()) {
            engine = eng
            _status.value = AiSubtitleStatus.READY
            true
        } else {
            _status.value = AiSubtitleStatus.ENGINE_ERROR
            false
        }
    }

    fun stop() {
        clearJob?.cancel()
        clearJob = null
        engine?.stop()
        engine = null
        resampler.reset()
        _subtitleText.value = ""
        lastFinalText = ""
        _status.value = AiSubtitleStatus.IDLE
    }

    /** 换台时调：清空当前行、reset 识别流，别把上一台的尾巴带过来 */
    fun resetForChannelChange() {
        engine?.resetStream()
        resampler.reset()
        clearJob?.cancel()
        _subtitleText.value = ""
        lastFinalText = ""
    }

    /** 播放器回调的 PCM16 音频（声道交错） */
    fun acceptPcm16(samples: ShortArray, sampleRate: Int, channels: Int) {
        val eng = engine ?: return
        val mono16k = resampler.processPcm16(samples, sampleRate, channels)
        if (mono16k.isNotEmpty()) eng.acceptAudio(mono16k)
    }

    /** 播放器回调的 float 音频（声道交错，-1..1） */
    fun acceptFloat(samples: FloatArray, sampleRate: Int, channels: Int) {
        val eng = engine ?: return
        val mono16k = resampler.processFloat(samples, sampleRate, channels)
        if (mono16k.isNotEmpty()) eng.acceptAudio(mono16k)
    }

    private fun onRecognized(text: String, isFinal: Boolean) {
        if (text.isBlank()) return
        // 进行中的文字直接显示（中文原样，英文先显示英文）
        _subtitleText.value = text
        scheduleClear()
        if (!isFinal) return
        lastFinalText = text
        // 一句话说完了：英文才翻（按模式），翻到了替换成中文
        if (shouldTranslate(text)) {
            val t = translator ?: return
            scope.launch(Dispatchers.IO) {
                val translated = t.translate(text)
                // 翻译回来时这行还是同一句才替换（别把新句盖掉）
                if (!translated.isNullOrBlank() && _subtitleText.value == text) {
                    _subtitleText.value = translated
                    scheduleClear()
                }
            }
        }
    }

    private fun shouldTranslate(text: String): Boolean {
        if (translateMode == AiTranslateMode.OFF) return false
        return when (detectSubtitleLanguage(text)) {
            SubtitleLanguage.ENGLISH -> true
            SubtitleLanguage.CHINESE -> false
            // 认不出来的：仅英语模式不翻，全部模式翻（宁可多翻一句）
            SubtitleLanguage.UNKNOWN -> translateMode == AiTranslateMode.ALL
        }
    }

    private fun scheduleClear() {
        clearJob?.cancel()
        clearJob = scope.launch {
            delay(5000)
            _subtitleText.value = ""
            lastFinalText = ""
        }
    }
}
