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
        // 刷新流用完必须关：原来只关了输出流，输入流读完也不关；
        // 节目单刷新走网络流（ResponseBody.byteStream），不关连接回不了
        // 共用池，每次刷新漏一个连接（第 52 轮修的是错误分支，成功分支一直漏着）
        writeAtomically(file) { tmp -> tmp.outputStream().use { out -> data.use { input -> input.copyTo(out) } } }
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

        if (useCache) return@withContext getCacheInputStream()!!

        // 刷新失败时用磁盘上的旧文件顶上：不断网时本来就会刷新成功；
        // 断网时旧节目单还能看，总比直接报错强；没旧文件时才抛错
        try {
            setCacheInputStream(refreshOp())
            getCacheInputStream()!!
        } catch (ex: Exception) {
            val stale = getCacheInputStream()
            if (stale != null && stale.available() > 0) stale
            else {
                stale?.close()
                throw ex
            }
        }
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
        // 开头先把旧数据读出来记着：没过期直接返回；过期去刷新，
        // 刷新失败（断网）时用它顶上，不再重读磁盘、也不直接抛错——
        // 断网时旧单/旧节目单还能看；没旧数据时才抛错
        val staleData = getCacheData()

        if (!isExpired(getCacheFile().lastModified(), staleData) && !staleData.isNullOrBlank()) {
            return staleData
        }

        return try {
            refreshOp().also { setCacheData(it) }
        } catch (ex: Exception) {
            if (!staleData.isNullOrBlank()) staleData else throw ex
        }
    }

    open suspend fun clearCache() = withContext(Dispatchers.IO) {
        try {
            val file = getCacheFile()
            file.delete()
            // 原子写崩溃后残留的 .tmp：只删目标文件的话它会一直留在缓存
            // 目录里，“清除缓存”也清不掉，崩溃多了越积越多
            File(file.absoluteFile.parent, "${file.name}.tmp").delete()
        } catch (ex: Exception) {
            ex.printStackTrace()
        }
    }

    fun lastModified(): Long {
        return getCacheFile().lastModified()
    }
}