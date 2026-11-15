package top.yogiczy.mytv.tv.voice

/**
 * AI 实时字幕的模型目录：初版 6 个，URL 均取自 3.x APK 字符串里实测的
 * 地址（GitHub k2-fsa/sherpa-onnx 的 asr-models release）。
 * 约大小是下载前的参考值（3.x 实测），以下载完的实际大小为准。
 *
 * 只列中文相关的模型：用户看的是中文台。英文/其它语种模型在
 * asr-models release 里还有，需要时再加。
 */
enum class AsrModelKind {
    /** 流式：边播边出字幕，延迟低，实时字幕默认用它 */
    STREAMING,

    /** 离线：整句识别完再出，准确率高，延迟大 */
    OFFLINE,
}

data class AsrModel(
    /** 唯一 id，也是模型目录名的一部分 */
    val id: String,
    /** 设置里显示的名字 */
    val displayName: String,
    val kind: AsrModelKind,
    /** 支持的语言 */
    val languages: String,
    /** .tar.bz2 下载地址 */
    val downloadUrl: String,
    /** 约大小（字节），仅作显示 */
    val approxSizeBytes: Long,
) {
    /** 压缩包文件名 */
    val archiveFileName: String get() = downloadUrl.substringAfterLast("/")

    /** 解压后的模型目录名（压缩包名去掉 .tar.bz2） */
    val modelDirName: String get() = archiveFileName.removeSuffix(".tar.bz2")
}

object AsrModels {
    private const val BASE_URL =
        "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/"

    /** 默认推荐：流式、中文、int8 量化，体积小、延迟低 */
    val DEFAULT_MODEL_ID = "streaming-zipformer-ctc-zh-hans-int8"

    val ALL: List<AsrModel> = listOf(
        AsrModel(
            id = "streaming-zipformer-ctc-zh-hans-int8",
            displayName = "流式 Zipformer 中文",
            kind = AsrModelKind.STREAMING,
            languages = "中文",
            downloadUrl = BASE_URL +
                "sherpa-onnx-streaming-zipformer-ctc-multi-zh-hans-int8-2023-12-13.tar.bz2",
            approxSizeBytes = 122L * 1024 * 1024,
        ),
        AsrModel(
            id = "streaming-zipformer-bilingual-zh-en",
            displayName = "流式 Zipformer 中英双语",
            kind = AsrModelKind.STREAMING,
            languages = "中文、英文",
            downloadUrl = BASE_URL +
                "sherpa-onnx-streaming-zipformer-bilingual-zh-en-2023-02-20.tar.bz2",
            approxSizeBytes = 488L * 1024 * 1024,
        ),
        AsrModel(
            id = "sense-voice-zh-en-ja-ko-yue",
            displayName = "SenseVoice 多语",
            kind = AsrModelKind.OFFLINE,
            languages = "中文、英文、日文、韩文、粤语",
            downloadUrl = BASE_URL +
                "sherpa-onnx-sense-voice-zh-en-ja-ko-yue-2024-07-17.tar.bz2",
            approxSizeBytes = 155L * 1024 * 1024,
        ),
        AsrModel(
            id = "paraformer-zh",
            displayName = "Paraformer 中文",
            kind = AsrModelKind.OFFLINE,
            languages = "中文",
            downloadUrl = BASE_URL +
                "sherpa-onnx-paraformer-zh-2023-09-14.tar.bz2",
            approxSizeBytes = 218L * 1024 * 1024,
        ),
        AsrModel(
            id = "whisper-tiny",
            displayName = "Whisper tiny",
            kind = AsrModelKind.OFFLINE,
            languages = "多语",
            downloadUrl = BASE_URL + "sherpa-onnx-whisper-tiny.tar.bz2",
            approxSizeBytes = 111L * 1024 * 1024,
        ),
        AsrModel(
            id = "whisper-base",
            displayName = "Whisper base",
            kind = AsrModelKind.OFFLINE,
            languages = "多语",
            downloadUrl = BASE_URL + "sherpa-onnx-whisper-base.tar.bz2",
            approxSizeBytes = 198L * 1024 * 1024,
        ),
    )

    fun byId(id: String): AsrModel? = ALL.firstOrNull { it.id == id }

    fun defaultModel(): AsrModel = byId(DEFAULT_MODEL_ID) ?: ALL.first()
}
