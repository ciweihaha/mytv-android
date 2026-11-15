package top.yogiczy.mytv.core.data.entities.epg

import androidx.compose.runtime.Immutable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import top.yogiczy.mytv.core.data.entities.channel.Channel
import top.yogiczy.mytv.core.data.entities.channel.ChannelList
import top.yogiczy.mytv.core.data.entities.epg.Epg.Companion.recentProgramme
import top.yogiczy.mytv.core.data.utils.Logger
import top.yogiczy.mytv.core.data.utils.LruMutableCache
import java.util.Locale
import kotlin.time.measureTimedValue

/**
 * 两个频道名是不是同一个台：先把大小写、数字、空白、“-”“+”去掉再比——
 * 直播源里写“CCTV综合”、节目单里写“CCTV-1 综合”，数字和空格只是
 * 写法差别。只在按 tvg-id 对上号之后才用它复核一次：上游把海南卫视的
 * id 写成了湖南卫视备的 id（hnws2），只按 id 认会把湖南卫视备的节目单
 * 贴到海南卫视上；名字对不上就宁可不贴（和原来一样显示无节目），也不
 * 贴错台。归一化后是空串（如名字只剩数字）不算相同，避免“1”和“2”撞到一起。
 * 数字串也要对得上：只去数字比时“CCTV-4K 超高清”和“CCTV-8K 超高清”
 * 都变成“cctvk超高清”，会被当成同一个台——上游一旦把 4K 的 id 标成
 * 8K（海南卫视的 id 被标错就有先例），复核就拦不住、错台的节目单直接
 * 贴过去。数字串相等、或一边没有数字（“CCTV综合”对“CCTV-1 综合”）
 * 才算对上；两边都有数字且不一样（如 4 对 8）就按不同台处理。
 * 顶层函数方便单元测试直接调用。
 */
internal fun isSameEpgChannelName(m3uName: String, epgName: String): Boolean {
    fun normalized(name: String): String =
        name.lowercase(Locale.ROOT)
            .filterNot { it.isDigit() || it.isWhitespace() || it == '-' || it == '+' }
    val first = normalized(m3uName)
    val second = normalized(epgName)
    if (first.isEmpty() || first != second) return false
    val firstDigits = m3uName.filter { it.isDigit() }
    val secondDigits = epgName.filter { it.isDigit() }
    return firstDigits.isEmpty() || secondDigits.isEmpty() || firstDigits == secondDigits
}

/**
 * 节目单里有没有正在播的节目（左闭右开，与 EpgProgramme.isLive 同义）：
 * 节目单文件只含当天节目，凌晨文件更新前（默认 2 点后）拉的缓存只有
 * 昨天节目，日期没过期、但一个正在播的节目都没有；调用方据此判定缓存
 * 已过期、重新拉一次。顶层函数方便单元测试直接调用。
 */
internal fun EpgList.hasLiveProgramme(now: Long = System.currentTimeMillis()): Boolean =
    any { epg -> epg.programmeList.any { it.startAt <= now && now < it.endAt } }

/**
 * 频道节目单列表
 */
@Serializable
@Immutable
data class EpgList(
    val value: List<Epg> = emptyList(),
) : List<Epg> by value {
    companion object {
        private val log = Logger.create("EpgList")
        private val matchCache = LruMutableCache<String, Epg>(64)

        fun EpgList.recentProgramme(channel: Channel): EpgProgrammeRecent? {
            if (isEmpty()) return null

            return match(channel)?.recentProgramme()
        }

        fun EpgList.match(channel: Channel): Epg? {
            if (isEmpty()) return null

            // 缓存键同时带上 tvg-id：两个台节目单名相同、tvg-id 不同时，
            // 只按名作键会把前一个台的匹配结果错发给后一个台
            return matchCache.getOrPut("${channel.epgName}\n${channel.tvgId}") {
                val t = measureTimedValue {
                    // 先按名查（原来的做法，名字对得上的台走这里，行为
                    // 和原来一字不变）；名字查不到时再按 tvg-id 查——
                    // 直播源里 19 个台名字写法和节目单不一样（直播源
                    // “CCTV综合”、节目单“CCTV-1 综合”），原来全显示
                    // “无节目”，按 id 能找回它们的节目单；id 对上后还
                    // 要名字复核通过才认，上游 id 标错的台不贴错节目单
                    firstOrNull { epg ->
                        epg.channelList.any { it.equals(channel.epgName, ignoreCase = true) }
                    } ?: matchByTvgId(channel) ?: Epg()
                }
                log.v("match(${matchCache.size()}): ${channel.epgName}", null, t.duration)

                t.value
            }
        }

        /**
         * 按 tvg-id 查节目单：直播源的 tvg-id 和节目单 channel 的 id
         * 是同一套；没 id 的频道、id 对不上的节目单都不进这里
         */
        private fun EpgList.matchByTvgId(channel: Channel): Epg? {
            if (channel.tvgId.isBlank()) return null
            val byId = firstOrNull { it.id.isNotBlank() && it.id == channel.tvgId }
                ?: return null
            return byId.takeIf { epg ->
                epg.channelList.any { isSameEpgChannelName(channel.epgName, it) }
            }
        }

        fun clearCache() {
            matchCache.evictAll()
        }

        fun example(channelList: ChannelList): EpgList {
            return EpgList(channelList.map(Epg.Companion::example))
        }

        private val semaphore = Semaphore(5)
        suspend fun <T> action(action: () -> T): T {
            return semaphore.withPermit {
                withContext(Dispatchers.Default) { action() }
            }
        }
    }
}