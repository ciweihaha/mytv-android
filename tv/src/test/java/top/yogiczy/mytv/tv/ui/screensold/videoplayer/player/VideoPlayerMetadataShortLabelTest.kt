package top.yogiczy.mytv.tv.ui.screensold.videoplayer.player

import org.junit.Assert.assertEquals
import org.junit.Test
import top.yogiczy.mytv.tv.ui.screensold.videoplayer.player.VideoPlayer.Metadata

/**
 * 选轨短标签：Video/Audio/Subtitle.shortLabel 是选轨列表项与快捷操作按钮
 * 上直接显示的字串，三个纯计算属性之前一个用例都没有。
 * 规则：有值的段拼起来、没值的段丢掉；全空时回落到编码，编码也没有
 * （或只有空白）才回落到“视轨/音轨/字幕 + 序号”。
 */
class VideoPlayerMetadataShortLabelTest {

    @Test
    fun `视频短标签完整值拼三段`() {
        val video = Metadata.Video(
            width = 1920,
            height = 1080,
            frameRate = 25f,
            bitrate = 8_000_000,
            mimeType = "video/hevc",
        )
        assertEquals("1920x1080, 25fps, 8.00Mbps", video.shortLabel)
    }

    @Test
    fun `视频短标签帧率四舍五入取整`() {
        // 29.97 帧的源显示 30fps，不显示小数
        val video = Metadata.Video(width = 1920, height = 1080, frameRate = 29.97f)
        assertEquals("1920x1080, 30fps", video.shortLabel)
    }

    @Test
    fun `视频短标签缺项与零值负值丢弃`() {
        // 只有宽没有高：分辨率段整段不要，只剩帧率
        assertEquals("30fps", Metadata.Video(width = 1280, frameRate = 30f).shortLabel)
        // 帧率 0、码率 0、帧率负数都不是真的 0，丢掉不显示
        assertEquals(
            "1920x1080",
            Metadata.Video(width = 1920, height = 1080, frameRate = 0f, bitrate = 0).shortLabel
        )
        assertEquals(
            "1920x1080",
            Metadata.Video(width = 1920, height = 1080, frameRate = -1f).shortLabel
        )
    }

    @Test
    fun `视频短标签空值回落编码与序号`() {
        // 全空但有编码：显示编码本身
        assertEquals("video/hevc", Metadata.Video(mimeType = "video/hevc").shortLabel)
        // 编码只有空白也算没有：回落到序号（index 从 0 计，显示 +1）
        assertEquals("视轨 3", Metadata.Video(index = 2, mimeType = "  ").shortLabel)
        // 全空：序号默认 1
        assertEquals("视轨 1", Metadata.Video().shortLabel)
    }

    @Test
    fun `音频短标签菁彩声标签优先于声道数`() {
        val audio = Metadata.Audio(
            channels = 6,
            channelsLabel = "菁彩声",
            bitrate = 384_000,
            language = "zh",
        )
        assertEquals("菁彩声, 384Kbps, 中文", audio.shortLabel)
        // 没有标签时声道数按表翻译：6 声道是 5.1 环绕声
        assertEquals("5.1 环绕声", Metadata.Audio(channels = 6).shortLabel)
    }

    @Test
    fun `音频短标签空值回落`() {
        assertEquals("audio/mp4a-latm", Metadata.Audio(mimeType = "audio/mp4a-latm").shortLabel)
        assertEquals("音轨 1", Metadata.Audio().shortLabel)
        // 语言是空串：humanizeLanguage 原样返回 ""，拼出来是空串，
        // 触发回落——不是显示一个空行
        assertEquals("audio/aac", Metadata.Audio(language = "", mimeType = "audio/aac").shortLabel)
        // 码率 0 丢掉，只剩语言
        assertEquals("中文", Metadata.Audio(bitrate = 0, language = "zh").shortLabel)
    }

    @Test
    fun `字幕短标签语言与回落`() {
        assertEquals("英语", Metadata.Subtitle(language = "en").shortLabel)
        assertEquals("text/vtt", Metadata.Subtitle(mimeType = "text/vtt").shortLabel)
        assertEquals("字幕 2", Metadata.Subtitle(index = 1).shortLabel)
        // 语言空串同样触发回落
        assertEquals("字幕 1", Metadata.Subtitle(language = "").shortLabel)
    }
}
