package com.cryptoresearch.ai.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cryptoresearch.ai.data.Sentiment
import com.cryptoresearch.ai.data.freshnessOf
import com.cryptoresearch.ai.ui.components.ChoiceRow
import com.cryptoresearch.ai.ui.components.CoinPicker
import com.cryptoresearch.ai.ui.components.Disclaimer
import com.cryptoresearch.ai.ui.components.EmptyState
import com.cryptoresearch.ai.ui.components.ErrorBanner
import com.cryptoresearch.ai.ui.components.FreshChip
import com.cryptoresearch.ai.ui.components.Muted
import com.cryptoresearch.ai.ui.components.Panel
import com.cryptoresearch.ai.ui.components.SectionTitle
import com.cryptoresearch.ai.ui.components.Tag
import com.cryptoresearch.ai.ui.components.WarnBanner
import com.cryptoresearch.ai.ui.theme.MarketColors
import com.cryptoresearch.ai.ui.vm.AppViewModel
import com.cryptoresearch.ai.util.Fmt

@Composable
fun NewsScreen(vm: AppViewModel) {
    val coins by vm.coins.collectAsState()
    val selId by vm.selectedCoinId.collectAsState()
    val ui by vm.news.collectAsState()
    val uri = LocalUriHandler.current
    var scope by rememberSaveable { mutableStateOf("Market") }
    var sentFilter by rememberSaveable { mutableStateOf("All") }
    var category by rememberSaveable { mutableStateOf("All") }
    val selected = coins.firstOrNull { it.id == selId } ?: coins.firstOrNull()
    val symbol: String? = if (scope == "Coin") selected?.symbol else null

    LaunchedEffect(symbol, scope) {
        if (scope == "Market" || selected != null) vm.loadNews(symbol)
        category = "All"
    }

    val cats = ui.items.flatMap { it.categories }.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.take(6).map { it.key }
    val shown = ui.items.filter { n ->
        (sentFilter == "All" || n.sentiment.label == sentFilter) && (category == "All" || category in n.categories)
    }

    LazyColumn(
        Modifier.fillMaxWidth(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Panel {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    SectionTitle("News & Sentiment")
                    if (ui.loading) CircularProgressIndicator(Modifier.padding(4.dp), strokeWidth = 2.dp)
                    else IconButton(onClick = { vm.loadNews(symbol, true) }) { Icon(Icons.Filled.Refresh, contentDescription = "Refresh") }
                }
                ChoiceRow(listOf("Market", "Coin"), scope, { if (it == "Coin") "Selected coin" else "All market" }, { scope = it })
                if (scope == "Coin") CoinPicker(coins, selected, { vm.select(it.id) })
                ChoiceRow(listOf("All", "Bullish", "Bearish", "Neutral"), sentFilter, { it }, { sentFilter = it })
                if (cats.isNotEmpty()) {
                    Muted("Categories")
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        ChoiceRow(listOf("All") + cats.take(3), category, { it }, { category = it })
                    }
                    if (cats.size > 3) ChoiceRow(cats.drop(3), category, { it }, { category = it })
                }
                if (ui.fetchedAt > 0) FreshChip(freshnessOf(ui.fetchedAt, ui.fromCache, ui.stale, 30 * 60_000L))
                Muted("Source: CryptoCompare news API. Sentiment is a keyword heuristic on headlines, not a trained model.")
            }
        }
        ui.error?.let { e -> item { ErrorBanner(e, { vm.loadNews(symbol, true) }) } }
        ui.note?.let { n -> item { WarnBanner("Refresh failed, showing cached articles. $n") } }
        if (ui.items.isNotEmpty()) {
            item {
                val b = ui.items.count { it.sentiment == Sentiment.BULLISH }
                val r = ui.items.count { it.sentiment == Sentiment.BEARISH }
                Panel {
                    SectionTitle("Sentiment of ${ui.items.size} articles")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Tag("$b bullish", MarketColors.Up)
                        Tag("$r bearish", MarketColors.Down)
                        Tag("${ui.items.size - b - r} neutral", MarketColors.Neutral)
                    }
                }
            }
        }
        if (shown.isEmpty() && !ui.loading && ui.error == null) item { EmptyState("No articles match.") }
        items(shown, key = { it.id + it.url }) { n ->
            Panel {
                Text(n.title, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                if (n.summary.isNotBlank()) Text(n.summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Tag(
                        n.sentiment.label,
                        when (n.sentiment) {
                            Sentiment.BULLISH -> MarketColors.Up
                            Sentiment.BEARISH -> MarketColors.Down
                            Sentiment.NEUTRAL -> MarketColors.Neutral
                        }
                    )
                    Muted("${n.source} · ${Fmt.shortDateTime(n.publishedAt)}")
                }
                if (n.categories.isNotEmpty()) Muted(n.categories.take(5).joinToString(" · "))
                TextButton(onClick = { try { uri.openUri(n.url) } catch (e: Exception) { } }) { Text("Open original article") }
            }
        }
        item { Disclaimer() }
    }
}
