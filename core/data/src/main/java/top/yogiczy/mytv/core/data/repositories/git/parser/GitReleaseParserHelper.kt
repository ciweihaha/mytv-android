package top.yogiczy.mytv.core.data.repositories.git.parser

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * 发行版解析的防御性小工具。
 *
 * 上游接口返回的 JSON 字段缺失、附件为空、附件顺序变化时，旧代码会直接抛
 * IndexOutOfBoundsException / NPE，更新检查只会得到一句误导性的
 * “请检查网络连接”。这里把这类问题转成带明确原因的 IllegalStateException，
 * 由 GitRepository 透传给界面。
 */
internal fun JsonElement.requiredObject(): JsonObject {
    return this as? JsonObject ?: throw IllegalStateException("发行版数据不是 JSON 对象")
}

internal fun JsonObject.requiredString(key: String): String {
    val value = (this[key] as? JsonPrimitive)?.contentOrNull
    if (value.isNullOrBlank()) throw IllegalStateException("发行版数据不完整：缺少字段 $key")
    return value
}

internal fun JsonObject.requiredArray(key: String): JsonArray {
    return this[key] as? JsonArray ?: throw IllegalStateException("发行版数据不完整：缺少字段 $key")
}

internal fun JsonObject.optionalString(key: String, default: String = ""): String {
    return (this[key] as? JsonPrimitive)?.contentOrNull ?: default
}

/**
 * 去掉版本号前缀（兼容 "v1.0.0" / "V1.0.0" / "1.0.0"）。
 * 旧代码用 substring(1)，tag 没有 v 前缀时会把版本号首字符截掉。
 */
internal fun String.stripVersionPrefix(): String {
    return removePrefix("v").removePrefix("V")
}

/**
 * 从发行版附件列表里挑一个 APK 的下载地址。
 *
 * 命中顺序：同时含全部 preferredKeywords 的 APK -> 含第一个关键词的 APK ->
 * 任意 APK -> 第一个带下载地址的附件。
 * 附件为空或没有任何附件带下载地址时抛 IllegalStateException。
 */
internal fun JsonArray.pickDownloadUrl(vararg preferredKeywords: String): String {
    val candidates = mapNotNull { element ->
        val obj = element as? JsonObject ?: return@mapNotNull null
        val name = obj.optionalString("name")
        val url = obj.optionalString("browser_download_url")
        if (name.isBlank() || url.isBlank()) null else name to url
    }
    if (candidates.isEmpty()) {
        throw IllegalStateException("发行版没有任何可下载的附件")
    }

    val preferred = preferredKeywords.takeIf { it.isNotEmpty() }?.let { keywords ->
        candidates.firstOrNull { (name, _) ->
            name.endsWith(".apk", ignoreCase = true) &&
                keywords.all { keyword -> name.contains(keyword, ignoreCase = true) }
        } ?: candidates.firstOrNull { (name, _) ->
            name.endsWith(".apk", ignoreCase = true) &&
                name.contains(keywords.first(), ignoreCase = true)
        }
    }
    val anyApk = candidates.firstOrNull { (name, _) ->
        name.endsWith(".apk", ignoreCase = true)
    }
    return (preferred ?: anyApk ?: candidates.first()).second
}

/**
 * 取附件列表里第 index 个附件的下载地址（保留旧行为），但附件不足或
 * 字段缺失时抛带明确原因的异常，而不是越界 / NPE。
 */
internal fun JsonArray.downloadUrlAt(index: Int): String {
    val assets = mapNotNull { it as? JsonObject }
    if (assets.isEmpty()) {
        throw IllegalStateException("发行版没有任何附件")
    }
    if (index !in assets.indices) {
        throw IllegalStateException(
            "发行版附件不足：需要第 ${index + 1} 个附件，实际只有 ${assets.size} 个"
        )
    }
    val url = assets[index].optionalString("browser_download_url")
    if (url.isBlank()) {
        throw IllegalStateException("发行版附件缺少下载地址")
    }
    return url
}
