package top.yogiczy.mytv.core.data.network

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import okhttp3.internal.closeQuietly
import java.io.IOException
import kotlin.coroutines.resumeWithException

@OptIn(ExperimentalCoroutinesApi::class)
suspend fun Call.await(): Response = suspendCancellableCoroutine { continuation ->
    enqueue(
        object : Callback {
            override fun onResponse(call: Call, response: Response) {
                continuation.resume(response) {
                    if (response.body != null) {
                        response.closeQuietly()
                    }
                }
            }

            override fun onFailure(call: Call, e: IOException) {
                continuation.resumeWithException(e)
            }
        }
    )

    continuation.invokeOnCancellation {
        try {
            cancel()
        } catch (t: Throwable) {
            // Ignore cancel exception
        }
    }
}

suspend fun <T> String.request(
    builder: (Request.Builder) -> Request.Builder = { it -> it },
    action: suspend CoroutineScope.(ResponseBody) -> T,
) = requestWithClient(sharedClient, builder) { response, _ -> response.body?.let { action(it) } }

suspend fun <T> String.request(
    builder: (Request.Builder) -> Request.Builder = { it -> it },
    action: suspend CoroutineScope.(Response, Request) -> T,
): T = requestWithClient(sharedClient, builder, action)

/**
 * 严格校验 TLS 证书的请求：更新检查（返回 APK 下载地址）和 Gist 云同步
 * （请求头带 token）走这里。共用客户端信任全部证书是为兼容用户自配的
 * 直播源/自建 WebDAV 的自签证书，但更新接口被中间人篡改可指向恶意 APK、
 * token 也会被窃取；GitHub/Gitee API 都是公开 CA 签发，严格校验不影响使用。
 */
suspend fun <T> String.requestStrict(
    builder: (Request.Builder) -> Request.Builder = { it -> it },
    action: suspend CoroutineScope.(ResponseBody) -> T,
) = requestWithClient(strictClient, builder) { response, _ -> response.body?.let { action(it) } }

suspend fun <T> String.requestStrict(
    builder: (Request.Builder) -> Request.Builder = { it -> it },
    action: suspend CoroutineScope.(Response, Request) -> T,
): T = requestWithClient(strictClient, builder, action)

private suspend fun <T> String.requestWithClient(
    client: OkHttpClient,
    builder: (Request.Builder) -> Request.Builder,
    action: suspend CoroutineScope.(Response, Request) -> T,
): T {
    val url = this

    return withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .let(builder)
            .build()

        val response = client.newCall(request).await()

        if (!response.isSuccessful) {
            // 错误分支原来不关 response：body 不关连接无法回池，
            // 共用客户端后会把池里的连接漏光
            response.closeQuietly()
            throw Exception("${response.code}: ${response.message}")
        }

        // action 抛错时 response 也要关：成功分支返回的 body 流由调用方
        // 读完再关（节目单就是这么走的），不能提前关；但 action 中途抛了
        // （如节目单拉取读魔数字节时断网），没人再关这个 response，连接
        // 回不了共用池，反复失败会把池漏光
        try {
            action(response, response.request)
        } catch (e: Throwable) {
            response.closeQuietly()
            throw e
        }
    }
}

// OkHttpClient 应全局共用：每次请求新建会丢掉连接复用，且每个客户端
// 自带连接池与线程池；信任全部证书等配置各次调用都相同
private val sharedClient by lazy {
    OkHttpClient.Builder()
        .sslSocketFactory(
            TrustAllSSLSocketFactory.sslSocketFactory,
            TrustAllSSLSocketFactory.trustManager
        )
        .hostnameVerifier { _, _ -> true }
        .followRedirects(true).build()
}

// 严格 TLS 客户端：默认证书校验，只给更新检查 / Gist 同步用
private val strictClient by lazy {
    OkHttpClient.Builder().followRedirects(true).build()
}