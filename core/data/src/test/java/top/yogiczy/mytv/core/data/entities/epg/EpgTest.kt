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
