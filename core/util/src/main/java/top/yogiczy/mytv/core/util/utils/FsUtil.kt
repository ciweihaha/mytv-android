package top.yogiczy.mytv.core.util.utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File

object FsUtil {
    /**
     * 把子路径解析为 dir 下的文件；子路径为绝对路径或含 .. 越出 dir 时返回 null，
     * 防止目录穿越。返回的文件已规范化（解析掉 . / .. / 符号链接）。
     */
    fun resolveWithin(dir: File, child: String): File? {
        val base = dir.canonicalFile
        val resolved = File(base, child).canonicalFile
        return if (resolved == base || resolved.path.startsWith(base.path + File.separator)) {
            resolved
        } else {
            null
        }
    }

    /**
     * 递归计算文件夹大小
     */
    fun getDirSizeFlow(dir: File): Flow<Long> = flow {
        if (dir.exists() && dir.isDirectory) {
            val totalSize = calculateDirSize(dir) { size -> emit(size) }
            emit(totalSize)
        } else emit(0L)
    }.flowOn(Dispatchers.IO)

    private suspend fun calculateDirSize(file: File, onFileSize: suspend (Long) -> Unit): Long {
        var totalSize = 0L
        file.listFiles()?.forEach { child ->
            if (child.isFile) {
                val size = child.length()
                totalSize += size
                onFileSize(size)
            } else if (child.isDirectory) {
                totalSize += calculateDirSize(child, onFileSize)
            }
        }
        return totalSize
    }
}