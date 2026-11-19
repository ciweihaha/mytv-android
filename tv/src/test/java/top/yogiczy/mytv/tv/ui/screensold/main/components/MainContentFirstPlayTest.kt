package top.yogiczy.mytv.tv.ui.screensold.main.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import top.yogiczy.mytv.core.data.entities.channel.Channel
import top.yogiczy.mytv.core.data.entities.channel.ChannelGroup
import top.yogiczy.mytv.core.data.entities.channel.ChannelGroupList
import top.yogiczy.mytv.core.data.entities.channel.ChannelLine
import top.yogiczy.mytv.core.data.entities.channel.ChannelLineList
import top.yogiczy.mytv.core.data.entities.channel.ChannelList

/**
 * 首播选台（250 第一次启动黑屏修法）：首播只在 init 里跑一次，那时
 * 列表还没加载完、只能兜底成空频道，之后再也没人补播，一直黑屏；
 * 列表到达后补一次，按“上次播放（若在列表里）否则第一个台”选台。
 * 选台规则抽成纯函数直接测；状态机接线（LaunchedEffect → ensureFirstPlay）
 * 靠平板真机验——MainContentState 构造要 SettingsViewModel，它的 init
 * 读 SharedPreferences，本地 JVM 单测里没有 Android 环境、建不出来
 *（本仓库既有用例也是只测纯函数）。
 */
class MainContentFirstPlayTest {
    private fun channel(name: String) = Channel(
        name = name,
        lineList = ChannelLineList(listOf(ChannelLine("https://example.com/$name.m3u8"))),
    )

    private fun groupList(vararg groups: ChannelGroup) = ChannelGroupList(groups.toList())

    private val cctv1 = channel("CCTV1")
    private val cctv5 = channel("CCTV5")
    private val cctv8 = channel("CCTV8")

    private fun list() = groupList(
        ChannelGroup(name = "央视", channelList = ChannelList(listOf(cctv1, cctv5))),
        ChannelGroup(name = "卫视", channelList = ChannelList(listOf(cctv8))),
    )

    @Test
    fun `列表还没到时不选台`() {
        // init 时列表为空：返回 null，调用方别播、等列表到了再补一次
        assertNull(resolveFirstPlayChannel(Channel.EMPTY, ChannelGroupList()))
    }

    @Test
    fun `列表到达后无上次播放时播第一个台`() {
        assertEquals(cctv1, resolveFirstPlayChannel(Channel.EMPTY, list()))
    }

    @Test
    fun `列表到达后上次播放在列表里时播上次播放的台`() {
        assertEquals(cctv5, resolveFirstPlayChannel(cctv5, list()))
    }

    @Test
    fun `上次播放不在新列表里时播第一个台`() {
        // 切源后旧台不在新源里：别播旧台（线路是旧源的），播新源第一个台
        val stale = channel("旧台")
        assertEquals(cctv1, resolveFirstPlayChannel(stale, list()))
    }

    @Test
    fun `首个分组为空时跳过取后面分组的第一个台`() {
        val groups = groupList(
            ChannelGroup(name = "空分组", channelList = ChannelList()),
            ChannelGroup(name = "央视", channelList = ChannelList(listOf(cctv1, cctv5))),
        )
        assertEquals(cctv1, resolveFirstPlayChannel(Channel.EMPTY, groups))
    }

    @Test
    fun `所有分组都空时不选台`() {
        val groups = groupList(ChannelGroup(name = "空", channelList = ChannelList()))
        assertNull(resolveFirstPlayChannel(Channel.EMPTY, groups))
    }
}
