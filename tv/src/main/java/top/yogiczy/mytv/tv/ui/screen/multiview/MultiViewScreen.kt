package top.yogiczy.mytv.tv.ui.screen.multiview

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import top.yogiczy.mytv.core.data.entities.channel.Channel
import top.yogiczy.mytv.core.data.entities.channel.ChannelGroupList
import top.yogiczy.mytv.core.data.entities.channel.ChannelGroupList.Companion.channelFirstOrNull
import top.yogiczy.mytv.core.data.entities.epg.EpgList
import top.yogiczy.mytv.tv.ui.material.Snackbar
import top.yogiczy.mytv.tv.ui.screen.components.AppScreen
import top.yogiczy.mytv.tv.ui.screen.multiview.components.MultiViewItem
import top.yogiczy.mytv.tv.ui.screen.multiview.components.MultiViewLayout

@Composable
fun MultiViewScreen(
    modifier: Modifier = Modifier,
    channelGroupListProvider: () -> ChannelGroupList = { ChannelGroupList() },
    epgListProvider: () -> EpgList = { EpgList() },
    onBackPressed: () -> Unit = {},
) {
    val channelList =
        remember {
            mutableStateListOf(channelGroupListProvider().channelFirstOrNull() ?: Channel.EMPTY)
        }
    var zoomInIndex by remember { mutableStateOf<Int?>(null) }

    AppScreen(
        modifier = modifier,
        onBackPressed = onBackPressed,
    ) {
        MultiViewLayout(
            count = channelList.size,
            keyList = channelList.map { it.hashCode() },
            zoomInIndex = zoomInIndex,
        ) { index ->
            MultiViewItem(
                channelGroupListProvider = channelGroupListProvider,
                epgListProvider = epgListProvider,
                channelProvider = { channelList[index] },
                viewIndexProvider = { index },
                viewCountProvider = { channelList.size },
                zoomInIndexProvider = { zoomInIndex },
                onAddChannel = {
                    if (channelList.size >= MULTI_VIEW_MAX_COUNT) {
                        Snackbar.show("最多只能添加${MULTI_VIEW_MAX_COUNT}个频道")
                        return@MultiViewItem
                    }

                    if (channelList.contains(it)) {
                        Snackbar.show("已存在该频道")
                        return@MultiViewItem
                    }

                    channelList.add(it)
                },
                onRemoveChannel = {
                    if (channelList.size == 1) {
                        Snackbar.show("至少保留一个频道")
                        return@MultiViewItem
                    }

                    val removedIdx = channelList.indexOf(it)
                    if (removedIdx == zoomInIndex) zoomInIndex = null
                    channelList.remove(it)
                    // 被删格在放大格之前：后面的格子前移一位，放大序号也要跟
                    // 着减一；否则放大落到下一格上，删的是最后一格时序号越
                    // 界、放大悄悄消失回网格
                    if (removedIdx >= 0 && zoomInIndex != null && removedIdx < zoomInIndex!!) {
                        zoomInIndex = zoomInIndex!! - 1
                    }

                    if (channelList.size <= 1) zoomInIndex = null
                },
                onChangeChannel = {
                    // 选的就是本格在播的台：不用换，也别报“已存在该频道”
                    if (it == channelList[index]) return@MultiViewItem
                    if (channelList.contains(it)) {
                        Snackbar.show("已存在该频道")
                        return@MultiViewItem
                    }

                    channelList[index] = it
                },
                onZoomIn = { zoomInIndex = index },
                onZoomOut = { zoomInIndex = null },
                onMoveTo = { newIndex ->
                    if (newIndex == index) return@MultiViewItem

                    // 交换的两格里有放大格：放大序号跟着被换的格子走，
                    // 否则放大留在原序号、落到另一格上
                    if (zoomInIndex == index) zoomInIndex = newIndex
                    else if (zoomInIndex == newIndex) zoomInIndex = index

                    val temp = channelList[index]
                    channelList[index] = channelList[newIndex]
                    channelList[newIndex] = temp
                },
            )
        }
    }
}

const val MULTI_VIEW_MAX_COUNT = 10