package top.yogiczy.mytv.tv.ui.screensold.epgreverse

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import androidx.tv.material3.WideButton
import kotlinx.coroutines.delay
import top.yogiczy.mytv.core.data.entities.epg.EpgProgrammeReserve
import top.yogiczy.mytv.core.data.entities.epg.EpgProgrammeReserveList
import top.yogiczy.mytv.tv.ui.material.Drawer
import top.yogiczy.mytv.tv.ui.material.DrawerPosition
import top.yogiczy.mytv.tv.ui.material.PopupContent
import top.yogiczy.mytv.tv.ui.screensold.components.rememberScreenAutoCloseState
import top.yogiczy.mytv.tv.ui.theme.MyTvTheme
import top.yogiczy.mytv.tv.ui.tooling.PreviewWithLayoutGrids
import top.yogiczy.mytv.tv.ui.utils.focusOnLaunched
import top.yogiczy.mytv.tv.ui.utils.handleKeyEvents

@Composable
fun EpgReverseScreen(
    modifier: Modifier = Modifier,
    epgProgrammeReserveListProvider: () -> EpgProgrammeReserveList = { EpgProgrammeReserveList() },
    onDeleteReserve: (EpgProgrammeReserve) -> Unit = {},
    onConfirmReserve: (EpgProgrammeReserve) -> Unit = {},
) {
    var activeProgrammeReserve by remember { mutableStateOf<EpgProgrammeReserve?>(null) }

    LaunchedEffect(Unit) {
        while (true) {
            val now = System.currentTimeMillis()
            val epgProgrammeReserveList = epgProgrammeReserveListProvider()

            // 已结束的预约不再提醒，直接清理（否则关机错过后重开应用，
            // 会对已播完的节目弹出“即将开始”）
            epgProgrammeReserveList
                .filter { it.endAt > 0 && now >= it.endAt }
                .forEach { onDeleteReserve(it) }

            // 每轮只提醒一个到点且未结束的预约：旧代码用 forEach 全删
            // 全弹，多个预约同时到点时只显示最后一个、其余的被删掉且
            // 永不提醒；其余的留待下一轮依次提醒。已有弹窗在显示时不抢
            if (activeProgrammeReserve == null) {
                epgProgrammeReserveList
                    .filter { now > it.startAt && (it.endAt <= 0 || now < it.endAt) }
                    .minByOrNull { it.startAt }
                    ?.let {
                        onDeleteReserve(it)
                        activeProgrammeReserve = it
                    }
            }

            delay(60 * 1000)
        }
    }

    PopupContent(
        visibleProvider = { activeProgrammeReserve != null },
        onDismissRequest = { activeProgrammeReserve = null },
    ) {
        EpgReverseScreenContent(
            modifier = modifier,
            activeProgrammeReserveProvider = { activeProgrammeReserve!! },
            onIgnoreReserve = { activeProgrammeReserve = null },
            onConfirmReserve = {
                onConfirmReserve(activeProgrammeReserve!!)
                activeProgrammeReserve = null
            },
        )
    }
}

@Composable
private fun EpgReverseScreenContent(
    modifier: Modifier = Modifier,
    activeProgrammeReserveProvider: () -> EpgProgrammeReserve = { EpgProgrammeReserve() },
    onIgnoreReserve: () -> Unit = {},
    onConfirmReserve: () -> Unit = {},
) {
    rememberScreenAutoCloseState(onTimeout = onIgnoreReserve)
    val activeProgrammeReserve = activeProgrammeReserveProvider()

    Drawer(
        onDismissRequest = onIgnoreReserve,
        position = DrawerPosition.Bottom,
    ) {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(
                modifier = Modifier.sizeIn(maxWidth = 556.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text("您所预约的节目即将开始", style = MaterialTheme.typography.headlineMedium)
                Text(
                    "${activeProgrammeReserve.channel} - ${activeProgrammeReserve.programme}",
                    style = MaterialTheme.typography.bodyLarge,
                )
            }

            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                WideButton(
                    modifier = Modifier
                        .focusOnLaunched()
                        .handleKeyEvents(onSelect = onConfirmReserve),
                    onClick = {},
                ) {
                    Text("立即前往")
                }

                WideButton(
                    modifier = Modifier.handleKeyEvents(onSelect = onIgnoreReserve),
                    onClick = {},
                ) {
                    Text("忽略")
                }
            }
        }
    }
}

@Preview(device = "id:Android TV (720p)")
@Composable
private fun EpgReverseScreenContentPreview() {
    MyTvTheme {
        PreviewWithLayoutGrids {
            EpgReverseScreenContent(
                activeProgrammeReserveProvider = {
                    EpgProgrammeReserve(
                        channel = "测试频道",
                        programme = "测试节目".repeat(10),
                        startAt = System.currentTimeMillis() + 100000,
                        endAt = System.currentTimeMillis() + 200000,
                    )
                },
            )
        }
    }
}