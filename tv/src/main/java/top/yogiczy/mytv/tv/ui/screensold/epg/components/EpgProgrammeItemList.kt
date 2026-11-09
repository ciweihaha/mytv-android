package top.yogiczy.mytv.tv.ui.screensold.epg.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.distinctUntilChanged
import top.yogiczy.mytv.core.data.entities.channel.Channel
import top.yogiczy.mytv.core.data.entities.epg.Epg
import top.yogiczy.mytv.core.data.entities.epg.EpgProgramme
import top.yogiczy.mytv.core.data.entities.epg.EpgProgramme.Companion.isLive
import top.yogiczy.mytv.core.data.entities.epg.EpgProgrammeList
import top.yogiczy.mytv.core.data.entities.epg.EpgProgrammeReserveList
import top.yogiczy.mytv.tv.ui.theme.MyTvTheme
import top.yogiczy.mytv.tv.ui.utils.saveFocusRestorer
import kotlin.math.max

@Composable
fun EpgProgrammeItemList(
    modifier: Modifier = Modifier,
    epgProgrammeListProvider: () -> EpgProgrammeList = { EpgProgrammeList() },
    epgProgrammeReserveListProvider: () -> EpgProgrammeReserveList = { EpgProgrammeReserveList() },
    supportPlaybackProvider: () -> Boolean = { false },
    currentPlaybackProvider: () -> EpgProgramme? = { null },
    onPlayback: (EpgProgramme) -> Unit = {},
    onReserve: (EpgProgramme) -> Unit = {},
    focusOnLive: Boolean = true,
    onUserAction: () -> Unit = {},
) {
    val epgProgrammeList = epgProgrammeListProvider()
    val itemFocusRequesterList = List(epgProgrammeList.size) { FocusRequester() }

    val listState = LazyListState(max(0, epgProgrammeList.indexOfFirst { it.isLive() } - 2))
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }
            .distinctUntilChanged()
            .collect { _ -> onUserAction() }
    }

    LazyColumn(
        modifier = modifier
            // 原先注释掉是因空列表时下标越界闪退：无直播节目时
            // indexOfFirst 得 -1，max(0, -1) = 0 在空列表上越界。
            // 改成 getOrNull + Default 恢复焦点回到直播节目
            .saveFocusRestorer {
                itemFocusRequesterList.getOrNull(
                    max(0, epgProgrammeList.indexOfFirst { it.isLive() })
                ) ?: FocusRequester.Default
            },
        state = listState,
        contentPadding = PaddingValues(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        itemsIndexed(
            epgProgrammeList,
            // key 不能只用 hashCode：EPG 源里重复的节目条目（同名同
            // 起止，含解析失败起止为 0 的条目）完全相等，撞 key 会让
            // LazyColumn 抛异常；缀上下标保证唯一
            key = { index, programme -> "${programme.hashCode()}_$index" },
        ) { index, programme ->
            EpgProgrammeItem(
                modifier = Modifier.focusRequester(itemFocusRequesterList[index]),
                epgProgrammeProvider = { programme },
                supportPlaybackProvider = supportPlaybackProvider,
                isPlaybackProvider = { currentPlaybackProvider() == programme },
                hasReservedProvider = { epgProgrammeReserveListProvider().firstOrNull { it.programme == programme.title } != null },
                onPlayback = { onPlayback(programme) },
                onReserve = { onReserve(programme) },
                focusOnLive = focusOnLive,
            )
        }
    }
}

@Preview
@Composable
private fun EpgProgrammeItemListPreview() {
    MyTvTheme {
        EpgProgrammeItemList(
            epgProgrammeListProvider = { EpgProgrammeList(Epg.example(Channel.EXAMPLE).programmeList) }
        )
    }
}