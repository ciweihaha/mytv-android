package top.yogiczy.mytv.core.data.repositories.iptv.parser

/**
 * 直播源文件常带 UTF-8 BOM（Windows 记事本默认保存格式）或开头的空行，
 * 会导致 M3U 识别（startsWith("#EXTM3U")）失败，整条源被当成未知格式。
 * 解析前统一去掉。
 */
internal fun String.stripPlaylistBom(): String {
    return removePrefix("\uFEFF").trimStart()
}

/**
 * 取 #EXTINF 行中的显示名：第一个不在双引号内的逗号之后的部分。
 * 旧代码用 split(",").last()，频道名本身含逗号（如 CCTV-1,高清）时只剩
 * 尾段；直接取第一个逗号则会在属性值含逗号（如 tvg-name="A,B"）时截错。
 * 无逗号时返回整行（与旧行为一致）。
 */
internal fun String.extinfDisplayName(): String {
    var inQuotes = false
    for (i in indices) {
        when (this[i]) {
            '"' -> inQuotes = !inQuotes
            ',' -> if (!inQuotes) return substring(i + 1).trim()
        }
    }
    return trim()
}
