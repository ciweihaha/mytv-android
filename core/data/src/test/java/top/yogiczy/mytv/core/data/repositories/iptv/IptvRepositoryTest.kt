package top.yogiczy.mytv.core.data.repositories.iptv

import org.junit.Assert.assertThrows
import org.junit.Test
import top.yogiczy.mytv.core.data.entities.channel.Channel
import top.yogiczy.mytv.core.data.entities.channel.ChannelGroup
import top.yogiczy.mytv.core.data.entities.channel.ChannelGroupList
import top.yogiczy.mytv.core.data.entities.channel.ChannelList

class IptvRepositoryTest {
    @Test
    fun `empty group list throws`() {
        assertThrows(IllegalStateException::class.java) {
            checkChannelGroupListNotEmpty(ChannelGroupList())
        }
    }

    @Test
    fun `groups with no channels throw`() {
        assertThrows(IllegalStateException::class.java) {
            checkChannelGroupListNotEmpty(
                ChannelGroupList(listOf(ChannelGroup("央视", ChannelList(emptyList())))),
            )
        }
    }

    @Test
    fun `non-empty group list passes`() {
        // 不抛错即通过
        checkChannelGroupListNotEmpty(
            ChannelGroupList(
                listOf(
                    ChannelGroup(
                        "央视",
                        ChannelList(listOf(Channel(name = "CCTV-1"))),
                    )
                )
            )
        )
    }
}
