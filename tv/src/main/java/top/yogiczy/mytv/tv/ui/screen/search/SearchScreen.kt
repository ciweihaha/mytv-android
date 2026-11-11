package top.yogiczy.mytv.tv.ui.screen.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import com.github.promeg.pinyinhelper.Pinyin
import top.yogiczy.mytv.core.data.entities.channel.Channel
import top.yogiczy.mytv.core.data.entities.channel.ChannelGroupList
import top.yogiczy.mytv.core.data.entities.channel.ChannelList
import top.yogiczy.mytv.core.data.entities.epg.EpgList
import top.yogiczy.mytv.tv.ui.rememberChildPadding
import top.yogiczy.mytv.tv.ui.screen.components.AppScreen
import top.yogiczy.mytv.tv.ui.screen.search.components.SearchKeyboard
import top.yogiczy.mytv.tv.ui.screen.search.components.SearchResult
import top.yogiczy.mytv.tv.ui.theme.MyTvTheme
import top.yogiczy.mytv.tv.ui.tooling.PreviewWithLayoutGrids
import top.yogiczy.mytv.tv.ui.utils.gridColumns

@Composable
fun SearchScreen(
    modifier: Modifier = Modifier,
    channelGroupListProvider: () -> ChannelGroupList = { ChannelGroupList() },
    onChannelSelected: (Channel) -> Unit = {},
    onChannelFavoriteToggle: (Channel) -> Unit = {},
    epgListProvider: () -> EpgList = { EpgList() },
    onBackPressed: () -> Unit = {},
) {
    val childPadding = rememberChildPadding()

    var key by rememberSaveable { mutableStateOf("") }

    // 全表过滤只在关键词或频道单变了时才重算：之前 provider 是现场算的，
    // SearchResult 每次重画（焦点挪动、节目单刷新等）都要把几千个频道重滤一遍
    val channelGroupList = channelGroupListProvider()
    val filteredChannelList = remember(key, channelGroupList) {
        if (key.isEmpty()) {
            ChannelList()
        } else {
            ChannelList(channelGroupList.channelList.filter { it.matchesSearchQuery(key) })
        }
    }

    AppScreen(
        modifier = modifier,
        header = {
            Text("搜索")
            Text(key.ifEmpty { "关键词..." })
        },
        canBack = true,
        onBackPressed = onBackPressed,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            SearchKeyboard(
                modifier = Modifier
                    .padding(start = childPadding.start)
                    .width(4.gridColumns()),
                onInput = { key += it },
                onDelete = { key = key.dropLast(1) },
            )

            SearchResult(
                channelListProvider = { filteredChannelList },
                onChannelSelected = onChannelSelected,
                onChannelFavoriteToggle = onChannelFavoriteToggle,
                epgListProvider = epgListProvider,
            )
        }
    }
}

@Preview(device = "id:Android TV (720p)")
@Composable
private fun SearchScreenPreview() {
    MyTvTheme {
        SearchScreen()
        PreviewWithLayoutGrids { }
    }
}

/**
 * 频道名是否匹配搜索关键词：原名、拼音首字母、全拼三者任一包含即可。
 * 旧实现只比拼音首字母，输全拼（如 YANGSHI 搜“央视”）搜不到。
 */
private fun Channel.matchesSearchQuery(query: String): Boolean {
    if (query.isEmpty()) return false
    val key = query.lowercase()
    val (lowerName, initials, fullPinyin) = SearchPinyinCache.partsFor(name)
    if (lowerName.contains(key)) return true
    if (initials.contains(key)) return true
    return fullPinyin.contains(key)
}

/**
 * 频道名拼音只算一次：搜索每敲一个键都要全表过滤，每个频道名都要转一次拼音；
 * 几千个频道的源，每敲一键就是几千次字典查，全在主线程上，直接拖慢输入。
 * 按频道名缓存小写名/首字母/全拼，之后每键只剩字符串包含比较。
 * 条目数跟源里不重名频道数一致；超过 1 万条时清空重建，防止换源攒得多。
 */
private object SearchPinyinCache {
    private val cache = mutableMapOf<String, Triple<String, String, String>>()

    fun partsFor(name: String): Triple<String, String, String> {
        if (cache.size >= 10_000) cache.clear()
        return cache.getOrPut(name) {
            val pinyinParts = Pinyin.toPinyin(name, ",").split(",")
            val initials = pinyinParts.joinToString("") { it.firstOrNull()?.lowercase() ?: "" }
            val fullPinyin = pinyinParts.joinToString("") { it.lowercase() }
            Triple(name.lowercase(), initials, fullPinyin)
        }
    }
}

