package top.yogiczy.mytv.tv

import android.app.Application
import android.content.Context
import android.content.Intent
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.request.CachePolicy
import coil3.svg.SvgDecoder
import coil3.util.DebugLogger
import okio.Path.Companion.toOkioPath
import io.sentry.Hint
import io.sentry.Sentry
import io.sentry.SentryEvent
import io.sentry.SentryLevel
import io.sentry.SentryOptions
import io.sentry.android.core.SentryAndroid
import android.os.Build
import top.yogiczy.mytv.core.data.AppData
import top.yogiczy.mytv.core.data.utils.Globals
import top.yogiczy.mytv.core.data.utils.Logger
import top.yogiczy.mytv.core.util.utils.CrashLog
import kotlin.system.exitProcess

class MyTVApplication : Application(), SingletonImageLoader.Factory {
    private val log = Logger.create("MyTVApplication")

    override fun onCreate() {
        super.onCreate()
        
        initSentry()
        crashHandle()
        AppData.init(applicationContext)
        UnsafeTrustManager.enableUnsafeTrustManager()
        
    }

    override fun newImageLoader(context: Context): ImageLoader {
        return ImageLoader.Builder(context)
            .logger(DebugLogger())
            .components {
                add(SvgDecoder.Factory())
            }
            .memoryCachePolicy(CachePolicy.ENABLED)
            .memoryCache {
                MemoryCache.Builder()
                    .build()
            }
            .diskCachePolicy(CachePolicy.ENABLED)
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache").toOkioPath())
                    .build()
            }
            .build()
    }

    private fun initSentry() {
        SentryAndroid.init(this) { options ->
            options.environment = BuildConfig.BUILD_TYPE
            options.dsn = BuildConfig.SENTRY_DSN
            options.tracesSampleRate = 1.0
            options.beforeSend =
                SentryOptions.BeforeSendCallback { event: SentryEvent, _: Hint ->
                    if (event.level == null) event.level = SentryLevel.FATAL

                    if (BuildConfig.DEBUG) return@BeforeSendCallback null
                    if (SentryLevel.ERROR != event.level && SentryLevel.FATAL != event.level) return@BeforeSendCallback null
                    if (event.exceptions?.any { ex -> ex.type?.contains("Http") == true } == true) return@BeforeSendCallback null

                    event
                }
        }

        @Suppress("UnstableApiUsage")
        Sentry.withScope { scope ->
            Globals.deviceId = scope.options.distinctId ?: ""
        }
    }

    private fun crashHandle() {
        Thread.setDefaultUncaughtExceptionHandler { _, throwable ->
            runCatching { log.e("未捕获异常", throwable) }

            // 崩溃日志先落盘再做别的：原来只靠崩溃屏 Intent 传堆栈、Logger 只写
            // logcat 和内存（进程一死就没了）、Sentry 在发版里又没配 DSN
            //（BuildConfig.SENTRY_DSN 是字面量 "null"），崩溃后什么都留不下；
            // 而且崩溃屏和主进程是同一个进程，原来 startActivity 后立刻
            // killProcess，系统还没来得及拉起崩溃屏进程就死了，HarmonyOS
            // 平板上尤其明显——用户只看到直接闪退、连日志都看不到。
            // 现在：落盘（原子写，失败也不抛）、崩溃屏读文件兜底、晚 1.2 秒
            // 再杀进程，给崩溃屏留出启动时间。
            val stacktrace = runCatching { throwable.stackTraceToString() }.getOrElse { "" }
            val crashLogFile = runCatching {
                CrashLog.writeToFile(
                    applicationContext.cacheDir,
                    CrashLog.buildText(
                        appVersion = "${BuildConfig.VERSION_NAME}(${BuildConfig.VERSION_CODE})",
                        deviceInfo = "${Build.MANUFACTURER} ${Build.MODEL} / Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
                        message = throwable.message,
                        stacktrace = stacktrace,
                    ),
                )
            }.getOrNull()

            runCatching { Sentry.captureException(throwable) }

            runCatching {
                val intent = Intent(this, CrashHandlerActivity::class.java).apply {
                    putExtra("error_message", throwable.message)
                    // 堆栈经 Intent 传有约 1MB 的 Binder 上限，超长先截断，
                    // 完整内容在落盘的日志文件里
                    putExtra("error_stacktrace", CrashLog.truncateForIntent(stacktrace))
                    crashLogFile?.let { putExtra("crash_log_path", it.absolutePath) }
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                }
                startActivity(intent)
            }

            Thread {
                try {
                    Thread.sleep(1200)
                } catch (_: InterruptedException) {
                }
                android.os.Process.killProcess(android.os.Process.myPid())
                exitProcess(1)
            }.start()
        }
    }
}
