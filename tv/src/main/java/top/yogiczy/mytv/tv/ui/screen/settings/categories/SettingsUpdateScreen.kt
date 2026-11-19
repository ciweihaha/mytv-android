package top.yogiczy.mytv.tv.ui.screen.settings.categories

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.tooling.preview.Preview
import androidx.tv.material3.Switch
import androidx.tv.material3.Text
import top.yogiczy.mytv.tv.BuildConfig
import top.yogiczy.mytv.tv.ui.screen.settings.SettingsViewModel
import top.yogiczy.mytv.tv.ui.screen.settings.components.SettingsCategoryScreen
import top.yogiczy.mytv.tv.ui.screen.settings.components.SettingsListItem
import top.yogiczy.mytv.tv.ui.screen.settings.settingsVM
import top.yogiczy.mytv.tv.ui.screen.settings.subcategories.updateChannelLabel
import top.yogiczy.mytv.tv.ui.theme.MyTvTheme

@Composable
fun SettingsUpdateScreen(
    modifier: Modifier = Modifier,
    settingsViewModel: SettingsViewModel = settingsVM,
    toUpdateChannelScreen: () -> Unit = {},
    onBackPressed: () -> Unit = {},
) {
    SettingsCategoryScreen(
        modifier = modifier,
        header = { Text("设置 / 更新") },
        onBackPressed = onBackPressed,
    ) { firstItemFocusRequester ->
        item {
            val channel = settingsViewModel.updateChannel

            SettingsListItem(
                modifier = Modifier.focusRequester(firstItemFocusRequester),
                headlineContent = "更新通道",
                trailingContent = updateChannelLabel(channel),
                onSelect = toUpdateChannelScreen,
                link = true,
            )
        }

        item {
            val forceRemind = settingsViewModel.updateForceRemind

            SettingsListItem(
                headlineContent = "更新强提醒",
                supportingContent = updateForceRemindSupportingText(forceRemind),
                trailingContent = {
                    Switch(forceRemind, null)
                },
                onSelect = {
                    settingsViewModel.updateForceRemind = !settingsViewModel.updateForceRemind
                },
            )
        }

        item {
            // 当前版本写清楚：用户在更新页一眼就能看到自己是哪个版本，
            // 再对照“发现新版本 V3.0.0.x”就知道要升到哪个版本。
            SettingsListItem(
                headlineContent = "当前版本",
                trailingContent = currentVersionText(
                    BuildConfig.VERSION_NAME,
                    BuildConfig.RELEASE_TAG,
                ),
            )
        }
    }
}

/**
 * “当前版本”行的显示文字：发版号是用户认的版本（V3.0.0.x），内部版是
 * 1.1.1.x；两个都写上，发版号在前。releaseTag 为空（本地/手动构建，
 * 没走 Release 流水线）时只写内部版。
 */
internal fun currentVersionText(versionName: String, releaseTag: String): String {
    return if (releaseTag.isBlank()) "v$versionName" else "$releaseTag（$versionName）"
}

/**
 * “更新强提醒”行的说明文字：开 → 全屏提醒，关 → 仅消息提示。
 * 从界面里抽出来，只为能离线断言两档写没写对；行为逐字不变。
 */
internal fun updateForceRemindSupportingText(forceRemind: Boolean): String {
    return if (forceRemind) "检测到新版本时会全屏提醒" else "检测到新版本时仅消息提示"
}

@Preview(device = "id:Android TV (720p)")
@Composable
private fun SettingsUpdateScreenPreview() {
    MyTvTheme {
        SettingsUpdateScreen()
    }
}