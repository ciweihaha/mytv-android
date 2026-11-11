package top.yogiczy.mytv.core.data.entities.channel

import kotlinx.serialization.Serializable

/**
 * 频道
 */
@Serializable
data class Channel(
    /**
     * 频道名称
     */
    val name: String = "",

    /**
     * 标准频道名称
     */
    val standardName: String = name,

    /**
     * 节目单名称，用于查询节目单
     */
    val epgName: String = "",

    /**
     * 节目单频道 id（直播源 tvg-id）：与节目单 XMLTV channel 的 id
     * 是同一套；频道名写法和节目单不一样时，节目单按名查不到，靠它
     * 兜底匹配；没有 tvg-id 的频道记空串，走原来的按名查
     */
    val tvgId: String = "",

    /**
     * 线路列表
     */
    val lineList: ChannelLineList = ChannelLineList(listOf(ChannelLine.EXAMPLE)),

    /**
     * 台标
     */
    val logo: String? = null,

    /**
     * 频道号
     */
    val index: Int = -1,
) {
    companion object {
        val EXAMPLE = Channel(
            name = "CCTV-1 法治与法治",
            epgName = "cctv1",
            lineList = ChannelLineList(
                listOf(
                    ChannelLine("http://dbiptv.sn.chinamobile.com/PLTV/88888890/224/3221226231/index.m3u8"),
                    ChannelLine(
                        "http://[2409:8087:5e01:34::20]:6610/ZTE_CMS/00000001000000060000000000000131/index.m3u8?IAS",
                        httpUserAgent = "aptv"
                    ),
                )
            ),
            logo = "https://live.fanmingming.com/tv/CCTV1.png",
        )

        val EMPTY = Channel(
            // “无频道”哨兵必须没有线路：默认 lineList 自带一条示例线路，
            // 不传空列表时 EMPTY 也能播——分组全隐藏（过滤后列表为空）时
            // 直播页/多画面会拿它当真频道，把写死的示例地址播出来；
            // 三处调用方（换台守卫、多画面取线、频道信息取线）注释里
            // 都已假定 EMPTY 没有可播线路，这里才对得上
            lineList = ChannelLineList(),
        )
    }

    val no: String
        get() = (index + 1).toString().padStart(2, '0')

    fun isEmptyOrElse(defaultValue: () -> Channel) = if (this == EMPTY) defaultValue() else this

    override fun equals(other: Any?): Boolean {
        if (other !is Channel) return false

        return name == other.name && lineList == other.lineList
    }

    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + lineList.hashCode()
        return result
    }
}
