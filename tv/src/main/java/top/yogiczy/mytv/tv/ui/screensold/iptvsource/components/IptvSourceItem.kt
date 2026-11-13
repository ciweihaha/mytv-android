package top.yogiczy.mytv.tv.ui.screensold.iptvsource.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Error

import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.ListItem
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.RadioButton
import androidx.tv.material3.Text
import top.yogiczy.mytv.core.data.entities.iptvsource.IptvSource
import top.yogiczy.mytv.tv.ui.material.CircularProgressIndicator
import top.yogiczy.mytv.tv.ui.material.Tag
import top.yogiczy.mytv.tv.ui.material.TagDefaults
import top.yogiczy.mytv.tv.ui.theme.MyTvTheme
import top.yogiczy.mytv.tv.ui.utils.focusOnLaunched
import top.yogiczy.mytv.tv.ui.utils.handleKeyEvents
import top.yogiczy.mytv.tv.ui.utils.ifElse
import top.yogiczy.mytv.tv.ui.screen.settings.subcategories.IptvSourceDetail
@Composable
fun IptvSourceItem(
    modifier: Modifier = Modifier,
    lineProvider: () -> IptvSource = { IptvSource() },
    lineIdxProvider: () -> Int = { 0 },
    isSelectedProvider: () -> Boolean = { false },
    iptvSourceDetailProvider: () -> IptvSourceDetail = { IptvSourceDetail.Loading },
    onSelected: () -> Unit = {},
) {
    val line = lineProvider()
    val lineIdx = lineIdxProvider()
    val isSelected = isSelectedProvider()
    val iptvSourceDetail = iptvSourceDetailProvider()
    ListItem(
        modifier = modifier
            .ifElse(isSelected, Modifier.focusOnLaunched())
            .handleKeyEvents(onSelect = onSelected),
        selected = false,
        onClick = {},
        headlineContent = { Text(line.name ?: "订阅源${lineIdx + 1}", maxLines = 1) },
        overlineContent = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Tag(
                    if (line.isLocal) "本地" else "远程",
                    colors = TagDefaults.colors(
                        containerColor = LocalContentColor.current.copy(0.1f)
                    ),
                )

                if (!line.transformJs.isNullOrEmpty()) {
                    Tag(
                        "转换JS",
                        colors = TagDefaults.colors(
                            containerColor = LocalContentColor.current.copy(0.1f)
                        ),
                    )
                }
            }
        },
        supportingContent = { 
            Text(line.url, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (iptvSourceDetail is IptvSourceDetail.Ready) {
                Text(
                    listOf(
                        "共${iptvSourceDetail.channelGroupCount}个分组",
                        "${iptvSourceDetail.channelCount}个频道",
                        "${iptvSourceDetail.lineCount}条源"
                    ).joinToString("，")
                )
            }
        },
        trailingContent = {
            when (iptvSourceDetail) {
                is IptvSourceDetail.Loading -> CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 3.dp,
                    color = LocalContentColor.current,
                    trackColor = MaterialTheme.colorScheme.surface.copy(0.1f),
                )

                is IptvSourceDetail.Error -> Icon(
                    Icons.Default.Error,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                )
                else -> RadioButton(selected = isSelected, onClick = {})
            }
        },
    )
}
@Preview
@Composable
private fun IptvSourceItemPreview() {
    MyTvTheme {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            IptvSourceItem(
                lineProvider = { IptvSource.EXAMPLE },
                lineIdxProvider = { 0 },
                isSelectedProvider = { true },

            )

            IptvSourceItem(
                lineProvider = { IptvSource.EXAMPLE },
                lineIdxProvider = { 0 },
            )

        }
    }
}