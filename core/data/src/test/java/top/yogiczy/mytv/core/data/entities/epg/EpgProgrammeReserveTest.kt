package top.yogiczy.mytv.core.data.entities.epg

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import top.yogiczy.mytv.core.data.entities.channel.Channel

/**
 * 节目预约匹配测试：预约用频道名 + 节目名 + 起止时间四项同时比对。
 * 全仓对照（862 轮）发现它一次也没断言过：MainContentState 的预约开关
 * 靠它判断节目有没有被预约过——多比一项会把别人的预约取消掉，少比
 * 一项会让同一节目重复预约。
 */
class EpgProgrammeReserveTest {
    private val reserve = EpgProgrammeReserve(
        channel = "CCTV-1",
        programme = "新闻联播",
        startAt = 1000,
        endAt = 2000,
    )
    private val channel = Channel(name = "CCTV-1")
    private val programme = EpgProgramme(startAt = 1000, endAt = 2000, title = "新闻联播")

    @Test
    fun test_allFieldsMatch() {
        assertTrue(reserve.test(channel, programme))
    }

    @Test
    fun test_channelNameMismatch() {
        assertFalse(reserve.test(Channel(name = "湖南卫视"), programme))
    }

    @Test
    fun test_programmeTitleMismatch() {
        assertFalse(reserve.test(channel, programme.copy(title = "天气预报")))
    }

    @Test
    fun test_startAtMismatch() {
        assertFalse(reserve.test(channel, programme.copy(startAt = 1001)))
    }

    @Test
    fun test_endAtMismatch() {
        assertFalse(reserve.test(channel, programme.copy(endAt = 2001)))
    }
}
