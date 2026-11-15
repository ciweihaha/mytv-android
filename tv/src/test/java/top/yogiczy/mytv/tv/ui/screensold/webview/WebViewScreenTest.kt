package top.yogiczy.mytv.tv.ui.screensold.webview

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * WebView 只拦 favicon：旧实现用 url.contains(".ico")，会误拦
 * `.icon`/`.icons` 这类路径的正常资源；isFaviconUrl 只认路径
 * 以 .ico 结尾（可带查询串/锚点），把这条线钉死。
 */
class WebViewScreenTest {
    @Test
    fun `ico 结尾的 favicon 请求要拦`() {
        assertTrue(isFaviconUrl("https://example.com/favicon.ico"))
        assertTrue(isFaviconUrl("https://example.com/favicon.ico?v=1"))
        assertTrue(isFaviconUrl("https://example.com/favicon.ico#frag"))
        assertTrue(isFaviconUrl("https://example.com/FAVICON.ICO"))
    }

    @Test
    fun `icon 与 icons 路径的正常资源不拦`() {
        assertFalse(isFaviconUrl("https://example.com/foo.icon"))
        assertFalse(isFaviconUrl("https://example.com/icons/logo.png"))
        assertFalse(isFaviconUrl("https://example.com/icon/logo.png"))
        assertFalse(isFaviconUrl("https://example.com/favicon.ico.png"))
    }

    @Test
    fun `普通页面与空串不拦`() {
        assertFalse(isFaviconUrl("https://example.com/index.html"))
        assertFalse(isFaviconUrl(""))
    }

    @Test
    fun `正常地址取 host`() {
        assertEquals("example.com", getURLHost("https://example.com/path"))
        assertEquals("example.com", getURLHost("https://example.com:8080/x"))
        assertEquals("sub.example.com", getURLHost("http://sub.example.com/"))
    }

    @Test
    fun `非法地址返空串不崩`() {
        assertEquals("", getURLHost("ht tp://example.com"))
        assertEquals("", getURLHost("http://exa mple.com/a"))
    }

    @Test
    fun `无 host 的地址返空串`() {
        assertEquals("", getURLHost(""))
        assertEquals("", getURLHost("foo/bar"))
        assertEquals("", getURLHost("file:///android_asset/index.html"))
    }
}
