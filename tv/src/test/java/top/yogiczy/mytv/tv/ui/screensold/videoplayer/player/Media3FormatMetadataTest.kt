package top.yogiczy.mytv.tv.ui.screensold.videoplayer.player

import androidx.media3.common.Format
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import top.yogiczy.mytv.tv.ui.screensold.videoplayer.player.VideoPlayer.Metadata

/**
 * Media3 Format 转界面元数据：三个纯转换之前是 Media3VideoPlayer 的
 * 私有成员函数、单测够不着，906 轮抽到顶层后直接断言。
 * 注意：Video/Audio/Subtitle 的 equals 只比部分字段（颜色、菁彩声
 * 标签、序号、解码器都不在比较里），所以这些字段必须逐字段断言，
 * 整对象 assertEquals 断不出来。
 */
class Media3FormatMetadataTest {

    @Test
    fun `视频元数据完整值原样转`() {
        val format = Format.Builder()
            .setWidth(1920)
            .setHeight(1080)
            .setFrameRate(25f)
            .setAverageBitrate(8_000_000)
            .setSampleMimeType("video/hevc")
            .build()
        val video = format.toVideoMetadata()
        assertEquals(1920, video.width)
        assertEquals(1080, video.height)
        assertEquals(25f, video.frameRate)
        assertEquals(8_000_000, video.bitrate)
        assertEquals("video/hevc", video.mimeType)
        assertNull(video.color)
    }

    @Test
    fun `视频元数据未知值转空不显示负数`() {
        // 默认 Format 全是 NO_VALUE(-1)：不转 null 界面会显示“-1x-1”
        val video = Format.Builder().build().toVideoMetadata()
        assertNull(video.width)
        assertNull(video.height)
        assertNull(video.frameRate)
        assertNull(video.bitrate)
        assertNull(video.mimeType)
        assertNull(video.color)
    }

    @Test
    fun `视频元数据零值转空`() {
        // 注意：setFrameRate(0f) 建不出来——Builder 要求帧率是 NO_VALUE
        // 或 > 0，0 直接抛 IllegalArgumentException；所以 0 帧率这档
        // 在生产代码里只是防御，Builder 造不出这样的 Format
        val format = Format.Builder()
            .setWidth(0)
            .setHeight(0)
            .setAverageBitrate(0)
            .build()
        val video = format.toVideoMetadata()
        assertNull(video.width)
        assertNull(video.height)
        assertNull(video.bitrate)
    }

    @Test
    fun `视频元数据码率取峰值优先于平均值`() {
        // Format.bitrate 的定义：峰值已知取峰值，否则取平均值，否则 NO_VALUE
        val format = Format.Builder()
            .setAverageBitrate(8_000_000)
            .setPeakBitrate(12_000_000)
            .build()
        assertEquals(12_000_000, format.toVideoMetadata().bitrate)
    }

    @Test
    fun `视频元数据保留旧的序号选中与解码器`() {
        val old = Metadata.Video(
            index = 2,
            isSelected = true,
            color = "BT709",
            decoder = "c2.goldfish.h264.decoder",
        )
        val format = Format.Builder()
            .setWidth(1280)
            .setHeight(720)
            .setSampleMimeType("video/avc")
            .build()
        val video = format.toVideoMetadata(old)
        assertEquals(2, video.index)
        assertEquals(true, video.isSelected)
        assertEquals("c2.goldfish.h264.decoder", video.decoder)
        assertEquals(1280, video.width)
        assertEquals(720, video.height)
        // 颜色是无条件覆盖的：Format 没带 colorInfo，旧颜色会被写成 null
        assertNull(video.color)
    }

    @Test
    fun `帧率缺失时用实测渲染帧数补`() {
        // 天光 2289 轮姊妹检查的反向回镜：tag 没给帧率时，1 秒内渲染
        // 的帧数就是帧率的近似值；壳这边帧率是可空，null 与天光的 0f 同义
        assertEquals(24f, supplementFrameRate(null, 24))
    }

    @Test
    fun `卡顿一帧没渲时帧率保持未知`() {
        assertNull(supplementFrameRate(null, 0))
    }

    @Test
    fun `tag 给了帧率时实测值不覆盖`() {
        assertEquals(25f, supplementFrameRate(25f, 24))
    }

    @Test
    fun `音频元数据完整值与菁彩声标签`() {
        val format = Format.Builder()
            .setChannelCount(6)
            .setSampleRate(48_000)
            .setAverageBitrate(384_000)
            .setSampleMimeType("audio/av3a")
            .setLanguage("zh")
            .build()
        val audio = format.toAudioMetadata()
        assertEquals(6, audio.channels)
        assertEquals("菁彩声", audio.channelsLabel)
        assertEquals(48_000, audio.sampleRate)
        assertEquals(384_000, audio.bitrate)
        assertEquals("audio/av3a", audio.mimeType)
        assertEquals("zh", audio.language)
    }

    @Test
    fun `音频元数据非菁彩声不打标签`() {
        val format = Format.Builder()
            .setChannelCount(2)
            .setSampleMimeType("audio/mp4a-latm")
            .build()
        val audio = format.toAudioMetadata()
        assertEquals(2, audio.channels)
        assertNull(audio.channelsLabel)
        assertEquals("audio/mp4a-latm", audio.mimeType)
    }

    @Test
    fun `音频元数据未知值转空并保留旧解码器`() {
        val old = Metadata.Audio(decoder = "c2.aac.decoder")
        val audio = Format.Builder().build().toAudioMetadata(old)
        assertNull(audio.channels)
        assertNull(audio.channelsLabel)
        assertNull(audio.sampleRate)
        assertNull(audio.bitrate)
        assertNull(audio.mimeType)
        assertNull(audio.language)
        assertEquals("c2.aac.decoder", audio.decoder)
    }

    @Test
    fun `音频元数据零值转空`() {
        // 天光 2159 姊妹检查：0 即“没测到”，不是真的 0 声道/0 采样率/
        // 0 码率——转换层用 takeIf { it > 0 } 转 null，界面那行直接不
        // 显示；视频那侧 906 轮已断过零值转空，音频这侧一次也没断过
        // （Builder 只校验帧率，setChannelCount(0)/setSampleRate(0)
        // 都能建出来，见 Media3 Format.java：全文件唯一的 checkArgument
        // 就是 setFrameRate 那行）
        val format = Format.Builder()
            .setChannelCount(0)
            .setSampleRate(0)
            .setAverageBitrate(0)
            .setSampleMimeType("audio/mp4a-latm")
            .setLanguage("zh")
            .build()
        val audio = format.toAudioMetadata()
        assertNull(audio.channels)
        assertNull(audio.sampleRate)
        assertNull(audio.bitrate)
        assertNull(audio.channelsLabel)
        assertEquals("audio/mp4a-latm", audio.mimeType)
        assertEquals("zh", audio.language)
    }

    @Test
    fun `字幕元数据零码率转空`() {
        // 同上：字幕码率 0 也转 null；编码与语言原样保留
        val format = Format.Builder()
            .setAverageBitrate(0)
            .setSampleMimeType("text/vtt")
            .setLanguage("en")
            .build()
        val subtitle = format.toSubtitleMetadata()
        assertNull(subtitle.bitrate)
        assertEquals("text/vtt", subtitle.mimeType)
        assertEquals("en", subtitle.language)
    }

    @Test
    fun `字幕元数据只转码率编码与语言`() {
        val format = Format.Builder()
            .setAverageBitrate(1000)
            .setSampleMimeType("text/vtt")
            .setLanguage("en")
            .build()
        val subtitle = format.toSubtitleMetadata()
        assertEquals(1000, subtitle.bitrate)
        assertEquals("text/vtt", subtitle.mimeType)
        assertEquals("en", subtitle.language)
    }

    @Test
    fun `字幕元数据未知码率转空并保留旧序号`() {
        val old = Metadata.Subtitle(index = 1, isSelected = true)
        val subtitle = Format.Builder().build().toSubtitleMetadata(old)
        assertNull(subtitle.bitrate)
        assertNull(subtitle.mimeType)
        assertNull(subtitle.language)
        assertEquals(1, subtitle.index)
        assertEquals(true, subtitle.isSelected)
    }
}
