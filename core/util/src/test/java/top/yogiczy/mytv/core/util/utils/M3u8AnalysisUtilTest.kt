package top.yogiczy.mytv.core.util.utils

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * m3u8 预览取帧：唯一能在单测里钉住的是入口的后缀门——
 * 非 m3u8 地址直接回 null，不联网、不碰 MediaMetadataRetriever。
 * 后面的取文本、找 ts、取帧都要真实网络与 Android 环境，单测里测不了。
 */
class M3u8AnalysisUtilTest {
    @Test
    fun getFirstFrame_nonM3u8Url_returnsNullWithoutNetwork() = runBlocking {
        // 不是 .m3u8 结尾：后缀门直接拦下，10 秒超时、取帧都走不到
        assertNull(M3u8AnalysisUtil.getFirstFrame("http://example.com/video.mp4"))
        assertNull(M3u8AnalysisUtil.getFirstFrame("http://example.com/stream.m3u"))
        assertNull(M3u8AnalysisUtil.getFirstFrame(""))
    }

    @Test
    fun getFirstFrame_m3u8OnlyInQuery_returnsNull() = runBlocking {
        // .m3u8 只在问号后面：先按 "?" 切、只看前面那段，后缀门照样拦下
        assertNull(M3u8AnalysisUtil.getFirstFrame("http://example.com/video.mp4?file=x.m3u8"))
    }
}
