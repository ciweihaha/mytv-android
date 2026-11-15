package top.yogiczy.mytv.tv.ui.screen.settings.categories

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.Switch
import androidx.tv.material3.Text
import top.yogiczy.mytv.core.data.entities.channel.ChannelGroupList
import top.yogiczy.mytv.core.data.utils.ChannelAlias
import top.yogiczy.mytv.core.util.utils.humanizeMs
import top.yogiczy.mytv.tv.ui.material.Tag
import top.yogiczy.mytv.tv.ui.material.TagDefaults
import top.yogiczy.mytv.tv.ui.screen.settings.SettingsViewModel
import top.yogiczy.mytv.tv.ui.screen.settings.components.SettingsCategoryScreen
import top.yogiczy.mytv.tv.ui.screen.settings.components.SettingsListItem
import top.yogiczy.mytv.tv.ui.screen.settings.settingsVM
import top.yogiczy.mytv.tv.ui.theme.MyTvTheme

@Composable
fun SettingsIptvScreen(
    modifier: Modifier = Modifier,
    channelGroupListProvider: () -> ChannelGroupList = { ChannelGroupList() },
    settingsViewModel: SettingsViewModel = settingsVM,
    toIptvSourceScreen: () -> Unit = {},
    toIptvSourceCacheTimeScreen: () -> Unit = {},
    toChannelGroupVisibilityScreen: () -> Unit = {},
    toIptvHybridModeScreen: () -> Unit = {},
    onBackPressed: () -> Unit = {},
) {
    SettingsCategoryScreen(
        modifier = modifier,
        header = { Text("设置 / 订阅源") },
        onBackPressed = onBackPressed,
    ) { firstItemFocusRequester ->
        item {
            val currentIptvSource = settingsViewModel.iptvSourceCurrent

            SettingsListItem(
                modifier = Modifier.focusRequester(firstItemFocusRequester),
                headlineContent = "自定义直播源",
                trailingContent = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (!currentIptvSource.name.isNullOrEmpty()) {
                            Tag(
                                if (currentIptvSource.isLocal) "本地" else "远程",
                                colors = TagDefaults.colors(
                                    containerColor = LocalContentColor.current.copy(0.1f)
                                ),
                            )
                        }
                        Text(currentIptvSource.name)
                    }
                },
                onSelect = toIptvSourceScreen,
                link = true,
            )
        }

        item {
            val cacheTime = settingsViewModel.iptvSourceCacheTime

            SettingsListItem(
                headlineContent = "直播源缓存时间",
                trailingContent = iptvSourceCacheTimeText(cacheTime),
                onSelect = toIptvSourceCacheTimeScreen,
                link = true,
            )
        }

        item {
            val allCount = channelGroupListProvider().size
            val hiddenCount = settingsViewModel.iptvChannelGroupHiddenList.size

            SettingsListItem(
                headlineContent = "频道分组管理",
                trailingContent = channelGroupManagementText(allCount, hiddenCount),
                onSelect = toChannelGroupVisibilityScreen,
                link = true,
            )
        }

        item {
            val alias = ChannelAlias.aliasMap

            SettingsListItem(
                headlineContent = "频道别名",
                trailingContent = {
                    Text("共${alias.size}个频道，${alias.values.sumOf { it.size }}个别名")
                },
                remoteConfig = true,
            )
        }

        item {
            SettingsListItem(
                headlineContent = "相似频道合并",
                supportingContent = "相同频道别名将进行合并",
                trailingContent = {
                    Switch(settingsViewModel.iptvSimilarChannelMerge, null)
                },
                onSelect = {
                    settingsViewModel.iptvSimilarChannelMerge =
                        !settingsViewModel.iptvSimilarChannelMerge
                },
            )
        }

        item {
            SettingsListItem(
                headlineContent = "频道图标提供",
                trailingContent = settingsViewModel.iptvChannelLogoProvider,
                remoteConfig = true,
            )
        }

        item {
            SettingsListItem(
                headlineContent = "频道图标覆盖",
                supportingContent = "使用频道图标提供覆盖订阅源中定义的频道图标",
                trailingContent = {
                    Switch(settingsViewModel.iptvChannelLogoOverride, null)
                },
                onSelect = {
                    settingsViewModel.iptvChannelLogoOverride =
                        !settingsViewModel.iptvChannelLogoOverride
                },
            )
        }

        item {
            SettingsListItem(
                headlineContent = "PLTV转TVOD",
                supportingContent = "自动将订阅源链接中的PLTV替换为TVOD以支持回看",
                trailingContent = {
                    Switch(settingsViewModel.iptvPLTVToTVOD, null)
                },
                onSelect = {
                    settingsViewModel.iptvPLTVToTVOD =
                        !settingsViewModel.iptvPLTVToTVOD
                },
            )
        }

        item {
            val hybridMode = settingsViewModel.iptvHybridMode

            SettingsListItem(
                headlineContent = "自动添加网页源",
                supportingContent = "为订阅源中的频道自动添加对应的网页源线路",
                trailingContent = { Text(hybridMode.label) },
                onSelect = toIptvHybridModeScreen,
                link = true,
            )
        }

        item {
            SettingsListItem(
                headlineContent = "网页源央视频Cookie",
                supportingContent = "登录到央视频以收看付费频道",
                trailingContent = truncateCookieForDisplay(settingsViewModel.iptvHybridYangshipinCookie),
                remoteConfig = true,
            )
        }
    }
}

/**
 * 直播源缓存时间的显示文字：0 → “不缓存”，最大值 → “永久”，其余走已钉死的 `humanizeMs`。
 * 从界面里抽出来，只为能离线断言两档特殊值写没写错；行为逐字不变。
 */
internal fun iptvSourceCacheTimeText(cacheTime: Long): String {
    return when (cacheTime) {
        0L -> "不缓存"
        Long.MAX_VALUE -> "永久"
        else -> cacheTime.humanizeMs()
    }
}

/**
 * 央视频 Cookie 的显示截断：超 50 字只显示前 50 字加 “...”，50 字以内原样显示。
 * 从界面里抽出来，只为能离线断言 50 字边界写没写对；行为逐字不变。
 */
internal fun truncateCookieForDisplay(cookie: String): String {
    return if (cookie.length > 50) cookie.take(50) + "..." else cookie
}

/**
 * 频道分组管理的显示文字：没隐藏分组只写总数，隐藏了就追加隐藏数。
 * 从界面里抽出来，只为能离线断言隐藏 0 个与非 0 两档写没写对；行为逐字不变。
 */
internal fun channelGroupManagementText(allCount: Int, hiddenCount: Int): String {
    return if (hiddenCount == 0) "共${allCount}个分组"
    else "共${allCount}个分组，已隐藏${hiddenCount}个分组"
}

@Preview(device = "id:Android TV (720p)")
@Composable
private fun SettingsIptvScreenPreview() {
    MyTvTheme {
        SettingsIptvScreen()
    }
}