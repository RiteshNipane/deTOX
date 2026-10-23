package com.detox.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp
import com.detox.DetoxApp
import java.time.LocalDate

@Composable
fun Dashboard(app: DetoxApp, dailyLimit: Int, onLimitChange: (Int) -> Unit) {
    val days = remember { (6 downTo 0).map { LocalDate.now().minusDays(it.toLong()) } }
    val stats by app.db.reelDao().observeSince(days.first().toString()).collectAsState(initial = emptyList())
    val names = remember { app.profiles.all().associate { it.id to it.displayName } }

    val perDay = days.map { d -> stats.filter { it.dayKey == d.toString() }.sumOf { it.reelCount } }
    val today = days.last().toString()
    val todayByApp = stats.filter { it.dayKey == today }
    val blockedToday = todayByApp.sumOf { it.blockedCount }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Today: ${perDay.last()}", style = MaterialTheme.typography.headlineSmall)
            if (dailyLimit > 0) Text("Limit: $dailyLimit", style = MaterialTheme.typography.bodyMedium)

            Text("Last 7 days", style = MaterialTheme.typography.titleSmall)
            WeekBars(values = perDay, labels = days.map { it.dayOfWeek.name.first().toString() })

            Text("By app (today)", style = MaterialTheme.typography.titleSmall)
            if (todayByApp.isEmpty()) {
                Text("Nothing counted yet.", style = MaterialTheme.typography.bodyMedium)
            } else todayByApp.forEach {
                Text("${names[it.appId] ?: it.appId}: ${it.reelCount}", style = MaterialTheme.typography.bodyMedium)
            }
            if (blockedToday > 0) Text("Blocked today: $blockedToday", style = MaterialTheme.typography.bodyMedium)

            Text("Daily limit", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { onLimitChange((dailyLimit - 10).coerceAtLeast(0)) }) { Text("-10") }
                Button(onClick = {}, enabled = false) { Text(if (dailyLimit == 0) "Off" else "$dailyLimit") }
                OutlinedButton(onClick = { onLimitChange(dailyLimit + 10) }) { Text("+10") }
            }
        }
    }
}

@Composable
private fun WeekBars(values: List<Int>, labels: List<String>) {
    val max = (values.maxOrNull() ?: 0).coerceAtLeast(1)
    val bar = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.surfaceVariant
    Canvas(Modifier.fillMaxWidth().height(96.dp)) {
        val slot = size.width / values.size
        val barW = slot * 0.55f
        values.forEachIndexed { i, v ->
            val x = i * slot + (slot - barW) / 2
            drawRoundRect(track, Offset(x, 0f), Size(barW, size.height), CornerRadius(8f))
            val h = size.height * v / max
            drawRoundRect(bar, Offset(x, size.height - h), Size(barW, h), CornerRadius(8f))
        }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
        labels.forEachIndexed { i, l -> Text("$l\n${values[i]}", style = MaterialTheme.typography.labelSmall) }
    }
}
