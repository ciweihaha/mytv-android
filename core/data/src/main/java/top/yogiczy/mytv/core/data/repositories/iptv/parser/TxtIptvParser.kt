package top.yogiczy.mytv.core.data.repositories.iptv.parser

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * txt直播源解析
 */
class TxtIptvParser : IptvParser {

    override fun isSupport(url: String, data: String): Boolean {
        return data.contains("#genre#")
    }

    override suspend fun parse(data: String) =
        withContext(Dispatchers.Default) {
            val lines = data.stripPlaylistBom().split("\r\n", "\n")
            val channelList = mutableListOf<IptvParser.ChannelItem>()

            var groupName: String? = null
            lines.forEach { line ->
                if (line.isBlank() || line.startsWith("#") || line.startsWith("//")) return@forEach

                if (line.contains("#genre#")) {
                    // 先去掉 #genre# 标记再取分组名：`,#genre#` 不应产生空分
                    // 组名，`央视 #genre#`（无逗号）不应把标记本身带进分组名；
                    // 名字为空时保留上一个分组
                    groupName = line.replace("#genre#", "").split(",", "，").firstOrNull()?.trim()
                        ?.ifBlank { null } ?: groupName
                } else {
                    // 只按第一个逗号（半角或全角）切分：名字取逗号前，地址取逗号后全部；
                    // 旧代码 split 后取 res[1]，地址里含逗号（如查询串 ?token=a,b）
                    // 会被截断；地址为空的行直接跳过（旧代码会加一条播不了的空地址）
                    val sepIndex = line.indexOfFirst { it == ',' || it == '，' }
                    if (sepIndex < 0) return@forEach
                    // 名字空白的行直接跳过：`,http://…` 会产生一条无名频道，
                    // 在选台列表里就是一条空白的垃圾条目（与 M3U 残缺行同类问题）
                    val name = line.substring(0, sepIndex).trim()
                    if (name.isBlank()) return@forEach
                    val urlPart = line.substring(sepIndex + 1)
                    if (urlPart.isBlank()) return@forEach
                    // 先 trim 再判空：尾部 `#`、连续 `##` 会产生空分段，旧代码
                    // 会加一条播不了的空地址线路；`# webview://…` 带空格时也
                    // 要先 trim 才能认出 WebView
                    channelList.addAll(urlPart.split("#").map { it.trim() }.filter { it.isNotBlank() }.map { url ->
                        if (url.startsWith("webview://")) {
                            IptvParser.ChannelItem(
                                name = name,
                                groupName = groupName ?: "其他",
                                url = url,
                                hybridType = IptvParser.ChannelItem.HybridType.WebView,
                            )
                        } else {
                            IptvParser.ChannelItem(
                                name = name,
                                groupName = groupName ?: "其他",
                                url = url,
                            )
                        }
                    })
                }
            }

            channelList
        }
}