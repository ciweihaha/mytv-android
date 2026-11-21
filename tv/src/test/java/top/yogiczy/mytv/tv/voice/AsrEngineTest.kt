package top.yogiczy.mytv.tv.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class AsrEngineTest {
    private fun tempDir(): File = Files.createTempDirectory("asr-engine-test").toFile()

    private fun File.child(name: String, content: String = "x") =
        File(this, name).apply { writeText(content) }

    @Test
    fun detectStreamingModelType_ctcModel() {
        // 默认中文模型：tokens.txt + 单个 .onnx（+ bpe.model）=> CTC
        val dir = tempDir()
        try {
            dir.child("tokens.txt")
            dir.child("bpe.model")
            dir.child("ctc-epoch-20-avg-1-chunk-16-left-128.int8.onnx")
            assertEquals(AsrModelType.CTC, detectStreamingModelType(dir))
            assertEquals(
                "ctc-epoch-20-avg-1-chunk-16-left-128.int8.onnx",
                findCtcModelFile(dir)?.name,
            )
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun detectStreamingModelType_transducerModel() {
        // 中英双语模型：tokens.txt + encoder/decoder/joiner => TRANSDUCER
        val dir = tempDir()
        try {
            dir.child("tokens.txt")
            dir.child("encoder.onnx")
            dir.child("decoder.onnx")
            dir.child("joiner.onnx")
            assertEquals(AsrModelType.TRANSDUCER, detectStreamingModelType(dir))
            assertNull(findCtcModelFile(dir))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun detectStreamingModelType_missingTokens_isNull() {
        // 缺 tokens.txt：不管 .onnx 齐不齐都不认，调用方报模型不完整
        val dir = tempDir()
        try {
            dir.child("encoder.onnx")
            dir.child("decoder.onnx")
            dir.child("joiner.onnx")
            assertNull(detectStreamingModelType(dir))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun detectStreamingModelType_twoCtcOnnx_isNull() {
        // 目录里混了两个 .onnx：不猜是哪个，回 null
        val dir = tempDir()
        try {
            dir.child("tokens.txt")
            dir.child("a.onnx")
            dir.child("b.onnx")
            assertNull(detectStreamingModelType(dir))
            assertNull(findCtcModelFile(dir))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun buildRecognizerConfig_transducer_usesAbsolutePaths() {
        // 识别器配置里的模型路径全是文件系统绝对路径（不是 assets 相对路径）：
        // 这是传 AssetManager=null 的前提——路径是绝对的，原生层才走文件读取。
        val dir = tempDir()
        try {
            dir.child("tokens.txt")
            dir.child("bpe.model")
            dir.child("encoder.onnx")
            dir.child("decoder.onnx")
            dir.child("joiner.onnx")
            val engine = AsrEngine(dir, {}, {})
            val config = engine.buildRecognizerConfig(AsrModelType.TRANSDUCER)
            val transducer = config.modelConfig.transducer
            assertEquals(File(dir, "encoder.onnx").absolutePath, transducer?.encoder)
            assertEquals(File(dir, "decoder.onnx").absolutePath, transducer?.decoder)
            assertEquals(File(dir, "joiner.onnx").absolutePath, transducer?.joiner)
            assertEquals(File(dir, "tokens.txt").absolutePath, config.modelConfig.tokens)
            assertEquals("bpe", config.modelConfig.modelingUnit)
            assertEquals(File(dir, "bpe.model").absolutePath, config.modelConfig.bpeVocab)
            assertTrue(File(transducer?.encoder ?: "").isAbsolute)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun buildRecognizerConfig_ctc_usesAbsolutePath() {
        val dir = tempDir()
        try {
            dir.child("tokens.txt")
            dir.child("model.onnx")
            val engine = AsrEngine(dir, {}, {})
            val config = engine.buildRecognizerConfig(AsrModelType.CTC)
            assertEquals(
                File(dir, "model.onnx").absolutePath,
                config.modelConfig.zipformer2Ctc.model,
            )
            assertEquals(File(dir, "tokens.txt").absolutePath, config.modelConfig.tokens)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun buildRecognizerConfig_noBpeModel_noBpeUnit() {
        // 没有 bpe.model：不走 BPE（建配置时别碰 modelingUnit/bpeVocab）
        val dir = tempDir()
        try {
            dir.child("tokens.txt")
            dir.child("encoder.onnx")
            dir.child("decoder.onnx")
            dir.child("joiner.onnx")
            val engine = AsrEngine(dir, {}, {})
            val config = engine.buildRecognizerConfig(AsrModelType.TRANSDUCER)
            assertEquals("", config.modelConfig.modelingUnit)
            assertEquals("", config.modelConfig.bpeVocab)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun detectStreamingModelType_emptyDir_isNull() {
        val dir = tempDir()
        try {
            assertNull(detectStreamingModelType(dir))
        } finally {
            dir.deleteRecursively()
        }
    }
}
