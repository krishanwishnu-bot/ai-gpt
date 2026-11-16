package com.cryptoresearch.ai.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cryptoresearch.ai.data.AgentStatus
import com.cryptoresearch.ai.ui.components.CoinPicker
import com.cryptoresearch.ai.ui.components.Disclaimer
import com.cryptoresearch.ai.ui.components.EmptyState
import com.cryptoresearch.ai.ui.components.ErrorBanner
import com.cryptoresearch.ai.ui.components.Muted
import com.cryptoresearch.ai.ui.components.Panel
import com.cryptoresearch.ai.ui.components.SectionTitle
import com.cryptoresearch.ai.ui.vm.AppViewModel

@Composable
fun ResearchScreen(vm: AppViewModel) {
    val coins by vm.coins.collectAsState()
    val selId by vm.selectedCoinId.collectAsState()
    val ui by vm.research.collectAsState()
    val agents by vm.agentStates.collectAsState()
    val selected = coins.firstOrNull { it.id == selId } ?: coins.firstOrNull()

    LazyColumn(
        Modifier.fillMaxWidth(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Panel {
                SectionTitle("AI Research")
                Muted("Eight specialised agents run one after another on the selected coin and produce a 12-section report. Everything is computed on your device from live public data.")
                CoinPicker(coins, selected, { vm.select(it.id) })
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (ui.running) {
                        OutlinedButton(onClick = { vm.cancelResearch() }, modifier = Modifier.weight(1f)) { Text("Stop analysis") }
                        CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                    } else {
                        Button(onClick = { selected?.let { vm.startResearch(it) } }, enabled = selected != null, modifier = Modifier.weight(1f)) {
                            Text(if (selected != null) "Analyse ${selected.symbol}" else "Analyse")
                        }
                    }
                }
                if (coins.isEmpty()) Muted("Load the Top 50 list first (Dashboard or Top 50 Coins).")
            }
        }
        if (ui.running) {
            item {
                Panel {
                    val done = agents.count { it.status == AgentStatus.COMPLETED || it.status == AgentStatus.FAILED }
                    SectionTitle("Running ${ui.coinSymbol ?: ""} — $done / 8 agents finished")
                    LinearProgressIndicator(progress = { done / 8f }, modifier = Modifier.fillMaxWidth())
                    for (a in agents) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("${a.id}. ${a.name}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                            Text("${a.status.label}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    val cur = agents.firstOrNull { it.status == AgentStatus.RUNNING || it.status == AgentStatus.WAITING }
                    if (cur != null) Muted(cur.processing)
                }
            }
        }
        ui.error?.let { e ->
            item { ErrorBanner(e, if (selected != null && !ui.running) ({ vm.startResearch(selected) }) else null) }
        }
        val rep = ui.report
        if (rep != null && !ui.running) {
            reportItems(rep)
        } else if (!ui.running && ui.error == null) {
            item { EmptyState("Select a coin and tap Analyse. The report is saved automatically to Research History.") }
            item { Disclaimer() }
        }
    }
}
