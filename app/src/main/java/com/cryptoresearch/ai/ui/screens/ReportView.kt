package com.cryptoresearch.ai.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cryptoresearch.ai.data.ResearchReport
import com.cryptoresearch.ai.data.Strength
import com.cryptoresearch.ai.ui.components.ClassTag
import com.cryptoresearch.ai.ui.components.Disclaimer
import com.cryptoresearch.ai.ui.components.KeyValue
import com.cryptoresearch.ai.ui.components.Muted
import com.cryptoresearch.ai.ui.components.Panel
import com.cryptoresearch.ai.ui.components.Tag
import com.cryptoresearch.ai.ui.components.WarnBanner
import com.cryptoresearch.ai.ui.theme.MarketColors
import com.cryptoresearch.ai.util.Fmt

/** Renders a full 12-section research report as lazy-list items. */
fun LazyListScope.reportItems(r: ResearchReport) {
    item(key = "rep-head") {
        Panel {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("${r.name} (${r.symbol})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                ClassTag(r.classification)
            }
            KeyValue("Evidence strength", if (r.strength == Strength.NONE) "n/a" else r.strength.label)
            KeyValue("Bullish / bearish score", "${r.bullScore} / ${r.bearScore}")
            KeyValue("Data confidence", "${r.confidencePct}% (${r.confidenceLabel})")
            KeyValue("Volatility level", r.volatility)
            KeyValue("Analysis timestamp", Fmt.dateTime(r.createdAt))
            Muted("Data freshness: ${r.freshness}")
            Muted("Sources: " + (if (r.sources.isEmpty()) "none" else r.sources.joinToString("; ")))
            Tag("Informational research finding — not a trading instruction", MarketColors.Warn)
        }
    }
    r.sections.forEachIndexed { idx, sec ->
        item(key = "rep-sec-$idx") {
            var open by remember { mutableStateOf(idx == 11 || idx == 0) }
            Panel(Modifier.clickable { open = !open }) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(sec.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(if (open) "▲" else "▼", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (open) {
                    for (line in sec.lines) {
                        Text(line, style = MaterialTheme.typography.bodySmall)
                    }
                    if (sec.lines.isEmpty()) Muted("No data.")
                }
            }
        }
    }
    item(key = "rep-disc") { Disclaimer() }
}

fun LazyListScope.compareItems(a: ResearchReport, b: ResearchReport) {
    item(key = "cmp-head") {
        Panel {
            Text("Comparison", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            if (a.coinId != b.coinId) WarnBanner("These reports are for different coins (${a.symbol} vs ${b.symbol}).")
        }
    }
    item(key = "cmp-table") {
        Panel {
            CompareRow("", "A: ${a.symbol} · ${Fmt.shortDateTime(a.createdAt)}", "B: ${b.symbol} · ${Fmt.shortDateTime(b.createdAt)}", header = true)
            CompareRow("Classification", a.classification.label, b.classification.label)
            CompareRow("Strength", a.strength.label, b.strength.label)
            CompareRow("Bull score", a.bullScore.toString(), b.bullScore.toString())
            CompareRow("Bear score", a.bearScore.toString(), b.bearScore.toString())
            CompareRow("Confidence", "${a.confidencePct}%", "${b.confidencePct}%")
            CompareRow("Volatility", a.volatility, b.volatility)
            val da = a.confidencePct - b.confidencePct
            Muted("Time between reports: " + Fmt.ago(minOf(a.createdAt, b.createdAt), maxOf(a.createdAt, b.createdAt)).replace(" ago", ""))
            if (da != 0) Muted("Confidence changed by ${-da} points from A to B.")
        }
    }
    item(key = "cmp-sum") {
        Panel {
            Text("Final summaries", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text("A", fontWeight = FontWeight.Bold)
            a.sections.lastOrNull()?.lines?.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
            Text("B", fontWeight = FontWeight.Bold)
            b.sections.lastOrNull()?.lines?.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
    }
    item(key = "cmp-disc") { Disclaimer() }
}

@androidx.compose.runtime.Composable
private fun CompareRow(label: String, a: String, b: String, header: Boolean = false) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(0.8f))
        Text(a, style = MaterialTheme.typography.bodySmall, fontWeight = if (header) FontWeight.Bold else FontWeight.Medium, modifier = Modifier.weight(1f))
        Text(b, style = MaterialTheme.typography.bodySmall, fontWeight = if (header) FontWeight.Bold else FontWeight.Medium, modifier = Modifier.weight(1f))
    }
}
