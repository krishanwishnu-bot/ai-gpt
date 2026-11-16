package com.cryptoresearch.ai.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cryptoresearch.ai.data.Coin
import com.cryptoresearch.ai.data.freshnessOf
import com.cryptoresearch.ai.ui.components.ChangeText
import com.cryptoresearch.ai.ui.components.ChoiceRow
import com.cryptoresearch.ai.ui.components.CoinLogo
import com.cryptoresearch.ai.ui.components.Disclaimer
import com.cryptoresearch.ai.ui.components.EmptyState
import com.cryptoresearch.ai.ui.components.ErrorBanner
import com.cryptoresearch.ai.ui.components.FreshChip
import com.cryptoresearch.ai.ui.components.KeyValue
import com.cryptoresearch.ai.ui.components.Muted
import com.cryptoresearch.ai.ui.components.Panel
import com.cryptoresearch.ai.ui.theme.MarketColors
import com.cryptoresearch.ai.ui.vm.AppViewModel
import com.cryptoresearch.ai.util.Fmt

@Composable
fun CoinRow(c: Coin, starred: Boolean = false, onClick: () -> Unit) {
    Panel(Modifier.clickable(onClick = onClick)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${c.rank}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(end = 8.dp))
            CoinLogo(c, 32.dp)
            Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(c.symbol, fontWeight = FontWeight.Bold)
                    if (starred) Icon(Icons.Filled.Star, contentDescription = "Watchlist", tint = MarketColors.Warn, modifier = Modifier.padding(start = 4.dp).size(14.dp))
                }
                Muted("${c.name} · MCap ${Fmt.compact(c.marketCap)} · Vol ${Fmt.compact(c.volume24h)}")
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(Fmt.price(c.price), fontWeight = FontWeight.SemiBold)
                ChangeText(c.change24h)
            }
        }
    }
}

@Composable
fun CoinsScreen(vm: AppViewModel, onAction: (String, String) -> Unit) {
    val coins by vm.coins.collectAsState()
    val watch by vm.watchlist.collectAsState()
    val status by vm.coinsStatus.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf("All") }
    var detailId by rememberSaveable { mutableStateOf<String?>(null) }

    val watchIds = watch.map { it.coinId }.toSet()
    val list = coins.filter { c ->
        (query.isBlank() || c.name.contains(query, true) || c.symbol.contains(query, true)) &&
            when (filter) {
                "Watchlist" -> c.id in watchIds
                "Gainers" -> (c.change24h ?: 0.0) > 0
                "Losers" -> (c.change24h ?: 0.0) < 0
                else -> true
            }
    }.let { l ->
        when (filter) {
            "Gainers" -> l.sortedByDescending { it.change24h ?: 0.0 }
            "Losers" -> l.sortedBy { it.change24h ?: 0.0 }
            else -> l
        }
    }

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = query, onValueChange = { query = it }, singleLine = true,
                    placeholder = { Text("Search name or symbol") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Filled.Clear, contentDescription = "Clear") } },
                    modifier = Modifier.weight(1f)
                )
                if (status.loading) CircularProgressIndicator(Modifier.padding(start = 8.dp).size(24.dp), strokeWidth = 2.dp)
                else IconButton(onClick = { vm.refreshCoins() }) { Icon(Icons.Filled.Refresh, contentDescription = "Refresh") }
            }
            ChoiceRow(listOf("All", "Watchlist", "Gainers", "Losers"), filter, { it }, { filter = it })
            val newest = coins.maxOfOrNull { it.fetchedAt }
            if (newest != null) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FreshChip(freshnessOf(newest, false, status.error != null))
                    Muted("Updated ${Fmt.dateTime(newest)} · ${coins.firstOrNull()?.source ?: ""}")
                }
            }
            status.error?.let { ErrorBanner(it + if (coins.isNotEmpty()) " Showing the last saved list (stale)." else "", { vm.refreshCoins() }) }
        }
        LazyColumn(
            Modifier.fillMaxWidth().weight(1f),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 12.dp, end = 12.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (list.isEmpty()) item { EmptyState(if (coins.isEmpty()) (if (status.loading) "Loading…" else "No data. Check your connection and refresh.") else "No coins match.") }
            items(list, key = { it.id }) { c -> CoinRow(c, c.id in watchIds) { detailId = c.id } }
            item { Disclaimer() }
        }
    }

    val d = coins.firstOrNull { it.id == detailId }
    if (d != null) {
        val inWatch = d.id in watchIds
        AlertDialog(
            onDismissRequest = { detailId = null },
            title = { Text("${d.name} (${d.symbol})") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    KeyValue("Rank", "#${d.rank}")
                    KeyValue("Price", Fmt.price(d.price))
                    KeyValue("24h change", Fmt.pct(d.change24h), valueColor = com.cryptoresearch.ai.ui.components.changeColor(d.change24h))
                    KeyValue("7d change", Fmt.pct(d.change7d), valueColor = com.cryptoresearch.ai.ui.components.changeColor(d.change7d))
                    KeyValue("Market cap", Fmt.compact(d.marketCap))
                    KeyValue("24h volume", Fmt.compact(d.volume24h))
                    KeyValue("24h high / low", "${Fmt.price(d.high24h)} / ${Fmt.price(d.low24h)}")
                    Muted("Source: ${d.source} · ${Fmt.dateTime(d.fetchedAt)}")
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(onClick = { detailId = null; onAction("research", d.id) }) { Text("Research") }
                        TextButton(onClick = { detailId = null; onAction("technical", d.id) }) { Text("Technical") }
                        TextButton(onClick = { detailId = null; onAction("news", d.id) }) { Text("News") }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { vm.toggleWatch(d) }) { Text(if (inWatch) "Remove from watchlist" else "Add to watchlist") } },
            dismissButton = { TextButton(onClick = { detailId = null }) { Text("Close") } }
        )
    }
}
