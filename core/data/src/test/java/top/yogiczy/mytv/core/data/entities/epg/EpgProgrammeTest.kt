package top.yogiczy.mytv.core.data.entities.epg

import org.junit.Assert.assertEquals
import org.junit.Test
import top.yogiczy.mytv.core.data.entities.epg.EpgProgramme.Companion.progress
import top.yogiczy.mytv.core.data.entities.epg.EpgProgramme.Companion.remainingMinutes

class EpgProgrammeTest {
    @Test
    fun progress_normal() {
        val programme = EpgProgramme(startAt = 1000, endAt = 2000)
        assertEquals(0.25f, programme.progress(1250))
        assertEquals(0f, programme.progress(1000))
        assertEquals(1f, programme.progress(2000))
    }

    @Test
    fun progress_clampedOutsideRange() {
        val programme = EpgProgramme(startAt = 1000, endAt = 2000)
        assertEquals(0f, programme.progress(500))
        assertEquals(1f, programme.progress(3000))
    }

    @Test
    fun progress_zeroOrNegativeDuration_returnsZero() {
        // EPG 里缺 start/stop、解析失败时 parseTime 得 0，会产出零时长节目：
        // 原先直接相除得 NaN/Infinity，调用方拿它当 fillMaxWidth 比例会布局异常
        assertEquals(0f, EpgProgramme(startAt = 0, endAt = 0).progress(1234567890))
        assertEquals(0f, EpgProgramme(startAt = 2000, endAt = 1000).progress(1500))
    }

    @Test
    fun remainingMinutes_neverNegative() {
        val programme = EpgProgramme(startAt = 1000, endAt = 2000)
        assertEquals(1, programme.remainingMinutes(1500))
        assertEquals(0, programme.remainingMinutes(2000))
        assertEquals(0, programme.remainingMinutes(3000))
        assertEquals(0, EpgProgramme(startAt = 0, endAt = 0).remainingMinutes(1234567890))
    }
}
