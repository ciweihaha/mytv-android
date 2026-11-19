package top.yogiczy.mytv.tv.voice

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import top.yogiczy.mytv.core.data.utils.Logger
import java.util.concurrent.TimeUnit

/** 翻译服务商：只接这两家（用户 2026-11-19 11:45 定版：Azure 默认 +
 * Cloudflare；谷歌/火山/百度都不加）。Azure 要用户自己去微软免费开
 * Key；Cloudflare 用他自己的账号 ID + Token。 */
private val log = Logger.create("FreeTranslator")

enum class AiTranslateProvider { AZURE, CLOUDFLARE }

/** 字幕语言：中文不用翻，英文才翻，其他（法语/德语/日语等）不翻 */
enum class SubtitleLanguage { CHINESE, ENGLISH, UNKNOWN }

/** 判断一段识别文字是什么语言（纯逻辑，可单测）。
 * 含中文字符就是中文；主要是拉丁字母就是英文；太短/全是符号/数字
 * 认不出来，回 UNKNOWN（不翻）。 */
fun detectSubtitleLanguage(text: String): SubtitleLanguage {
    var cjk = 0
    var latin = 0
    for (ch in text) {
        when {
            ch in '\u4E00'..'\u9FFF' || ch in '\u3400'..'\u4DBF' -> cjk++
            ch in 'a'..'z' || ch in 'A'..'Z' -> latin++
        }
    }
    return when {
        cjk > 0 -> SubtitleLanguage.CHINESE
        latin >= 3 -> SubtitleLanguage.ENGLISH
        else -> SubtitleLanguage.UNKNOWN
    }
}

/** 免费翻译接口：只翻文字（ASR 已经把声音变文字了）。失败回 null，
 * 调用方跳过那行、只显示原文，绝不抛错、绝不重试卡字幕。 */
interface FreeTranslator {
    /** 把英文翻成中文；失败回 null */
    suspend fun translate(text: String): String?
}

/** 微软 Azure Translator（免费层 F0，每月 200 万字符免费）。
 * Key 用户自己去微软免费开，填在设置里。 */
class AzureTranslator(
    private val apiKey: String,
    private val region: String = "",
) : FreeTranslator {
    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    override suspend fun translate(text: String): String? = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) return@withContext null
        try {
            val body = JSONArray().put(JSONObject().put("Text", text)).toString()
                .toRequestBody("application/json".toMediaType())
            val reqBuilder = Request.Builder()
                .url("https://api.cognitive.microsofttranslator.com/translate?api-version=3.0&from=en&to=zh-Hans")
                .header("Ocp-Apim-Subscription-Key", apiKey)
                .header("Content-Type", "application/json")
                .post(body)
            // region 只有用户填了才带（免费层有些区不用填）
            if (region.isNotBlank()) reqBuilder.header("Ocp-Apim-Subscription-Region", region)
            client.newCall(reqBuilder.build()).execute().use { resp ->
                if (!resp.isSuccessful) {
                    log.i("AI 字幕：Azure 翻译失败 HTTP ${resp.code}")
                    return@withContext null
                }
                val json = resp.body?.string() ?: return@withContext null
                // [{"translations":[{"text":"...","to":"zh-Hans"}]}]
                JSONArray(json).optJSONObject(0)
                    ?.optJSONArray("translations")
                    ?.optJSONObject(0)
                    ?.optString("text")
                    ?.takeIf { it.isNotBlank() }
            }
        } catch (ex: Exception) {
            log.i("AI 字幕：Azure 翻译异常：${ex.message}")
            null
        }
    }
}

/** Cloudflare Workers AI（免费层，模型 @cf/meta/m2m100-1.2b）。
 * 用用户的 Cloudflare 账号 ID + API Token（有 Workers AI 权限的）。
 * 没有 Key 也能走这条路的前提是用户在 Cloudflare 上有账号；
 * 没配好就回 null、跳过翻译，不影响字幕本身。 */
class CloudflareTranslator(
    private val accountId: String,
    private val apiToken: String,
) : FreeTranslator {
    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    override suspend fun translate(text: String): String? = withContext(Dispatchers.IO) {
        if (accountId.isBlank() || apiToken.isBlank()) return@withContext null
        try {
            val body = JSONObject()
                .put("text", text)
                .put("source_lang", "en")
                .put("target_lang", "zh")
                .toString()
                .toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url("https://api.cloudflare.com/client/v4/accounts/$accountId/ai/run/@cf/meta/m2m100-1.2b")
                .header("Authorization", "Bearer $apiToken")
                .header("Content-Type", "application/json")
                .post(body)
                .build()
            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) {
                    log.i("AI 字幕：Cloudflare 翻译失败 HTTP ${resp.code}")
                    return@withContext null
                }
                val json = resp.body?.string() ?: return@withContext null
                // {"success":true,"result":{"translated_text":"..."}}
                JSONObject(json).optJSONObject("result")
                    ?.optString("translated_text")
                    ?.takeIf { it.isNotBlank() }
            }
        } catch (ex: Exception) {
            log.i("AI 字幕：Cloudflare 翻译异常：${ex.message}")
            null
        }
    }
}
