package top.yogiczy.mytv.core.data.repositories.epg.fetcher

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.ResponseBody
import java.io.InputStream

/**
 * 缺省节目单数据获取
 */
class DefaultEpgFetcher : EpgFetcher {
    override fun isSupport(url: String): Boolean {
        return true
    }

    override suspend fun fetch(body: ResponseBody): InputStream =
        withContext(Dispatchers.IO) {
            // 兜底：地址没有 .xml/.gz 后缀时也把内容原样交给 XML 解析——
            // 很多节目单地址是不带后缀的接口地址（如带 token 查询串的 API），
            // 旧实现直接返回一段空 XML，节目单永远为空；原样返回后是 XML
            // 就能正常解析，不是 XML 时解析失败走旧缓存兜底，不比原来差
            body.byteStream()
        }
}