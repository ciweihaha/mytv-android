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
fun SettingsUpdateChannelScreen(
    modifier: Modifier = Modifier,
    updateChannelProvider: () -> String = { "" },
    onUpdateChannelChanged: (String) -> Unit = {},
    onBackPressed: () -> Unit = {},
) {
    val updateChannelList = listOf("stable", "beta")// "dev" 开发版不公开
    val currentUpdateChannel = updateChannelProvider()
    val childPadding = rememberChildPadding()

    AppScreen(
        modifier = modifier.padding(top = 10.dp),
        header = { Text("设置 / 更新 / 更新通道") },
        canBack = true,
        onBackPressed = onBackPressed,
    ) {
        LazyVerticalGrid(
            modifier = Modifier,
            columns = GridCells.Fixed(6),
            contentPadding = childPadding.copy(top = 10.dp).paddingValues,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(updateChannelList) { channel ->
                ListItem(
                    modifier = Modifier
                        .handleKeyEvents(onSelect = { onUpdateChannelChanged(channel) }),
                    headlineContent = {
                        Text(
                            updateChannelLabel(channel),
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center,
                        )
                    },
                    trailingContent = {
                        if (currentUpdateChannel == channel) {
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
 * 更新通道代号的显示文案：stable→稳定版、beta→测试版、dev→开发版，其他代号原样显示。
 * 原先同一份 `when` 在更新分类页与更新通道子页两处各写了一份，改错一处另一处就对不上；
 * 抽成一个共用函数，两处都调它，文案逐字不变。
 */
internal fun updateChannelLabel(channel: String): String {
    return when (channel) {
        "stable" -> "稳定版"
        "beta" -> "测试版"
        "dev" -> "开发版"
        else -> channel
    }
}

@Preview(device = "id:Android TV (720p)")
@Composable
private fun SettingsUpdateChannelScreenPreview() {
    MyTvTheme {
        SettingsUpdateChannelScreen(
            updateChannelProvider = { "stable" },
        )
    }
}