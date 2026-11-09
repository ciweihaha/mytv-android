package top.yogiczy.mytv.core.util.utils

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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
    suspend fun downloadTo(url: String, filePath: String, onProgressCb: ((Int) -> Unit)?) =
        withContext(Dispatchers.IO) {
            val interceptor = Interceptor { chain ->
                val originalResponse = chain.proceed(chain.request())
                originalResponse.newBuilder()
                    .body(DownloadResponseBody(originalResponse, onProgressCb)).build()
            }

            val client = OkHttpClient.Builder().addNetworkInterceptor(interceptor).build()
            val request = okhttp3.Request.Builder().url(url).build()

            val targetFile = File(filePath)
            // 先下到同目录临时文件，成功才 rename 覆盖目标：原先直接写目标文件，
            // 下载中途失败会留下残缺文件，而更新页打开时按“文件存在即安装”，
            // 会拿残缺的 APK 去安装（报解析失败）
            val tmpFile = File(targetFile.absoluteFile.parent, "${targetFile.name}.tmp")
            try {
                val response = try {
                    client.newCall(request).execute()
                } catch (ex: Exception) {
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
                throw ex
            }
        }

    private class DownloadResponseBody(
        private val originalResponse: okhttp3.Response,
        private val onProgressCb: ((Int) -> Unit)?,
    ) : okhttp3.ResponseBody() {
        override fun contentLength() = originalResponse.body?.contentLength() ?: -1

        override fun contentType() = originalResponse.body?.contentType()

        override fun source(): BufferedSource {
            val body = originalResponse.body
                ?: throw Exception("下载文件失败：服务器返回空数据")
            return object : ForwardingSource(body.source()) {
                var totalBytesRead = 0L

                override fun read(sink: okio.Buffer, byteCount: Long): Long {
                    val bytesRead = super.read(sink, byteCount)
                    totalBytesRead += if (bytesRead != -1L) bytesRead else 0
                    val progress = calcDownloadProgress(totalBytesRead, contentLength())
                    CoroutineScope(Dispatchers.IO).launch {
                        onProgressCb?.invoke(progress)
                    }
                    return bytesRead
                }
            }.buffer()
        }
    }
}
