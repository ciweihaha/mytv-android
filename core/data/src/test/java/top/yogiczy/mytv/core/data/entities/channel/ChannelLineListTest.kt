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

    @Test
    fun resolveLineIdx_deepNegative_clampedToZero() {
        // 深负数不环绕到尾部：-4 在 3 条线路上按现行写法回 0（max(0, min(...)) 故意夹住）。
        // 调用方下标只按 ±1 走、到不了这么深，且调用方还会再夹一次，这里只钉行为、不改生产代码。
        assertEquals(0, lines.resolveLineIdx(-4))
        assertEquals(0, lines.resolveLineIdx(-5))
        assertEquals(0, lines.resolveLineIdx(-6))
        // 边界：一圈内的负数仍环绕
        assertEquals(1, lines.resolveLineIdx(-2))
        assertEquals(0, lines.resolveLineIdx(-3))
    }
}
