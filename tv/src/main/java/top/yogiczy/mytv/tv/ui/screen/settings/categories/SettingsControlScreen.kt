package top.yogiczy.mytv.tv.ui.screen.settings.categories

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.tooling.preview.Preview
import androidx.tv.material3.Switch
import androidx.tv.material3.Text
import top.yogiczy.mytv.tv.ui.screen.settings.SettingsViewModel
import top.yogiczy.mytv.tv.ui.screen.settings.components.SettingsCategoryScreen
import top.yogiczy.mytv.tv.ui.screen.settings.components.SettingsListItem
import top.yogiczy.mytv.tv.ui.screen.settings.settingsVM
import top.yogiczy.mytv.tv.ui.theme.MyTvTheme

@Composable
fun SettingsControlScreen(
    modifier: Modifier = Modifier,
    settingsViewModel: SettingsViewModel = settingsVM,
    toUiControlActionSettingsScreen: () -> Unit = {},
    onBackPressed: () -> Unit = {},
) {
    SettingsCategoryScreen(
        modifier = modifier,
        header = { Text("设置 / 控制") },
        onBackPressed = onBackPressed,
    ) { firstItemFocusRequester ->
        item {
            val enable = settingsViewModel.iptvChannelNoSelectEnable

            SettingsListItem(
                modifier = Modifier.focusRequester(firstItemFocusRequester),
                headlineContent = "数字选台",
                supportingContent = "通过数字键选择频道",
                trailingContent = {
                    Switch(enable, null)
                },
                onSelect = {
                    settingsViewModel.iptvChannelNoSelectEnable = !settingsViewModel.iptvChannelNoSelectEnable
                },
            )
        }

        item {
            val flip = settingsViewModel.iptvChannelChangeFlip

            SettingsListItem(
                headlineContent = "换台反转",
                supportingContent = channelChangeFlipSupportingText(flip),
                trailingContent = {
                    Switch(flip, null)
                },
                onSelect = {
                    settingsViewModel.iptvChannelChangeFlip = !settingsViewModel.iptvChannelChangeFlip
                },
            )
        }

        item {
            val loop = settingsViewModel.iptvChannelChangeListLoop

            SettingsListItem(
                headlineContent = "频道列表首尾循环",
                supportingContent = "启用后，到达列表首尾时将循环切换到另一端",
                trailingContent = {
                    Switch(loop, null)
                },
                onSelect = {
                    settingsViewModel.iptvChannelChangeListLoop = !settingsViewModel.iptvChannelChangeListLoop
                },
            )
        }
        
        item {
            val crossGroup = settingsViewModel.iptvChannelChangeCrossGroup

            SettingsListItem(
                headlineContent = "频道切换跨分组",
                supportingContent = "启用后，上下键可在所有频道间切换；关闭则仅在当前分组内切换",
                trailingContent = {
                    Switch(crossGroup, null)
                },
                onSelect = {
                    settingsViewModel.iptvChannelChangeCrossGroup = !settingsViewModel.iptvChannelChangeCrossGroup
                },
            )
        }
        
        item {
            SettingsListItem(
                headlineContent = "按键行为",
                supportingContent = "自定义播放界面的按键行为",
                onSelect = toUiControlActionSettingsScreen,
                link = true,
            )
        }
    }
}

/**
 * “换台反转”行的说明文字：反转开 → 方向键上是下一个频道、方向键下是上一个频道；反转关 → 反过来。
 * 从界面里抽出来，只为能离线断言上下方向写没写反；两句话里“上/下”与“上一个/下一个”各出现一次，写反了肉眼很难发现。
 */
internal fun channelChangeFlipSupportingText(flip: Boolean): String {
    return if (flip) "方向键上：下一个频道；方向键下：上一个频道"
    else "方向键上：上一个频道；方向键下：下一个频道"
}

@Preview(device = "id:Android TV (720p)")
@Composable
private fun SettingsControlScreenPreview() {
    MyTvTheme {
        SettingsControlScreen()
    }
}