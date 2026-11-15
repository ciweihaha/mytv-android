package top.yogiczy.mytv.tv.ui.screen.about

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.ListItem
import androidx.tv.material3.Text
import top.yogiczy.mytv.core.data.utils.Constants
import top.yogiczy.mytv.core.data.utils.Globals
import top.yogiczy.mytv.core.util.utils.compareVersion
import top.yogiczy.mytv.tv.BuildConfig
import top.yogiczy.mytv.tv.R
import top.yogiczy.mytv.tv.ui.material.SimplePopup
import top.yogiczy.mytv.tv.ui.rememberChildPadding
import top.yogiczy.mytv.tv.ui.screen.components.AppScreen
import top.yogiczy.mytv.tv.ui.screen.components.QrcodePopup
import top.yogiczy.mytv.tv.ui.theme.MyTvTheme
import top.yogiczy.mytv.tv.ui.utils.handleKeyEvents

@Composable
fun AboutScreen(
    modifier: Modifier = Modifier,
    latestVersionProvider: () -> String = { "" },
    checkFailedProvider: () -> Boolean = { false },
    toUpdateScreen: () -> Unit = {},
    onBackPressed: () -> Unit = {},
) {
    val childPadding = rememberChildPadding()

    AppScreen(
        modifier = modifier,
        header = { Text("关于") },
        canBack = true,
        onBackPressed = onBackPressed,
    ) {
        LazyColumn(
            modifier = Modifier.padding(top = 10.dp),
            contentPadding = childPadding.copy(top = 10.dp).paddingValues,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                ListItem(
                    headlineContent = { Text("应用标识") },
                    trailingContent = {
                        Text(
                            listOf(
                                BuildConfig.APPLICATION_ID,
                                BuildConfig.FLAVOR,
                                BuildConfig.BUILD_TYPE,
                                "${BuildConfig.VERSION_NAME}(${BuildConfig.VERSION_CODE})",
                            ).joinToString("_")
                        )
                    },
                    selected = false,
                    onClick = {},
                )
            }

            item {
                var visible by remember { mutableStateOf(false) }
                ListItem(
                    modifier = Modifier.handleKeyEvents(onSelect = { visible = true }),
                    headlineContent = { Text("本项目代码仓库") },
                    trailingContent = {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(Constants.APP_REPO)

                            Icon(
                                Icons.AutoMirrored.Default.OpenInNew,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    },
                    selected = false,
                    onClick = {},
                )

                QrcodePopup(
                    visibleProvider = { visible },
                    onDismissRequest = { visible = false },
                    text = Constants.APP_REPO,
                    description = "扫码前往代码仓库",
                )
            }

            item {
                ListItem(
                    headlineContent = { Text("设备名称") },
                    trailingContent = { Text(Globals.deviceName) },
                    selected = false,
                    onClick = {},
                )
            }

            item {
                var visible by remember { mutableStateOf(false) }

                ListItem(
                    modifier = Modifier.handleKeyEvents(onSelect = {
                        visible = true
                    }),
                    headlineContent = { Text("设备ID") },
                    trailingContent = {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(Globals.deviceId)

                            Icon(
                                Icons.AutoMirrored.Default.OpenInNew,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    },
                    selected = false,
                    onClick = {},
                )

                QrcodePopup(
                    visibleProvider = { visible },
                    onDismissRequest = { visible = false },
                    text = Globals.deviceId,
                )
            }
            item {
                var visible by remember { mutableStateOf(false) }
                ListItem(
                    modifier = Modifier.handleKeyEvents(onSelect = { visible = true }),
                    headlineContent = { Text("天光云影仓库") },
                    trailingContent = {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(Constants.ORIGIN_APP_REPO)

                            Icon(
                                Icons.AutoMirrored.Default.OpenInNew,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    },
                    selected = false,
                    onClick = {},
                )

                QrcodePopup(
                    visibleProvider = { visible },
                    onDismissRequest = { visible = false },
                    text = Constants.ORIGIN_APP_REPO,
                    description = "本项目的诞生离不开天光云影项目，扫码以前往",
                )
            }
            item {
                var visible by remember { mutableStateOf(false) }

                ListItem(
                    modifier = Modifier.handleKeyEvents(onSelect = { visible = true }),
                    headlineContent = { Text("向天光云影作者赞赏") },
                    supportingContent = { Text("仅支持微信赞赏码") },
                    trailingContent = {
                        Icon(
                            Icons.AutoMirrored.Default.OpenInNew,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                    },
                    selected = false,
                    onClick = {},
                )

                SimplePopup(
                    visibleProvider = { visible },
                    onDismissRequest = { visible = false },
                ) {
                    val painter = painterResource(R.drawable.mm_reward_qrcode)

                    Image(
                        painter,
                        contentDescription = null,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(300.dp),
                    )
                }
            }

            item {
                ListItem(
                    modifier = Modifier.handleKeyEvents(onSelect = toUpdateScreen),
                    headlineContent = { Text("检查更新") },
                    trailingContent = {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                updateCheckHintText(
                                    checkFailedProvider(),
                                    BuildConfig.VERSION_NAME,
                                    latestVersionProvider(),
                                )
                            )

                            Icon(
                                Icons.Default.ChevronRight,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    },
                    selected = false,
                    onClick = {},
                )
            }
        }
    }
}

/**
 * 关于页“检查更新”行的提示文字：检查失败 → “检查更新失败”；最新版比当前版新 → “新版本: 最新版”；否则 → “无更新”。
 * 最新版为空时按当前版算（界面上就是“无更新”）。版本号比较走已钉死的 `compareVersion`。
 * 从界面里抽出来，只为能离线断言三档分支写没写错；行为逐字不变。
 */
internal fun updateCheckHintText(checkFailed: Boolean, currentVersion: String, latestVersion: String): String {
    val latest = latestVersion.ifBlank { currentVersion }
    return when {
        checkFailed -> "检查更新失败"
        latest.compareVersion(currentVersion) > 0 -> "新版本: $latest"
        else -> "无更新"
    }
}

@Preview(device = "id:Android TV (720p)")
@Composable
private fun AboutScreenPreview() {
    MyTvTheme {
        AboutScreen(
            latestVersionProvider = { "9.0.0" },
        )
    }
}