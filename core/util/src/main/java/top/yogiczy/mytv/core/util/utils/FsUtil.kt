package top.yogiczy.mytv.core.util.utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File

object FsUtil {
    /**
     * 判断文件是否在 dir 之内（规范化后比较），防止 .. 越出。
     * 与 resolveWithin 不同：这里入参就是目标文件本身（常为绝对路径），
     * 不做 parent/child 拼接。
     */
    fun isWithin(file: File, dir: File): Boolean {
        val base = dir.canonicalFile
        val target = file.canonicalFile
        return target == base || target.path.startsWith(base.path + File.separator)
    }

    /**
     * 把子路径解析为 dir 下的文件；子路径为绝对路径或含 .. 越出 dir 时返回 null，
     * 防止目录穿越。返回的文件已规范化（解析掉 . / .. / 符号链接）。
     */
    fun resolveWithin(dir: File, child: String): File? {
        val resolved = File(dir.canonicalFile, child).canonicalFile
        return if (isWithin(resolved, dir)) resolved else null
    }

    /**
     * 原子写文本：先写同目录临时文件再改名覆盖，写一半被杀只留 .tmp，
     * 目标文件要么是旧的完整内容、要么是新的完整内容。
     * 极少数文件系统改名失败时回退为覆盖复制；写失败时删临时文件再抛错。
     * （缓存与下载两处已有同款写法，这里抽成共用，云同步本地文件也走它。）
     */
    fun writeTextAtomically(file: File, text: String) {
        val tmpFile = File(file.absoluteFile.parent, "${file.name}.tmp")
        try {
            tmpFile.writeText(text)
            if (!tmpFile.renameTo(file)) {
                tmpFile.copyTo(file, overwrite = true)
                tmpFile.delete()
            }
        } catch (e: Exception) {
            tmpFile.delete()
            throw e
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