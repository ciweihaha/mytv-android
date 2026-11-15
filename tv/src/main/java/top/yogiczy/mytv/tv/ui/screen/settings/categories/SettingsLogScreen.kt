package top.yogiczy.mytv.tv.ui.screen.settings.categories

import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.tooling.preview.Preview
import androidx.tv.material3.ListItem
import androidx.tv.material3.Text
import top.yogiczy.mytv.core.data.utils.Logger
import top.yogiczy.mytv.tv.ui.screen.settings.components.SettingsCategoryScreen
import top.yogiczy.mytv.tv.ui.theme.MyTvTheme
import top.yogiczy.mytv.tv.ui.utils.ifElse
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun SettingsLogScreen(
    modifier: Modifier = Modifier,
    historyItemList: List<Logger.HistoryItem> = Logger.history,
    onBackPressed: () -> Unit = {},
) {
    val timeFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }

    SettingsCategoryScreen(
        modifier = modifier,
        header = { Text("设置 / 日志") },
        onBackPressed = onBackPressed,
    ) { firstItemFocusRequester ->
        itemsIndexed(historyItemList.reversed()) { index, log ->
            ListItem(
                modifier = Modifier.ifElse(
                    index == 0,
                    Modifier.focusRequester(firstItemFocusRequester)
                ),
                leadingContent = { Text(logLevelLetter(log.level)) },
                headlineContent = { Text(log.tag) },
                supportingContent = { Text(logSupportingText(log.message, log.cause)) },
                trailingContent = { Text(timeFormat.format(log.time)) },
                selected = false,
                onClick = {},
            )
        }
    }
}

/**
 * 日志行前面的等级字母：取等级名的首字母（DEBUG→D、INFO→I、WARN→W、ERROR→E）。
 * 从界面里抽出来，只为能离线断言字母写没写错；等级名一改字母就跟着变，单测会立刻发现。
 */
internal fun logLevelLetter(level: Logger.LevelType): String {
    return level.toString()[0].toString()
}

/**
 * 日志行的正文：消息后面跟原因，没有原因时留一个空格（现行写法原样保留）。
 */
internal fun logSupportingText(message: String, cause: String?): String {
    return "$message ${cause ?: ""}"
}

@Preview(device = "id:Android TV (720p)")
@Composable
private fun Preview() {
    MyTvTheme {
        SettingsLogScreen(
            historyItemList = listOf(
                Logger.HistoryItem(
                    level = Logger.LevelType.DEBUG,
                    tag = "SettingsLogScreen",
                    message = "message level = Logger.LevelType.INFO,",
                    time = System.currentTimeMillis(),
                ),
                Logger.HistoryItem(
                    level = Logger.LevelType.INFO,
                    tag = "SettingsLogScreen",
                    message = "message level = Logger.LevelType.INFO,",
                    time = System.currentTimeMillis(),
                ),
                Logger.HistoryItem(
                    level = Logger.LevelType.WARN,
                    tag = "SettingsLogScreen",
                    message = "message level = Logger.LevelType.INFO,",
                    time = System.currentTimeMillis(),
                ),
                Logger.HistoryItem(
                    level = Logger.LevelType.ERROR,
                    tag = "SettingsLogScreen",
                    message = "message level = Logger.LevelType.INFO,",
                    cause = "java.lang.Exception: 401: Unauthorized",
                    time = System.currentTimeMillis(),
                ),
            )
        )
    }
}