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
) = request(builder) { response, _ -> response.body?.let { action(it) } }

suspend fun <T> String.request(
    builder: (Request.Builder) -> Request.Builder = { it -> it },
    action: suspend CoroutineScope.(Response, Request) -> T,
): T {
    val url = this

    return withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .let(builder)
            .build()

        val response = sharedClient.newCall(request).await()

        if (!response.isSuccessful) {
            // 错误分支原来不关 response：body 不关连接无法回池，
            // 共用客户端后会把池里的连接漏光
            response.closeQuietly()
            throw Exception("${response.code}: ${response.message}")
        }

        action(response, response.request)
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