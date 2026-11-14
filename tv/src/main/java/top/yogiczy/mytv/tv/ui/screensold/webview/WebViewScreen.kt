package top.yogiczy.mytv.tv.ui.screensold.webview

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.view.MotionEvent
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.CookieManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import top.yogiczy.mytv.tv.ui.material.Visibility
import top.yogiczy.mytv.tv.ui.screensold.webview.components.WebViewPlaceholder
import top.yogiczy.mytv.core.data.utils.Logger
import top.yogiczy.mytv.tv.ui.screen.settings.settingsVM
import java.net.URI
import org.json.JSONObject

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebViewScreen(
    modifier: Modifier = Modifier,
    urlProvider: () -> Pair<String, String> = { Pair("", "") },
    onVideoResolutionChanged: (width: Int, height: Int) -> Unit = { _, _ -> },
) {
    val (url, httpUserAgent) = urlProvider()
    var placeholderVisible by remember { mutableStateOf(true) }
    var placeholderMessage by remember { mutableStateOf("正在加载...") }
    val logger = remember { Logger.create("WebViewScreen") }

    // 处理webview://前缀
    val actualUrl = remember(url) {
        val processedUrl = if (url.startsWith("webview://")) {
            logger.i("检测到webview://前缀，正在处理WebView URL")
            url.substring("webview://".length)
        } else {
            url
        }
        logger.i("WebView加载URL: $processedUrl")
        processedUrl
    }
    
    val onUpdatePlaceholderVisible = { visible: Boolean, message: String ->
        placeholderVisible = visible
        placeholderMessage = message
    }
    var cookies:List<String> = emptyList()
    if (actualUrl.contains("yangshipin.cn")){
        cookies = settingsVM.iptvHybridYangshipinCookie.split(";")
    }
    val urlHost = getURLHost(actualUrl)
    // 黑名单要读 assets 并解析 JSON：每次重组都读一次是主线程 I/O，
    // 只在 host 变化时读一次
    val context = getContext()
    val blacklist = remember(urlHost) { readBlacklist(context, urlHost) }
    // 只在地址变了时才加载：AndroidView 的 update 每次重组都跑，
    // 占位文案一变就会重组，原先每次重组都 loadUrl，网页反复重载、放不下去；
    // 记的是“上次要求加载的地址”——页面跳转后当前地址会变，用当前地址比对
    // 会在下次重组时又把原地址重载一遍
    val lastLoadedUrl = remember { object { var value: String? = null } }
    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxHeight()
                .background(Color.Black.copy(alpha = 0.5f)),
            factory = {
                MyWebView(it).apply {
                    webViewClient = MyClient(
                        onPageStarted = { 
                            placeholderVisible = true
                            placeholderMessage = "正在加载网页，请稍候..."
                            logger.i("WebView开始加载页面")
                            // placeholderVisible = false
                        },
                        onPageFinished = { 
                            placeholderMessage = "网页页面加载完成，正在初始化..."
                            logger.i("WebView页面加载完成")
                            // placeholderVisible = false
                        },
                        blacklist = blacklist,
                    )
                    val cookieManager = CookieManager.getInstance()
                    cookieManager.setAcceptCookie(true)
                    cookieManager.setAcceptThirdPartyCookies(this, true)
                    cookies.map { it.trim() }.filter { it.isNotEmpty() }.forEach { cookie ->
                        cookieManager.setCookie(".yangshipin.cn", cookie)
                    }
                    cookieManager.flush()
                    
                    setBackgroundColor(Color.Black.toArgb())
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )

                    settings.javaScriptEnabled = true
                    settings.useWideViewPort = true
                    settings.loadWithOverviewMode = true
                    settings.domStorageEnabled = true
                    settings.loadsImagesAutomatically = false
                    settings.blockNetworkImage = true
                    settings.userAgentString = httpUserAgent
                    settings.cacheMode = WebSettings.LOAD_DEFAULT
                    settings.javaScriptCanOpenWindowsAutomatically = true
                    settings.setSupportZoom(false)
                    settings.displayZoomControls = false
                    settings.builtInZoomControls = false
                    settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                    settings.mediaPlaybackRequiresUserGesture = false
                    isHorizontalScrollBarEnabled = false
                    isVerticalScrollBarEnabled = false
                    isClickable = false
                    isFocusable = false
                    isLongClickable = false
                    isFocusableInTouchMode = false

                    addJavascriptInterface(
                        MyWebViewInterface(
                            onVideoResolutionChanged = onVideoResolutionChanged,
                            onUpdatePlaceholderVisible = onUpdatePlaceholderVisible,
                        ), "Android"
                    )
                }
            },
            update = {
                if (lastLoadedUrl.value != actualUrl) {
                    lastLoadedUrl.value = actualUrl
                    it.loadUrl(actualUrl)
                }
            },
            // 离开组合时必须销毁：WebView 不调 destroy() 不会释放渲染器
            // 等原生资源；切出台（Visibility 收起）或播放模式一变（外层
            // key 换）都会重建一个，不销毁就越积越多
            onRelease = { it.destroy() },
        )

        Visibility({ placeholderVisible }) {
            WebViewPlaceholder(message = placeholderMessage)
        }
    }
}

class MyClient(
    private val onPageStarted: () -> Unit,
    private val onPageFinished: () -> Unit,
    private val blacklist: List<String> = emptyList(),
) : WebViewClient() {
    private val logger = Logger.create("WebViewClient")
    
    override fun shouldInterceptRequest(
        view: WebView?,
        request: WebResourceRequest?
    ): WebResourceResponse? {
        val url = request?.url.toString() ?: ""
        // 只拦 favicon 文件：旧实现用 url.contains(".ico")，会误拦
        // `.icon`/`.icons` 这类路径的正常资源
        if (isFaviconUrl(url)) {
            return WebResourceResponse("text", "UTF-8", null) // 返回空响应以阻止加载
        }
        if (blacklist.isNotEmpty()) {
            for (item in blacklist) {
                if (url.contains(item)) {
                    return WebResourceResponse("text", "UTF-8", null) // 返回空响应以阻止加载
                }
            }
        }
        return super.shouldInterceptRequest(view, request)
    }

    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
        logger.i("WebView页面开始加载: $url")
        onPageStarted()
        super.onPageStarted(view, url, favicon)
    }

    override fun onPageFinished(view: WebView, url: String) {
        onPageFinished()
        val scriptContent = readAssetFile(view.context, "webview_player_impl.js")
        logger.i("注入脚本到WebView")
        view.evaluateJavascript(scriptContent.trimIndent()
        ) {
            logger.i("脚本注入完成")
        }
        logger.i("WebView页面注入完成: $url")
    }
}

class MyWebView(context: Context) : WebView(context) {
    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent?): Boolean {
        return false
    }
}

class MyWebViewInterface(
    private val onVideoResolutionChanged: (width: Int, height: Int) -> Unit = { _, _ -> },
    private val onUpdatePlaceholderVisible: (visible: Boolean, message: String) -> Unit,
) {
    @JavascriptInterface
    fun changeVideoResolution(width: Int, height: Int) {
        onVideoResolutionChanged(width, height)
        onUpdatePlaceholderVisible(false, "")
    }

    @JavascriptInterface
    fun updatePlaceholderVisible(visible: Boolean, message: String) {
        onUpdatePlaceholderVisible(visible, message)
    }
}

fun readAssetFile(context: Context, fileName: String): String {
    // read(byte[]) 不保证一次读满：资源在包里是压缩存放的，解压流一次
    // 可能只吐出一部分，返回值丢弃后 buffer 尾部就留着没填上的零字节；
    // 改成读到文件尾为止，use 保证不管成败都关流
    return context.assets.open(fileName).use { it.readBytes().toString(Charsets.UTF_8) }
}

fun readBlacklist(context: Context, urlHost: String): List<String> {
    val jsonString = readAssetFile(context, "webview_loading_blacklist.json")
    val jsonObject = JSONObject(jsonString)
    val items = mutableListOf<String>()
    jsonObject.keys().forEach { key ->
        if (key == urlHost) {
            val list = jsonObject.getJSONArray(key)
            for (i in 0 until list.length()) {
                items.add(list.getString(i))
            }
        }
    }
    return items
}

fun getURLHost(url: String): String {
    // 非法 URL（如含空格）会让 URI 抛异常：组合期间抛会导致崩溃，返回空串
    // 让黑名单匹配不到即可
    return runCatching { URI(url).host ?: "" }.getOrDefault("")
}

@Composable
fun getContext(): Context {
    return LocalContext.current
}

/**
 * 是否为 favicon 请求：路径以 .ico 结尾（可带查询串/锚点）才拦；
 * 不要用子串匹配，`.icon`/`.icons` 路径的正常资源也会含 ".ico"
 */
internal fun isFaviconUrl(url: String): Boolean {
    return url.substringBefore("?").substringBefore("#").endsWith(".ico", ignoreCase = true)
}