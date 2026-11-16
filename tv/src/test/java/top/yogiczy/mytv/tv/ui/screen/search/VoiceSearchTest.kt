package top.yogiczy.mytv.tv.ui.screen.search

import android.speech.RecognizerIntent
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VoiceSearchTest {
    @Test
    fun pickVoiceText_recognizerResultsFirst() {
        // 系统识别器的结果列表优先
        assertEquals(
            "央视",
            pickVoiceText(
                mapOf(
                    RecognizerIntent.EXTRA_RESULTS to arrayListOf("央视", "央视新闻"),
                    "text" to "卫视",
                )
            )
        )
    }

    @Test
    fun pickVoiceText_fallsBackToTextKeys() {
        // 没有结果列表时依次试厂商常用的文本键
        assertEquals("央视", pickVoiceText(mapOf("text" to "央视")))
        assertEquals("央视", pickVoiceText(mapOf("result" to "央视")))
        assertEquals("央视", pickVoiceText(mapOf("voice_text" to "央视")))
        assertEquals("央视", pickVoiceText(mapOf("keyword" to "央视")))
        assertEquals("央视", pickVoiceText(mapOf("content" to "央视")))
        // text 键优先于 result 键
        assertEquals(
            "央视",
            pickVoiceText(mapOf("result" to "卫视", "text" to "央视"))
        )
    }

    @Test
    fun pickVoiceText_skipsBlankValues() {
        // 空串和空白串都跳过，取后面第一个非空的
        assertEquals(
            "央视",
            pickVoiceText(mapOf("text" to "", "result" to "  ", "keyword" to "央视"))
        )
        assertNull(pickVoiceText(mapOf("text" to "", "result" to "  ")))
        assertNull(pickVoiceText(emptyMap()))
    }

    @Test
    fun pickVoiceText_skipsBlankFirstElementInList() {
        // 877 轮 P3-4：识别结果列表第一个是空白串时，取后面第一个非空的，
        // 别直接换下一个键。
        assertEquals(
            "央视",
            pickVoiceText(mapOf(RecognizerIntent.EXTRA_RESULTS to arrayListOf("", "  ", "央视")))
        )
        assertNull(
            pickVoiceText(mapOf(RecognizerIntent.EXTRA_RESULTS to arrayListOf("", "  ")))
        )
    }

    @Test
    fun pickVoiceText_ignoresNonStringValues() {
        // 非字符串的值不算识别文本
        assertNull(pickVoiceText(mapOf("text" to 123)))
        assertNull(pickVoiceText(mapOf(RecognizerIntent.EXTRA_RESULTS to arrayListOf(123))))
    }

    @Test
    fun voiceSearchBus_secondResultReplacesFirstWhenPageNotOpen() = runBlocking {
        // 搜索页没打开时连按两次语音键：第二次的结果要留下来（DROP_OLDEST），
        // 而不是第一次占着缓冲、第二次被丢掉
        VoiceSearchBus.emit("第一次")
        VoiceSearchBus.emit("第二次")
        val received = withTimeout(2000) { VoiceSearchBus.voiceText.first() }
        assertEquals("第二次", received)
    }
}
