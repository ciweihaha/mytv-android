package top.yogiczy.mytv.tv.sync.repositories

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.RequestBody.Companion.toRequestBody
import top.yogiczy.mytv.core.data.network.request
import top.yogiczy.mytv.core.data.utils.Globals
import top.yogiczy.mytv.core.data.utils.Loggable
import top.yogiczy.mytv.tv.sync.CloudSyncData

class GithubGistSyncRepository(
    private val gistId: String,
    private val token: String,
) : CloudSyncRepository, Loggable("GithubGistSyncRepository") {
    override suspend fun push(data: CloudSyncData): Boolean {
        try {
            return "https://api.github.com/gists/${gistId}".request({ builder ->
                builder
                    .header("Authorization", "Bearer $token")
                    .header("X-GitHub-Api-Version", "2022-11-28")
                    .patch(
                        Globals.json.encodeToString(
                            mapOf(
                                "files" to mapOf(
                                    "all_configs.json" to mapOf(
                                        "content" to Globals.json.encodeToString(data)
                                    )
                                )
                            )
                        ).toRequestBody()
                    )
            }) { _ -> true } ?: false
        } catch (ex: Exception) {
            log.e("推送云端失败", ex)
            // 按接口契约返回 false 而不是抛异常：调用方按返回值提示成败
            false
        }
    }

    override suspend fun pull(): CloudSyncData {
        try {
            return "https://api.github.com/gists/${gistId}".request({ builder ->
                builder
                    .header("Authorization", "Bearer $token")
                    .header("X-GitHub-Api-Version", "2022-11-28")
            }) { body ->
                val res = Globals.json.parseToJsonElement(body.string()).jsonObject
                val file = res["files"]?.jsonObject?.get("all_configs.json")?.jsonObject

                file?.get("truncated")?.jsonPrimitive?.booleanOrNull?.let { nnTruncated ->
                    if (nnTruncated) {
                        file["raw_url"]?.jsonPrimitive?.content?.let { rawUrl ->
                            // 大文件走 raw 链接拉取：同样用统一的 OkHttp 请求，
                            // 原先 URL.readText() 无超时，服务端假死时会无限挂住
                            val text = rawUrl.request { body -> body.string() }
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
            log.e("拉取云端失败", ex)
            throw Exception("拉取云端失败", ex)
        }
    }
}