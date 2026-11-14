package top.yogiczy.mytv.core.data.entities.epg

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import top.yogiczy.mytv.core.data.entities.channel.Channel
import top.yogiczy.mytv.core.data.entities.epg.EpgList.Companion.clearCache
import top.yogiczy.mytv.core.data.entities.epg.EpgList.Companion.match

/**
 * 节目单按 tvg-id 兜底匹配测试：直播源的 tvg-id 和节目单 XMLTV
 * channel 的 id 是同一套；频道名写法和节目单不一样时（直播源
 * “CCTV综合”、节目单“CCTV-1 综合”），原来按名查不到、显示
 * “无节目”，按 id 能找回节目单；id 对上后还要名字复核通过才认，
 * 上游 id 标错的台不贴错节目单。
 */
class EpgListTest {
    @Before
    fun clearMatchCache() {
        // match 结果有 64 条的 LRU 缓存：用例之间清掉，避免同名不同
        // tvg-id 的两个台互相串结果
        clearCache()
    }

    private fun channel(epgName: String, tvgId: String = "") =
        Channel(name = epgName, epgName = epgName, tvgId = tvgId)

    private fun epgList() = EpgList(
        listOf(
            Epg(id = "cctv1", channelList = listOf("CCTV-1 综合")),
            Epg(id = "cctv5", channelList = listOf("CCTV-5 综合")),
            Epg(id = "cctv8k", channelList = listOf("CCTV-8K 超高清")),
        )
    )

    @Test
    fun match_byName_unchanged() {
        // 名字对得上的台走原来的按名查，行为不变
        val matched = epgList().match(channel("CCTV-1 综合"))
        assertEquals("cctv1", matched?.id)
    }

    @Test
    fun match_byTvgId_fallsBackWhenNameDiffers() {
        // 直播源“CCTV综合”、节目单“CCTV-1 综合”：按名查不到，
        // 按 tvg-id=cctv1 找回，名字复核（去数字比）也通过
        val matched = epgList().match(channel("CCTV综合", "cctv1"))
        assertEquals("cctv1", matched?.id)
    }

    @Test
    fun match_byTvgId_nameVerificationRejectsWrongId() {
        // 上游把 4K 台的 id 标成 8K：id 对上了，但名字复核里数字串
        // 4 对 8 不一样，宁可不贴也不贴错台
        val matched = epgList().match(channel("CCTV-4K 超高清", "cctv8k"))
        assertEquals(Epg(), matched)
    }

    @Test
    fun match_withoutTvgId_noFallback() {
        // 没 tvg-id 的频道：按名查不到就直接空，不走 id 兜底
        val matched = epgList().match(channel("CCTV综合"))
        assertEquals(Epg(), matched)
    }

    @Test
    fun match_byTvgId_idNotInEpg_returnsEmpty() {
        val matched = epgList().match(channel("CCTV综合", "cctv99"))
        assertEquals(Epg(), matched)
    }

    @Test
    fun match_nameTakesPrecedenceOverId() {
        // 名字能对上的台不走 id：epgName 是 CCTV-1 综合、tvg-id
        // 却写着 cctv5，按名认到 cctv1，不按 id 认到 cctv5
        val matched = epgList().match(channel("CCTV-1 综合", "cctv5"))
        assertEquals("cctv1", matched?.id)
    }

    @Test
    fun match_sameEpgNameDifferentTvgId_notMixedByCache() {
        // 两个台节目单名相同、tvg-id 不同：缓存键必须同时带 tvg-id，
        // 否则前一个台的匹配结果会错发给后一个台
        val list = epgList()
        assertEquals("cctv1", list.match(channel("CCTV综合", "cctv1"))?.id)
        assertEquals("cctv5", list.match(channel("CCTV综合", "cctv5"))?.id)
    }

    @Test
    fun match_sameName_returnsFirst() {
        // 同名节目单有两条：按名查留第一次出现的那份
        val list = EpgList(
            listOf(
                Epg(id = "cctv1", channelList = listOf("CCTV-1 综合")),
                Epg(id = "cctv1b", channelList = listOf("CCTV-1 综合")),
            )
        )
        val matched = list.match(channel("CCTV-1 综合"))
        assertEquals("cctv1", matched?.id)
    }

    @Test
    fun match_sameId_returnsFirst() {
        // 同 id 节目单有两条：按 id 查留第一次出现的那份
        val list = EpgList(
            listOf(
                Epg(id = "cctv1", channelList = listOf("CCTV-1 综合")),
                Epg(id = "cctv1", channelList = listOf("CCTV-1 综合", "CCTV-1 高清")),
            )
        )
        val matched = list.match(channel("CCTV综合", "cctv1"))
        assertEquals(listOf("CCTV-1 综合"), matched?.channelList)
    }

    @Test
    fun isSameEpgChannelName_digitsAndSpacesIgnored() {
        assertTrue(isSameEpgChannelName("CCTV综合", "CCTV-1 综合"))
        // 空白与“-”只是写法差别，归一化后相同、数字串也相同
        assertTrue(isSameEpgChannelName("CCTV-1 综合", "CCTV-1综合"))
    }

    @Test
    fun isSameEpgChannelName_digitMismatch_rejected() {
        // 4K 对 8K：归一化后名字相同，但数字串不一样，是两个台
        assertFalse(isSameEpgChannelName("CCTV-4K 超高清", "CCTV-8K 超高清"))
    }

    @Test
    fun isSameEpgChannelName_digitsOnly_rejected() {
        // 归一化后是空串（如名字只剩数字）不算相同，避免“1”和“2”撞到一起
        assertFalse(isSameEpgChannelName("1", "2"))
    }

    @Test
    fun isSameEpgChannelName_differentName_rejected() {
        assertFalse(isSameEpgChannelName("CCTV综合", "湖南卫视"))
    }

    private fun programmeListWith(vararg programmes: EpgProgramme) =
        EpgList(listOf(Epg(id = "cctv1", programmeList = EpgProgrammeList(programmes.toList()))))

    @Test
    fun hasLiveProgramme_liveProgrammePresent_returnsTrue() {
        // 有节目正在播：凌晨缓存是昨天文件时一个都没有，靠它判定过期
        val now = 1_700_000_000_000L
        val list = programmeListWith(
            EpgProgramme(startAt = now - 1000, endAt = now + 1000, title = "正在播"),
        )
        assertTrue(list.hasLiveProgramme(now))
    }

    @Test
    fun hasLiveProgramme_allProgrammesInPast_returnsFalse() {
        // 全是过去的节目：昨天文件的典型样子，判定过期、重新拉一次
        val now = 1_700_000_000_000L
        val list = programmeListWith(
            EpgProgramme(startAt = now - 3000, endAt = now - 2000, title = "已播完"),
            EpgProgramme(startAt = now - 2000, endAt = now - 1000, title = "已播完"),
        )
        assertFalse(list.hasLiveProgramme(now))
    }

    @Test
    fun hasLiveProgramme_allProgrammesInFuture_returnsFalse() {
        val now = 1_700_000_000_000L
        val list = programmeListWith(
            EpgProgramme(startAt = now + 1000, endAt = now + 2000, title = "还没开始"),
        )
        assertFalse(list.hasLiveProgramme(now))
    }

    @Test
    fun hasLiveProgramme_emptyList_returnsFalse() {
        assertFalse(EpgList().hasLiveProgramme(1_700_000_000_000L))
    }

    @Test
    fun hasLiveProgramme_programmeEndingNow_returnsFalse() {
        // 右开：结束时间正好是现在，不算正在播
        val now = 1_700_000_000_000L
        val list = programmeListWith(
            EpgProgramme(startAt = now - 1000, endAt = now, title = "刚结束"),
        )
        assertFalse(list.hasLiveProgramme(now))
    }

    @Test
    fun hasLiveProgramme_programmeStartingNow_returnsTrue() {
        // 左闭：开始时间正好是现在，算正在播
        val now = 1_700_000_000_000L
        val list = programmeListWith(
            EpgProgramme(startAt = now, endAt = now + 1000, title = "刚开始"),
        )
        assertTrue(list.hasLiveProgramme(now))
    }
}
