package top.yogiczy.mytv.core.util.utils

import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import java.util.Locale
import java.util.regex.Pattern
import kotlin.math.max

fun Long.humanizeMs(): String {
    return when (this) {
        in 0..<60_000 -> "${this / 1000}秒"
        in 60_000..<3_600_000 -> "${this / 60_000}分钟"
        in 3_600_000..<86_400_000 -> "${this / 3_600_000}小时"
        else -> "${this / 86_400_000}天"
    }
}

fun Long.humanizeBufferNum(): String {
    return "${this.humanizeMs()} | ${(this * 0.03).toLong()}f"
}

fun Long.humanizeBytes(): String {
    return when (this) {
        in 0..<1024 -> "${this}B"
        in 1024..<1048576 -> "${this / 1024}KB"
        in 1048576..<1073741824 -> "${this / 1048576}MB"
        else -> "${this / 1073741824}GB"
    }
}

fun String.isIPv6(): Boolean {
    return ipv6UrlPattern.matcher(this).matches()
}

// 判 IPv6 的正则：提到文件顶层只编译一次，之前每次调用都重新编译；
// 4 处调用全在界面重画路径上（频道列表项、直播频道信息、线路项）。
// Pattern 不可变、线程安全，每次调用新生成 Matcher。
private val ipv6UrlPattern = Pattern.compile(
    "^((http|https)://)?(\\[[0-9a-fA-F:]+])(:[0-9]+)?(/.*)?$"
)

fun String.compareVersion(version2: String): Int {
    fun parseVersion(version: String): Pair<List<Int>, String?> {
        // 防御：版本号可能带 v 前缀、非数字段（如自定义更新渠道的奇怪 tag）；
        // 原先 toInt() 直接抛异常，而 AboutScreen 里比较时没有任何保护，
        // 打开关于页就闪退。非数字段按 0 计，永不抛异常
        val cleaned = version.trim().removePrefix("v").removePrefix("V")
        val mainParts = cleaned.split("-", limit = 2)
        val versionNumbers = mainParts[0].split(".").map { it.toIntOrNull() ?: 0 }
        val preReleaseLabel = mainParts.getOrNull(1)
        return versionNumbers to preReleaseLabel
    }

    fun comparePreRelease(label1: String?, label2: String?): Int {
        if (label1 == null && label2 == null) return 0
        if (label1 == null) return 1
        if (label2 == null) return -1

        return label1.compareTo(label2)
    }

    val (v1, preRelease1) = parseVersion(this)
    val (v2, preRelease2) = parseVersion(version2)
    val maxLength = maxOf(v1.size, v2.size)

    for (i in 0 until maxLength) {
        val part1 = v1.getOrElse(i) { 0 }
        val part2 = v2.getOrElse(i) { 0 }
        if (part1 > part2) return 1
        if (part1 < part2) return -1
    }

    return comparePreRelease(preRelease1, preRelease2)
}

fun String.urlHost(): String {
    return this.split("://").getOrElse(1) { "" }.split("/").firstOrNull() ?: this
}

fun Context.actionView(url: String) {
    runCatching {
        val intent = Intent(Intent.ACTION_VIEW, url.toUri())
        startActivity(intent)
    }
}

fun String.toHeaders(): Map<String, String> {
    // 逐行解析，跳过空行/非法行：原先用 associate + runCatching，一行非法
    // （粘贴时常见的末尾空行也算）就丢掉全部 headers，需要鉴权的流播不了；
    // 与 headersValid() 用同一条“合法行”判定
    return lines().mapNotNull { line ->
        val parts = line.split(":", limit = 2)
        if (parts.size == 2 && parts[0].isNotBlank() && parts[1].isNotBlank()) {
            parts[0].trim() to parts[1].trim()
        } else {
            null
        }
    }.toMap()
}

fun String.headersValid(): Boolean {
    if (isBlank()) return true

    // 空行不算非法（粘贴末尾换行很常见）：只校验非空行
    return lines().filter { it.isNotBlank() }.all { line ->
        val parts = line.split(":", limit = 2)
        parts.size == 2 && parts[0].isNotBlank() && parts[1].isNotBlank()
    }
}

fun Int.humanizeBitrate(base: Int = 1000): String {
    return when (val value = max(0, this)) {
        in 0..<base -> "${value}bps"
        in base..<base * base -> "${value / base}Kbps"

        else -> "${String.format(Locale.getDefault(), "%.2f", value.toFloat() / base / base)}Mbps"
    }
}

fun Int.humanizeAudioChannels(): String {
    return when (this) {
        1 -> "单声道"
        2 -> "立体声"
        3 -> "2.1 声道"
        4 -> "4.0 四声道"
        5 -> "5.0 环绕声"
        6 -> "5.1 环绕声"
        7 -> "6.1 环绕声"
        8 -> "7.1 环绕声"
        10 -> "7.1.2 杜比全景声"
        12 -> "7.1.4 杜比全景声"
        else -> "${this}声道"
    }
}

fun String.humanizeLanguage(): String {
    return when (this.lowercase()) {
        "zh" -> "中文"
        "chs" -> "简体中文"
        "en" -> "英语"
        else -> this
    }
}

fun String.ensureSuffix(suffix: String): String {
    return if (endsWith(suffix)) this else "$this$suffix"
}