package com.cryptoresearch.ai.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cryptoresearch.ai.analysis.Ind
import com.cryptoresearch.ai.analysis.TechnicalAnalyzer
import com.cryptoresearch.ai.data.Timeframe
import com.cryptoresearch.ai.data.freshnessOf
import com.cryptoresearch.ai.ui.components.CandleChart
import com.cryptoresearch.ai.ui.components.ChoiceRow
import com.cryptoresearch.ai.ui.components.CoinPicker
import com.cryptoresearch.ai.ui.components.Disclaimer
import com.cryptoresearch.ai.ui.components.ErrorBanner
import com.cryptoresearch.ai.ui.components.FreshChip
import com.cryptoresearch.ai.ui.components.Muted
import com.cryptoresearch.ai.ui.components.Panel
import com.cryptoresearch.ai.ui.components.ProfileChart
import com.cryptoresearch.ai.ui.components.SectionTitle
import com.cryptoresearch.ai.ui.components.Tag
import com.cryptoresearch.ai.ui.components.WarnBanner
import com.cryptoresearch.ai.ui.theme.MarketColors
import com.cryptoresearch.ai.ui.vm.AppViewModel
import com.cryptoresearch.ai.util.Fmt

@Composable
fun TechnicalScreen(vm: AppViewModel) {
    val coins by vm.coins.collectAsState()
    val selId by vm.selectedCoinId.collectAsState()
    val ui by vm.tech.collectAsState()
    val settings by vm.settings.collectAsState()
    var tfLabel by rememberSaveable { mutableStateOf(settings.defaultTimeframe) }
    val tf = Timeframe.fromLabel(tfLabel)
    val selected = coins.firstOrNull { it.id == selId } ?: coins.firstOrNull()

    LaunchedEffect(selected?.id, tfLabel) {
        if (selected != null) vm.loadTech(selected, tf)
    }

    LazyColumn(
        Modifier.fillMaxWidth(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Panel {
                SectionTitle("Technical Analysis")
                CoinPicker(coins, selected, { vm.select(it.id) })
                ChoiceRow(Timeframe.values().toList(), tf, { it.label }, { tfLabel = it.label })
                if (coins.isEmpty()) Muted("Load the Top 50 list first.")
            }
        }
        if (ui.loading) {
            item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) { CircularProgressIndicator() } }
        }
        ui.error?.let { e -> item { ErrorBanner(e, if (selected != null) ({ vm.loadTech(selected, tf, true) }) else null) } }
        val snap = ui.snap
        if (snap != null && !ui.loading) {
            if (!snap.ok) {
                item { WarnBanner("${snap.tf.label}: ${snap.note ?: "analysis unavailable"} (source: ${snap.source})") }
            } else {
                ui.note?.let { n -> item { WarnBanner("Refresh failed, showing cached data. $n") } }
                item {
                    val candles = ui.candles
                    val close = remember(candles) { DoubleArray(candles.size) { candles[it].close } }
                    val e20 = remember(close) { Ind.ema(close, 20) }
                    val e50 = remember(close) { Ind.ema(close, 50) }
                    Panel {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("${selected?.symbol ?: ""} · ${snap.tf.label}", fontWeight = FontWeight.Bold)
                            FreshChip(freshnessOf(ui.fetchedAt, ui.fromCache, ui.stale, 60 * 60_000L))
                        }
                        CandleChart(
                            candles = candles,
                            overlays = listOf(e20 to MarketColors.Accent, e50 to MarketColors.Warn),
                            showVolume = snap.volume?.available == true
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Tag("EMA20", MarketColors.Accent)
                            Tag("EMA50", MarketColors.Warn)
                            Tag(snap.trend, if (snap.trend == "Bullish") MarketColors.Up else if (snap.trend == "Bearish") MarketColors.Down else MarketColors.Neutral)
                        }
                        Muted("Last ${minOf(80, candles.size)} closed candles · data: ${snap.source} · ${Fmt.dateTime(snap.lastTime)}")
                        if (snap.approximate) Muted("Approximate: candles derived from a price series (no real OHLC/volume).")
                    }
                }
                item {
                    Panel {
                        SectionTitle("Indicators & structure")
                        for (l in TechnicalAnalyzer.describe(snap).drop(1)) Text(l, style = MaterialTheme.typography.bodySmall)
                    }
                }
                item {
                    val v = snap.volume
                    Panel {
                        SectionTitle("Volume")
                        if (v == null || !v.available) {
                            Muted(v?.note ?: "Volume unavailable.")
                        } else {
                            Text("Last candle: ${Fmt.num(v.lastVsAvg, 2)}× the 20-candle average · spikes ${v.spikes}/${v.spikeWindow} · trend ${v.trend}", style = MaterialTheme.typography.bodySmall)
                            Text(
                                if (v.deltaAvailable && v.deltaPct20 != null) "Volume delta (20 candles): ${Fmt.pct(v.deltaPct20, 1)} of volume"
                                else (v.deltaNote ?: "Volume delta unavailable"),
                                style = MaterialTheme.typography.bodySmall
                            )
                            if (v.profile.isNotEmpty()) {
                                Muted("Volume profile (approximate, last 100 candles, volume placed at typical price). Orange bar = point of control ${Fmt.price(v.poc)}.")
                                ProfileChart(v.profile, v.poc)
                                Muted("Value area (≈70%): ${Fmt.price(v.vaLow)} – ${Fmt.price(v.vaHigh)}")
                            }
                        }
                    }
                }
                item {
                    Panel {
                        SectionTitle("Signals (net score ${snap.net})")
                        if (snap.signals.isEmpty()) Muted("No signals.")
                        for (s in snap.signals) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Tag(if (s.direction > 0) "BULL" else "BEAR", if (s.direction > 0) MarketColors.Up else MarketColors.Down)
                                Text(s.text, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                            }
                        }
                        Muted("Signals describe indicator states, not recommendations.")
                    }
                }
            }
        }
        item { Disclaimer() }
    }
}
