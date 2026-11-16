package com.cryptoresearch.ai.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.cryptoresearch.ai.data.Classification
import com.cryptoresearch.ai.data.Timeframe
import com.cryptoresearch.ai.ui.components.ChoiceRow
import com.cryptoresearch.ai.ui.components.CoinPicker
import com.cryptoresearch.ai.ui.components.Disclaimer
import com.cryptoresearch.ai.ui.components.ErrorBanner
import com.cryptoresearch.ai.ui.components.KeyValue
import com.cryptoresearch.ai.ui.components.LineChart
import com.cryptoresearch.ai.ui.components.Muted
import com.cryptoresearch.ai.ui.components.Panel
import com.cryptoresearch.ai.ui.components.SectionTitle
import com.cryptoresearch.ai.ui.components.WarnBanner
import com.cryptoresearch.ai.ui.components.changeColor
import com.cryptoresearch.ai.ui.theme.MarketColors
import com.cryptoresearch.ai.ui.vm.AppViewModel
import com.cryptoresearch.ai.util.Fmt

private fun periodOptions(tf: Timeframe): List<Int> = when (tf) {
    Timeframe.M15 -> listOf(7, 14, 30)
    Timeframe.H1 -> listOf(30, 90, 180)
    Timeframe.H4 -> listOf(90, 180, 365)
    Timeframe.D1 -> listOf(180, 365, 730)
}

@Composable
fun BacktestScreen(vm: AppViewModel) {
    val coins by vm.coins.collectAsState()
    val selId by vm.selectedCoinId.collectAsState()
    val ui by vm.backtest.collectAsState()
    var tfLabel by rememberSaveable { mutableStateOf("1d") }
    val tf = Timeframe.fromLabel(tfLabel)
    var days by rememberSaveable { mutableStateOf(365) }
    var horizon by rememberSaveable { mutableStateOf(5) }
    var feeText by rememberSaveable { mutableStateOf("0.10") }
    var slipText by rememberSaveable { mutableStateOf("0.05") }
    val selected = coins.firstOrNull { it.id == selId } ?: coins.firstOrNull()
    val options = periodOptions(tf)
    val effectiveDays = if (days in options) days else options[1]

    val fee = feeText.replace(',', '.').toDoubleOrNull()
    val slip = slipText.replace(',', '.').toDoubleOrNull()
    val inputError: String? = when {
        fee == null || fee < 0 || fee > 5 -> "Fee must be a number between 0 and 5 (%)."
        slip == null || slip < 0 || slip > 5 -> "Slippage must be a number between 0 and 5 (%)."
        else -> null
    }

    LazyColumn(
        Modifier.fillMaxWidth(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Panel {
                SectionTitle("Backtesting (educational)")
                Muted("Evaluates how a fixed, rule-based research classification would have compared with the market's movement over the following candles. It does not predict anything.")
                CoinPicker(coins, selected, { vm.select(it.id) })
                Muted("Timeframe")
                ChoiceRow(Timeframe.values().toList(), tf, { it.label }, { tfLabel = it.label })
                Muted("Historical period")
                ChoiceRow(options, effectiveDays, { "${it}d" }, { days = it })
                Muted("Holding period after each classification (candles)")
                ChoiceRow(listOf(1, 3, 5, 10, 20), horizon, { it.toString() }, { horizon = it })
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = feeText, onValueChange = { feeText = it }, singleLine = true, label = { Text("Fee % per side") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = slipText, onValueChange = { slipText = it }, singleLine = true, label = { Text("Slippage % per side") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f)
                    )
                }
                inputError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (ui.loading) {
                        OutlinedButton(onClick = { vm.cancelBacktest() }, modifier = Modifier.weight(1f)) { Text("Cancel") }
                        CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.padding(4.dp))
                    } else {
                        Button(
                            onClick = { if (selected != null && fee != null && slip != null) vm.runBacktest(selected, tf, effectiveDays, horizon, fee, slip) },
                            enabled = selected != null && inputError == null,
                            modifier = Modifier.weight(1f)
                        ) { Text("Run historical evaluation") }
                    }
                }
            }
        }
        ui.error?.let { e -> item { ErrorBanner(e) } }
        ui.note?.let { n -> item { WarnBanner(n) } }
        val r = ui.result
        if (r != null && !ui.loading) {
            item {
                Panel {
                    SectionTitle("Results — ${ui.coinSymbol} · ${r.params.tf.label}")
                    Muted("Window: ${Fmt.date(r.windowStart)} → ${Fmt.date(r.windowEnd)} (${r.windowCandles} candles) · data: ${ui.source ?: "—"}")
                    KeyValue("Bullish classifications", (r.distribution[Classification.BULLISH] ?: 0).toString())
                    KeyValue("Bearish classifications", (r.distribution[Classification.BEARISH] ?: 0).toString())
                    KeyValue("Neutral classifications", (r.distribution[Classification.NEUTRAL] ?: 0).toString())
                    KeyValue("Non-overlapping samples", r.trades.size.toString())
                }
            }
            item {
                Panel {
                    SectionTitle("Historical accuracy")
                    KeyValue("Bullish: market rose afterwards", if (r.bullishHitRate != null) "${Fmt.plainPct(r.bullishHitRate)} of ${r.bullishTrades}" else "no samples")
                    KeyValue("Bearish: market fell afterwards", if (r.bearishHitRate != null) "${Fmt.plainPct(r.bearishHitRate)} of ${r.bearishTrades}" else "no samples")
                    KeyValue("Overall directional hit rate", Fmt.plainPct(r.overallHitRate))
                    Muted("A coin flip would score about 50%. Small samples are unreliable.")
                }
            }
            item {
                Panel {
                    SectionTitle("Hypothetical results after fees & slippage")
                    KeyValue("Average net result per sample", Fmt.pct(r.avgNetReturnPct, 2), valueColor = changeColor(r.avgNetReturnPct))
                    KeyValue("Compounded net result", Fmt.pct(r.compoundedNetPct, 1), valueColor = changeColor(r.compoundedNetPct))
                    KeyValue("Buy & hold over same window", Fmt.pct(r.buyHoldPct, 1), valueColor = changeColor(r.buyHoldPct))
                    KeyValue("Max drawdown (trade-level)", Fmt.plainPct(r.maxDrawdownPct), valueColor = MarketColors.Down)
                    KeyValue("Std. deviation per sample", Fmt.plainPct(r.tradeStdDevPct, 2))
                    KeyValue("Annualised volatility (price)", Fmt.plainPct(r.annualizedVolPct, 1))
                    Muted("Hypothetical equity curve (start = 1.0)")
                    LineChart(r.equityCurve, color = MarketColors.Accent, baseline = 1.0)
                }
            }
            item {
                Panel {
                    SectionTitle("Assumptions & limitations")
                    for (n in r.notes) Text("• $n", style = MaterialTheme.typography.bodySmall)
                    Text("• Historical performance is shown separately from any future expectation: none is implied.", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        item { Disclaimer() }
    }
}
