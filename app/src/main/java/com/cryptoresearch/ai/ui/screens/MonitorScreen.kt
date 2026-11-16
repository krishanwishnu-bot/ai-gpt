package com.cryptoresearch.ai.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cryptoresearch.ai.data.AgentStatus
import com.cryptoresearch.ai.ui.components.Disclaimer
import com.cryptoresearch.ai.ui.components.Muted
import com.cryptoresearch.ai.ui.components.Panel
import com.cryptoresearch.ai.ui.components.Tag
import com.cryptoresearch.ai.ui.theme.MarketColors
import com.cryptoresearch.ai.ui.vm.AppViewModel
import com.cryptoresearch.ai.util.Fmt

@Composable
fun MonitorScreen(vm: AppViewModel) {
    val agents by vm.agentStates.collectAsState()
    val research by vm.research.collectAsState()

    LazyColumn(
        Modifier.fillMaxWidth(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Panel {
                Text("Agent Monitor", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Muted("Nine rule-based research modules run sequentially through a central orchestrator. They are calculation modules, not large language models. Tap an agent for its detailed report.")
                Muted(
                    if (research.running) "Currently analysing ${research.coinSymbol}."
                    else if (research.coinSymbol != null) "Last run: ${research.coinSymbol}."
                    else "No analysis has been run in this session."
                )
            }
        }
        items(agents, key = { it.id }) { a ->
            var open by remember { mutableStateOf(false) }
            val col = when (a.status) {
                AgentStatus.IDLE -> MarketColors.Neutral
                AgentStatus.RUNNING -> MarketColors.Accent
                AgentStatus.COMPLETED -> MarketColors.Up
                AgentStatus.FAILED -> MarketColors.Down
                AgentStatus.WAITING -> MarketColors.Warn
            }
            Panel(Modifier.clickable { open = !open }) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("${a.id}. ${a.name}", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    Tag(a.status.label, col)
                }
                Muted(a.role)
                Muted("Last execution: " + (a.lastRunAt?.let { Fmt.dateTime(it) } ?: "never"))
                Muted("Processing: ${a.processing}")
                Muted("Data sources: " + (if (a.sources.isEmpty()) "—" else a.sources.joinToString("; ")))
                if (a.summary.isNotBlank()) Text(a.summary, style = MaterialTheme.typography.bodySmall)
                a.error?.let { Text("Error: $it", color = MarketColors.Down, style = MaterialTheme.typography.bodySmall) }
                if (open) {
                    if (a.details.isEmpty()) Muted("No detailed report yet.")
                    for (d in a.details) Text(d, style = MaterialTheme.typography.bodySmall)
                } else if (a.details.isNotEmpty()) {
                    Muted("Tap to show the detailed report ▼")
                }
            }
        }
        item { Disclaimer() }
    }
}
