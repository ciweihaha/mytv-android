package top.yogiczy.mytv.tv.ui.screen.settings.categories

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Switch
import androidx.tv.material3.Text
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import top.yogiczy.mytv.tv.sync.CloudSync
import top.yogiczy.mytv.tv.sync.CloudSyncData
import top.yogiczy.mytv.tv.sync.CloudSyncProvider
import top.yogiczy.mytv.tv.ui.material.CircularProgressIndicator
import top.yogiczy.mytv.tv.ui.material.Snackbar
import top.yogiczy.mytv.tv.ui.screen.components.AppScaffoldHeaderBtn
import top.yogiczy.mytv.tv.ui.screen.settings.SettingsViewModel
import top.yogiczy.mytv.tv.ui.screen.settings.components.SettingsCategoryScreen
import top.yogiczy.mytv.tv.ui.screen.settings.components.SettingsListItem
import top.yogiczy.mytv.tv.ui.screen.settings.settingsVM
import top.yogiczy.mytv.tv.ui.theme.MyTvTheme
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun SettingsCloudSyncScreen(
    modifier: Modifier = Modifier,
    settingsViewModel: SettingsViewModel = settingsVM,
    toCloudSyncProviderScreen: () -> Unit = {},
    onReload: () -> Unit = {},
    onBackPressed: () -> Unit = {},
    debugInitialSyncData: CloudSyncData? = null,
) {
    val coroutineScope = rememberCoroutineScope()
    var syncData by remember { mutableStateOf(debugInitialSyncData) }
    var pullFailed by remember { mutableStateOf(false) }
    val timeFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()) }

    suspend fun pullSyncData() {
        syncData = null
        pullFailed = false
        runCatching { syncData = CloudSync.pull() }
            .onFailure {
                // 拉取中途被取消不是拉取失败：`runCatching` 会把取消信号也吃掉，
                // 原样抛出去，别弹“拉取云端失败”的假报错、别把数据置空（690 轮同款问题）
                if (it is CancellationException) throw it
                Snackbar.show("拉取云端失败")
                pullFailed = true
                syncData = CloudSyncData.EMPTY
            }
    }

    LaunchedEffect(Unit) { pullSyncData() }

    SettingsCategoryScreen(
        modifier = modifier,
        header = { Text("设置 / 云同步") },
        headerExtra = {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (settingsViewModel.cloudSyncProvider.supportPull) {
                    AppScaffoldHeaderBtn(
                        title = "拉取云端",
                        imageVector = Icons.Outlined.CloudDownload,
                        loading = syncData == null,
                        onSelect = { coroutineScope.launch { pullSyncData() } },
                    )
                }

                if (settingsViewModel.cloudSyncProvider.supportPush) {
                    var pushLoading by remember { mutableStateOf(false) }
                    AppScaffoldHeaderBtn(
                        title = "推送云端",
                        imageVector = Icons.Outlined.CloudUpload,
                        loading = pushLoading,
                        onSelect = {
                            coroutineScope.launch {
                                pushLoading = true
                                runCatching { CloudSync.push() }
                                    .onSuccess { success ->
                                        // 原先只看是否抛异常：push 返回 false
                                        // 也会显示“推送云端成功”
                                        if (success) {
                                            Snackbar.show("推送云端成功")
                                            pullSyncData()
                                        } else {
                                            Snackbar.show("推送云端失败")
                                        }
                                    }
                                    .onFailure {
                                        // 推送中途被取消不是推送失败：`runCatching` 会把取消信号
                                        // 也吃掉，原样抛出去，别弹“推送云端失败”的假报错
                                        // （690 轮同款问题）
                                        if (it is CancellationException) throw it
                                        Snackbar.show("推送云端失败")
                                    }
                                pushLoading = false
                            }
                        },
                    )
                }
            }
        },
        onBackPressed = onBackPressed,
    ) { firstItemFocusRequester ->
        item {
            SettingsListItem(
                modifier = Modifier.focusRequester(firstItemFocusRequester),
                headlineContent = "云端数据",
                supportingContent = "长按应用当前云端数据",
                trailingContent = {
                    when (cloudSyncDataState(syncData, pullFailed)) {
                        CloudSyncDataState.LOADING -> CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            color = LocalContentColor.current,
                            trackColor = MaterialTheme.colorScheme.surface.copy(0.1f),
                            strokeWidth = 3.dp,
                        )

                        CloudSyncDataState.FAILED -> Text("拉取失败，请重试")
                        CloudSyncDataState.EMPTY -> Text("无云端数据")
                        CloudSyncDataState.DATA -> syncData?.let { nnSyncData ->
                            Column {
                                Text("云端版本：${nnSyncData.version}")

                                Text("推送时间：${timeFormat.format(nnSyncData.syncAt)}")

                                Text("推送设备：${nnSyncData.syncFrom}")

                                nnSyncData.description?.let { Text("备注：$it") }
                            }
                        }
                    }
                },
                onLongSelect = {
                    syncData?.let { nnSyncData ->
                        if (syncData != CloudSyncData.EMPTY) {
                            coroutineScope.launch {
                                nnSyncData.apply()
                                settingsViewModel.refresh()
                                Snackbar.show("应用云端数据成功")
                                onReload()
                            }
                        }
                    }
                },
            )
        }

        item {
            SettingsListItem(
                headlineContent = "自动拉取",
                supportingContent = "应用启动时自动拉取云端数据并应用",
                trailingContent = {
                    Switch(settingsViewModel.cloudSyncAutoPull, null)
                },
                onSelect = {
                    settingsViewModel.cloudSyncAutoPull = !settingsViewModel.cloudSyncAutoPull
                },
            )
        }

        item {
            SettingsListItem(
                headlineContent = "云同步服务商",
                trailingContent = settingsViewModel.cloudSyncProvider.label,
                onSelect = toCloudSyncProviderScreen,
                link = true,
            )
        }

        when (settingsViewModel.cloudSyncProvider) {
            CloudSyncProvider.CLOUDFLARE -> {
                item {
                    SettingsListItem(
                        headlineContent = "Cloudflare 同步地址",
                        supportingContent = "内置，不用填",
                        trailingContent = settingsViewModel.cloudSyncCloudflareUrl,
                        remoteConfig = true,
                    )
                }
            }

            CloudSyncProvider.GITHUB_GIST -> {
                item {
                    SettingsListItem(
                        headlineContent = "Github Gist Id",
                        trailingContent = settingsViewModel.cloudSyncGithubGistId,
                        remoteConfig = true,
                    )
                }

                item {
                    SettingsListItem(
                        headlineContent = "Github Gist Token",
                        trailingContent = settingsViewModel.cloudSyncGithubGistToken,
                        remoteConfig = true,
                    )
                }
            }

            CloudSyncProvider.GITEE_GIST -> {
                item {
                    SettingsListItem(
                        headlineContent = "Gitee 代码片段 Id",
                        trailingContent = settingsViewModel.cloudSyncGiteeGistId,
                        remoteConfig = true,
                    )
                }

                item {
                    SettingsListItem(
                        headlineContent = "Gitee 代码片段 Token",
                        trailingContent = settingsViewModel.cloudSyncGiteeGistToken,
                        remoteConfig = true,
                    )
                }
            }

            CloudSyncProvider.NETWORK_URL -> {
                item {
                    SettingsListItem(
                        headlineContent = "网络链接",
                        trailingContent = settingsViewModel.cloudSyncNetworkUrl,
                        remoteConfig = true,
                    )
                }
            }

            CloudSyncProvider.LOCAL_FILE -> {
                item {
                    SettingsListItem(
                        headlineContent = "本地文件路径",
                        trailingContent = settingsViewModel.cloudSyncLocalFilePath,
                        remoteConfig = true,
                    )
                }
            }

            CloudSyncProvider.WEBDAV -> {
                item {
                    SettingsListItem(
                        headlineContent = "WebDAV 地址",
                        trailingContent = settingsViewModel.cloudSyncWebDavUrl,
                        remoteConfig = true,
                    )
                }

                item {
                    SettingsListItem(
                        headlineContent = "WebDAV 用户名",
                        trailingContent = settingsViewModel.cloudSyncWebDavUsername,
                        remoteConfig = true,
                    )
                }

                item {
                    SettingsListItem(
                        headlineContent = "WebDAV 密码",
                        trailingContent = settingsViewModel.cloudSyncWebDavPassword,
                        remoteConfig = true,
                    )
                }
            }
        }
    }
}

/**
 * “云端数据”行的显示状态：数据为空是加载中；拉取失败优先于空数据
 *（失败时 `pullSyncData()` 同时把数据置成 EMPTY，空档先判会把失败
 * 显示成“无云端数据”）；空数据是没有云端数据；其余是有数据。
 * 只抽判定本身，渲染原样不动。
 */
internal enum class CloudSyncDataState {
    LOADING, FAILED, EMPTY, DATA
}

internal fun cloudSyncDataState(syncData: CloudSyncData?, pullFailed: Boolean): CloudSyncDataState {
    return when {
        syncData == null -> CloudSyncDataState.LOADING
        pullFailed -> CloudSyncDataState.FAILED
        syncData == CloudSyncData.EMPTY -> CloudSyncDataState.EMPTY
        else -> CloudSyncDataState.DATA
    }
}

@Preview(device = "id:Android TV (720p)")
@Composable
private fun SettingsCloudSyncScreenPreview() {
    MyTvTheme {
        SettingsCloudSyncScreen(
            settingsViewModel = SettingsViewModel().apply {
                cloudSyncGithubGistId = "GistId".repeat(3)
                cloudSyncGithubGistToken = "sjdoiasjidosjd".repeat(10)
            },
            debugInitialSyncData = CloudSyncData(
                version = "9.9.9",
                syncFrom = "客厅的电视",
                syncAt = System.currentTimeMillis(),
                description = "mytv-android云同步测试",
            ),
        )
    }
}