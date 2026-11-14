package top.yogiczy.mytv.core.util.utils

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okio.BufferedSource
import okio.ForwardingSource
import okio.buffer
import java.io.File
import java.io.FileOutputStream

/**
 * 下载进度（0~100）；总长度未知（<=0，如 chunked 传输）时返回 0，避免除零崩溃。
 * 超过 100 时记 99（100 由下载完成方另行表示，与原逻辑一致）。
 */
internal fun calcDownloadProgress(totalBytesRead: Long, contentLength: Long): Int {
    if (contentLength <= 0) return 0
    val progress = (totalBytesRead * 100 / contentLength).toInt()
    return when {
        progress > 100 -> 99
        progress < 0 -> 0
        else -> progress
    }
}

object Downloader {
    // 进度拦截器各次下载不同，但连接池/线程池应共用：newBuilder() 共享
    // 基础客户端的连接池与调度器，而不是每次下载都新建一套
    private val baseClient by lazy { OkHttpClient.Builder().build() }

    suspend fun downloadTo(url: String, filePath: String, onProgressCb: ((Int) -> Unit)?) =
        withContext(Dispatchers.IO) {
            val interceptor = Interceptor { chain ->
                val originalResponse = chain.proceed(chain.request())
                originalResponse.newBuilder()
                    .body(DownloadResponseBody(originalResponse, onProgressCb)).build()
            }

            val client = baseClient.newBuilder().addNetworkInterceptor(interceptor).build()
            val request = okhttp3.Request.Builder().url(url).build()

            val targetFile = File(filePath)
            // 先下到同目录临时文件，成功才 rename 覆盖目标：原先直接写目标文件，
            // 下载中途失败会留下残缺文件，而更新页打开时按“文件存在即安装”，
            // 会拿残缺的 APK 去安装（报解析失败）
            val tmpFile = File(targetFile.absoluteFile.parent, "${targetFile.name}.tmp")
            // 取消下载立刻停下来：协程取消只在挂起点生效，OkHttp 的
            // execute() 与 copyTo() 都是阻塞调用、取消送不到；以前用户按
            // 返回离开更新页，下载会在后台把整个安装包下完才停。父协程一取
            // 消就顺手取消这次请求，读流立刻抛错、下载马上停（天光 1673 轮同款问题）
            val call = client.newCall(request)
            val cancelHandle = coroutineContext[Job]?.invokeOnCompletion { call.cancel() }
            try {
                val response = try {
                    call.execute()
                } catch (ex: Exception) {
                    // 被取消的请求会抛错：别包成“下载文件失败”，按取消原样抛，
                    // 不然上层 691 轮那处守卫认不出来、会弹“下载更新失败”假报错
                    if (!isActive) throw CancellationException("下载已取消", ex)
                    throw Exception("下载文件失败，请检查网络连接", ex)
                }

                response.use {
                    if (!it.isSuccessful) throw Exception("下载文件失败: ${it.code}")

                    // 流式写入：原先用 body.bytes() 把整个文件读进内存再写，
                    // 下百 MB 的 APK 时低端电视盒子容易 OOM
                    val body = it.body ?: throw Exception("下载文件失败：服务器返回空数据")
                    FileOutputStream(tmpFile).use { fos ->
                        body.byteStream().use { input -> input.copyTo(fos) }
                    }
                }

                if (!tmpFile.renameTo(targetFile)) {
                    // 极少数文件系统 rename 失败时回退为覆盖复制
                    tmpFile.copyTo(targetFile, overwrite = true)
                    tmpFile.delete()
                }
            } catch (ex: Exception) {
                tmpFile.delete()
                // 读流中途被取消同样走这里：协程已取消就按取消抛，别当成下载失败
                if (ex !is CancellationException && !isActive) {
                    throw CancellationException("下载已取消", ex)
                }
                throw ex
            } finally {
                cancelHandle?.dispose()
            }
        }

    private class DownloadResponseBody(
        private val originalResponse: okhttp3.Response,
        private val onProgressCb: ((Int) -> Unit)?,
    ) : okhttp3.ResponseBody() {
        // 进度回调的协程作用域：原来每次 read()（每 8KB 一次）都新建一个，
        // 百 MB 下载要建上万个；一个下载共用一个即可
        private val progressScope = CoroutineScope(Dispatchers.IO)

        override fun contentLength() = originalResponse.body?.contentLength() ?: -1

        override fun contentType() = originalResponse.body?.contentType()

        override fun source(): BufferedSource {
            val body = originalResponse.body
                ?: throw Exception("下载文件失败：服务器返回空数据")
            return object : ForwardingSource(body.source()) {
                var totalBytesRead = 0L
                var lastProgress = -1

                override fun read(sink: okio.Buffer, byteCount: Long): Long {
                    val bytesRead = super.read(sink, byteCount)
                    totalBytesRead += if (bytesRead != -1L) bytesRead else 0
                    val progress = calcDownloadProgress(totalBytesRead, contentLength())
                    // 百分比不变时不回调：原来每读 8KB 就调一次，100MB 下载要
                    // 触发上万次 Snackbar 状态更新；百分比最多变 100 次
                    if (progress != lastProgress) {
                        lastProgress = progress
                        progressScope.launch {
                            onProgressCb?.invoke(progress)
                        }
                    }
                    return bytesRead
                }
            }.buffer()
        }
    }
}
