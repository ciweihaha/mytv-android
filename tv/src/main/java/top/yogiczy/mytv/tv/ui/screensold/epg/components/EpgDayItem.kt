package top.yogiczy.mytv.tv.ui.screensold.epg.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.tv.material3.DenseListItem
import androidx.tv.material3.ListItemDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import top.yogiczy.mytv.tv.ui.theme.MyTvTheme
import top.yogiczy.mytv.tv.ui.utils.handleKeyEvents
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun EpgDayItem(
    modifier: Modifier = Modifier,
    dayProvider: () -> String = { "" }, // 格式：E MM-dd
    isSelectedProvider: () -> Boolean = { false },
    onDaySelected: () -> Unit = {},
) {
    val day = dayProvider()

    val dateFormat = remember { SimpleDateFormat("E MM-dd", Locale.getDefault()) }
    val (today, tomorrow, dayAfterTomorrow) = remember {
        val now = System.currentTimeMillis()
        Triple(
            dateFormat.format(now),
            dateFormat.format(now + 24 * 3600 * 1000),
            dateFormat.format(now + 48 * 3600 * 1000),
        )
    }
    val lines = remember(day) { day.split(" ") }

    DenseListItem(
        modifier = modifier
            .handleKeyEvents(onSelect = onDaySelected),
        colors = ListItemDefaults.colors(
            selectedContainerColor = MaterialTheme.colorScheme.inverseSurface.copy(0.1f),
            selectedContentColor = MaterialTheme.colorScheme.onSurface,
        ),
        selected = isSelectedProvider(),
        onClick = {},
        headlineContent = {
            Text(
                when (day) {
                    today -> "今天"
                    tomorrow -> "明天"
                    dayAfterTomorrow -> "后天"
                    else -> lines[0]
                },
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
            Text(
                lines[1],
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
        },
    )
}

@Preview
@Composable
private fun EpgDayItemPreview() {
    MyTvTheme {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            EpgDayItem(
                dayProvider = { "周一 07-09" },
            )

            EpgDayItem(
                dayProvider = { "周一 07-09" },
                isSelectedProvider = { true },
            )
        }
    }
}