package com.cryptoresearch.ai.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.cryptoresearch.ai.ui.components.CoinPicker
import com.cryptoresearch.ai.ui.components.Disclaimer
import com.cryptoresearch.ai.ui.components.KeyValue
import com.cryptoresearch.ai.ui.components.LineChart
import com.cryptoresearch.ai.ui.components.Muted
import com.cryptoresearch.ai.ui.components.Panel
import com.cryptoresearch.ai.ui.components.SectionTitle
import com.cryptoresearch.ai.ui.components.Tag
import com.cryptoresearch.ai.ui.components.changeColor
import com.cryptoresearch.ai.ui.theme.MarketColors
import com.cryptoresearch.ai.ui.vm.AppViewModel
import com.cryptoresearch.ai.util.Fmt

@Composable
fun PortfolioScreen(vm: AppViewModel) {
    val coins by vm.coins.collectAsState()
    val selId by vm.selectedCoinId.collectAsState()
    val positions by vm.positions.collectAsState()
    val account by vm.account.collectAsState()
    val snaps by vm.snapshots.collectAsState()
    val txs by vm.transactions.collectAsState()
    var amountText by rememberSaveable { mutableStateOf("500") }
    var confirmReset by rememberSaveable { mutableStateOf(false) }
    val selected = coins.firstOrNull { it.id == selId } ?: coins.firstOrNull()

    val cash = account?.cash ?: 0.0
    val start = account?.startBalance ?: 10_000.0
    val holdingsValue = positions.sumOf { it.quantity * (coins.firstOrNull { c -> c.id == it.coinId }?.price ?: it.lastPrice) }
    val total = cash + holdingsValue
    val pl = total - start
    val plPct = if (start > 0) pl / start * 100.0 else 0.0

    LazyColumn(
        Modifier.fillMaxWidth(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Panel {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    SectionTitle("Virtual portfolio")
                    Tag("SIMULATION ONLY", MarketColors.Warn)
                }
                Muted("Educational sandbox with fictional money. No exchange connection, no deposits, withdrawals or real orders.")
                KeyValue("Total virtual value", Fmt.price(total))
                KeyValue("Virtual cash", Fmt.price(cash))
                KeyValue("Holdings value", Fmt.price(holdingsValue))
                KeyValue("Profit / loss vs start (${Fmt.price(start)})", "${Fmt.price(pl)}  (${Fmt.pct(plPct)})", valueColor = changeColor(pl))
            }
        }
        item {
            Panel {
                SectionTitle("Portfolio value history")
                val series = snaps.asReversed().map { it.value }
                if (series.size >= 2) {
                    LineChart(series, color = MarketColors.Accent)
                    Muted("${series.size} snapshots (recorded when prices refresh, at most every 30 minutes).")
                } else {
                    Muted("History builds up as prices refresh. Add a hypothetical holding and refresh later.")
                }
            }
        }
        item {
            Panel {
                SectionTitle("Hypothetical asset allocation")
                if (positions.isEmpty()) {
                    Muted("No holdings yet.")
                } else {
                    val rows = positions.map { p ->
                        val px = coins.firstOrNull { it.id == p.coinId }?.price ?: p.lastPrice
                        p.symbol to p.quantity * px
                    } + listOf("Cash" to cash)
                    val sum = rows.sumOf { it.second }
                    for ((name, v) in rows.sortedByDescending { it.second }) {
                        val frac = if (sum > 0) (v / sum).toFloat() else 0f
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(name, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(0.7f))
                            Box(Modifier.weight(2f).height(8.dp).clip(RoundedCornerShape(4.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
                                if (frac > 0f) Box(Modifier.fillMaxWidth(frac).height(8.dp).clip(RoundedCornerShape(4.dp)).background(MarketColors.Accent))
                            }
                            Text("  ${Fmt.plainPct(frac * 100.0)}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(0.7f))
                        }
                    }
                }
            }
        }
        item {
            Panel {
                SectionTitle("Add a hypothetical holding")
                CoinPicker(coins, selected, { vm.select(it.id) })
                OutlinedTextField(
                    value = amountText, onValueChange = { amountText = it }, singleLine = true, label = { Text("Amount in virtual USD") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth()
                )
                val amt = amountText.replace(',', '.').toDoubleOrNull()
                Button(
                    onClick = { if (selected != null && amt != null) vm.addHolding(selected, amt) },
                    enabled = selected != null && amt != null && amt > 0,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Add at current price${if (selected != null) " (${Fmt.price(selected.price)})" else ""}") }
            }
        }
        item { SectionTitle("Holdings & profit/loss simulation") }
        if (positions.isEmpty()) item { Muted("No holdings.") }
        items(positions, key = { it.coinId }) { p ->
            val coin = coins.firstOrNull { it.id == p.coinId }
            val px = coin?.price ?: p.lastPrice
            val value = p.quantity * px
            val cost = p.quantity * p.avgCost
            val pnl = value - cost
            val pnlPct = if (cost > 0) pnl / cost * 100.0 else 0.0
            Panel {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("${p.name} (${p.symbol})", fontWeight = FontWeight.SemiBold)
                    Text(Fmt.price(value), fontWeight = FontWeight.SemiBold)
                }
                Muted("Qty ${Fmt.num(p.quantity, 6)} · avg cost ${Fmt.price(p.avgCost)} · price ${Fmt.price(px)}" + if (coin == null) " (last known)" else "")
                Text("P/L ${Fmt.price(pnl)} (${Fmt.pct(pnlPct)})", color = changeColor(pnl), style = MaterialTheme.typography.bodyMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(onClick = { vm.reduceHolding(p, 0.25) }, modifier = Modifier.weight(1f)) { Text("−25%") }
                    OutlinedButton(onClick = { vm.reduceHolding(p, 0.5) }, modifier = Modifier.weight(1f)) { Text("−50%") }
                    OutlinedButton(onClick = { vm.reduceHolding(p, 1.0) }, modifier = Modifier.weight(1f)) { Text("Remove") }
                }
            }
        }
        if (txs.isNotEmpty()) {
            item { SectionTitle("Recent simulated activity") }
            items(txs.take(10), key = { "tx-" + it.id }) { t ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("${Fmt.shortDateTime(t.time)} · ${if (t.action == "ADD") "Added" else "Removed"} ${t.symbol}", style = MaterialTheme.typography.bodySmall)
                    Text("${Fmt.num(t.quantity, 6)} @ ${Fmt.price(t.price)}", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        item {
            OutlinedButton(onClick = { confirmReset = true }, modifier = Modifier.fillMaxWidth()) { Text("Reset virtual portfolio") }
        }
        item { Disclaimer() }
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Reset virtual portfolio?") },
            text = { Text("All simulated holdings, history and activity will be deleted and the virtual balance restored to $10,000.") },
            confirmButton = { TextButton(onClick = { confirmReset = false; vm.resetPortfolio(10_000.0) }) { Text("Reset") } },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Cancel") } }
        )
    }
}
