package top.yogiczy.mytv.core.data.utils

import android.util.Log
import kotlinx.serialization.Serializable
import kotlin.time.Duration

/**
 * 日志工具类
 */
class Logger private constructor(
    private val tag: String
) {
    private fun messageWithDuration(message: String, duration: Duration? = null): String {
        return if (duration != null) "$message \t [$duration]" else message
    }

    fun v(message: String, throwable: Throwable? = null, duration: Duration? = null) {
        val messageWithDuration = messageWithDuration(message, duration)
        Log.v(tag, messageWithDuration, throwable)
        addHistoryItem(HistoryItem(LevelType.DEBUG, tag, messageWithDuration, throwable?.message))
    }

    fun d(message: String, throwable: Throwable? = null, duration: Duration? = null) {
        val messageWithDuration = messageWithDuration(message, duration)
        Log.d(tag, messageWithDuration, throwable)
        addHistoryItem(HistoryItem(LevelType.DEBUG, tag, messageWithDuration, throwable?.message))
    }

    fun i(message: String, throwable: Throwable? = null, duration: Duration? = null) {
        val messageWithDuration = messageWithDuration(message, duration)
        Log.i(tag, messageWithDuration, throwable)
        addHistoryItem(HistoryItem(LevelType.INFO, tag, messageWithDuration, throwable?.message))
    }

    fun w(message: String, throwable: Throwable? = null, duration: Duration? = null) {
        val messageWithDuration = messageWithDuration(message, duration)
        Log.w(tag, messageWithDuration, throwable)
        addHistoryItem(HistoryItem(LevelType.WARN, tag, messageWithDuration, throwable?.message))
    }

    fun e(message: String, throwable: Throwable? = null, duration: Duration? = null) {
        val messageWithDuration = messageWithDuration(message, duration)
        Log.e(tag, messageWithDuration, throwable)
        addHistoryItem(HistoryItem(LevelType.ERROR, tag, messageWithDuration, throwable?.message))
    }

    fun wtf(message: String, throwable: Throwable? = null, duration: Duration? = null) {
        val messageWithDuration = messageWithDuration(message, duration)
        Log.wtf(tag, messageWithDuration, throwable)
        addHistoryItem(HistoryItem(LevelType.ERROR, tag, messageWithDuration, throwable?.message))
    }

    companion object {
        fun create(tag: String) = Logger(tag)

        private val _history = mutableListOf<HistoryItem>()
        private val historyLock = Any()
        // 历史记录多线程读写：记日志的调用在各 IO 线程上，读的
        // 地方（设置页日志组合期间遍历、本地服务两个接口序列化）
        // 直接拿原列表会和 add 撞上抛 ConcurrentModificationException；
        // 这里读返回快照、写加锁原地裁剪，不再整个换列表
        val history: List<HistoryItem>
            get() = synchronized(historyLock) { _history.toList() }

        fun addHistoryItem(item: HistoryItem) {
            if (listOf(LevelType.INFO, LevelType.WARN, LevelType.ERROR).contains(item.level)) {
                synchronized(historyLock) {
                    _history.add(item)
                    if (_history.size > Constants.LOG_HISTORY_MAX_SIZE) {
                        _history.subList(0, _history.size - Constants.LOG_HISTORY_MAX_SIZE)
                            .clear()
                    }
                }
            }
        }
    }

    enum class LevelType {
        DEBUG, INFO, WARN, ERROR
    }

    @Serializable
    data class HistoryItem(
        val level: LevelType,
        val tag: String,
        val message: String,
        val cause: String? = null,
        val time: Long = System.currentTimeMillis(),
    )
}

/**
 * 注入日志
 */
abstract class Loggable(private val tag: String) {
    protected val log: Logger by lazy { Logger.create(tag) }
}