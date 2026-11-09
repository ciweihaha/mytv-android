package top.yogiczy.mytv.tv.sync.repositories

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.yogiczy.mytv.core.data.network.request
import top.yogiczy.mytv.core.data.utils.Globals
import top.yogiczy.mytv.core.data.utils.Loggable
import top.yogiczy.mytv.tv.sync.CloudSyncData

class NetworkUrlSyncRepository(
    private val url: String
) : CloudSyncRepository, Loggable("NetworkUrlSyncRepository") {
    override suspend fun push(data: CloudSyncData): Boolean {
        return false
    }

    override suspend fun pull() = withContext(Dispatchers.IO) {
        try {
            // 用统一的 OkHttp 请求（默认 10 秒超时）：原先 URL.readText()
            // 无任何超时，服务端假死时会无限挂住，启动自动拉取时还会卡住主界面
            val data = url.request { body -> body.string() }
                ?: throw Exception("拉取云端失败：服务器返回空数据")
            return@withContext Globals.json.decodeFromString<CloudSyncData>(data)
        } catch (ex: Exception) {
            log.e("拉取云端失败", ex)
            throw Exception("拉取云端失败", ex)
        }
    }
}
