package top.yogiczy.mytv.tv.sync.repositories

import kotlinx.coroutines.CancellationException
import kotlinx.serialization.encodeToString
import okhttp3.Credentials
import okhttp3.RequestBody.Companion.toRequestBody
import top.yogiczy.mytv.core.data.network.request
import top.yogiczy.mytv.core.data.utils.Globals
import top.yogiczy.mytv.core.data.utils.Loggable
import top.yogiczy.mytv.core.util.utils.ensureSuffix
import top.yogiczy.mytv.tv.sync.CloudSyncData

class WebDavSyncRepository(
    private val url: String,
    private val username: String,
    private val password: String
) : CloudSyncRepository, Loggable("WebDavSyncRepository") {

    private val fullUrl by lazy {
        val hasFileName = url.split("/").last().contains(".")
        if (hasFileName) url
        else url.ensureSuffix("/") + "all_configs.json"
    }

    override suspend fun push(data: CloudSyncData): Boolean {
        try {
            return fullUrl.request({ builder ->
                builder
                    .header("Authorization", Credentials.basic(username, password))
                    .put(Globals.json.encodeToString(data).toRequestBody())
            }) { body ->
                // 推送成功也要关响应体：body 不读不关，连接回不了共用池，
                // 每次推送都会漏一个连接（和 OkHttpExtensions 里修过的
                // 错误分支是同一类问题，只是这里走的是成功分支）
                body.close()
                true
            } ?: false
        } catch (ex: Exception) {
            // 推送中途被取消不是推送失败：取消信号原样抛出去，别按接口契约返回 false、
            // 让界面弹一条“推送云端失败”的假报错（690 轮同款问题）
            if (ex is CancellationException) throw ex
            log.e("推送云端失败", ex)
            // 按接口契约返回 false 而不是抛异常：调用方按返回值提示成败
            return false
        }
    }

    override suspend fun pull(): CloudSyncData {
        try {
            return fullUrl.request({ builder ->
                builder.header("Authorization", Credentials.basic(username, password))
            }) { body ->
                Globals.json.decodeFromString<CloudSyncData>(body.string())
            } ?: CloudSyncData.EMPTY
        } catch (ex: Exception) {
            // 拉取中途被取消不是拉取失败：取消信号原样抛出去，别包成普通失败
            // （690 轮同款问题）
            if (ex is CancellationException) throw ex
            log.e("拉取云端失败", ex)
            throw Exception("拉取云端失败", ex)
        }
    }
}