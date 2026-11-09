package top.yogiczy.mytv.core.data.repositories

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.yogiczy.mytv.core.data.utils.Globals
import java.io.File
import java.io.InputStream

/**
 * 用于将数据缓存至本地
 */
abstract class FileCacheRepository(
    private val fileName: String,
    private val isFullPath: Boolean = false,
) {
    init {
        getCacheFile().parentFile?.let {
            if (!it.exists()) it.mkdirs()
        }
    }

    private fun getCacheFile() =
        if (isFullPath) File(fileName) else File(Globals.cacheDir, fileName)

    private suspend fun getCacheData(): String? = withContext(Dispatchers.IO) {
        val file = getCacheFile()
        if (file.exists()) file.readText()
        else null
    }

    private suspend fun getCacheInputStream(): InputStream? = withContext(Dispatchers.IO) {
        val file = getCacheFile()
        if (file.exists()) file.inputStream()
        else null
    }

    private suspend fun setCacheData(data: String) = withContext(Dispatchers.IO) {
        val file = getCacheFile()
        writeAtomically(file) { it.writeText(data) }
    }

    protected suspend fun setCacheInputStream(data: InputStream) = withContext(Dispatchers.IO) {
        val file = getCacheFile()
        writeAtomically(file) { tmp -> tmp.outputStream().use { data.copyTo(it) } }
    }

    /**
     * 先写同目录临时文件再 rename 覆盖目标：同文件系统内 rename 是原子的。
     * 原先直接 writeText / outputStream 写目标文件，进程在写一半时被杀会
     * 留下截断的缓存，下次读取时因未过期直接返回损坏数据、解析失败，
     * 频道/节目单一直加载失败直到缓存过期。
     * 原子写后崩溃只会留下 .tmp 临时文件，目标要么是旧的完整内容、要么是新的完整内容。
     */
    private fun writeAtomically(file: File, write: (File) -> Unit) {
        val tmpFile = File(file.absoluteFile.parent, "${file.name}.tmp")
        write(tmpFile)
        if (!tmpFile.renameTo(file)) {
            // 极少数文件系统 rename 失败时回退为覆盖复制
            tmpFile.copyTo(file, overwrite = true)
            tmpFile.delete()
        }
    }

    protected suspend fun getOrRefreshInputStream(
        cacheTime: Long,
        refreshOp: suspend () -> InputStream
    ): InputStream {
        return getOrRefreshInputStream(
            { lastModified, _ -> System.currentTimeMillis() - lastModified >= cacheTime },
            refreshOp,
        )
    }

    protected suspend fun getOrRefreshInputStream(
        isExpired: (lastModified: Long, cacheData: InputStream?) -> Boolean,
        refreshOp: suspend () -> InputStream,
    ) = withContext(Dispatchers.IO) {
        // 仅用打开的流判断缓存是否为空，判断完就关闭：原先这个流从不关闭，
        // 缓存命中时每次都漏一个文件描述符
        val cached = getCacheInputStream()
        val expired = isExpired(getCacheFile().lastModified(), cached)
        val useCache = !expired && cached != null && cached.available() > 0
        cached?.close()

        if (!useCache) {
            setCacheInputStream(refreshOp())
        }

        getCacheInputStream()!!
    }

    protected suspend fun getOrRefresh(cacheTime: Long, refreshOp: suspend () -> String): String {
        return getOrRefresh(
            { lastModified, _ -> System.currentTimeMillis() - lastModified >= cacheTime },
            refreshOp,
        )
    }

    protected suspend fun getOrRefresh(
        isExpired: (lastModified: Long, cacheData: String?) -> Boolean,
        refreshOp: suspend () -> String,
    ): String {
        var data = getCacheData()

        if (isExpired(getCacheFile().lastModified(), data)) {
            data = null
        }

        if (data.isNullOrBlank()) {
            data = refreshOp()
            setCacheData(data)
        }

        return data
    }

    open suspend fun clearCache() = withContext(Dispatchers.IO) {
        try {
            getCacheFile().delete()
        } catch (ex: Exception) {
            ex.printStackTrace()
        }
    }

    fun lastModified(): Long {
        return getCacheFile().lastModified()
    }
}