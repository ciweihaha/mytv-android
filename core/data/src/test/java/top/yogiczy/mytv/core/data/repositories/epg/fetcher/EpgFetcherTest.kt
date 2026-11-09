package top.yogiczy.mytv.core.data.repositories.epg.fetcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 节目单解析器 URL 识别测试。
 * 旧实现用 url.endsWith(".xml" / ".gz")，带 token 查询串（如
 * epg.xml.gz?token=xxx）的地址会被误判为不支持，走缺省解析后节目单为空。
 */
class EpgFetcherTest {
    private val xmlFetcher = XmlEpgFetcher()
    private val gzFetcher = XmlGzEpgFetcher()

    @Test
    fun xml_plain() {
        assertTrue(xmlFetcher.isSupport("http://example.com/epg.xml"))
        assertFalse(gzFetcher.isSupport("http://example.com/epg.xml"))
    }

    @Test
    fun xml_withQueryString() {
        assertTrue(xmlFetcher.isSupport("http://example.com/epg.xml?token=abc&x=1"))
        assertFalse(gzFetcher.isSupport("http://example.com/epg.xml?token=abc"))
    }

    @Test
    fun gz_plain() {
        assertTrue(gzFetcher.isSupport("http://example.com/epg.xml.gz"))
        assertFalse(xmlFetcher.isSupport("http://example.com/epg.xml.gz"))
    }

    @Test
    fun gz_withQueryStringAndFragment() {
        assertTrue(gzFetcher.isSupport("http://example.com/epg.xml.gz?token=abc&x=1"))
        assertTrue(gzFetcher.isSupport("http://example.com/epg.xml.gz#frag"))
        assertFalse(xmlFetcher.isSupport("http://example.com/epg.xml.gz?token=abc"))
    }

    @Test
    fun suffix_caseInsensitive() {
        assertTrue(xmlFetcher.isSupport("http://example.com/EPG.XML"))
        assertTrue(gzFetcher.isSupport("http://example.com/EPG.XML.GZ"))
    }

    @Test
    fun noSuffix_notSupported() {
        assertFalse(xmlFetcher.isSupport("http://example.com/epg"))
        assertFalse(gzFetcher.isSupport("http://example.com/epg"))
        assertFalse(xmlFetcher.isSupport("http://example.com/epg.json"))
        assertFalse(gzFetcher.isSupport("http://example.com/epg.json"))
    }

    @Test
    fun queryContainingSuffix_doesNotFoolMatcher() {
        // 查询串里出现 .gz 也不应误判
        assertFalse(gzFetcher.isSupport("http://example.com/epg.xml?x=.gz"))
        assertTrue(xmlFetcher.isSupport("http://example.com/epg.xml?x=.gz"))
    }

    @Test
    fun epgUrlPath_stripsQueryAndFragment() {
        assertEquals(
            "http://example.com/epg.xml.gz",
            "http://example.com/epg.xml.gz?token=abc#frag".epgUrlPath(),
        )
        assertEquals("http://example.com/epg.xml", "http://example.com/epg.xml".epgUrlPath())
    }
}
