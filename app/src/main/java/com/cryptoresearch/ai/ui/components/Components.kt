package com.cryptoresearch.ai.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.cryptoresearch.ai.data.Classification
import com.cryptoresearch.ai.data.Coin
import com.cryptoresearch.ai.data.FreshLevel
import com.cryptoresearch.ai.data.Freshness
import com.cryptoresearch.ai.ui.theme.MarketColors
import com.cryptoresearch.ai.util.Fmt

@Composable
fun Panel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp), content = content)
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier
    )
}

@Composable
fun Muted(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = modifier)
}

@Composable
fun changeColor(v: Double?): Color = when {
    v == null -> MarketColors.Neutral
    v > 0 -> MarketColors.Up
    v < 0 -> MarketColors.Down
    else -> MarketColors.Neutral
}

@Composable
fun ChangeText(v: Double?, modifier: Modifier = Modifier, bold: Boolean = true) {
    Text(
        Fmt.pct(v),
        color = changeColor(v),
        fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal,
        style = MaterialTheme.typography.bodyMedium,
        modifier = modifier
    )
}

@Composable
fun FreshChip(f: Freshness, modifier: Modifier = Modifier) {
    val col = when (f.level) {
        FreshLevel.LIVE -> MarketColors.Up
        FreshLevel.CACHED -> MarketColors.Accent
        FreshLevel.STALE -> MarketColors.Warn
    }
    Row(modifier = modifier.clip(RoundedCornerShape(50)).background(col.copy(alpha = 0.15f)).padding(horizontal = 8.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(col))
        Text(" " + f.text, color = col, fontSize = 11.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun Tag(text: String, color: Color, modifier: Modifier = Modifier) {
    Text(
        text,
        color = color,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        modifier = modifier.clip(RoundedCornerShape(6.dp)).background(color.copy(alpha = 0.15f)).padding(horizontal = 7.dp, vertical = 3.dp)
    )
}

fun classColor(c: Classification): Color = when (c) {
    Classification.BULLISH -> MarketColors.Up
    Classification.BEARISH -> MarketColors.Down
    Classification.NEUTRAL -> MarketColors.Warn
    Classification.INSUFFICIENT -> MarketColors.Neutral
}

@Composable
fun ClassTag(c: Classification, modifier: Modifier = Modifier) = Tag(c.label, classColor(c), modifier)

@Composable
fun CoinLogo(coin: Coin, size: Dp = 32.dp) {
    if (coin.image != null) {
        AsyncImage(model = coin.image, contentDescription = coin.name, modifier = Modifier.size(size).clip(CircleShape))
    } else {
        LetterLogo(coin.symbol, size)
    }
}

@Composable
fun LetterLogo(symbol: String, size: Dp = 32.dp) {
    Box(
        Modifier.size(size).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        Text(symbol.take(1), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
fun ErrorBanner(message: String, onRetry: (() -> Unit)? = null, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(MarketColors.Down.copy(alpha = 0.14f)).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(message, color = MaterialTheme.colorScheme.onBackground, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
        if (onRetry != null) TextButton(onClick = onRetry) { Text("Retry") }
    }
}

@Composable
fun WarnBanner(message: String, modifier: Modifier = Modifier) {
    Text(
        message,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(MarketColors.Warn.copy(alpha = 0.16f)).padding(10.dp)
    )
}

@Composable
fun Disclaimer(modifier: Modifier = Modifier) {
    Text(
        "Educational research only. Not financial advice, not a buy/sell signal and not a prediction. Past behaviour does not guarantee future results.",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.fillMaxWidth().padding(vertical = 6.dp)
    )
}

@Composable
fun KeyValue(label: String, value: String, modifier: Modifier = Modifier, valueColor: Color = Color.Unspecified) {
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = valueColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun EmptyState(text: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
    }
}

/** Compact dropdown to pick a coin from the loaded Top-50 list. */
@Composable
fun CoinPicker(coins: List<Coin>, selected: Coin?, onSelect: (Coin) -> Unit, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedButton(onClick = { open = true }, enabled = coins.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
            if (selected != null) {
                CoinLogo(selected, 20.dp)
                Text("  ${selected.name} (${selected.symbol})", maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            } else {
                Text(if (coins.isEmpty()) "No coins loaded" else "Select a coin", modifier = Modifier.weight(1f))
            }
            Text("▾")
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            for (c in coins) {
                DropdownMenuItem(
                    text = { Text("#${c.rank}  ${c.name} (${c.symbol})") },
                    onClick = {
                        open = false
                        onSelect(c)
                    }
                )
            }
        }
    }
}

@Composable
fun <T> ChoiceRow(options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        for (o in options) {
            val sel = o == selected
            if (sel) {
                Button(onClick = { onSelect(o) }, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 4.dp), modifier = Modifier.weight(1f)) {
                    Text(label(o), fontSize = 12.sp, maxLines = 1)
                }
            } else {
                OutlinedButton(onClick = { onSelect(o) }, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 4.dp), modifier = Modifier.weight(1f)) {
                    Text(label(o), fontSize = 12.sp, maxLines = 1)
                }
            }
        }
    }
}
