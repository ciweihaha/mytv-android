package top.yogiczy.mytv.core.data.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HashUtilTest {
    @Test
    fun sha256Hex_isDeterministic() {
        assertEquals(
            HashUtil.sha256Hex("http://1.2.3.4/tv.txt"),
            HashUtil.sha256Hex("http://1.2.3.4/tv.txt")
        )
    }

    @Test
    fun sha256Hex_defaultLength16Hex() {
        val hex = HashUtil.sha256Hex("http://1.2.3.4/tv.txt")
        assertEquals(16, hex.length)
        assertTrue(hex.all { it in '0'..'9' || it in 'a'..'f' })
    }

    @Test
    fun sha256Hex_differentInputs_differentOutputs() {
        assertNotEquals(
            HashUtil.sha256Hex("http://1.2.3.4/tv.txt"),
            HashUtil.sha256Hex("http://1.2.3.5/tv.txt")
        )
        assertNotEquals(
            HashUtil.sha256Hex("http://1.2.3.4/tv.txt\u0000"),
            HashUtil.sha256Hex("http://1.2.3.4/tv.txt\u0000main()")
        )
    }

    @Test
    fun sha256Hex_customLength() {
        assertEquals(32, HashUtil.sha256Hex("x", 32).length)
        assertEquals(8, HashUtil.sha256Hex("x", 8).length)
    }
}
