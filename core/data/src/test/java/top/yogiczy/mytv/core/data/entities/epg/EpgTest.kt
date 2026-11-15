package top.yogiczy.mytv.core.data.entities.epg

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import top.yogiczy.mytv.core.data.entities.epg.Epg.Companion.recentProgramme
import top.yogiczy.mytv.core.data.entities.epg.Epg.Companion.sortedProgrammes

/**
 * 节目单排序测试：源文件不保证节目按时间顺序，而 recentProgramme
 * 的二分查找和节目单界面都依赖有序列表。
 */
class EpgTest {
    @Test
    fun sortedProgrammes_sortsByStartAt() {
        val epg = Epg(
            channelList = listOf("CCTV-1"),
            programmeList = EpgProgrammeList(
                listOf(
                    EpgProgramme(startAt = 3000, endAt = 4000, title = "第三"),
                    EpgProgramme(startAt = 1000, endAt = 2000, title = "第一"),
                    EpgProgramme(startAt = 2000, endAt = 3000, title = "第二"),
                )
            ),
        )
        val sorted = epg.sortedProgrammes()
        assertEquals(listOf("第一", "第二", "第三"), sorted.programmeList.map { it.title })
    }

    @Test
    fun sortedProgrammes_stableForEqualStartAt() {
        // 同一开始时间的节目保持原文顺序（sortedBy 是稳定排序）
        val epg = Epg(
            channelList = listOf("CCTV-1"),
            programmeList = EpgProgrammeList(
                listOf(
                    EpgProgramme(startAt = 1000, endAt = 2000, title = "甲"),
                    EpgProgramme(startAt = 1000, endAt = 2000, title = "乙"),
                )
            ),
        )
        assertEquals(listOf("甲", "乙"), epg.sortedProgrammes().programmeList.map { it.title })
    }

    @Test
    fun sortedProgrammes_emptyStaysEmpty() {
        assertEquals(0, Epg().sortedProgrammes().programmeList.size)
    }

    @Test
    fun recentProgramme_findsLiveAfterSorting() {
        // 乱序输入：二分查找在未排序列表上会找错（甚至返回空），排好序后
        // 才能找到正在播的节目和下一个节目
        val now = System.currentTimeMillis()
        val epg = Epg(
            channelList = listOf("CCTV-1"),
            programmeList = EpgProgrammeList(
                listOf(
                    EpgProgramme(startAt = now + 3600_000, endAt = now + 7200_000, title = "稍后"),
                    EpgProgramme(startAt = now - 3600_000, endAt = now - 1000, title = "已播"),
                    EpgProgramme(startAt = now - 1000, endAt = now + 3600_000, title = "正在播"),
                )
            ),
        ).sortedProgrammes()
        val recent = epg.recentProgramme()
        assertEquals("正在播", recent.now?.title)
        assertEquals("稍后", recent.next?.title)
    }

    @Test
    fun recentProgramme_noLive_nextIsUpcoming() {
        // 当天第一个节目还没开始：没有正在播的节目，但下一个节目是已知
        // 的；原来 next 也返回空，选台页“稍后播放”只能显示“精彩节目”占位
        val now = System.currentTimeMillis()
        val epg = Epg(
            channelList = listOf("CCTV-1"),
            programmeList = EpgProgrammeList(
                listOf(
                    EpgProgramme(startAt = now + 3600_000, endAt = now + 7200_000, title = "稍后"),
                )
            ),
        ).sortedProgrammes()
        val recent = epg.recentProgramme()
        assertNull(recent.now)
        assertEquals("稍后", recent.next?.title)
    }

    @Test
    fun recentProgramme_inGap_nextIsUpcoming() {
        // 当前时间落在两个节目之间的空档：now 为空，next 是空档后第一个节目
        val now = System.currentTimeMillis()
        val epg = Epg(
            channelList = listOf("CCTV-1"),
            programmeList = EpgProgrammeList(
                listOf(
                    EpgProgramme(startAt = now - 7200_000, endAt = now - 3600_000, title = "已播"),
                    EpgProgramme(startAt = now + 3600_000, endAt = now + 7200_000, title = "稍后"),
                )
            ),
        ).sortedProgrammes()
        val recent = epg.recentProgramme()
        assertNull(recent.now)
        assertEquals("稍后", recent.next?.title)
    }

    @Test
    fun recentProgramme_boundaryStartIsLive() {
        // 开始那一刻算正在播（左闭）：与 isLive() 一致
        val epg = Epg(
            channelList = listOf("CCTV-1"),
            programmeList = EpgProgrammeList(
                listOf(
                    EpgProgramme(startAt = 1000, endAt = 2000, title = "第一"),
                    EpgProgramme(startAt = 2000, endAt = 3000, title = "第二"),
                )
            ),
        ).sortedProgrammes()
        val recent = epg.recentProgramme(1000)
        assertEquals("第一", recent.now?.title)
        assertEquals("第二", recent.next?.title)
    }

    @Test
    fun recentProgramme_boundaryEndIsNotLive() {
        // 结束那一刻算已播完（右开）：与 isLive() 一致；相邻节目共用边界点时
        // 只能命中后一个，不会随手返回前一个
        val epg = Epg(
            channelList = listOf("CCTV-1"),
            programmeList = EpgProgrammeList(
                listOf(
                    EpgProgramme(startAt = 1000, endAt = 2000, title = "第一"),
                    EpgProgramme(startAt = 2000, endAt = 3000, title = "第二"),
                )
            ),
        ).sortedProgrammes()
        val recent = epg.recentProgramme(2000)
        assertEquals("第二", recent.now?.title)
        assertNull(recent.next)
    }

    @Test
    fun recentProgramme_nextSkipsAlreadyFinishedOverlap() {
        // 脏数据：下一档与正在播的重叠、且已经播完（开始在现在之前、结束也
        // 在现在之前），不能把它当成“下一个节目”；原来 next 直接取下一条，
        // 会把已经播完的节目显示成“稍后播放”
        val epg = Epg(
            channelList = listOf("CCTV-1"),
            programmeList = EpgProgrammeList(
                listOf(
                    EpgProgramme(startAt = 1000, endAt = 3000, title = "正在播"),
                    EpgProgramme(startAt = 1500, endAt = 2000, title = "已播完的重叠档"),
                    EpgProgramme(startAt = 3000, endAt = 4000, title = "下一档"),
                )
            ),
        ).sortedProgrammes()
        val recent = epg.recentProgramme(2500)
        assertEquals("正在播", recent.now?.title)
        assertEquals("下一档", recent.next?.title)
    }

    @Test
    fun recentProgramme_nextKeepsOverlappingNotYetFinished() {
        // 脏数据：下一档与正在播的重叠、但还没播完，仍是“下一个节目”；
        // 上一条的修法不能把这种也跳过
        val epg = Epg(
            channelList = listOf("CCTV-1"),
            programmeList = EpgProgrammeList(
                listOf(
                    EpgProgramme(startAt = 1000, endAt = 3000, title = "正在播"),
                    EpgProgramme(startAt = 2000, endAt = 2500, title = "重叠的下一档"),
                )
            ),
        ).sortedProgrammes()
        val recent = epg.recentProgramme(1500)
        assertEquals("正在播", recent.now?.title)
        assertEquals("重叠的下一档", recent.next?.title)
    }

    @Test
    fun recentProgramme_nextPrefersOnTimeOverOverlapping() {
        // 正在播 + 重叠档（开始在当前结束之前）+ 正点下一档（开始正好
        // 等于当前结束）：下一档应是正点那档，不是重叠那档——
        // 正点接档非空时不走“还没播完就当下一档”的退路；上一条用例
        // （只有重叠档时仍算下一档）分不出这一档
        val epg = Epg(
            channelList = listOf("CCTV-1"),
            programmeList = EpgProgrammeList(
                listOf(
                    EpgProgramme(startAt = 1000000, endAt = 1020000, title = "正在播"),
                    EpgProgramme(startAt = 1010000, endAt = 1015000, title = "重叠档"),
                    EpgProgramme(startAt = 1020000, endAt = 1030000, title = "正点下一档"),
                )
            ),
        ).sortedProgrammes()
        val recent = epg.recentProgramme(1000000)
        assertEquals("正在播", recent.now?.title)
        assertEquals("正点下一档", recent.next?.title)
    }

    @Test
    fun recentProgramme_tieStarts_liveTakesFirstWritten() {
        // 天光 1989 轮姊妹检查：两档节目开始时间完全相同、都在播，
        // indexOfFirst 取先写的那档；排序稳定（已钉死）+ 先写优先
        // （这一档）合起来才是平局语义，比较符改成取后写的那档时
        // 这里会失败——现有用例全是不同开始时间，拦不住
        val epg = Epg(
            channelList = listOf("CCTV-1"),
            programmeList = EpgProgrammeList(
                listOf(
                    EpgProgramme(startAt = 1000, endAt = 2000, title = "甲"),
                    EpgProgramme(startAt = 1000, endAt = 3000, title = "乙"),
                )
            ),
        ).sortedProgrammes()
        val recent = epg.recentProgramme(1500)
        assertEquals("甲", recent.now?.title)
    }

    @Test
    fun recentProgramme_tieStarts_nextTakesFirstWritten() {
        // 天光 1989 轮姊妹检查：正在播 + 两档下一档开始时间都正好
        // 等于当前档结束，下一档取先写的那档
        val epg = Epg(
            channelList = listOf("CCTV-1"),
            programmeList = EpgProgrammeList(
                listOf(
                    EpgProgramme(startAt = 1000000, endAt = 1020000, title = "正在播"),
                    EpgProgramme(startAt = 1020000, endAt = 1030000, title = "甲"),
                    EpgProgramme(startAt = 1020000, endAt = 1040000, title = "乙"),
                )
            ),
        ).sortedProgrammes()
        val recent = epg.recentProgramme(1000000)
        assertEquals("正在播", recent.now?.title)
        assertEquals("甲", recent.next?.title)
    }

    @Test
    fun recentProgramme_tieStarts_noLiveNextTakesFirstWritten() {
        // 天光 1989 轮姊妹检查：没有正在播、两档未来节目开始时间
        // 相同，稍后播放取先写的那档
        val epg = Epg(
            channelList = listOf("CCTV-1"),
            programmeList = EpgProgrammeList(
                listOf(
                    EpgProgramme(startAt = 2000000, endAt = 3000000, title = "甲"),
                    EpgProgramme(startAt = 2000000, endAt = 3000000, title = "乙"),
                )
            ),
        ).sortedProgrammes()
        val recent = epg.recentProgramme(1000000)
        assertNull(recent.now)
        assertEquals("甲", recent.next?.title)
    }

    @Test
    fun recentProgramme_atExactEndOfLast_returnsEmpty() {
        // 最后一档结束那一刻：正在播与下一个都为空（右开）；旧比较器下会
        // 把最后一档当成正在播
        val epg = Epg(
            channelList = listOf("CCTV-1"),
            programmeList = EpgProgrammeList(
                listOf(
                    EpgProgramme(startAt = 1000, endAt = 2000, title = "第一"),
                    EpgProgramme(startAt = 2000, endAt = 3000, title = "第二"),
                )
            ),
        ).sortedProgrammes()
        val recent = epg.recentProgramme(3000)
        assertNull(recent.now)
        assertNull(recent.next)
    }

    @Test
    fun recentProgramme_afterLast_returnsEmpty() {
        // 当天最后一个节目也播完了：后面没有节目，now 和 next 都为空
        val now = System.currentTimeMillis()
        val epg = Epg(
            channelList = listOf("CCTV-1"),
            programmeList = EpgProgrammeList(
                listOf(
                    EpgProgramme(startAt = now - 7200_000, endAt = now - 3600_000, title = "已播"),
                )
            ),
        ).sortedProgrammes()
        val recent = epg.recentProgramme()
        assertNull(recent.now)
        assertNull(recent.next)
    }
}
