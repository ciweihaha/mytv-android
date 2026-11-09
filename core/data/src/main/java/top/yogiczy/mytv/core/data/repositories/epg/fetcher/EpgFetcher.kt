package top.yogiczy.mytv.core.data.repositories.epg.fetcher

import okhttp3.ResponseBody
import java.io.InputStream

/**
 * 节目单数据获取接口
 */
interface EpgFetcher {
    /**
     * 是否支持该格式
     */
    fun isSupport(url: String): Boolean

    /**
     * 获取节目单
     */
    suspend fun fetch(body: ResponseBody): InputStream

    companion object {
        val instances = listOf(
            XmlEpgFetcher(),
            XmlGzEpgFetcher(),
            DefaultEpgFetcher(),
        )
    }
}

/**
 * 取 URL 去掉查询串与锚点后的路径部分，供后缀判断用。
 * 节目单地址常带 token 查询串（如 epg.xml.gz?token=xxx），直接 endsWith
 * 会误判为不支持，走缺省解析后节目单为空。
 */
internal fun String.epgUrlPath(): String {
    return substringBefore("?").substringBefore("#")
}