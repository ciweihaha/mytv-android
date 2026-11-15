package top.yogiczy.mytv.tv

import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import top.yogiczy.mytv.core.util.utils.CrashLog
import top.yogiczy.mytv.tv.ui.screen.crash.CrashHandlerScreen
import top.yogiczy.mytv.tv.ui.theme.MyTvTheme
import top.yogiczy.mytv.tv.utils.HttpServer
import kotlin.system.exitProcess

class CrashHandlerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val errorMessage = intent.getStringExtra("error_message") ?: "未知错误"
        // Intent 传堆栈有大小上限、也可能整个 extra 丢了：读落盘的 crash.log 兜底
        val crashLogFile = java.io.File(applicationContext.cacheDir, CrashLog.FILE_NAME)
        val crashLogPath = intent.getStringExtra("crash_log_path")
            ?.takeIf { it.isNotBlank() }
            ?: crashLogFile.takeIf { it.exists() && it.length() > 0 }?.absolutePath.orEmpty()
        val errorStacktrace = intent.getStringExtra("error_stacktrace")
            ?.takeIf { it.isNotBlank() }
            ?: CrashLog.readFromFile(applicationContext.cacheDir)
                ?.let { CrashLog.extractStacktrace(it) }
                .orEmpty()

        enableEdgeToEdge()
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE

        setContent {
            WindowCompat.setDecorFitsSystemWindows(window, false)
            WindowCompat.getInsetsController(window, window.decorView).let { insetsController ->
                insetsController.hide(WindowInsetsCompat.Type.statusBars())
                insetsController.hide(WindowInsetsCompat.Type.navigationBars())
                insetsController.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }

            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

            MyTvTheme {
                CrashHandlerScreen(
                    errorMessage = errorMessage,
                    errorStacktrace = errorStacktrace,
                    crashLogPath = crashLogPath,
                    onRestart = {
                        startActivity(Intent(this, MainActivity::class.java))
                        finish()
                    },
                    onBackPressed = {
                        finish()
                        exitProcess(0)
                    },
                )
            }
        }

        HttpServer.startService(applicationContext)
    }

    override fun onDestroy() {
        HttpServer.stopService(applicationContext)
        super.onDestroy()
    }
}