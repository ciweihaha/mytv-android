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
                    groupName = line.split(",", "，").firstOrNull()?.trim()
                } else {
                    // 只按第一个逗号（半角或全角）切分：名字取逗号前，地址取逗号后全部；
                    // 旧代码 split 后取 res[1]，地址里含逗号（如查询串 ?token=a,b）
                    // 会被截断；地址为空的行直接跳过（旧代码会加一条播不了的空地址）
                    val sepIndex = line.indexOfFirst { it == ',' || it == '，' }
                    if (sepIndex < 0) return@forEach
                    val name = line.substring(0, sepIndex).trim()
                    val urlPart = line.substring(sepIndex + 1)
                    if (urlPart.isBlank()) return@forEach
                    channelList.addAll(urlPart.split("#").map { url ->
                        if (url.startsWith("webview://")) {
                            IptvParser.ChannelItem(
                                name = name,
                                groupName = groupName ?: "其他",
                                url = url.trim(),
                                hybridType = IptvParser.ChannelItem.HybridType.WebView,
                            )
                        } else {
                            IptvParser.ChannelItem(
                                name = name,
                                groupName = groupName ?: "其他",
                                url = url.trim(),
                            )
                        }
                    })
                }
            }

            channelList
        }
}