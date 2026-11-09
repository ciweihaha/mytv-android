package top.yogiczy.mytv.core.data.repositories.iptv.parser

/**
 * 直播源文件常带 UTF-8 BOM（Windows 记事本默认保存格式）或开头的空行，
 * 会导致 M3U 识别（startsWith("#EXTM3U")）失败，整条源被当成未知格式。
 * 解析前统一去掉。
 */
internal fun String.stripPlaylistBom(): String {
    return removePrefix("\uFEFF").trimStart()
}
