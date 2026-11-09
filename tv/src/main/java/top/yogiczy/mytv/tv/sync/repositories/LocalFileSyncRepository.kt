package top.yogiczy.mytv.tv.sync.repositories

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import top.yogiczy.mytv.core.data.utils.Globals
import top.yogiczy.mytv.core.data.utils.Loggable
import top.yogiczy.mytv.core.util.utils.ensureSuffix
import top.yogiczy.mytv.tv.sync.CloudSyncData
import java.io.File

class LocalFileSyncRepository(
    private val path: String
) : CloudSyncRepository, Loggable("LocalFileSyncRepository") {

    val file by lazy {
        val hasFileName = path.split("/").last().contains(".")
        if (hasFileName) File(path.substringAfter("file://"))
        else File(path.substringAfter("file://").ensureSuffix("/") + "all_configs.json")
    }

    override suspend fun push(data: CloudSyncData): Boolean = withContext(Dispatchers.IO) {
        try {
            // 用户配的路径所在目录可能不存在：先建目录，否则 writeText 抛
            // FileNotFoundException，推送永远失败
            file.parentFile?.mkdirs()
            file.writeText(Globals.json.encodeToString(data))
            true
        } catch (ex: Exception) {
            log.e("推送失败", ex)
            // 按接口契约返回 false 而不是抛异常：调用方按返回值提示成败
            false
        }
    }

    override suspend fun pull() = withContext(Dispatchers.IO) {
        try {
            val text = file.readText()
            Globals.json.decodeFromString<CloudSyncData>(text)
        } catch (ex: Exception) {
            log.e("拉取失败", ex)
            throw Exception("拉取失败", ex)
        }
    }
}