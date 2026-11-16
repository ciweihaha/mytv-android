package top.yogiczy.mytv.tv.ui.utils

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Configs 里剩下五处 SP 枚举兜底（872 轮只钉了 KeyDownAction、
 * CloudSyncProvider、VideoPlayerDisplayMode 三处）：SP 里存的整数
 * → 枚举，越界时各有兜底默认值，且五处都没单测。
 * 越界值现实里会出现：旧版本删掉某个选项、SP 被写坏——兜底值一旦
 * 被改错，时间显示、混合模式、播放器内核、网页内核、渲染方式会
 * 悄悄变掉，不抛错、用户很难发现是哪改的。
 */
class ConfigsEnumFromValueTest {
    @Test
    fun uiTimeShowMode_fromValue_allKnownValues_mapCorrectly() {
        for (mode in Configs.UiTimeShowMode.entries) {
            assertEquals(mode, Configs.UiTimeShowMode.fromValue(mode.value))
        }
    }

    @Test
    fun uiTimeShowMode_fromValue_unknownValue_fallsBackToAlways() {
        assertEquals(Configs.UiTimeShowMode.ALWAYS, Configs.UiTimeShowMode.fromValue(999))
        assertEquals(Configs.UiTimeShowMode.ALWAYS, Configs.UiTimeShowMode.fromValue(-1))
        assertEquals(Configs.UiTimeShowMode.ALWAYS, Configs.UiTimeShowMode.fromValue(4))
    }

    @Test
    fun iptvHybridMode_fromValue_allKnownValues_mapCorrectly() {
        for (mode in Configs.IptvHybridMode.entries) {
            assertEquals(mode, Configs.IptvHybridMode.fromValue(mode.value))
        }
    }

    @Test
    fun iptvHybridMode_fromValue_unknownValue_fallsBackToDisable() {
        assertEquals(Configs.IptvHybridMode.DISABLE, Configs.IptvHybridMode.fromValue(999))
        assertEquals(Configs.IptvHybridMode.DISABLE, Configs.IptvHybridMode.fromValue(-1))
        assertEquals(Configs.IptvHybridMode.DISABLE, Configs.IptvHybridMode.fromValue(3))
    }

    @Test
    fun videoPlayerCore_fromValue_allKnownValues_mapCorrectly() {
        for (mode in Configs.VideoPlayerCore.entries) {
            assertEquals(mode, Configs.VideoPlayerCore.fromValue(mode.value))
        }
    }

    @Test
    fun videoPlayerCore_fromValue_unknownValue_fallsBackToMedia3() {
        assertEquals(Configs.VideoPlayerCore.MEDIA3, Configs.VideoPlayerCore.fromValue(999))
        assertEquals(Configs.VideoPlayerCore.MEDIA3, Configs.VideoPlayerCore.fromValue(-1))
        assertEquals(Configs.VideoPlayerCore.MEDIA3, Configs.VideoPlayerCore.fromValue(2))
    }

    @Test
    fun webViewCore_fromValue_allKnownValues_mapCorrectly() {
        for (mode in Configs.WebViewCore.entries) {
            assertEquals(mode, Configs.WebViewCore.fromValue(mode.value))
        }
    }

    @Test
    fun webViewCore_fromValue_unknownValue_fallsBackToSystem() {
        assertEquals(Configs.WebViewCore.SYSTEM, Configs.WebViewCore.fromValue(999))
        assertEquals(Configs.WebViewCore.SYSTEM, Configs.WebViewCore.fromValue(-1))
        assertEquals(Configs.WebViewCore.SYSTEM, Configs.WebViewCore.fromValue(2))
    }

    @Test
    fun videoPlayerRenderMode_fromValue_allKnownValues_mapCorrectly() {
        for (mode in Configs.VideoPlayerRenderMode.entries) {
            assertEquals(mode, Configs.VideoPlayerRenderMode.fromValue(mode.value))
        }
    }

    @Test
    fun videoPlayerRenderMode_fromValue_unknownValue_fallsBackToSurfaceView() {
        assertEquals(
            Configs.VideoPlayerRenderMode.SURFACE_VIEW,
            Configs.VideoPlayerRenderMode.fromValue(999),
        )
        assertEquals(
            Configs.VideoPlayerRenderMode.SURFACE_VIEW,
            Configs.VideoPlayerRenderMode.fromValue(-1),
        )
        assertEquals(
            Configs.VideoPlayerRenderMode.SURFACE_VIEW,
            Configs.VideoPlayerRenderMode.fromValue(2),
        )
    }
}
