package top.yogiczy.mytv.tv.voice

import kotlinx.coroutines.runBlocking
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AsrModelManagerTest {
    private fun tempDir(): File {
        return File.createTempFile("asr-test", "").apply { delete(); mkdirs() }
    }

    @Test
    fun isModelDirComplete_needsTokensAndOnnx() {
        val dir = tempDir()
        try {
            assertFalse(isModelDirComplete(dir))
            File(dir, "tokens.txt").writeText("a b")
            assertFalse(isModelDirComplete(dir))
            File(dir, "model.onnx").writeText("fake")
            assertTrue(isModelDirComplete(dir))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun isModelDirComplete_nonDirIsFalse() {
        val file = File.createTempFile("asr-test", ".txt")
        try {
            assertFalse(isModelDirComplete(file))
        } finally {
            file.delete()
        }
    }

    @Test
    fun resolveModelContentDir_singleSubdir() {
        val extractDir = tempDir()
        try {
            assertNull(resolveModelContentDir(extractDir))
            val sub = File(extractDir, "sherpa-onnx-whisper-tiny").apply { mkdirs() }
            File(sub, "tokens.txt").writeText("a")
            assertEquals(sub, resolveModelContentDir(extractDir))
        } finally {
            extractDir.deleteRecursively()
        }
    }

    @Test
    fun resolveModelContentDir_filesAtTopLevel() {
        val extractDir = tempDir()
        try {
            File(extractDir, "tokens.txt").writeText("a")
            File(extractDir, "model.onnx").writeText("fake")
            assertEquals(extractDir, resolveModelContentDir(extractDir))
        } finally {
            extractDir.deleteRecursively()
        }
    }

    @Test
    fun extractTarBz2_roundTrip() {
        // 用 commons-compress 现打一个 .tar.bz2，再用 extractTarBz2 解开
        val work = tempDir()
        try {
            val archive = File(work, "model.tar.bz2")
            BZip2CompressorOutputStream(archive.outputStream()).use { bz2 ->
                TarArchiveOutputStream(bz2).use { tar ->
                    val content = "hello".toByteArray()
                    val entry = TarArchiveEntry("sherpa-onnx-test/tokens.txt")
                    entry.size = content.size.toLong()
                    tar.putArchiveEntry(entry)
                    tar.write(content)
                    tar.closeArchiveEntry()
                }
            }
            val dest = File(work, "out").apply { mkdirs() }
            extractTarBz2(archive, dest)
            assertEquals(
                "hello",
                File(dest, "sherpa-onnx-test/tokens.txt").readText(),
            )
        } finally {
            work.deleteRecursively()
        }
    }

    @Test
    fun extractTarBz2_rejectsPathTraversal() {
        // tar 里带 ../ 的条目：拒绝，不写到目标目录外面
        val work = tempDir()
        try {
            val archive = File(work, "evil.tar.bz2")
            BZip2CompressorOutputStream(archive.outputStream()).use { bz2 ->
                TarArchiveOutputStream(bz2).use { tar ->
                    val content = "evil".toByteArray()
                    val entry = TarArchiveEntry("../escape.txt")
                    entry.size = content.size.toLong()
                    tar.putArchiveEntry(entry)
                    tar.write(content)
                    tar.closeArchiveEntry()
                }
            }
            val dest = File(work, "out").apply { mkdirs() }
            try {
                extractTarBz2(archive, dest)
                throw AssertionError("应拒绝目录穿越")
            } catch (ex: IllegalArgumentException) {
                // 预期
            }
            assertFalse(File(work, "escape.txt").exists())
        } finally {
            work.deleteRecursively()
        }
    }

    @Test
    fun downloadModel_installsAndSkipsRedownload() = runBlocking {
        val work = tempDir()
        try {
            val model = AsrModels.byId("whisper-tiny")!!
            // 假下载：把现打的 .tar.bz2 写到目标路径
            val archive = File(work, "pkg.tar.bz2")
            BZip2CompressorOutputStream(archive.outputStream()).use { bz2 ->
                TarArchiveOutputStream(bz2).use { tar ->
                    fun add(name: String, text: String) {
                        val bytes = text.toByteArray()
                        val entry = TarArchiveEntry(name)
                        entry.size = bytes.size.toLong()
                        tar.putArchiveEntry(entry)
                        tar.write(bytes)
                        tar.closeArchiveEntry()
                    }
                    add("sherpa-onnx-whisper-tiny/tokens.txt", "a b")
                    add("sherpa-onnx-whisper-tiny/encoder.onnx", "fake")
                    add("sherpa-onnx-whisper-tiny/decoder.onnx", "fake")
                }
            }
            var downloadCalls = 0
            val manager = AsrModelManager(
                modelsDir = File(work, "models"),
                download = { _, path, _ ->
                    downloadCalls++
                    archive.copyTo(File(path), overwrite = true)
                },
            )
            assertEquals(AsrModelState.NOT_INSTALLED, manager.modelState(model))
            val result = manager.downloadModel(model)
            assertTrue(result.isSuccess)
            assertEquals(AsrModelState.INSTALLED, manager.modelState(model))
            assertTrue(manager.installedModels().contains(model))
            assertTrue(manager.installedSizeBytes() > 0)

            // 已安装：再调一次不重复下载
            manager.downloadModel(model)
            assertEquals(1, downloadCalls)

            // 删除后回到未安装
            assertTrue(manager.deleteModel(model))
            assertEquals(AsrModelState.NOT_INSTALLED, manager.modelState(model))
        } finally {
            work.deleteRecursively()
        }
    }

    @Test
    fun downloadModel_failedDownloadLeavesNothing() = runBlocking {
        val work = tempDir()
        try {
            val model = AsrModels.byId("whisper-tiny")!!
            val manager = AsrModelManager(
                modelsDir = File(work, "models"),
                download = { _, _, _ -> throw RuntimeException("网络断了") },
            )
            val result = manager.downloadModel(model)
            assertTrue(result.isFailure)
            assertEquals(AsrModelState.NOT_INSTALLED, manager.modelState(model))
            assertFalse(File(work, "models/${model.id}").exists())
        } finally {
            work.deleteRecursively()
        }
    }

    @Test
    fun asrModels_catalogIsWellFormed() {
        // 目录里的模型：URL 都是 asr-models release 下的 .tar.bz2，
        // 目录名就是包名去后缀，默认模型是流式（实时字幕用）
        val base = "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/"
        for (model in AsrModels.ALL) {
            assertTrue(model.downloadUrl.startsWith(base))
            assertTrue(model.archiveFileName.endsWith(".tar.bz2"))
            assertEquals(
                model.archiveFileName.removeSuffix(".tar.bz2"),
                model.modelDirName,
            )
            assertTrue(model.approxSizeBytes > 0)
        }
        assertEquals(
            AsrModelKind.STREAMING,
            AsrModels.defaultModel().kind,
        )
    }

    @Test
    fun asrModels_idsUniqueAndByIdFindsEach() {
        // id 是下载目录名的一部分、也是设置的存值：重了会让 byId 悄悄返回
        // 先写的那一个，后写的模型永远选不到；未知 id 必须回 null 而不是
        // 抛错（设置里存了旧 id 时走默认模型）
        val ids = AsrModels.ALL.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        for (model in AsrModels.ALL) {
            assertEquals(model, AsrModels.byId(model.id))
        }
        assertNull(AsrModels.byId("no-such-model"))
    }
}
