package top.yogiczy.mytv.core.data.entities.channel

import org.junit.Assert.assertEquals
import org.junit.Test

class ChannelLineListTest {
    private val lines = ChannelLineList(List(3) { ChannelLine.EXAMPLE.copy(url = "http://1.2.3.$it") })

    @Test
    fun resolveLineIdx_emptyList() {
        assertEquals(0, ChannelLineList().resolveLineIdx(0))
        assertEquals(0, ChannelLineList().resolveLineIdx(5))
        assertEquals(0, ChannelLineList().resolveLineIdx(-1))
    }

    @Test
    fun resolveLineIdx_inRange() {
        assertEquals(0, lines.resolveLineIdx(0))
        assertEquals(1, lines.resolveLineIdx(1))
        assertEquals(2, lines.resolveLineIdx(2))
    }

    @Test
    fun resolveLineIdx_wrapsAround() {
        assertEquals(0, lines.resolveLineIdx(3))
        assertEquals(1, lines.resolveLineIdx(4))
        assertEquals(2, lines.resolveLineIdx(-1))
    }
}
