package top.yogiczy.mytv.tv.ui.screen.settings.categories

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.tooling.preview.Preview
import androidx.tv.material3.Switch
import androidx.tv.material3.Text
import top.yogiczy.mytv.core.util.utils.humanizeMs
import top.yogiczy.mytv.tv.ui.screen.settings.SettingsViewModel
import top.yogiczy.mytv.tv.ui.screen.settings.components.SettingsCategoryScreen
import top.yogiczy.mytv.tv.ui.screen.settings.components.SettingsListItem
import top.yogiczy.mytv.tv.ui.screen.settings.settingsVM
import top.yogiczy.mytv.tv.ui.theme.MyTvTheme
import java.text.DecimalFormat

@Composable
fun SettingsUiScreen(
    modifier: Modifier = Modifier,
    settingsViewModel: SettingsViewModel = settingsVM,
    toUiTimeShowModeScreen: () -> Unit = {},
    toUiScreenAutoCloseDelayScreen: () -> Unit = {},
    toUiDensityScaleRatioScreen: () -> Unit = {},
    toUiFontScaleRatioScreen: () -> Unit = {},
    toUiVideoPlayerSubtitleSettingsScreen: () -> Unit = {},
    onBackPressed: () -> Unit = {},
) {
    val decimalFormat = remember { DecimalFormat("#.#") }

    SettingsCategoryScreen(
        modifier = modifier,
        header = { Text("设置 / 界面") },
        onBackPressed = onBackPressed,
    ) { firstItemFocusRequester ->
        item {
            SettingsListItem(
                modifier = Modifier.focusRequester(firstItemFocusRequester),
                headlineContent = "节目进度",
                supportingContent = "在频道底部显示当前节目进度条",
                trailingContent = { Switch(settingsViewModel.uiShowEpgProgrammeProgress, null) },
                onSelect = {
                    settingsViewModel.uiShowEpgProgrammeProgress = !settingsViewModel.uiShowEpgProgrammeProgress
                },
            )
        }

        item {
            SettingsListItem(
                headlineContent = "常驻节目进度",
                supportingContent = "在播放器底部显示当前节目进度条",
                trailingContent = {
                    Switch(settingsViewModel.uiShowEpgProgrammePermanentProgress, null)
                },
                onSelect = {
                    settingsViewModel.uiShowEpgProgrammePermanentProgress = !settingsViewModel.uiShowEpgProgrammePermanentProgress
                },
            )
        }

        item {
            SettingsListItem(
                headlineContent = "台标显示",
                trailingContent = {
                    Switch(settingsViewModel.uiShowChannelLogo, null)
                },
                onSelect = {
                    settingsViewModel.uiShowChannelLogo = !settingsViewModel.uiShowChannelLogo
                },
            )
        }

        item {
            SettingsListItem(
                headlineContent = "频道预览",
                trailingContent = {
                    Switch(settingsViewModel.uiShowChannelPreview, null)
                },
                onSelect = {
                    settingsViewModel.uiShowChannelPreview = !settingsViewModel.uiShowChannelPreview
                },
            )
        }

        item {
            SettingsListItem(
                headlineContent = "经典选台界面",
                supportingContent = "将选台界面替换为经典三段式结构",
                trailingContent = {
                    Switch(settingsViewModel.uiUseClassicPanelScreen, null)
                },
                onSelect = {
                    settingsViewModel.uiUseClassicPanelScreen = !settingsViewModel.uiUseClassicPanelScreen
                },
            )
        }

        item {
            val timeShowMode = settingsViewModel.uiTimeShowMode

            SettingsListItem(
                headlineContent = "时间显示",
                trailingContent = { Text(timeShowMode.label) },
                onSelect = toUiTimeShowModeScreen,
                link = true,
            )
        }

        item {
            val delay = settingsViewModel.uiScreenAutoCloseDelay

            SettingsListItem(
                headlineContent = "超时自动关闭界面",
                trailingContent = uiScreenAutoCloseDelayText(delay),
                onSelect = toUiScreenAutoCloseDelayScreen,
                link = true,
            )
        }

        item {
            val scaleRatio = settingsViewModel.uiDensityScaleRatio

            SettingsListItem(
                headlineContent = "界面整体缩放比例",
                trailingContent = uiDensityScaleRatioText(scaleRatio),
                onSelect = toUiDensityScaleRatioScreen,
                link = true,
            )
        }

        item {
            val scaleRatio = settingsViewModel.uiFontScaleRatio

            SettingsListItem(
                headlineContent = "界面字体缩放比例",
                trailingContent = "×${decimalFormat.format(scaleRatio)}",
                onSelect = toUiFontScaleRatioScreen,
                link = true,
            )
        }

        item {
            SettingsListItem(
                headlineContent = "字幕设置",
                supportingContent = "字幕样式调整",
                onSelect = toUiVideoPlayerSubtitleSettingsScreen,
                link = true,
            )
        }

        item {
            SettingsListItem(
                headlineContent = "焦点优化",
                supportingContent = "关闭后可解决触摸设备在部分场景下闪退",
                trailingContent = {
                    Switch(settingsViewModel.uiFocusOptimize, null)
                },
                onSelect = {
                    settingsViewModel.uiFocusOptimize = !settingsViewModel.uiFocusOptimize
                },
            )
        }

        item {
            SettingsListItem(
                headlineContent = "启用收藏",
                supportingContent = "是否显示订阅源频道收藏列表",
                trailingContent = {
                    Switch(settingsViewModel.iptvChannelFavoriteEnable, null)
                },
                onSelect = {
                    settingsViewModel.iptvChannelFavoriteEnable = !settingsViewModel.iptvChannelFavoriteEnable
                },
            )
        }

    }
}

/**
 * “超时自动关闭界面”行的显示文字：最大值 → “不关闭”，其余走已钉死的 `humanizeMs`。
 * 从界面里抽出来，只为能离线断言最大值那档写没写对；行为逐字不变。
 */
internal fun uiScreenAutoCloseDelayText(delay: Long): String {
    return when (delay) {
        Long.MAX_VALUE -> "不关闭"
        else -> delay.humanizeMs()
    }
}

/**
 * “界面整体缩放比例”行的显示文字：0 → “自适应”，其余 “×” 加一位小数。
 * 从界面里抽出来，只为能离线断言 0 那档写没写对；行为逐字不变
 *（函数内自己建的 `DecimalFormat("#.#")` 与界面里 remember 的那台同一格式）。
 */
internal fun uiDensityScaleRatioText(scaleRatio: Float): String {
    return when (scaleRatio) {
        0f -> "自适应"
        else -> "×${DecimalFormat("#.#").format(scaleRatio)}"
    }
}

@Preview(device = "id:Android TV (720p)")
@Composable
private fun SettingsUiScreenPreview() {
    MyTvTheme {
        SettingsUiScreen()
    }
}