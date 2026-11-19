package top.yogiczy.mytv.core.data.repositories.epg

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import top.yogiczy.mytv.core.data.entities.epg.EpgList
import top.yogiczy.mytv.core.data.entities.epg.hasLiveProgramme
import top.yogiczy.mytv.core.data.entities.epgsource.EpgSource
import top.yogiczy.mytv.core.data.network.HttpException
import top.yogiczy.mytv.core.data.network.HttpStatusException
import top.yogiczy.mytv.core.data.network.request
import top.yogiczy.mytv.core.data.repositories.FileCacheRepository
import top.yogiczy.mytv.core.data.repositories.epg.fetcher.EpgFetcher
import top.yogiczy.mytv.core.data.utils.Globals
import top.yogiczy.mytv.core.data.utils.Logger
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.time.measureTimedValue

/**
 * 节目单获取
 */
class EpgRepository(private val source: EpgSource) :
    FileCacheRepository(source.cacheFileName("json")) {

    private val log = Logger.create("EpgRepository")
    private val epgXmlRepository = EpgXmlRepository(source)

    private fun isExpired(lastModified: Long): Boolean {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        return dateFormat.format(System.currentTimeMillis()) != dateFormat.format(lastModified)
    }

    private suspend fun refresh(): String {
        val xml = epgXmlRepository.getXml()
        val epgList = EpgParser.fromXml(xml)
        if (epgList.isEmpty()) throw Exception("获取节目单为空")

        return Globals.json.encodeToString(epgList)
    }

    /**
     * 获取节目单列表
     *
     * forceRefresh（天光 v2.2.33 回镜）：定时重拉（每小时）与回前台重拉
     * 时用。缓存是否“新鲜”只看日期——服务端每天 05:32 左右出新文件、
     * 白天也可能修正，app 常驻不重启时同一天的缓存再新鲜也是旧文件；
     * 强制刷新跳过日期这道关，直接重新拉取。失败时 getOrRefresh 照常
     * 拿旧缓存顶上，不会把节目单刷空
     */
    suspend fun getEpgList(forceRefresh: Boolean = false): EpgList = withContext(Dispatchers.Default) {
        try {
            var xmlJson = getOrRefresh({ lastModified, _ -> forceRefresh || isExpired(lastModified) }) { refresh() }
            var epgList = Globals.json.decodeFromString<EpgList>(xmlJson)

            // 凌晨节目单文件更新前拉的缓存只有昨天节目：日期没过期、但一个
            // 正在播的节目都没有；按过期处理再拉一次——文件更新后能拿到今天的；
            // 更新前再拉也还是旧文件，只多一次请求、不断网不报错
            if (!epgList.hasLiveProgramme()) {
                xmlJson = getOrRefresh({ _, _ -> true }) { refresh() }
                epgList = Globals.json.decodeFromString<EpgList>(xmlJson)
            }

            return@withContext epgList.also { list ->
                log.i("加载节目单（${source.name}）：${list.size}个频道，${list.sumOf { it.programmeList.size }}个节目")
            }
        } catch (ex: Exception) {
            log.e("加载节目单（${source.name}）失败", ex)
            throw ex
        }
    }

    override suspend fun clearCache() {
        epgXmlRepository.clearCache()
        super.clearCache()
    }

    companion object {
        suspend fun clearAllCache() = withContext(Dispatchers.IO) {
            EpgSource.cacheDir.deleteRecursively()
        }
    }
}

/**
 * 节目单xml获取
 */
private class EpgXmlRepository(private val source: EpgSource) :
    FileCacheRepository(source.cacheFileName("xml")) {

    private val log = Logger.create("EpgXmlRepository")

    suspend fun getXml(): InputStream {
        return getOrRefreshInputStream(0) {
            log.i("开始获取节目单（${source.name}）xml: ${source.url}")

            try {
                val t = measureTimedValue {
                    source.url.request { response, request ->
                        val fetcher =
                            EpgFetcher.instances.first { it.isSupport(request.url.toString()) }
                        fetcher.fetch(response.body!!)
                    }
                }
                log.i("获取节目单（${source.name}）xml成功", null, t.duration)

                t.value
            } catch (ex: Exception) {
                // 拉取中途被取消不是拉取失败：取消信号原样抛出去，别包成“请检查网络连接”
                // 的 HttpException——否则外层 690 轮的守卫接不到取消信号，会拿旧文件顶上、
                // 把一次取消记成一次成功加载（691 轮发版仓库同款问题）
                if (ex is CancellationException) throw ex
                log.e("获取节目单（${source.name}）xml失败", ex)
                // 服务端状态码错（404/500 等）是地址/服务端问题，不是网络问题：原样抛
                if (ex is HttpStatusException) throw ex
                throw HttpException("获取节目单xml失败，请检查网络连接", ex)
            }
        }
    }
}
