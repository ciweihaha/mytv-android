package top.yogiczy.mytv.core.data.utils

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 频道名去质量后缀测试：后缀表只扫一遍时，叠后缀名
 * （如 `CCTV-1-4K-HD`）会残留后缀，标准名错导致 EPG 匹配不上。
 * standardChannelName 本体依赖 Globals.resources（Android 资源），
 * JVM 单测跑不了，这里测抽出来的纯函数。
 */
class ChannelAliasTest {
    // 按默认映射表里的实际顺序：-4K-SDR 在 -4K/-SDR 前，-4K 在 -HD 前
    private val suffixes = listOf("-4K-SDR", "-4K", "-SDR", "-HD", "-高清")

    @Test
    fun stackedSuffixes_allStripped() {
        // -HD 去掉后露出 -4K，而 -4K 在表里排在 -HD 前面，单遍扫会漏掉
        assertEquals(
            "CCTV-1",
            ChannelAlias.stripChannelNameSuffixes("CCTV-1-4K-HD", suffixes),
        )
    }

    @Test
    fun stackedSuffixes_otherOrder_allStripped() {
        assertEquals(
            "CCTV-1",
            ChannelAlias.stripChannelNameSuffixes("CCTV-1-HD-4K", suffixes),
        )
    }

    @Test
    fun longestSuffixFirst_stillStripped() {
        assertEquals(
            "CCTV-1",
            ChannelAlias.stripChannelNameSuffixes("CCTV-1-4K-SDR", suffixes),
        )
    }

    @Test
    fun noSuffix_unchanged() {
        assertEquals("CCTV-1", ChannelAlias.stripChannelNameSuffixes("CCTV-1", suffixes))
    }

    @Test
    fun suffixNotAtEnd_unchanged() {
        // 后缀不在尾部不去掉
        assertEquals(
            "CCTV-1-4K-卫视",
            ChannelAlias.stripChannelNameSuffixes("CCTV-1-4K-卫视", suffixes),
        )
    }

    @Test
    fun chineseSuffix_stacked() {
        assertEquals(
            "CCTV-1",
            ChannelAlias.stripChannelNameSuffixes("CCTV-1-高清-HD", suffixes),
        )
    }
}
