package top.yogiczy.mytv.core.data.entities.subtitle

import android.graphics.Color
import androidx.media3.ui.CaptionStyleCompat
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import org.junit.Assert.assertEquals
import org.junit.Test
import top.yogiczy.mytv.core.data.utils.Globals

/**
 * 字幕样式序列化往返：CaptionStyleCompat 是 Media3 的类、本身没有
 * @Serializable，样式靠手写的 CaptionStyleCompatSerializer 存 5 个
 * 整数（前景/背景/窗口/描边类型/描边色）；存盘再读回来必须一模一样，
 * 否则用户调好的字幕颜色重启后悄悄变掉。
 */
class VideoPlayerSubtitleStyleTest {
    private val json = Globals.json

    private fun distinctiveStyle() = VideoPlayerSubtitleStyle(
        useSystemDefault = true,
        isApplyEmbeddedStyles = false,
        textSize = 88f,
        style = CaptionStyleCompat(
            Color.RED,
            Color.BLUE,
            Color.GREEN,
            CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW,
            Color.YELLOW,
            null,
        ),
    )

    @Test
    fun `非默认样式序列化再读回来全对上`() {
        val original = distinctiveStyle()
        val restored = json.decodeFromString<VideoPlayerSubtitleStyle>(
            json.encodeToString(original),
        )
        assertEquals(original, restored)
    }

    @Test
    fun `默认样式序列化再读回来不变`() {
        val original = VideoPlayerSubtitleStyle()
        val restored = json.decodeFromString<VideoPlayerSubtitleStyle>(
            json.encodeToString(original),
        )
        assertEquals(original, restored)
    }

    @Test
    fun `手写 JSON 读出五个颜色字段与开关`() {
        val restored = json.decodeFromString<VideoPlayerSubtitleStyle>(
            """{"useSystemDefault":true,"isApplyEmbeddedStyles":false,"textSize":88.0,"style":{"foregroundColor":-65536,"backgroundColor":-16776961,"windowColor":-16711936,"edgeType":2,"edgeColor":-256}}""",
        )
        assertEquals(true, restored.useSystemDefault)
        assertEquals(false, restored.isApplyEmbeddedStyles)
        assertEquals(88f, restored.textSize, 0.001f)
        assertEquals(Color.RED, restored.style.foregroundColor)
        assertEquals(Color.BLUE, restored.style.backgroundColor)
        assertEquals(Color.GREEN, restored.style.windowColor)
        assertEquals(CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW, restored.style.edgeType)
        assertEquals(Color.YELLOW, restored.style.edgeColor)
    }
}
