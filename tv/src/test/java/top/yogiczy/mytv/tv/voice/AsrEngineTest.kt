package top.yogiczy.mytv.tv.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
    fun detectStreamingModelType_emptyDir_isNull() {
        val dir = tempDir()
        try {
            assertNull(detectStreamingModelType(dir))
        } finally {
            dir.deleteRecursively()
        }
    }
}
