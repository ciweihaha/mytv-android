package top.yogiczy.mytv.tv.ui.screen.settings.subcategories

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.ListItem
import androidx.tv.material3.ListItemDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import top.yogiczy.mytv.tv.ui.rememberChildPadding
import top.yogiczy.mytv.tv.ui.screen.components.AppScreen
import top.yogiczy.mytv.tv.ui.theme.MyTvTheme
import top.yogiczy.mytv.tv.ui.utils.handleKeyEvents

@Composable
fun SettingsEpgRefreshTimeThresholdScreen(
    modifier: Modifier = Modifier,
    timeThresholdProvider: () -> Int = { 0 },
    onTimeThresholdChanged: (Int) -> Unit = {},
    onBackPressed: () -> Unit = {},
) {
    val currentTimeThreshold = timeThresholdProvider()
    val timeThresholdList = (0..<13).toList()

    val childPadding = rememberChildPadding()

    AppScreen(
        modifier = modifier.padding(top = 10.dp),
        header = { Text("设置 / 节目单 / 刷新时间阈值") },
        canBack = true,
        onBackPressed = onBackPressed,
    ) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(6),
            contentPadding = childPadding.copy(top = 10.dp).paddingValues,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(timeThresholdList) { timeThreshold ->
                ListItem(
                    modifier = Modifier
                        .handleKeyEvents(onSelect = { onTimeThresholdChanged(timeThreshold) }),
                    headlineContent = {
                        Text(
                            text = epgRefreshTimeThresholdText(timeThreshold),
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center,
                        )
                    },
                    trailingContent = {
                        if (currentTimeThreshold == timeThreshold) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                            )
                        }
                    },
                    colors = ListItemDefaults.colors(
                        containerColor = MaterialTheme.colorScheme.onSurface.copy(0.1f),
                    ),
                    selected = false,
                    onClick = {},
                )
            }
        }
    }
}

/**
 * 刷新时间阈值的显示文字：阈值是整点小时（0–12），直接拼“:00”（"5:00"、"12:00"）。
 * 原先同一份插值在节目单分类页与阈值子页两处各写了一份；抽成一个共用函数，文案逐字不变。
 * 注意：主界面日志里那处是补零的 "05:00"（`MainViewModel.refreshEpg`），那是日志写法，不是一处，别顺手统一。
 */
internal fun epgRefreshTimeThresholdText(threshold: Int): String {
    return "$threshold:00"
}

/**
 * 刷新时间阈值的说明文字：复用上面的时间写法。
 */
internal fun epgRefreshTimeThresholdHintText(threshold: Int): String {
    return "时间不到${epgRefreshTimeThresholdText(threshold)}节目单将不会刷新"
}

@Preview(device = "id:Android TV (720p)")
@Composable
private fun SettingsEpgRefreshTimeThresholdScreenPreview() {
    MyTvTheme {
        SettingsEpgRefreshTimeThresholdScreen()
    }
}