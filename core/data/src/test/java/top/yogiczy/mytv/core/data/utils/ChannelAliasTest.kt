package top.yogiczy.mytv.core.data.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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

    // 别名查表（buildAliasLookup）：与旧的线性扫描同优先级，
    // 自定义键 > 自定义别名 > 默认键 > 默认别名，先到的赢
    private val customMap = linkedMapOf(
        "CCTV-1" to listOf("央视一套", "CCTV1"),
        "CCTV-2" to listOf("央视二套"),
    )
    private val defaultMap = linkedMapOf(
        "CCTV-1" to listOf("央视1台"),
        "CCTV-3" to listOf("央视一套", "央视三套"),
    )

    @Test
    fun aliasLookup_keyMatch_caseInsensitive() {
        val lookup = ChannelAlias.buildAliasLookup(customMap, defaultMap)
        // 查表键统一小写：源表里的 "CCTV-1" 用小写 "cctv-1" 能查到，
        // 说明大小写不敏感；直接用大写键查是查不到的（键本身就是小写的）
        assertEquals("CCTV-1", lookup["cctv-1"])
        assertEquals("CCTV-2", lookup["cctv-2"])
    }

    @Test
    fun aliasLookup_aliasMatch() {
        val lookup = ChannelAlias.buildAliasLookup(customMap, defaultMap)
        assertEquals("CCTV-1", lookup["央视一套"])
        assertEquals("CCTV-1", lookup["cctv1"])
        assertEquals("CCTV-2", lookup["央视二套"])
    }

    @Test
    fun aliasLookup_customBeatsDefault() {
        // “央视一套”在自定义和默认里都有，自定义的赢
        val lookup = ChannelAlias.buildAliasLookup(customMap, defaultMap)
        assertEquals("CCTV-1", lookup["央视一套"])
        // 只在默认里有的，兜底能找到
        assertEquals("CCTV-3", lookup["央视三套"])
        assertEquals("CCTV-1", lookup["央视1台"])
    }

    @Test
    fun aliasLookup_customKeyBeatsDefaultAlias() {
        // “CCTV-1”既是自定义键，又是默认别名（挂在 CCTV-3 下）：自定义键赢
        val custom = linkedMapOf("CCTV-1" to listOf("A"))
        val default = linkedMapOf("CCTV-3" to listOf("CCTV-1"))
        val lookup = ChannelAlias.buildAliasLookup(custom, default)
        assertEquals("CCTV-1", lookup["cctv-1"])
    }

    @Test
    fun aliasLookup_firstEntryWins() {
        // 同一个别名挂在两个自定义键下，先出现的键赢
        val custom = linkedMapOf(
            "CCTV-1" to listOf("共用别名"),
            "CCTV-2" to listOf("共用别名"),
        )
        val lookup = ChannelAlias.buildAliasLookup(custom, emptyMap())
        assertEquals("CCTV-1", lookup["共用别名"])
    }

    @Test
    fun aliasLookup_unknown_returnsNull() {
        val lookup = ChannelAlias.buildAliasLookup(customMap, defaultMap)
        assertNull(lookup["不存在的台"])
    }
}
