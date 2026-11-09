package top.yogiczy.mytv.core.data.repositories.epg.fetcher

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.ResponseBody
import java.util.zip.GZIPInputStream

/**
 * 节目单xml.gz数据获取
 */
class XmlGzEpgFetcher : EpgFetcher {
    override fun isSupport(url: String): Boolean {
        return url.epgUrlPath().endsWith(".gz", ignoreCase = true)
    }

    override suspend fun fetch(body: ResponseBody) = withContext(Dispatchers.IO) {
        val stream = body.byteStream().buffered()
        // 按魔数判断：有的服务端对 .gz 地址返回明文 XML（或错误页），
        // 直接 GZIPInputStream 会抛“Not in GZIP format”，整个节目单
        // 加载失败；非 gzip 内容原样返回，让后续 XML 解析处理
        stream.mark(2)
        val first = stream.read()
        val second = stream.read()
        stream.reset()
        if (first == 0x1f && second == 0x8b) GZIPInputStream(stream) else stream
    }
}