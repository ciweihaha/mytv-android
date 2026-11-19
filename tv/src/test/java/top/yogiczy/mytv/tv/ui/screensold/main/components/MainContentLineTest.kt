package top.yogiczy.mytv.tv.ui.screensold.main.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import top.yogiczy.mytv.core.data.entities.channel.ChannelLine
import top.yogiczy.mytv.core.data.entities.channel.ChannelLineList

/**
 * 自动选线跳过网页线（切台跳央视网页修法）：
 * 播失败自动换线、切台默认选线都只走能播的线，绝不自动落到 WebView
 * 线上；网页线只留给用户在换线列表里手动点选。
 */
class MainContentLineTest {
    private fun playable(url: String) = ChannelLine(url = url)

    private fun webView(url: String) =
        ChannelLine(url = url, hybridType = ChannelLine.HybridType.WebView)

    // 线路表：直播优先模式 [m3u8, 官网, 央视频]，网页优先模式反过来
    private fun iptvFirstLines() = ChannelLineList(
        listOf(
            playable("https://example.com/cctv1.m3u8"),
            webView("https://tv.cctv.com/live/cctv1/"),
            webView("https://yangshipin.cn/tv/home?pid=600001859"),
        )
    )

    private fun hybridFirstLines() = ChannelLineList(
        listOf(
            webView("https://tv.cctv.com/live/cctv1/"),
            webView("https://yangshipin.cn/tv/home?pid=600001859"),
            playable("https://example.com/cctv1.m3u8"),
        )
    )

    @Test
    fun `播失败换线时跳过中间的网页线`() {
        val lines = ChannelLineList(
            listOf(
                playable("https://example.com/cctv1.m3u8"),
                webView("https://tv.cctv.com/live/cctv1/"),
                playable("https://example.com/cctv1-backup.m3u8"),
            )
        )
        assertEquals(2, nextPlayableLineIdx(lines, 0))
    }

    @Test
    fun `西藏卫视那种两条线时播失败不跳网页`() {
        // 平板实测：西藏卫视只有 [m3u8, 央视频网页线] 两条，
        // m3u8 一失败原来 +1 就落到网页线上、跳出央视网页；
        // 现在后面没有能播的线，返回 null、不换线
        val lines = ChannelLineList(
            listOf(
                playable("https://example.com/xzws.m3u8"),
                webView("https://yangshipin.cn/tv/home?pid=600190403"),
            )
        )
        assertNull(nextPlayableLineIdx(lines, 0))
    }

    @Test
    fun `当前就是网页线时往后找能播的线`() {
        // 网页优先顺序 [官网, 央视频, m3u8]，从 0 号往后找要跳过 1 号央视频、落到 2 号 m3u8
        assertEquals(2, nextPlayableLineIdx(hybridFirstLines(), 0))
    }

    @Test
    fun `连续多条网页线都跳过`() {
        val lines = ChannelLineList(
            listOf(
                playable("https://example.com/a.m3u8"),
                webView("https://tv.cctv.com/live/cctv1/"),
                webView("https://yangshipin.cn/tv/home?pid=600001859"),
                playable("https://example.com/b.m3u8"),
            )
        )
        assertEquals(3, nextPlayableLineIdx(lines, 0))
    }

    @Test
    fun `已经在末条线时返回 null`() {
        assertNull(nextPlayableLineIdx(iptvFirstLines(), 2))
    }

    @Test
    fun `空列表返回 null`() {
        assertNull(nextPlayableLineIdx(ChannelLineList(), 0))
    }

    @Test
    fun `全是网页线时返回 null`() {
        val lines = ChannelLineList(
            listOf(
                webView("https://tv.cctv.com/live/cctv1/"),
                webView("https://yangshipin.cn/tv/home?pid=600001859"),
            )
        )
        assertNull(nextPlayableLineIdx(lines, 0))
    }

    @Test
    fun `网页优先顺序切台默认选线跳过前面的网页线`() {
        assertEquals(2, firstPlayableLineIdx(hybridFirstLines()))
    }

    @Test
    fun `直播优先时第一条就是能播的线`() {
        assertEquals(0, firstPlayableLineIdx(iptvFirstLines()))
    }

    @Test
    fun `全是网页线时回 0 保持原行为`() {
        val lines = ChannelLineList(
            listOf(
                webView("https://tv.cctv.com/live/cctv1/"),
                webView("https://yangshipin.cn/tv/home?pid=600001859"),
            )
        )
        assertEquals(0, firstPlayableLineIdx(lines))
    }

    @Test
    fun `空列表回 0`() {
        assertEquals(0, firstPlayableLineIdx(ChannelLineList()))
    }
}
