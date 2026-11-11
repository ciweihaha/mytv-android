package top.yogiczy.mytv.core.data.repositories.epg.fetcher

import kotlinx.coroutines.runBlocking
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream

/**
 * 节目单解析器 URL 识别测试。
 * 旧实现用 url.endsWith(".xml" / ".gz")，带 token 查询串（如
 * epg.xml.gz?token=xxx）的地址会被误判为不支持，走缺省解析后节目单为空。
 */
class EpgFetcherTest {
    private val xmlFetcher = XmlEpgFetcher()
    private val gzFetcher = XmlGzEpgFetcher()
    private val defaultFetcher = DefaultEpgFetcher()

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

    private fun gzipBytes(text: String): ByteArray {
        val out = ByteArrayOutputStream()
        GZIPOutputStream(out).use { it.write(text.toByteArray(Charsets.UTF_8)) }
        return out.toByteArray()
    }

    @Test
    fun gz_gzipBody_decompressed() = runBlocking {
        val xml = "<?xml version=\"1.0\"?><tv></tv>"
        val stream = gzFetcher.fetch(gzipBytes(xml).toResponseBody())
        assertEquals(xml, stream.readBytes().toString(Charsets.UTF_8))
    }

    @Test
    fun gz_plainXmlBody_passedThrough() = runBlocking {
        // 服务端对 .gz 地址返回明文 XML 时旧实现直接抛“Not in GZIP format”，
        // 整个节目单加载失败；应原样返回让后续 XML 解析处理
        val xml = "<?xml version=\"1.0\"?><tv></tv>"
        val stream = gzFetcher.fetch(xml.toByteArray(Charsets.UTF_8).toResponseBody())
        assertEquals(xml, stream.readBytes().toString(Charsets.UTF_8))
    }

    @Test
    fun gz_emptyBody_noThrow() = runBlocking {
        val stream = gzFetcher.fetch(ByteArray(0).toResponseBody())
        assertEquals(0, stream.readBytes().size)
    }

    @Test
    fun gz_singleByteBody_noThrow() = runBlocking {
        // 只有 1 个字节时 mark/reset 不能丢数据
        val bytes = gzFetcher.fetch(byteArrayOf(0x1f).toResponseBody()).readBytes()
        assertEquals(1, bytes.size)
        assertEquals(0x1f.toByte(), bytes[0])
    }

    @Test
    fun default_noSuffixXml_passedThrough() = runBlocking {
        // 不带后缀的节目单地址走缺省解析：内容是 XML 时应原样交给解析，
        // 而不是返回一段空 XML 让节目单永远为空
        assertTrue(defaultFetcher.isSupport("http://example.com/epg"))
        val xml = "<?xml version=\"1.0\"?><tv></tv>"
        val stream = defaultFetcher.fetch(xml.toByteArray(Charsets.UTF_8).toResponseBody())
        assertEquals(xml, stream.readBytes().toString(Charsets.UTF_8))
    }

    @Test
    fun default_nonXmlBody_passedThrough() = runBlocking {
        // 非 XML 内容也原样返回：解析失败时由上层走旧缓存兜底，
        // 不在这里提前丢弃
        val body = "<html>not epg</html>"
        val stream = defaultFetcher.fetch(body.toByteArray(Charsets.UTF_8).toResponseBody())
        assertEquals(body, stream.readBytes().toString(Charsets.UTF_8))
    }
}
