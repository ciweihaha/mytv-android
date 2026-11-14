package top.yogiczy.mytv.tv.sync.repositories

import kotlinx.coroutines.CancellationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.RequestBody.Companion.toRequestBody
import top.yogiczy.mytv.core.data.network.requestStrict
import top.yogiczy.mytv.core.data.utils.Globals
import top.yogiczy.mytv.core.data.utils.Loggable
import top.yogiczy.mytv.tv.sync.CloudSyncData

class GiteeGistSyncRepository(
    private val gistId: String,
    private val token: String,
) : CloudSyncRepository, Loggable("GiteeGistSyncRepository") {
    override suspend fun push(data: CloudSyncData): Boolean {
        try {
            return "https://gitee.com/api/v5/gists/${gistId}".requestStrict({ builder ->
                val body = "{ \"access_token\": \"$token\", \"files\": { \"all_configs.json\": ${
                    Globals.json.encodeToString(mapOf("content" to Globals.json.encodeToString(data)))
                }}}"

                builder
                    .addHeader("Content-Type", "application/json")
                    .patch(body.toRequestBody())
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
            return "https://gitee.com/api/v5/gists/${gistId}?access_token=${token}".request { body ->
                val res = Globals.json.parseToJsonElement(body.string()).jsonObject
                val file = res["files"]?.jsonObject?.get("all_configs.json")?.jsonObject

                file?.get("truncated")?.jsonPrimitive?.booleanOrNull?.let { nnTruncated ->
                    if (nnTruncated) {
                        file["raw_url"]?.jsonPrimitive?.content?.let { rawUrl ->
                            // 大文件走 raw 链接拉取：同样用统一的 OkHttp 请求，
                            // 原先 URL.readText() 无超时，服务端假死时会无限挂住
                            val text = rawUrl.requestStrict { body -> body.string() }
                                ?: throw Exception("拉取云端失败：服务器返回空数据")
                            Globals.json.decodeFromString<CloudSyncData>(text)
                        }
                    } else {
                        file["content"]?.jsonPrimitive?.content?.let {
                            Globals.json.decodeFromString<CloudSyncData>(it)
                        }
                    }
                }?.let { syncData ->
                    res["description"]?.jsonPrimitive?.content?.let {
                        syncData.copy(description = it)
                    } ?: syncData
                }
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