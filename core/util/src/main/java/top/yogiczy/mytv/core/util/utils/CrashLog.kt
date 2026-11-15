package top.yogiczy.mytv.core.util.utils

import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 崩溃日志：拼文本 + 落盘 + 读回。
 *
 * 崩溃上报链原来是断的：Logger 只写 logcat 和内存历史（进程一死就没了），
 * 发版里 Sentry 也没配 DSN（BuildConfig.SENTRY_DSN 是字面量 "null"），
 * 崩溃屏又是和主进程同一个进程、启动后立刻被 killProcess 杀掉，HarmonyOS
 * 平板上经常连崩溃屏都出不来、用户只看到直接闪退、事后什么都拿不到。
 * 所以崩溃要先落盘（cacheDir/crash.log，原子写），崩溃屏读文件兜底。
 * 这里全是纯函数，不碰 Android 框架，方便单测。
 */
object CrashLog {
    const val FILE_NAME = "crash.log"

    /** 堆栈经 Intent 传有约 1MB 的 Binder 上限，超长时截断，完整内容只留在文件里。 */
    const val MAX_INTENT_STACKTRACE_LENGTH = 200_000

    private const val STACKTRACE_MARKER = "堆栈跟踪：\n"

    fun buildText(
        timestampMillis: Long = System.currentTimeMillis(),
        appVersion: String = "",
        deviceInfo: String = "",
        message: String?,
        stacktrace: String,
    ): String = buildString {
        append("崩溃时间：${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(timestampMillis))}\n")
        if (appVersion.isNotBlank()) append("应用版本：$appVersion\n")
        if (deviceInfo.isNotBlank()) append("设备：$deviceInfo\n")
        append("错误信息：\n${message ?: "未知错误"}\n\n")
        append(STACKTRACE_MARKER)
        append(stacktrace)
    }

    /**
     * 原子写崩溃日志；写失败返回 null，调用方不得因此再抛异常——
     * 上报本身绝不能盖掉原始崩溃。
     */
    fun writeToFile(dir: File, text: String): File? {
        return runCatching {
            val file = File(dir, FILE_NAME)
            FsUtil.writeTextAtomically(file, text)
            file
        }.getOrNull()
    }

    /** 读回崩溃日志全文；文件不存在/为空/读失败返回 null。 */
    fun readFromFile(dir: File): String? {
        return runCatching {
            File(dir, FILE_NAME).takeIf { it.exists() && it.length() > 0 }?.readText()
        }.getOrNull()
    }

    /**
     * 从落盘的崩溃日志里只取堆栈部分（崩溃屏 Intent 的 extra 丢了时用）。
     * 没有标记时原样返回全文。
     */
    fun extractStacktrace(text: String): String {
        val idx = text.indexOf(STACKTRACE_MARKER)
        return if (idx < 0) text else text.substring(idx + STACKTRACE_MARKER.length)
    }

    /** 堆栈太长时截断后再经 Intent 传，完整内容见文件。 */
    fun truncateForIntent(stacktrace: String): String {
        return if (stacktrace.length <= MAX_INTENT_STACKTRACE_LENGTH) stacktrace
        else stacktrace.take(MAX_INTENT_STACKTRACE_LENGTH) +
            "\n……（堆栈过长已截断，完整内容见崩溃日志文件）"
    }
}
