package top.yogiczy.mytv.tv.ui.screensold.videoplayer.player

import androidx.media3.common.C
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 容器重试顺序：HLS → DASH → RTSP → OTHER → SS，全试过回 null。
 * 顺序是契约——先试最常见的 HLS，再试其他；监听器里原来那串
 * if/else 抽成顶层纯函数，顺序写反了这里会先红。
 * 壳比天光多试两种：天光的播放器不接 DASH、只试 HLS/OTHER/RTSP
 * 三种；壳这边 DASH 与 SS 也在兜底表里，是 deliberate 差异。
 */
class Media3ContainerRetryTest {
    @Test
    fun `空表先试 HLS`() {
        assertEquals(C.CONTENT_TYPE_HLS, nextUntriedContentType(emptyMap()))
    }

    @Test
    fun `试过 HLS 后试 DASH`() {
        assertEquals(
            C.CONTENT_TYPE_DASH,
            nextUntriedContentType(mapOf(C.CONTENT_TYPE_HLS to true)),
        )
    }

    @Test
    fun `试过 HLS 与 DASH 后试 RTSP`() {
        assertEquals(
            C.CONTENT_TYPE_RTSP,
            nextUntriedContentType(
                mapOf(C.CONTENT_TYPE_HLS to true, C.CONTENT_TYPE_DASH to true),
            ),
        )
    }

    @Test
    fun `试过 HLS DASH RTSP 后试 OTHER`() {
        assertEquals(
            C.CONTENT_TYPE_OTHER,
            nextUntriedContentType(
                mapOf(
                    C.CONTENT_TYPE_HLS to true,
                    C.CONTENT_TYPE_DASH to true,
                    C.CONTENT_TYPE_RTSP to true,
                ),
            ),
        )
    }

    @Test
    fun `试过前四种后试 SS`() {
        assertEquals(
            C.CONTENT_TYPE_SS,
            nextUntriedContentType(
                mapOf(
                    C.CONTENT_TYPE_HLS to true,
                    C.CONTENT_TYPE_DASH to true,
                    C.CONTENT_TYPE_RTSP to true,
                    C.CONTENT_TYPE_OTHER to true,
                ),
            ),
        )
    }

    @Test
    fun `五种全试过回 null`() {
        assertNull(
            nextUntriedContentType(
                mapOf(
                    C.CONTENT_TYPE_HLS to true,
                    C.CONTENT_TYPE_DASH to true,
                    C.CONTENT_TYPE_RTSP to true,
                    C.CONTENT_TYPE_OTHER to true,
                    C.CONTENT_TYPE_SS to true,
                ),
            ),
        )
    }

    @Test
    fun `记 false 当没试过`() {
        assertEquals(
            C.CONTENT_TYPE_HLS,
            nextUntriedContentType(mapOf(C.CONTENT_TYPE_HLS to false)),
        )
    }
}
