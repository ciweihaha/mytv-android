package top.yogiczy.mytv.core.data.entities.epg

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import top.yogiczy.mytv.core.data.entities.channel.Channel
import top.yogiczy.mytv.core.data.entities.epg.Epg.Companion.recentProgramme
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
    fun match_blankIdEntry_stillMatchedByName() {
        // 天光 1951 轮姊妹检查：没 id 的节目单不是废数据——按名查不看 id，
        // 空串与只有空白的 id 都应照常按名认到；id 为空只影响 id 兜底那支
        val list = EpgList(
            listOf(
                Epg(id = "", channelList = listOf("CCTV-1 综合")),
                Epg(id = "   ", channelList = listOf("CCTV-2 综合")),
            )
        )
        assertEquals("", list.match(channel("CCTV-1 综合"))?.id)
        assertEquals("   ", list.match(channel("CCTV-2 综合"))?.id)
    }

    @Test
    fun match_blankIdEntry_neverMatchedById() {
        // 空 id 的条目不进 id 兜底：频道带 tvg-id 时也不能认到空 id 的条目
        // 上（壳这边没有天光那样的 id 表，matchByTvgId 里 `it.id.isNotBlank()`
        // 是同一道守卫；`==` 本来也不会让空 id 等上号，这里只钉行为）
        val list = EpgList(
            listOf(
                Epg(id = "", channelList = listOf("CCTV-1 综合")),
                Epg(id = "   ", channelList = listOf("CCTV-2 综合")),
            )
        )
        assertEquals(Epg(), list.match(channel("CCTV综合", "cctv1")))
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

    @Test
    fun isSameEpgChannelName_caseIgnored() {
        // 天光 1940 轮姊妹检查：注释里写了“先把大小写去掉再比”，
        // 但已有用例只覆盖了数字、空白、“-”“+”，大小写那档一次
        // 也没断言过——源里小写写的台名对节目单里大写的，归一化
        // 要先转小写才对得上；转小写写漏时这里会失败
        assertTrue(isSameEpgChannelName("cctv综合", "CCTV-1 综合"))
        assertTrue(isSameEpgChannelName("cctv-8k 超高清", "CCTV-8K 超高清"))
        // 大小写不同、但本来就不是一个台：还是不能算对上
        assertFalse(isSameEpgChannelName("cctv综合", "CCTV财经"))
    }

    @Test
    fun match_byTvgId_lowercaseName_matches() {
        // 同一档走完整匹配：直播源里小写写的“cctv综合”，按名查不到、
        // 按 tvg-id=cctv1 找回后名字复核要过大小写这一关
        val matched = epgList().match(channel("cctv综合", "cctv1"))
        assertEquals("cctv1", matched?.id)
    }

    @Test
    fun match_byTvgId_idCaseIgnored() {
        // 天光 910d613 姊妹检查：有的节目单源 id 是大写（CCTV1），
        // 直播源的 tvg-id 是小写（cctv1），原样比查不到、显示“无节目”
        val list = EpgList(
            listOf(Epg(id = "CCTV1", channelList = listOf("CCTV-1综合")))
        )
        val matched = list.match(channel("CCTV-1 综合", "cctv1"))
        assertEquals("CCTV1", matched?.id)
    }

    @Test
    fun match_byTvgId_displayNameIsTvgId() {
        // 天光 910d613 姊妹检查：节目单显示名本身就是 tvg-id
        // （备用源的 CCTV1 对 tvg-id cctv1）：名字复核按写法比永远
        // 对不上，但显示名与 tvg-id 相等本身就是同一台的强证据
        val list = EpgList(
            listOf(Epg(id = "cctv1", channelList = listOf("CCTV1")))
        )
        val matched = list.match(channel("CCTV-1 综合", "cctv1"))
        assertEquals("cctv1", matched?.id)
    }

    @Test
    fun match_byTvgId_displayNameIsOtherTvgId_rejected() {
        // 显示名是 tvg-id 才认：节目单显示名是另一个台的 id
        // （cctv1）时不能认到 tvg-id=cctv5 的台上，拦法还在
        val list = EpgList(
            listOf(Epg(id = "cctv5", channelList = listOf("cctv1")))
        )
        val matched = list.match(channel("CCTV-1 综合", "cctv5"))
        assertEquals(Epg(), matched)
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

    // ---- filterForChannels（250 轮：节目单过滤，与我的电视对齐） ----

    @Test
    fun filterForChannels_keepsCctvById() {
        // 250 轮的根因：直播源“CCTV综合”、节目单“CCTV-1 综合”，
        // 只按显示名精确比会把央视台全滤掉；我的电视 keepEpgChannel
        // 是显示名或 id 对上就留，id cctv1 对上就留下来，match() 再
        // 按名复核一次才认
        val list = EpgList(listOf(Epg(id = "cctv1", channelList = listOf("CCTV-1 综合"))))
        val filtered = list.filterForChannels(listOf(channel("CCTV综合", "cctv1")))
        assertEquals(listOf("cctv1"), filtered.map { it.id })
    }

    @Test
    fun filterForChannels_keepsSatelliteByName() {
        // 名字对得上的台走原来的按名留
        val list = EpgList(listOf(Epg(id = "szws", channelList = listOf("深圳卫视"))))
        val filtered = list.filterForChannels(listOf(channel("深圳卫视", "szws")))
        assertEquals(listOf("szws"), filtered.map { it.id })
    }

    @Test
    fun filterForChannels_keepsEntryWhenIdMatchesButNameDiffers() {
        // 上游把湖南卫视备的 id 写成海南卫视的 id（hnws2）：过滤这层
        // 只看显示名或 id 对不对得上，id 对上就留（和我的电视一样）；
        // 贴不贴错台是 match() 那层按名复核的事，海南卫视拿不到它
        val list = EpgList(listOf(Epg(id = "hnws2", channelList = listOf("湖南卫视"))))
        val filtered = list.filterForChannels(listOf(channel("海南卫视", "hnws2")))
        assertEquals(listOf("hnws2"), filtered.map { it.id })
    }

    @Test
    fun filterForChannels_caseInsensitiveId() {
        // 备用源的 id 是大写（CCTV1）、直播源的 tvg-id 是小写
        // （cctv1），大小写敏感比会对不上、整台被滤掉
        val list = EpgList(listOf(Epg(id = "cctv1", channelList = listOf("CCTV-1 综合"))))
        val filtered = list.filterForChannels(listOf(channel("CCTV综合", "CCTV1")))
        assertEquals(listOf("cctv1"), filtered.map { it.id })
    }

    @Test
    fun filterForChannels_keepsCctvAndDropsUnrelated() {
        // 央视靠 id 留下来，卫视靠名字留下来，不在频道列表里的台
        // 的节目单照样丢掉
        val list = EpgList(
            listOf(
                Epg(id = "cctv1", channelList = listOf("CCTV-1 综合")),
                Epg(id = "szws", channelList = listOf("深圳卫视")),
                Epg(id = "xyz", channelList = listOf("XYZ台")),
            )
        )
        val channels = listOf(
            channel("CCTV综合", "cctv1"),
            channel("深圳卫视", "szws"),
        )
        val filtered = list.filterForChannels(channels)
        assertEquals(listOf("cctv1", "szws"), filtered.map { it.id })
    }

    @Test
    fun filterForChannels_emptyChannels_keepsAll() {
        // 过滤集为空时全留，和我的电视 keepEpgChannel 一致
        assertEquals(epgList(), epgList().filterForChannels(emptyList()))
    }

    @Test
    fun match_cctv5p_afterAliasFix_showsProgramme() {
        // cctv5p 回归（2026-11-21 平板实测：epg.xml 里 17 个节目、
        // tvg-id 与直播源一致，TV Live 251 的 06 行仍无节目名）：
        // 别名表修好后，直播源“CCTV体育赛事”与节目单“CCTV-5+ 体育赛事”
        // 都标成 CCTV5+，频道 epgName 就是 CCTV5+，按名直接认到，
        // recentProgramme 拿得到正在播的那档。修前这个用例的形状
        // 是 epgName “CCTV体育赛事”——按名查不到、按 id 查到后名字
        // 复核与“显示名即 tvg-id”两档全不过，match 回空 Epg、行空白
        val now = 1_700_000_000_000L
        val list = EpgList(
            listOf(
                Epg(
                    id = "cctv5p",
                    channelList = listOf("CCTV5+"),
                    programmeList = EpgProgrammeList(
                        listOf(
                            EpgProgramme(now - 3_600_000, now + 3_600_000, "正在播的比赛"),
                            EpgProgramme(now + 3_600_000, now + 7_200_000, "下一场比赛"),
                        )
                    ),
                ),
            )
        )
        val matched = list.match(channel("CCTV5+", "cctv5p"))
        assertEquals("cctv5p", matched?.id)
        assertEquals("正在播的比赛", matched?.recentProgramme(now)?.now?.title)
    }
}
