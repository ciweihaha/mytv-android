package top.yogiczy.mytv.core.data.repositories.epg

import android.util.Xml
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import top.yogiczy.mytv.core.data.entities.epg.Epg
import top.yogiczy.mytv.core.data.entities.epg.Epg.Companion.sortedProgrammes
import top.yogiczy.mytv.core.data.entities.epg.EpgList
import top.yogiczy.mytv.core.data.entities.epg.EpgProgramme
import top.yogiczy.mytv.core.data.entities.epg.EpgProgrammeList
import top.yogiczy.mytv.core.data.utils.ChannelAlias
import top.yogiczy.mytv.core.data.utils.Loggable
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.time.measureTimedValue

object EpgParser : Loggable("EpgParser") {
    private fun parseTime(time: String, dateFormat: SimpleDateFormat): Long {
        if (time.length < 14) return 0
        return dateFormat.parse(time)?.time ?: 0
    }

    private data class ChannelItem(
        val id: String,
        val displayNames: MutableList<String> = mutableListOf(),
        var icon: String? = null,
    )

    private data class ProgrammeItem(
        val channel: String,
        val start: Long,
        val end: Long,
        val title: String,
    )

    private suspend fun parse(inputStream: InputStream) = withContext(Dispatchers.IO) {
        inputStream.use { stream ->
            val parser: XmlPullParser = Xml.newPullParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            parser.setInput(stream.reader(Charsets.UTF_8))

            val totalBytes = inputStream.available().toLong()
            var bytesRead: Long
            var lastLoggedProgress = 0

            var lastChannel: ChannelItem? = null
            val channelList = mutableListOf<ChannelItem>()
            // 频道 id 查表：原先每条节目都用 channelList.any 顺序扫描，
            // 大节目单（数千频道 × 数十万条节目）是 O(频道 × 节目)
            val channelIds = mutableSetOf<String>()
            val programmeList = mutableListOf<ProgrammeItem>()
            // 同一次解析内复用同一个 SimpleDateFormat（原先每条节目 new 一个）
            val dateFormat = SimpleDateFormat("yyyyMMddHHmmss Z", Locale.getDefault())

            var eventType = parser.eventType
            while (eventType != XmlPullParser.END_DOCUMENT) {
                bytesRead = totalBytes - stream.available().toLong()
                val progress = (bytesRead.toDouble() / totalBytes * 100).toInt()
                if (progress / 10 > lastLoggedProgress / 10) {
                    lastLoggedProgress = progress
                    log.v("节目单xml解析进度：$progress%")
                }

                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        when (parser.name) {
                            "channel" -> {
                                // 缺 id 的 channel 直接记空串：getAttributeValue
                                // 找不到属性回 null，ChannelItem.id 是非空
                                // String，Kotlin 会在传参时抛 NPE；这行又
                                // 不在 runCatching 里，一条坏 channel 会让
                                // 整份节目单解析失败、整个源的节目单都加载
                                // 不出来；记空串后该台仍能按名匹配，其余频
                                // 道也不受影响
                                lastChannel = ChannelItem(parser.getAttributeValue(null, "id").orEmpty())
                            }

                            "display-name" -> {
                                lastChannel?.let {
                                    runCatching {
                                        val displayName =
                                            ChannelAlias.standardChannelName(parser.nextText())
                                        lastChannel.displayNames.add(displayName)
                                    }
                                }
                            }

                            "icon" -> {
                                lastChannel?.let {
                                    lastChannel.icon = parser.getAttributeValue(null, "src")
                                }
                            }

                            "programme" -> {
                                runCatching {
                                    val channel = parser.getAttributeValue(null, "channel")
                                    val start = parser.getAttributeValue(null, "start")
                                    val stop = parser.getAttributeValue(null, "stop")
                                    // 按标签名取 title：原先用 nextTag/nextText 取第一个子
                                    // 元素，子元素顺序不是 title 在前时会把 desc 等错当标题；
                                    // 无论频道是否已知，都消费完子元素再回到外层循环
                                    var title: String? = null
                                    var event = parser.next()
                                    while (event != XmlPullParser.END_DOCUMENT &&
                                        !(event == XmlPullParser.END_TAG && parser.name == "programme")
                                    ) {
                                        if (event == XmlPullParser.START_TAG &&
                                            parser.name == "title" && title == null
                                        ) {
                                            title = parser.nextText()
                                        }
                                        event = parser.next()
                                    }
                                    if (channelIds.contains(channel)) {
                                        title?.let {
                                            programmeList.add(
                                                ProgrammeItem(
                                                    channel,
                                                    parseTime(start, dateFormat),
                                                    parseTime(stop, dateFormat),
                                                    it
                                                )
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    XmlPullParser.END_TAG -> {
                        when (parser.name) {
                            "channel" -> {
                                lastChannel?.let {
                                    if (it.displayNames.isNotEmpty()) {
                                        channelList.add(it)
                                        channelIds.add(it.id)
                                    }
                                }
                            }
                        }
                    }
                }
                eventType = parser.next()
            }

            val programmesByChannel = programmeList.groupBy { it.channel }
            EpgList(channelList.map { channel ->
                Epg(
                    id = channel.id,
                    channelList = channel.displayNames,
                    logo = channel.icon,
                    programmeList = EpgProgrammeList(
                        programmesByChannel[channel.id]?.map { programme ->
                            EpgProgramme(programme.start, programme.end, programme.title)
                        } ?: emptyList()
                    ),
                ).sortedProgrammes()
            })
        }
    }

    suspend fun fromXml(inputStream: InputStream): EpgList {
        return runCatching {
            log.d("开始解析节目单xml...")
            val t = measureTimedValue { parse(inputStream) }
            log.i("节目单xml解析完成", null, t.duration)

            t.value
        }
            .onFailure { log.e("节目单xml解析失败", it) }
            .getOrThrow()
    }
}