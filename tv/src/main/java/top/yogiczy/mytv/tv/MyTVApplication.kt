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
import top.yogiczy.mytv.core.data.AppData
import top.yogiczy.mytv.core.data.utils.Globals
import kotlin.system.exitProcess
import com.tencent.smtt.sdk.QbSdk

class MyTVApplication : Application(), SingletonImageLoader.Factory {
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
                    // .maxSizePercent(0.25)
                    .build()
            }
            .diskCachePolicy(CachePolicy.ENABLED)
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache").toOkioPath())
                    // .maxSizeBytes(1024 * 1024 * 100)
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
            throwable.printStackTrace()
            Sentry.captureException(throwable)

            val intent = Intent(this, CrashHandlerActivity::class.java).apply {
                putExtra("error_message", throwable.message)
                putExtra("error_stacktrace", throwable.stackTraceToString())
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
            startActivity(intent)

            android.os.Process.killProcess(android.os.Process.myPid())
            exitProcess(1)
        }
    }
}
