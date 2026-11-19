package top.yogiczy.mytv.tv.voice

import android.content.res.AssetManager
import com.k2fsa.sherpa.onnx.EndpointConfig
import com.k2fsa.sherpa.onnx.EndpointRule
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OnlineCtcFstDecoderConfig
import com.k2fsa.sherpa.onnx.OnlineLMConfig
import com.k2fsa.sherpa.onnx.HomophoneReplacerConfig
import com.k2fsa.sherpa.onnx.OnlineModelConfig
import com.k2fsa.sherpa.onnx.OnlineRecognizer
import com.k2fsa.sherpa.onnx.OnlineRecognizerConfig
import com.k2fsa.sherpa.onnx.OnlineStream
import com.k2fsa.sherpa.onnx.OnlineTransducerModelConfig
import com.k2fsa.sherpa.onnx.OnlineZipformer2CtcModelConfig
import top.yogiczy.mytv.core.data.utils.Logger
import java.io.File
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/** 流式模型分两类：transducer（encoder/decoder/joiner 三件套，
 * 比如中英双语模型）和 CTC（单个 .onnx，比如默认中文模型）。
 * 引擎按目录里的文件自己判断，不用写死 .onnx 文件名。 */
private val log = Logger.create("AsrEngine")

enum class AsrModelType { TRANSDUCER, CTC }

/** 按模型目录里的文件判断是哪类流式模型；文件不全（缺 tokens.txt、
 * 或既不是三件套也不是单个 .onnx）回 null，调用方报“模型不完整”。 */
internal fun detectStreamingModelType(modelDir: File): AsrModelType? {
    if (!File(modelDir, "tokens.txt").exists()) return null
    val encoder = File(modelDir, "encoder.onnx")
    val decoder = File(modelDir, "decoder.onnx")
    val joiner = File(modelDir, "joiner.onnx")
    if (encoder.exists() && decoder.exists() && joiner.exists()) {
        return AsrModelType.TRANSDUCER
    }
    val onnxFiles = modelDir.listFiles { f ->
        f.isFile && f.extension == "onnx" &&
            f.name != "encoder.onnx" && f.name != "decoder.onnx" && f.name != "joiner.onnx"
    } ?: return null
    // CTC 模型只有一个 .onnx；多个说明目录混了，不猜
    return if (onnxFiles.size == 1) AsrModelType.CTC else null
}

/** CTC 模型的单个 .onnx 文件（detectStreamingModelType 是 CTC 时才有） */
internal fun findCtcModelFile(modelDir: File): File? {
    val onnxFiles = modelDir.listFiles { f ->
        f.isFile && f.extension == "onnx" &&
            f.name != "encoder.onnx" && f.name != "decoder.onnx" && f.name != "joiner.onnx"
    } ?: return null
    return if (onnxFiles.size == 1) onnxFiles[0] else null
}

/**
 * 流式语音识别引擎：把 16kHz 单声道 float 音频喂进来，边播边出文字。
 *
 * 用 sherpa-onnx 的 OnlineRecognizer（本地跑，不联网）。模型在
 * 应用缓存 asr-models/<id>/ 里，目录结构由 AsrModelManager 落盘。
 *
 * 线程：acceptAudio 可以从播放器线程调（只进队列，不阻塞）；
 * 解码跑在一条专用线程上，按顺序喂流、decode、出结果。
 * onPartial 是进行中的文字（会变），onFinal 是一句话说完的文字
 * （端点检测到停顿后出，出了就 reset 流、下一句重新开始）。
 * onError 只报引擎起不来/解码崩了这类致命问题，识别中途的空结果不算错。
 */
class AsrEngine(
    private val assets: AssetManager,
    private val modelDir: File,
    private val onPartial: (String) -> Unit,
    private val onFinal: (String) -> Unit,
    private val onError: (Throwable) -> Unit = {},
) {
    private val audioQueue = LinkedBlockingQueue<FloatArray>()
    @Volatile
    private var running = false
    private var decodeThread: Thread? = null
    private var recognizer: OnlineRecognizer? = null
    private var stream: OnlineStream? = null
    private var lastText = ""

    /** 建识别器并启动解码线程；模型文件不对或 native 库起不来回 false */
    fun start(): Boolean {
        val type = detectStreamingModelType(modelDir)
        if (type == null) {
            log.i("AI 字幕：模型目录不完整（缺 tokens.txt 或 .onnx 不对）：${modelDir.path}")
            return false
        }
        return try {
            recognizer = createRecognizer(type)
            stream = recognizer!!.createStream()
            running = true
            decodeThread = Thread(::decodeLoop, "ai-subtitle-decode").apply { start() }
            log.i("AI 字幕：识别引擎已启动（${modelDir.name}，$type）")
            true
        } catch (ex: Exception) {
            log.i("AI 字幕：识别引擎启动失败：${ex.message}")
            releaseRecognizer()
            onError(ex)
            false
        }
    }

    /** 喂一小段 16kHz 单声道 float 音频；队列满了丢最旧的（直播不能积压） */
    fun acceptAudio(samples: FloatArray) {
        if (!running || samples.isEmpty()) return
        // 队列最多攒约 5 秒音频（16000*5 个采样点），再多说明解码跟不上，
        // 丢旧的保实时，别越攒越 lag
        while (audioQueue.size >= 50) audioQueue.poll()
        audioQueue.offer(samples)
    }

    /** 停解码线程、释放识别器；可反复调 */
    fun stop() {
        running = false
        decodeThread?.interrupt()
        try {
            decodeThread?.join(2000)
        } catch (_: InterruptedException) {
        }
        decodeThread = null
        audioQueue.clear()
        lastText = ""
        releaseRecognizer()
    }

    /** 一句话说完后 reset 流（下一句重新开始）；换台时也调 */
    fun resetStream() {
        val r = recognizer
        val s = stream
        if (r != null && s != null) {
            try {
                r.reset(s)
            } catch (_: Exception) {
            }
        }
        lastText = ""
    }

    private fun createRecognizer(type: AsrModelType): OnlineRecognizer {
        val tokens = File(modelDir, "tokens.txt").absolutePath
        // 有 bpe.model 的模型（默认中文 CTC、中英双语）走 BPE 分词；
        // 没有的用默认 cjkchar（别传空串，会盖掉默认值）
        val bpeModel = File(modelDir, "bpe.model").takeIf { it.exists() }
        val modelConfig = when (type) {
            AsrModelType.TRANSDUCER -> OnlineModelConfig(
                transducer = OnlineTransducerModelConfig(
                    encoder = File(modelDir, "encoder.onnx").absolutePath,
                    decoder = File(modelDir, "decoder.onnx").absolutePath,
                    joiner = File(modelDir, "joiner.onnx").absolutePath,
                ),
                tokens = tokens,
                numThreads = 2,
            ).apply {
                if (bpeModel != null) {
                    modelingUnit = "bpe"
                    bpeVocab = bpeModel.absolutePath
                }
            }
            AsrModelType.CTC -> OnlineModelConfig(
                zipformer2Ctc = OnlineZipformer2CtcModelConfig(
                    model = findCtcModelFile(modelDir)!!.absolutePath,
                ),
                tokens = tokens,
                numThreads = 2,
            ).apply {
                if (bpeModel != null) {
                    modelingUnit = "bpe"
                    bpeVocab = bpeModel.absolutePath
                }
            }
        }
        val config = OnlineRecognizerConfig(
            featConfig = FeatureConfig(sampleRate = 16000, featureDim = 80),
            modelConfig = modelConfig,
            lmConfig = OnlineLMConfig(),
            ctcFstDecoderConfig = OnlineCtcFstDecoderConfig(),
            // homophone replacer 用默认值（参数名是 hr，不传就是默认的空配置）
            endpointConfig = EndpointConfig(
                rule1 = EndpointRule(mustContainNonSilence = true, minTrailingSilence = 1.2f, minUtteranceLength = 0f),
                rule2 = EndpointRule(mustContainNonSilence = false, minTrailingSilence = 2.4f, minUtteranceLength = 0f),
                rule3 = EndpointRule(mustContainNonSilence = false, minTrailingSilence = 0f, minUtteranceLength = 20f),
            ),
            enableEndpoint = true,
            decodingMethod = "greedy_search",
            maxActivePaths = 4,
        )
        return OnlineRecognizer(assets, config)
    }

    private fun decodeLoop() {
        val recognizer = this.recognizer ?: return
        val stream = this.stream ?: return
        try {
            while (running) {
                val samples = audioQueue.poll(200, TimeUnit.MILLISECONDS) ?: continue
                stream.acceptWaveform(samples, 16000)
                while (recognizer.isReady(stream)) {
                    recognizer.decode(stream)
                }
                val text = recognizer.getResult(stream).text.trim()
                if (text.isNotEmpty() && text != lastText) {
                    lastText = text
                    onPartial(text)
                }
                if (recognizer.isEndpoint(stream)) {
                    if (lastText.isNotEmpty()) {
                        onFinal(lastText)
                    }
                    recognizer.reset(stream)
                    lastText = ""
                }
            }
        } catch (ex: InterruptedException) {
            // 正常停止，不算错
        } catch (ex: Exception) {
            log.i("AI 字幕：解码出错：${ex.message}")
            onError(ex)
        }
    }

    private fun releaseRecognizer() {
        try {
            stream = null
            recognizer?.release()
        } catch (_: Exception) {
        }
        recognizer = null
    }
}
