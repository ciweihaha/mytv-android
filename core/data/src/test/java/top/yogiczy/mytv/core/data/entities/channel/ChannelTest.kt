package top.yogiczy.mytv.core.data.entities.channel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Channel.EMPTY 是“无频道”哨兵：必须没有线路。默认 lineList 自带一条
 * 示例线路，不传空列表时 EMPTY 也能播——分组全隐藏（过滤后列表为空）
 * 时直播页/多画面会拿它当真频道，把写死的示例地址播出来；换台守卫、
 * 多画面取线、频道信息取线三处注释里都已假定 EMPTY 没有可播线路。
 */
class ChannelTest {
    @Test
    fun empty_hasNoLines() {
        assertTrue(Channel.EMPTY.lineList.isEmpty())
    }

    @Test
    fun empty_isRecognizedByIsEmptyOrElse() {
        assertEquals(
            Channel.EXAMPLE,
            Channel.EMPTY.isEmptyOrElse { Channel.EXAMPLE },
        )
    }

    @Test
    fun empty_notEqualToChannelWithExampleLine() {
        // 哨兵与“带示例线路的空名频道”不是同一个：旧的 EMPTY（带示例线路）
        // 不应再被当成无频道
        assertTrue(Channel.EMPTY != Channel(lineList = ChannelLineList(listOf(ChannelLine.EXAMPLE))))
    }
}
