package top.yogiczy.mytv.tv.voice

import org.junit.Assert.assertEquals
import org.junit.Test

class FreeTranslatorTest {
    @Test
    fun detectSubtitleLanguage_chinese() {
        assertEquals(SubtitleLanguage.CHINESE, detectSubtitleLanguage("你好，欢迎收看新闻联播"))
        assertEquals(SubtitleLanguage.CHINESE, detectSubtitleLanguage("今天天气不错 Hello"))
    }

    @Test
    fun detectSubtitleLanguage_english() {
        assertEquals(SubtitleLanguage.ENGLISH, detectSubtitleLanguage("Good evening, welcome to BBC News"))
        assertEquals(SubtitleLanguage.ENGLISH, detectSubtitleLanguage("Breaking news from London"))
    }

    @Test
    fun detectSubtitleLanguage_unknown() {
        // 太短、没字母：认不出来，不翻
        assertEquals(SubtitleLanguage.UNKNOWN, detectSubtitleLanguage(""))
        assertEquals(SubtitleLanguage.UNKNOWN, detectSubtitleLanguage("123"))
        assertEquals(SubtitleLanguage.UNKNOWN, detectSubtitleLanguage("..."))
        assertEquals(SubtitleLanguage.UNKNOWN, detectSubtitleLanguage("Hi"))
    }
}
