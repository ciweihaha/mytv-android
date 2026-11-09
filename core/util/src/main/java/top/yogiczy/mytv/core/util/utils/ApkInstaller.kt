package top.yogiczy.mytv.core.util.utils

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import java.io.File

object ApkInstaller {
    /**
     * 把 APK 放到 cacheDir 供 FileProvider 分享安装。文件已在 cacheDir 时
     * 直接复用，不再复制；否则流式复制。
     */
    internal fun prepareCachedApk(file: File, cacheDir: File): File {
        val cachedApkFile = File(cacheDir, file.name)
        if (cachedApkFile.absoluteFile != file.absoluteFile) {
            // 流式复制：原先 writeBytes(file.readBytes()) 把整个 APK 读进
            // 内存再写，100MB+ 的包在低端电视盒子上会 OOM
            file.inputStream().use { input ->
                cachedApkFile.outputStream().use { output -> input.copyTo(output) }
            }
        }
        return cachedApkFile
    }

    @SuppressLint("SetWorldReadable")
    fun installApk(context: Context, filePath: String) {
        val file = File(filePath)
        if (file.exists()) {
            val cachedApkFile = prepareCachedApk(file, context.cacheDir).apply {
                // 解决Android6 无法解析安装包
                setReadable(true, false)
            }

            val uri =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    FileProvider.getUriForFile(
                        context, context.packageName + ".FileProvider", cachedApkFile
                    )
                }
                else Uri.fromFile(cachedApkFile)

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
                setDataAndType(uri, "application/vnd.android.package-archive")
            }

            // 无包安装器处理该 intent 的设备上直接 startActivity 会抛
            // ActivityNotFoundException（经 LaunchedEffect 上抛即闪退）
            if (installIntent.resolveActivity(context.packageManager) != null) {
                context.startActivity(installIntent)
            }
        }
    }
}