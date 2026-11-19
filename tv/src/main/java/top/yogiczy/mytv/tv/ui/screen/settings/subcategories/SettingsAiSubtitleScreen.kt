package top.yogiczy.mytv.tv.ui.screen.settings.subcategories

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Switch
import androidx.tv.material3.Text
import kotlinx.coroutines.launch
import top.yogiczy.mytv.tv.ui.screen.components.AppScreen
import top.yogiczy.mytv.tv.ui.screen.settings.SettingsViewModel
import top.yogiczy.mytv.tv.ui.screen.settings.components.SettingsListItem
import top.yogiczy.mytv.tv.ui.screen.settings.settingsVM
import top.yogiczy.mytv.tv.ui.theme.SAFE_AREA_HORIZONTAL_PADDING
import top.yogiczy.mytv.tv.ui.utils.Configs
import top.yogiczy.mytv.tv.voice.AiTranslateMode
import top.yogiczy.mytv.tv.voice.AiTranslateProvider
import top.yogiczy.mytv.tv.voice.AsrModelKind
import top.yogiczy.mytv.tv.voice.AsrModelManager
import top.yogiczy.mytv.tv.voice.AsrModelState
import top.yogiczy.mytv.tv.voice.AsrModels

/**
 * AI 实时字幕设置（设置 / 播放器 / AI 实时字幕）。
 *
 * 本地听写（sherpa-onnx）+ 云端免费接口翻译（Azure/Cloudflare）。
 * 模型只列流式模型（实时字幕用）：默认中文、中文+英文双语；
 * 离线模型（SenseVoice、Paraformer、Whisper）是整句识别、不能边播边出，
 * 这期不接，不列出来。
 * 模型下载走国内镜像（ghfast.top），下之前显示大小（100–500MB），
 * 电视只有 16GB 存储；下载失败自动回退 GitHub 直链再试一次。
 * 翻译只在英文台开：默认“仅英语台”。接口失败跳过那行，不卡字幕。
 */
@Composable
fun SettingsAiSubtitleScreen(
    modifier: Modifier = Modifier,
    settingsViewModel: SettingsViewModel = settingsVM,
    onBackPressed: () -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    // 模型管理器：默认用应用缓存 asr-models/（和识别引擎同一个目录）
    val modelManager = remember { AsrModelManager() }
    var downloadProgress by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    var downloadError by remember { mutableStateOf<String?>(null) }
    // 强制刷新下载状态：下载/删除后改一次，界面重算 modelState
    var stateVersion by remember { mutableStateOf(0) }

    fun modelSizeText(bytes: Long): String = "约 ${bytes / 1024 / 1024}MB"

    AppScreen(
        modifier = Modifier.padding(top = 10.dp),
        header = { Text("设置 / 播放器 / AI 实时字幕") },
        canBack = true,
        onBackPressed = onBackPressed,
    ) {
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = modifier
                .fillMaxWidth()
                .padding(SAFE_AREA_HORIZONTAL_PADDING.dp),
        ) {
            item {
                SettingsListItem(
                    modifier = Modifier.fillMaxWidth(),
                    headlineContent = "AI 实时字幕",
                    supportingContent = "本地模型听写，边播边出字幕；英文台再走免费接口翻中文。只支持 Media3 内核，IJK 内核没有音频回调、用不了。",
                    trailingContent = {
                        Switch(settingsViewModel.aiSubtitleEnabled, null)
                    },
                    onSelect = {
                        settingsViewModel.aiSubtitleEnabled = !settingsViewModel.aiSubtitleEnabled
                    },
                )
            }

            // 识别模型（只列流式模型）
            item {
                Text(
                    "识别模型（本地，下载后才能用）",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            for (model in AsrModels.ALL.filter { it.kind == AsrModelKind.STREAMING }) {
                item {
                    // stateVersion 只是为了下载/删除后强制重算
                    @Suppress("UNUSED_EXPRESSION")
                    stateVersion
                    val state = modelManager.modelState(model)
                    val progress = downloadProgress[model.id]
                    val isCurrent = settingsViewModel.aiSubtitleModelId == model.id
                    SettingsListItem(
                        modifier = Modifier.fillMaxWidth(),
                        headlineContent = model.displayName,
                        supportingContent = listOfNotNull(
                            model.languages,
                            modelSizeText(model.approxSizeBytes),
                            when (state) {
                                AsrModelState.INSTALLED -> "已下载"
                                AsrModelState.INCOMPLETE -> "下载不完整，需重新下载"
                                AsrModelState.NOT_INSTALLED -> "未下载"
                            },
                            progress?.let { "下载中 $it%" },
                        ).joinToString(" · "),
                        trailingContent = {
                            if (isCurrent) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null)
                            }
                        },
                        onSelect = {
                            settingsViewModel.aiSubtitleModelId = model.id
                        },
                    )
                }
            }

            // 选中的模型：下载 / 删除
            item {
                val currentModel = AsrModels.byId(settingsViewModel.aiSubtitleModelId)
                    ?: AsrModels.defaultModel()
                val state = modelManager.modelState(currentModel)
                val progress = downloadProgress[currentModel.id]
                SettingsListItem(
                    modifier = Modifier.fillMaxWidth(),
                    headlineContent = when {
                        progress != null -> "正在下载 ${currentModel.displayName}：$progress%"
                        state == AsrModelState.INSTALLED -> "删除模型 ${currentModel.displayName}"
                        else -> "下载模型 ${currentModel.displayName}（${modelSizeText(currentModel.approxSizeBytes)}）"
                    },
                    supportingContent = when {
                        progress != null -> "下载走国内镜像，失败自动换 GitHub 直链；别切台，等下完"
                        state == AsrModelState.INSTALLED -> "已占空间 ${modelSizeText(modelManager.installedSizeBytes())}（全部模型合计）"
                        else -> "模型是 100–500MB 的大文件，电视只有 16GB 存储；下之前确认空间够"
                    },
                    trailingContent = {
                        if (state == AsrModelState.INSTALLED && progress == null) {
                            Icon(Icons.Default.Delete, contentDescription = null)
                        } else if (progress == null) {
                            Icon(Icons.Default.Download, contentDescription = null)
                        }
                    },
                    onSelect = {
                        if (progress != null) return@SettingsListItem
                        if (state == AsrModelState.INSTALLED) {
                            scope.launch {
                                modelManager.deleteModel(currentModel)
                                stateVersion++
                            }
                        } else {
                            downloadError = null
                            scope.launch {
                                val result = modelManager.downloadModel(currentModel) { p ->
                                    downloadProgress =
                                        downloadProgress + (currentModel.id to p)
                                }
                                downloadProgress = downloadProgress - currentModel.id
                                if (result.isSuccess) {
                                    stateVersion++
                                } else {
                                    downloadError = "下载失败，请检查网络后重试"
                                }
                            }
                        }
                    },
                )
            }

            downloadError?.let { err ->
                item {
                    Text(err, color = MaterialTheme.colorScheme.error)
                }
            }

            // 翻译模式
            item {
                Text(
                    "英文翻中文",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            for (mode in AiTranslateMode.entries) {
                item {
                    SettingsListItem(
                        modifier = Modifier.fillMaxWidth(),
                        headlineContent = when (mode) {
                            AiTranslateMode.OFF -> "关闭"
                            AiTranslateMode.ENGLISH_ONLY -> "仅英语台"
                            AiTranslateMode.ALL -> "全部台"
                        },
                        supportingContent = when (mode) {
                            AiTranslateMode.OFF -> "只显示识别的原文"
                            AiTranslateMode.ENGLISH_ONLY -> "只有英文才翻（默认）"
                            AiTranslateMode.ALL -> "非中文都翻"
                        },
                        trailingContent = {
                            if (settingsViewModel.aiSubtitleTranslateMode == mode) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null)
                            }
                        },
                        onSelect = {
                            settingsViewModel.aiSubtitleTranslateMode = mode
                        },
                    )
                }
            }

            // 翻译服务商
            item {
                Text(
                    "翻译服务商（免费接口，失败跳过那行）",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            for (provider in AiTranslateProvider.entries) {
                item {
                    SettingsListItem(
                        modifier = Modifier.fillMaxWidth(),
                        headlineContent = when (provider) {
                            AiTranslateProvider.AZURE -> "微软 Azure（默认）"
                            AiTranslateProvider.CLOUDFLARE -> "Cloudflare"
                        },
                        supportingContent = when (provider) {
                            AiTranslateProvider.AZURE -> "每月 200 万字符免费，要自己去微软开 Key"
                            AiTranslateProvider.CLOUDFLARE -> "Workers AI 免费层，用自己的账号"
                        },
                        trailingContent = {
                            if (settingsViewModel.aiSubtitleTranslateProvider == provider) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null)
                            }
                        },
                        onSelect = {
                            settingsViewModel.aiSubtitleTranslateProvider = provider
                        },
                    )
                }
            }

            // API Key（按服务商显示，手机扫码远程输入）
            when (settingsViewModel.aiSubtitleTranslateProvider) {
                AiTranslateProvider.AZURE -> {
                    item {
                        SettingsListItem(
                            modifier = Modifier.fillMaxWidth(),
                            headlineContent = "Azure 翻译 Key",
                            supportingContent = "去微软 Azure 开免费 Translator（F0），把 Key 贴进来；没 Key 就不翻译，只显示英文原文",
                            trailingContent = settingsViewModel.aiSubtitleAzureApiKey
                                .takeIf { it.isNotBlank() }?.let { "已填" } ?: "未填",
                            remoteConfig = true,
                        )
                    }
                    item {
                        SettingsListItem(
                            modifier = Modifier.fillMaxWidth(),
                            headlineContent = "Azure 区域",
                            supportingContent = "可空；免费层有些区不用填，接口报区域错再填（比如 eastasia）",
                            trailingContent = settingsViewModel.aiSubtitleAzureRegion
                                .takeIf { it.isNotBlank() } ?: "未填",
                            remoteConfig = true,
                        )
                    }
                }
                AiTranslateProvider.CLOUDFLARE -> {
                    item {
                        SettingsListItem(
                            modifier = Modifier.fillMaxWidth(),
                            headlineContent = "Cloudflare 账号 ID",
                            supportingContent = "Cloudflare 控制台右下角的 Account ID",
                            trailingContent = settingsViewModel.aiSubtitleCfAccountId
                                .takeIf { it.isNotBlank() }?.let { "已填" } ?: "未填",
                            remoteConfig = true,
                        )
                    }
                    item {
                        SettingsListItem(
                            modifier = Modifier.fillMaxWidth(),
                            headlineContent = "Cloudflare API Token",
                            supportingContent = "要有 Workers AI 权限的 Token",
                            trailingContent = settingsViewModel.aiSubtitleCfApiToken
                                .takeIf { it.isNotBlank() }?.let { "已填" } ?: "未填",
                            remoteConfig = true,
                        )
                    }
                }
            }

            // 播放器内核提示
            item {
                Text(
                    "当前播放器内核：${Configs.videoPlayerCore.label}。AI 字幕只在 Media3 内核下工作，切到 IJK 后字幕不出（设置里改内核）。",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}
