package top.yogiczy.mytv.core.data.repositories.iptv.parser

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.yogiczy.mytv.core.data.utils.Logger

/**
 * m3u直播源解析
 */
class M3uIptvParser : IptvParser {

    private val logger = Logger.create("M3uIptvParser")

    // 属性正则只编译一次：原来每行每个属性都 new 一个 Regex，
    // 大源（几千个频道）一次解析要编译几万次；现在整个类只编一次
    private companion object {
        private val catchupRegex = Regex("catchup=\"(.+?)\"")
        private val catchupSourceRegex = Regex("catchup-source=\"(.+?)\"")
        private val tvgNameRegex = Regex("tvg-name=\"(.*?)\"")
        private val groupTitleRegex = Regex("group-title=\"(.+?)\"")
        private val tvgLogoRegex = Regex("tvg-logo=\"(.+?)\"")
        private val httpUserAgentRegex = Regex("http-user-agent=\"(.+?)\"")
        private val httpReferrerRegex = Regex("http-referrer=\"(.+?)\"")
        private val httpOriginRegex = Regex("http-origin=\"(.+?)\"")
        private val xTvgUrlRegex = Regex("x-tvg-url=\"(.*?)\"")
        private val urlTvgRegex = Regex("url-tvg=\"(.*?)\"")
    }

    override fun isSupport(url: String, data: String): Boolean {
        return data.stripPlaylistBom().startsWith("#EXTM3U")
    }

    override suspend fun parse(data: String) =
        withContext(Dispatchers.Default) {
            val lines = data.stripPlaylistBom().split("\r\n", "\n")
            val channelList = mutableListOf<IptvParser.ChannelItem>()
            var globalPlaybackType: Int? = null
            var globalPlaybackFormat: String? = null
            var addedChannels: List<IptvParser.ChannelItem> = listOf()
            lines.forEach { line ->
                if (line.isBlank()) return@forEach
                if (line.startsWith("#EXTM3U")) {
                    // 解析扩展信息
                    val playbackTypeString =
                        catchupRegex.find(line)?.groupValues?.get(1)?.trim()
                            ?.ifBlank { null } ?: null
                    if (playbackTypeString != null) {
                        logger.i("检测到全局回放类型: $playbackTypeString")
                        globalPlaybackType = when (playbackTypeString.lowercase()) {
                            "disabled" -> null
                            "default" -> 0
                            "append" -> 1
                            // "shift" -> 2
                            // "flussonic" -> 3
                            // "xtream codes" -> 4
                            else -> null
                        }
                        if (globalPlaybackType != null) {
                            globalPlaybackFormat = catchupSourceRegex.find(line)?.groupValues?.get(1)?.trim()
                            ?.ifBlank { null } ?: null
                        }
                    } 
                } else if (line.startsWith("#EXTINF")) {
                    // 残缺行：既没有逗号后的显示名、又没有 tvg-name，直接跳过，
                    // 不把整行当频道名；同时清空待填 URL 的占位，避免下一行
                    // URL 误填到上一条频道上造成重复
                    val displayName = line.extinfDisplayName()?.ifBlank { null }
                    val tvgName =
                        tvgNameRegex.find(line)?.groupValues?.get(1)?.trim()
                            ?.ifBlank { null }
                    val name = displayName ?: tvgName
                    if (name == null) {
                        addedChannels = emptyList()
                        return@forEach
                    }
                    val epgName = tvgName ?: name
                    val groupNames =
                        groupTitleRegex.find(line)?.groupValues?.get(1)?.split(";")
                            ?.map { it.trim() }
                            ?: listOf("其他")
                    val logo = tvgLogoRegex.find(line)?.groupValues?.get(1)?.trim()
                    val httpUserAgent =
                        httpUserAgentRegex.find(line)?.groupValues?.get(1)?.trim()
                    val httpReferrer =
                        httpReferrerRegex.find(line)?.groupValues?.get(1)?.trim()
                    val httpOrigin =
                        httpOriginRegex.find(line)?.groupValues?.get(1)?.trim()
                    var playbackType: Int? = null
                    var playbackFormat: String? = null
                    if(globalPlaybackType != null){
                        playbackType = globalPlaybackType
                        playbackFormat = globalPlaybackFormat
                    } else {
                        val playbackTypeString =
                            catchupRegex.find(line)?.groupValues?.get(1)?.trim()
                                ?.ifBlank { null } ?: null
                        if (playbackTypeString != null) {
                            logger.i("检测到回放类型: $playbackTypeString")
                            playbackType = when (playbackTypeString.lowercase()) {
                                "disabled" -> null
                                "default" -> 0
                                "append" -> 1
                                // "shift" -> 2
                                // "flussonic" -> 3
                                // "xtream codes" -> 4
                                else -> null
                            }
                            if (playbackType != null) {
                                playbackFormat = catchupSourceRegex.find(line)?.groupValues?.get(1)?.trim()
                                ?.ifBlank { null } ?: null
                            }
                        } 
                    }
                    // 逐频道日志只写 logcat（v 级不进应用内日志历史）：
                    // 原先每频道一条 INFO，应用内日志只留 100 条，一次刷新
                    // 就把历史全顶成解析流水，真正的报错全被挤出去
                    logger.v("解析结果: name=$name, epgName=$epgName, groupNames=$groupNames, logo=$logo, httpUserAgent=$httpUserAgent, httpReferrer=$httpReferrer, httpOrigin=$httpOrigin, playbackType=$playbackType, playbackFormat=$playbackFormat")

                    addedChannels = groupNames.map { groupName ->
                        IptvParser.ChannelItem(
                            name = name,
                            epgName = epgName,
                            groupName = groupName,
                            url = "",
                            logo = logo,
                            httpUserAgent = httpUserAgent,
                            httpReferrer = httpReferrer,
                            httpOrigin = httpOrigin,
                            playbackType = playbackType,
                            playbackFormat = playbackFormat,
                        )
                    }
                } else {
                    if (line.startsWith("#KODIPROP:inputstream.adaptive.manifest_type")) {
                        addedChannels =
                            addedChannels.map { it.copy(manifestType = line.substringAfter("=")) }
                    } else if (line.startsWith("#KODIPROP:inputstream.adaptive.license_type")) {
                        addedChannels =
                            addedChannels.map { it.copy(licenseType = line.substringAfter("=")) }
                    } else if (line.startsWith("#KODIPROP:inputstream.adaptive.license_key")) {
                        addedChannels =
                            addedChannels.map { it.copy(licenseKey = line.substringAfter("=")) }
                    } else if (line.startsWith("#EXTVLCOPT:http-origin")) {
                        addedChannels =
                            addedChannels.map { it.copy(httpOrigin = line.substringAfter("=")) }
                    } else if (line.startsWith("#EXTVLCOPT:http-referrer")) {
                        addedChannels =
                            addedChannels.map { it.copy(httpReferrer = line.substringAfter("=")) }
                    } else if (line.startsWith("#EXTVLCOPT:http-user-agent")) {
                        addedChannels =
                            addedChannels.map { it.copy(httpUserAgent = line.substringAfter("=")) }                      
                    } else if (line.startsWith("#") || line.startsWith("//")) {
                        return@forEach
                    } else{
                        // 记录URL行（v 级：每行一条 INFO 会冲掉 100 条的应用内日志历史）
                        logger.v("解析URL行: $line")
                        val trimmedUrl = line.trim()
                        
                        // 检查是否是webview链接
                        if (trimmedUrl.startsWith("webview://")) {
                            logger.i("检测到WebView链接: $trimmedUrl")
                            logger.i("将URL的hybridType设置为WebView")
                            channelList.addAll(addedChannels.map { 
                                it.copy(
                                    url = trimmedUrl, 
                                    hybridType = IptvParser.ChannelItem.HybridType.WebView
                                ) 
                            })
                        } else {
                            logger.v("普通URL: $trimmedUrl, hybridType=None")
                            channelList.addAll(addedChannels.map { it.copy(url = trimmedUrl) })
                        }
                    }
                }
            }

            channelList
        }

    override suspend fun getEpgUrl(data: String): String? {
        val lines = data.stripPlaylistBom().split("\r\n", "\n")
        return lines.firstOrNull { it.startsWith("#EXTM3U") }?.let { defLine ->
            // 取首个非空地址：x-tvg-url 常写多个地址，首项为空时不能直接返回
            // 空串（会生成空地址的 EPG 源导致节目单全空），要跳过并继续找 url-tvg
            listOf(xTvgUrlRegex, urlTvgRegex).firstNotNullOfOrNull { regex ->
                regex.find(defLine)?.groupValues?.get(1)
                    ?.split(",")
                    ?.firstOrNull { it.isNotBlank() }
                    ?.trim()
            }
        }
    }
}