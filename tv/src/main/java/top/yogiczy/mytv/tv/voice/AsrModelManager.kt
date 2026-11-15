package top.yogiczy.mytv.tv.voice

import kotlinx.coroutines.CancellationException
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import top.yogiczy.mytv.core.data.utils.Globals
import top.yogiczy.mytv.core.data.utils.Logger
import top.yogiczy.mytv.core.util.utils.Downloader
import java.io.File
import java.io.FileInputStream

/**
 * 模型安装状态。
 */
enum class AsrModelState {
    /** 没下载过 */
    NOT_INSTALLED,

    /** 已下载且文件齐全，可用 */
    INSTALLED,

    /** 目录在但文件不全（上次下载/解压被打断），需重新下载 */
    INCOMPLETE,
}

/**
 * ASR 模型下载管理器：下载 .tar.bz2 → 解压 → 校验 → 落到模型目录。
 *
 * 模型放应用私有缓存下的 asr-models/<model-id>/，随应用卸载清除。
 * 下载复用 Downloader（先写 .tmp 成功才改名，失败不留残缺文件）；
 * 解压用 commons-compress；整个流程可取消，取消后清理半成品。
 *
 * 注意：下载的是 100–500MB 的大文件，电视只有 16GB 存储；
 * 调用方（设置页）应在下载前提示大小、下载后显示已用空间。
 */
class AsrModelManager(
    private val modelsDir: File = File(Globals.cacheDir, "asr-models"),
    private val download: suspend (String, String, ((Int) -> Unit)?) -> Unit =
        { url, path, cb -> Downloader.downloadTo(url, path, cb) },
) {
    private val log = Logger.create("AsrModelManager")

    fun modelDir(model: AsrModel): File = File(modelsDir, model.id)

    fun modelState(model: AsrModel): AsrModelState {
        val dir = modelDir(model)
        if (!dir.exists() || !dir.isDirectory) return AsrModelState.NOT_INSTALLED
        return if (isModelDirComplete(dir)) AsrModelState.INSTALLED
        else AsrModelState.INCOMPLETE
    }

    fun installedModels(): List<AsrModel> {
        return AsrModels.ALL.filter { modelState(it) == AsrModelState.INSTALLED }
    }

    /** 模型目录已占用的字节数（设置页显示用）。 */
    fun installedSizeBytes(): Long {
        if (!modelsDir.exists()) return 0
        return modelsDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }

    /**
     * 下载并安装一个模型：下载压缩包 → 解压到临时目录 → 挪到模型目录 → 校验。
     * 已安装的直接返回，不重复下载。失败或取消时清掉半成品，不留残缺模型。
     */
    suspend fun downloadModel(
        model: AsrModel,
        onProgress: ((Int) -> Unit)? = null,
    ): Result<Unit> {
        if (modelState(model) == AsrModelState.INSTALLED) return Result.success(Unit)

        val tmpArchive = File(modelsDir, "${model.id}.tar.bz2.tmp")
        val tmpExtractDir = File(modelsDir, "${model.id}.extracting")
        val targetDir = modelDir(model)
        return try {
            modelsDir.mkdirs()
            // 半成品先清掉：上次失败/取消可能留下了残缺目录
            tmpExtractDir.deleteRecursively()
            targetDir.deleteRecursively()

            log.i("开始下载模型 ${model.displayName}（约 ${model.approxSizeBytes / 1024 / 1024}MB）...")
            download(model.downloadUrl, tmpArchive.path, onProgress)
            if (!tmpArchive.exists() || tmpArchive.length() == 0L) {
                throw IllegalStateException("模型下载失败：文件为空")
            }

            log.i("正在解压模型 ${model.displayName}...")
            tmpExtractDir.mkdirs()
            extractTarBz2(tmpArchive, tmpExtractDir)
            val modelContentDir = resolveModelContentDir(tmpExtractDir)
                ?: throw IllegalStateException("模型解压失败：包里没有模型文件")
            // 挪到正式目录：先删目标（上面已删过），再整体移动
            if (!modelContentDir.renameTo(targetDir)) {
                modelContentDir.copyRecursively(targetDir, overwrite = true)
                modelContentDir.deleteRecursively()
            }

            if (!isModelDirComplete(targetDir)) {
                throw IllegalStateException("模型文件不完整：缺少 tokens.txt 或 .onnx 模型文件")
            }
            // 内容已挪走，空的临时解压目录也清掉
            tmpExtractDir.deleteRecursively()
            log.i("模型 ${model.displayName} 安装完成")
            Result.success(Unit)
        } catch (ex: Exception) {
            if (ex is CancellationException) throw ex
            log.e("模型 ${model.displayName} 安装失败", ex)
            tmpExtractDir.deleteRecursively()
            targetDir.deleteRecursively()
            Result.failure(ex)
        } finally {
            tmpArchive.delete()
        }
    }

    /** 删除一个已安装的模型（释放空间）。 */
    fun deleteModel(model: AsrModel): Boolean {
        return modelDir(model).deleteRecursively()
    }
}

/**
 * 模型目录是否完整：有 tokens.txt 且至少一个 .onnx 文件。
 * 各家模型的 .onnx 文件名不固定（有的带 epoch 号），不硬编码文件名。
 */
internal fun isModelDirComplete(dir: File): Boolean {
    if (!dir.isDirectory) return false
    val files = dir.listFiles() ?: return false
    val hasTokens = files.any { it.isFile && it.name == "tokens.txt" }
    val hasOnnx = files.any { it.isFile && it.name.endsWith(".onnx", ignoreCase = true) }
    return hasTokens && hasOnnx
}

/**
 * 解压后找到真正的模型内容目录：压缩包里一般是单层目录
 * （如 sherpa-onnx-whisper-tiny/），也有直接散着文件的。
 * 前者取那个唯一子目录，后者取解压目录本身；都没有返回 null。
 */
internal fun resolveModelContentDir(extractDir: File): File? {
    if (!extractDir.isDirectory) return null
    val entries = extractDir.listFiles() ?: return null
    val subDirs = entries.filter { it.isDirectory }
    if (subDirs.size == 1 && entries.none { it.isFile }) return subDirs.first()
    if (entries.any { it.isFile }) return extractDir
    return null
}

/**
 * 解 .tar.bz2 到目标目录。防目录穿越：tar 里带 ../ 的条目直接拒绝，
 * 不写到模型目录外面。
 */
internal fun extractTarBz2(archive: File, destDir: File) {
    val destCanonical = destDir.canonicalPath + File.separator
    FileInputStream(archive).use { fis ->
        BZip2CompressorInputStream(fis).use { bz2 ->
            TarArchiveInputStream(bz2).use { tar ->
                var entry = tar.nextEntry
                while (entry != null) {
                    val outFile = File(destDir, entry.name)
                    require(outFile.canonicalPath.startsWith(destCanonical)) {
                        "模型包里有非法路径: ${entry.name}"
                    }
                    if (entry.isDirectory) {
                        outFile.mkdirs()
                    } else {
                        outFile.parentFile?.mkdirs()
                        outFile.outputStream().use { tar.copyTo(it) }
                    }
                    entry = tar.nextEntry
                }
            }
        }
    }
}
