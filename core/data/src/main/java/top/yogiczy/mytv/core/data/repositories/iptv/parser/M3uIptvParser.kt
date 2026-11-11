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
        // 属性名不区分大小写：有源把属性写成 TVG-NAME / Tvg-Name /
        // GROUP-TITLE；区分大小写时 tvg-name、group-title 全取不到，
        // 频道名退到逗号后、节目单名丢了、分组退到“其他”
        private val catchupRegex = Regex("catchup=\"(.+?)\"", RegexOption.IGNORE_CASE)
        private val catchupSourceRegex = Regex("catchup-source=\"(.+?)\"", RegexOption.IGNORE_CASE)
        private val tvgNameRegex = Regex("tvg-name=\"(.*?)\"", RegexOption.IGNORE_CASE)
        private val tvgIdRegex = Regex("tvg-id=\"(.*?)\"", RegexOption.IGNORE_CASE)
        private val groupTitleRegex = Regex("group-title=\"(.+?)\"", RegexOption.IGNORE_CASE)
        private val tvgLogoRegex = Regex("tvg-logo=\"(.+?)\"", RegexOption.IGNORE_CASE)
        private val httpUserAgentRegex = Regex("http-user-agent=\"(.+?)\"", RegexOption.IGNORE_CASE)
        private val httpReferrerRegex = Regex("http-referrer=\"(.+?)\"", RegexOption.IGNORE_CASE)
        private val httpOriginRegex = Regex("http-origin=\"(.+?)\"", RegexOption.IGNORE_CASE)
        private val xTvgUrlRegex = Regex("x-tvg-url=\"(.*?)\"", RegexOption.IGNORE_CASE)
        private val urlTvgRegex = Regex("url-tvg=\"(.*?)\"", RegexOption.IGNORE_CASE)
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
            lines.forEach { rawLine ->
                // 行首尾空白先去掉：手写/带缩进的源里 #EXTINF、#KODIPROP 行带前导
                // 空格时，旧实现认不出标记、会把整行当 URL 处理——#EXTINF 行带缩
                // 进会让整条频道直接丢掉，#KODIPROP 行带缩进还会多出一路垃圾地址
                val line = rawLine.trim()
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
                    // tvg-id 和节目单 channel 的 id 是同一套：名字写法不同
                    // 时（直播源“CCTV综合”、节目单“CCTV-1 综合”），节目
                    // 单按名查不到，靠它兜底；没有 tvg-id 的行记空串，走原
                    // 来的按名查；属性名同样不区分大小写
                    val tvgId = tvgIdRegex.find(line)?.groupValues?.get(1)?.trim().orEmpty()
                    val groupNames =
                        groupTitleRegex.find(line)?.groupValues?.get(1)?.split(";")
                            ?.map { it.trim() }
                            // 空分段不要：group-title 里多余的分号（`;;`、尾部
                            // `;`）会切出空串，频道会被分进一个名字空白的分
                            // 组、选台页多一个空白页签；全空时退回“其他”
                            ?.filter { it.isNotBlank() }
                            ?.ifEmpty { listOf("其他") }
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
                    logger.v("解析结果: name=$name, epgName=$epgName, tvgId=$tvgId, groupNames=$groupNames, logo=$logo, httpUserAgent=$httpUserAgent, httpReferrer=$httpReferrer, httpOrigin=$httpOrigin, playbackType=$playbackType, playbackFormat=$playbackFormat")

                    addedChannels = groupNames.map { groupName ->
                        IptvParser.ChannelItem(
                            name = name,
                            epgName = epgName,
                            tvgId = tvgId,
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
        // 行首先 trim：parse() 里每行都 trim，手写带缩进的源 #EXTM3U 行
        // 本身带空格时也能解析出频道；这里不 trim 就找不到首行、返回
        // null，源里明明写了 x-tvg-url，节目单却加载不出来
        return lines.firstOrNull { it.trim().startsWith("#EXTM3U") }?.let { defLine ->
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