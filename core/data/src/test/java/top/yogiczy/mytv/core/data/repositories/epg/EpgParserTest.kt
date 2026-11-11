package top.yogiczy.mytv.core.data.repositories.epg

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
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
}
