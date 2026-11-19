package top.yogiczy.mytv.tv.sync.repositories

import kotlinx.coroutines.CancellationException
import kotlinx.serialization.encodeToString
import okhttp3.RequestBody.Companion.toRequestBody
import top.yogiczy.mytv.core.data.network.HttpStatusException
import top.yogiczy.mytv.core.data.network.requestStrict
import top.yogiczy.mytv.core.data.utils.Globals
import top.yogiczy.mytv.core.data.utils.Loggable
import top.yogiczy.mytv.tv.sync.CloudSyncData

/**
 * Cloudflare 云同步：后端是用户自己的 Worker（mytv-sync）+ KV。
 * 存的数据和 GitHub Gist 同款——CloudSyncData 的 JSON 原文
 * （Gist 是包在 all_configs.json 文件里，这里直接存整段 JSON），
 * 所以 Gist 和 Cloudflare 两边的数据格式完全一致。
 *
 * 接口：GET 拉取，PUT 推送；请求头 X-Sync-Key 做鉴权。
 * 云端还没存过数据时 GET 回 404，按“无云端数据”处理，不算失败。
 */
class CloudflareSyncRepository(
    private val url: String,
    private val syncKey: String,
) : CloudSyncRepository, Loggable("CloudflareSyncRepository") {
    override suspend fun push(data: CloudSyncData): Boolean {
        try {
            return url.requestStrict({ builder ->
                builder
                    .header("X-Sync-Key", syncKey)
                    .put(Globals.json.encodeToString(data).toRequestBody())
            }) { body ->
                // 推送成功也要关响应体：body 不读不关，连接回不了共用池，
                // 每次推送都会漏一个连接（Gist 仓库同款问题）
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
            return url.requestStrict({ builder ->
                builder.header("X-Sync-Key", syncKey)
            }) { body ->
                val text = body.string()
                if (text.isBlank()) CloudSyncData.EMPTY
                else Globals.json.decodeFromString<CloudSyncData>(text)
            } ?: CloudSyncData.EMPTY
        } catch (ex: HttpStatusException) {
            // 404 = 云端还没存过数据：不是失败，是“无云端数据”
            if (ex.code == 404) return CloudSyncData.EMPTY
            log.e("拉取云端失败", ex)
            throw Exception("拉取云端失败", ex)
        } catch (ex: Exception) {
            // 拉取中途被取消不是拉取失败：取消信号原样抛出去，别包成普通失败
            // （690 轮同款问题）
            if (ex is CancellationException) throw ex
            log.e("拉取云端失败", ex)
            throw Exception("拉取云端失败", ex)
        }
    }
}
