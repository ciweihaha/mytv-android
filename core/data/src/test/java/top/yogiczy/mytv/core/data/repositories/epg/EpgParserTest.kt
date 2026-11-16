package top.yogiczy.mytv.core.data.repositories.epg

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * 节目单时间解析测试：带时区的标准格式原样解析；不写时区、时区带冒号、
 * 时区前没空格时旧实现抛 ParseException，整条节目被外层 runCatching
 * 吞掉——整个源都不带时区时节目单全空；现在退回按前 14 位本地时间解析。
 */
class EpgParserTest {
    private val dateFormat = SimpleDateFormat("yyyyMMddHHmmss Z", Locale.getDefault())
    private val noZoneDateFormat = SimpleDateFormat("yyyyMMddHHmmss", Locale.getDefault())

    private fun parse(time: String) =
        EpgParser.parseTime(time, dateFormat, noZoneDateFormat)

    @Test
    fun parseTime_withZone_parsed() {
        // 同一时刻、不同时区写法，解析出的时间戳正好差 8 小时（与机器时区无关）
        val plus8 = parse("20261112010000 +0800")
        val plus0 = parse("20261112010000 +0000")
        assertTrue(plus8 > 0)
        assertEquals(8 * 3600 * 1000L, plus0 - plus8)
    }

    @Test
    fun parseTime_negativeZone_parsed() {
        // 负时区（美洲源 -0800）：Z 格式本身认负号，应按正确时刻解析、
        // 而不是掉进兜底按本地时间算——同一时刻、不同时区写法，时间戳
        // 正好差 8/16 小时（与机器时区无关）
        val minus8 = parse("20261112010000 -0800")
        val plus0 = parse("20261112010000 +0000")
        val plus8 = parse("20261112010000 +0800")
        assertTrue(minus8 > 0)
        assertEquals(8 * 3600 * 1000L, minus8 - plus0)
        assertEquals(16 * 3600 * 1000L, minus8 - plus8)
    }

    @Test
    fun parseTime_withoutZone_fallsBackToLocalTime() {
        // 不写时区：旧实现抛异常、节目丢掉；现在按本地时间解析出有效值
        val bare = parse("20261112010000")
        assertTrue(bare > 0)
        assertEquals(noZoneDateFormat.parse("20261112010000")!!.time, bare)
    }

    @Test
    fun parseTime_colonZone_fallsBackToLocalTime() {
        // 时区带冒号 "+08:00"：Z 格式不认，退回按前 14 位解析，和不写时区同值
        assertEquals(parse("20261112010000"), parse("20261112010000 +08:00"))
    }

    @Test
    fun parseTime_noSpaceBeforeZone_fallsBackToLocalTime() {
        // 时区前没空格 "+0800"：同样退回按前 14 位解析
        assertEquals(parse("20261112010000"), parse("20261112010000+0800"))
    }

    @Test
    fun parseTime_tooShort_returnsZero() {
        assertEquals(0, parse("20261112"))
        assertEquals(0, parse(""))
    }

    @Test
    fun parseTime_invalidDate_returnsZero() {
        // 非法日期（2 月 30 日、13 月、24 点）：宽容模式会顺延成有效日期，
        // 节目出现在错误的那天；严格模式下应解析失败、返回 0
        assertEquals(0, parse("20260230010000 +0800"))
        assertEquals(0, parse("20261301010000 +0800"))
        assertEquals(0, parse("20261112240000 +0800"))
    }

    private fun parseXml(xml: String) = runBlocking {
        EpgParser.fromXml(ByteArrayInputStream(xml.toByteArray(Charsets.UTF_8)))
    }

    private fun channelXml(id: String, name: String) =
        "<channel id=\"$id\"><display-name>$name</display-name></channel>"

    private fun programmeXml(
        channel: String,
        start: String,
        stop: String,
        inner: String,
    ) = "<programme channel=\"$channel\" start=\"$start\" stop=\"$stop\">$inner</programme>"

    @Test
    fun fromXml_programmeForUnknownChannel_skipped() {
        // 没声明过的频道 id 的节目直接丢掉：不然它挂不到任何台上、
        // 只会在按名匹配时凭空多出节目
        val xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><tv>" +
            channelXml("cctv1", "CCTV-1") +
            programmeXml("cctv1", "20261112010000 +0800", "20261112020000 +0800", "<title>本台节目</title>") +
            programmeXml("unknown", "20261112010000 +0800", "20261112020000 +0800", "<title>野节目</title>") +
            "</tv>"

        val result = parseXml(xml)
        assertEquals(1, result.size)
        assertEquals(1, result[0].programmeList.size)
        assertEquals("本台节目", result[0].programmeList[0].title)
    }

    @Test
    fun fromXml_titleAfterDesc_stillFound() {
        // 子元素顺序不是 title 在前时（desc 在前），按标签名取 title
        // 仍能找到；旧实现取第一个子元素会把简介错当标题
        val xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><tv>" +
            channelXml("cctv1", "CCTV-1") +
            programmeXml(
                "cctv1", "20261112010000 +0800", "20261112020000 +0800",
                "<desc>简介在前</desc><title>正标题</title>",
            ) +
            "</tv>"

        val result = parseXml(xml)
        assertEquals(1, result[0].programmeList.size)
        assertEquals("正标题", result[0].programmeList[0].title)
    }

    @Test
    fun fromXml_channelWithoutDisplayName_dropped() {
        // 没 display-name 的频道不进列表：进去了也是个没名字的空台、
        // 按名匹配永远匹配不上
        val xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><tv>" +
            channelXml("cctv1", "CCTV-1") +
            "<channel id=\"noname\"></channel>" +
            programmeXml("cctv1", "20261112010000 +0800", "20261112020000 +0800", "<title>本台节目</title>") +
            "</tv>"

        val result = parseXml(xml)
        assertEquals(1, result.size)
        assertEquals("cctv1", result[0].id)
    }

    @Test
    fun fromXml_programmeEndBeforeStart_dropped() {
        // 结束早于开始的节目直接丢掉：开始/结束都有效的零时长或倒挂节目，
        // recentProgramme 只比时间不验有效性，会把它当成正在播
        val xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><tv>" +
            channelXml("cctv1", "CCTV-1") +
            programmeXml("cctv1", "20261112010000 +0800", "20261112020000 +0800", "<title>正常节目</title>") +
            programmeXml("cctv1", "20261112030000 +0800", "20261112020000 +0800", "<title>倒挂节目</title>") +
            "</tv>"

        val result = parseXml(xml)
        assertEquals(1, result[0].programmeList.size)
        assertEquals("正常节目", result[0].programmeList[0].title)
    }
}
