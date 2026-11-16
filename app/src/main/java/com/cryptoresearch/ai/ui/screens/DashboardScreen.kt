package com.cryptoresearch.ai.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cryptoresearch.ai.data.Classification
import com.cryptoresearch.ai.data.freshnessOf
import com.cryptoresearch.ai.ui.components.ChangeText
import com.cryptoresearch.ai.ui.components.ClassTag
import com.cryptoresearch.ai.ui.components.CoinLogo
import com.cryptoresearch.ai.ui.components.Disclaimer
import com.cryptoresearch.ai.ui.components.EmptyState
import com.cryptoresearch.ai.ui.components.ErrorBanner
import com.cryptoresearch.ai.ui.components.FreshChip
import com.cryptoresearch.ai.ui.components.KeyValue
import com.cryptoresearch.ai.ui.components.Muted
import com.cryptoresearch.ai.ui.components.Panel
import com.cryptoresearch.ai.ui.components.SectionTitle
import com.cryptoresearch.ai.ui.components.Tag
import com.cryptoresearch.ai.ui.components.classColor
import com.cryptoresearch.ai.ui.theme.MarketColors
import com.cryptoresearch.ai.ui.vm.AppViewModel
import com.cryptoresearch.ai.util.Fmt
import kotlin.math.abs

@Composable
fun DashboardScreen(vm: AppViewModel, onOpenCoin: (String) -> Unit, onNav: (String) -> Unit, onOpenReport: (Long) -> Unit) {
    val coins by vm.coins.collectAsState()
    val status by vm.coinsStatus.collectAsState()
    val global by vm.global.collectAsState()
    val fng by vm.fearGreed.collectAsState()
    val extrasError by vm.extrasError.collectAsState()
    val watch by vm.watchlist.collectAsState()
    val rows by vm.reportRows.collectAsState()

    LazyColumn(
        Modifier.fillMaxWidth(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    val newest = coins.maxOfOrNull { it.fetchedAt }
                    if (newest != null) {
                        val f = freshnessOf(newest, false, status.error != null)
                        FreshChip(f)
                        Muted("Last update: ${Fmt.dateTime(newest)} · source: ${coins.firstOrNull()?.source ?: "—"}")
                    } else {
                        Muted(if (status.loading) "Loading market data…" else "No market data yet")
                    }
                }
                if (status.loading) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                else IconButton(onClick = { vm.refreshCoins() }) { Icon(Icons.Filled.Refresh, contentDescription = "Refresh") }
            }
        }
        status.error?.let { e ->
            item { ErrorBanner(e + if (coins.isNotEmpty()) " Showing the last saved data (marked stale)." else "", { vm.refreshCoins() }) }
        }
        if (coins.isEmpty() && status.error == null) {
            item { EmptyState(if (status.loading) "Fetching the Top 50 coins…" else "Pull data with the refresh button.") }
        }

        // ---- total market overview
        item {
            Panel {
                SectionTitle("Total market overview")
                val g = global?.data
                if (g != null) {
                    KeyValue("Total market cap", Fmt.compact(g.totalMarketCap))
                    KeyValue("24h volume", Fmt.compact(g.totalVolume))
                    KeyValue("Market cap 24h change", Fmt.pct(g.marketCapChange24h), valueColor = com.cryptoresearch.ai.ui.components.changeColor(g.marketCapChange24h))
                    KeyValue("BTC dominance", Fmt.plainPct(g.btcDominance))
                    KeyValue("ETH dominance", Fmt.plainPct(g.ethDominance))
                    global?.let { Muted("CoinGecko global · " + freshnessOf(it.fetchedAt, it.fromCache, it.stale, 30 * 60_000L).text) }
                } else {
                    Muted("Global statistics unavailable" + (extrasError?.let { ": $it" } ?: "."))
                }
                val f = fng?.data
                if (f != null) {
                    KeyValue("Fear & Greed Index", "${f.value} · ${f.label}")
                    Muted("alternative.me · market-wide index")
                }
            }
        }

        if (coins.isNotEmpty()) {
            // ---- movement summary + volatility (computed from the live Top-50 list)
            item {
                val withCh = coins.filter { it.change24h != null }
                val up = withCh.count { (it.change24h ?: 0.0) > 0 }
                val down = withCh.count { (it.change24h ?: 0.0) < 0 }
                val best = withCh.maxByOrNull { it.change24h ?: -1e9 }
                val worst = withCh.minByOrNull { it.change24h ?: 1e9 }
                Panel {
                    SectionTitle("Market movement (Top 50, 24h)")
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Tag("$up rising", MarketColors.Up)
                        Tag("$down falling", MarketColors.Down)
                        Tag("${withCh.size - up - down} flat", MarketColors.Neutral)
                    }
                    if (best != null) KeyValue("Top gainer", "${best.symbol}  ${Fmt.pct(best.change24h)}", valueColor = MarketColors.Up)
                    if (worst != null) KeyValue("Top loser", "${worst.symbol}  ${Fmt.pct(worst.change24h)}", valueColor = MarketColors.Down)
                }
            }
            item {
                val chs = coins.mapNotNull { it.change24h }
                Panel {
                    SectionTitle("Market volatility overview")
                    if (chs.size >= 10) {
                        val avgAbs = chs.map { abs(it) }.average()
                        val big = chs.count { abs(it) >= 5.0 }
                        val level = when {
                            avgAbs < 1.5 -> "Calm"
                            avgAbs < 3.0 -> "Normal"
                            avgAbs < 5.0 -> "Active"
                            else -> "Turbulent"
                        }
                        KeyValue("Average absolute 24h move", Fmt.plainPct(avgAbs, 2))
                        KeyValue("Coins moving ≥ 5%", "$big of ${chs.size}")
                        KeyValue("Derived volatility level", level)
                        Muted("Derived from the 24h changes of the Top 50 coins.")
                    } else Muted("Not enough data.")
                }
            }
        }

        // ---- research distribution (latest report per coin)
        item {
            val latest = rows.groupBy { it.coinId }.map { (_, v) -> v.maxByOrNull { it.createdAt }!! }
            Panel {
                SectionTitle("Research distribution")
                if (latest.isEmpty()) {
                    Muted("No research reports yet. Open AI Research to analyse a coin.")
                } else {
                    val total = latest.size
                    for (c in listOf(Classification.BULLISH, Classification.BEARISH, Classification.NEUTRAL, Classification.INSUFFICIENT)) {
                        val n = latest.count { it.classification == c.name }
                        DistRow(c.label, n, total, classColor(c))
                    }
                    Muted("Based on the latest saved report for each of $total analysed coin(s).")
                }
            }
        }

        // ---- watchlist
        item {
            Panel {
                SectionTitle("Watchlist")
                if (watch.isEmpty()) Muted("Your watchlist is empty. Add coins from the Top 50 screen.")
            }
        }
        items(watch, key = { "w-" + it.coinId }) { w ->
            val c = coins.firstOrNull { it.id == w.coinId }
            Panel(Modifier.clickable { onOpenCoin(w.coinId) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (c != null) CoinLogo(c, 28.dp)
                    Column(Modifier.weight(1f).padding(start = 8.dp)) {
                        Text("${w.name} (${w.symbol})", fontWeight = FontWeight.SemiBold)
                        Muted(if (c != null) Fmt.price(c.price) else "Not in current Top 50")
                    }
                    if (c != null) ChangeText(c.change24h)
                }
            }
        }

        // ---- recently analysed + latest reports
        item {
            val recent = rows.distinctBy { it.coinId }.take(8)
            Panel {
                SectionTitle("Recently analysed")
                if (recent.isEmpty()) Muted("Nothing analysed yet.")
                else Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (r in recent.take(5)) {
                        Tag(r.symbol, classColor(Classification.fromName(r.classification)), Modifier.clickable { onOpenReport(r.id) })
                    }
                }
            }
        }
        item {
            Panel {
                SectionTitle("Latest research reports")
                if (rows.isEmpty()) Muted("No saved reports.")
            }
        }
        items(rows.take(5), key = { "r-" + it.id }) { r ->
            Panel(Modifier.clickable { onOpenReport(r.id) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("${r.name} (${r.symbol})", fontWeight = FontWeight.SemiBold)
                        Muted("${Fmt.dateTime(r.createdAt)} · confidence ${r.confidencePct}% · volatility ${r.volatility}")
                    }
                    ClassTag(Classification.fromName(r.classification))
                }
            }
        }

        // ---- top 50 preview
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                SectionTitle("Top coins by market cap")
                TextButton(onClick = { onNav("coins") }) { Text("View all 50") }
            }
        }
        items(coins.take(10), key = { "c-" + it.id }) { c ->
            CoinRow(c, onClick = { onOpenCoin(c.id) })
        }
        item { Disclaimer() }
    }
}

@Composable
private fun DistRow(label: String, n: Int, total: Int, color: Color) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1.1f))
        Box(Modifier.weight(1.6f).height(10.dp).clip(RoundedCornerShape(5.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
            val frac = if (total == 0) 0f else n.toFloat() / total
            if (frac > 0f) Box(Modifier.fillMaxWidth(frac).height(10.dp).clip(RoundedCornerShape(5.dp)).background(color))
        }
        Text("  $n", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
    }
}
