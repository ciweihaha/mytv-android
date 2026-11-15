package top.yogiczy.mytv.tv.ui.screensold.videoplayer

import org.junit.Assert.assertEquals
import org.junit.Test

class VideoPlayerDisplayModeTest {
    @Test
    fun fromValue_allKnownValues_mapCorrectly() {
        // 6 种画面比例全按 value 对上：SP 里存的整数就是这一张表
        for (mode in VideoPlayerDisplayMode.entries) {
            assertEquals(mode, VideoPlayerDisplayMode.fromValue(mode.value))
        }
    }

    @Test
    fun fromValue_unknownValue_fallsBackToOriginal() {
        // 存的值越界（旧版本删掉的比例、SP 被写坏）：兜底成“原始”，
        // 不能抛错、也不能悄悄变成别的比例
        assertEquals(
            VideoPlayerDisplayMode.ORIGINAL,
            VideoPlayerDisplayMode.fromValue(999),
        )
        assertEquals(
            VideoPlayerDisplayMode.ORIGINAL,
            VideoPlayerDisplayMode.fromValue(-1),
        )
        assertEquals(
            VideoPlayerDisplayMode.ORIGINAL,
            VideoPlayerDisplayMode.fromValue(6),
        )
    }
}
