package com.cryptoresearch.ai.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cryptoresearch.ai.data.Classification
import com.cryptoresearch.ai.ui.components.ChoiceRow
import com.cryptoresearch.ai.ui.components.ClassTag
import com.cryptoresearch.ai.ui.components.Disclaimer
import com.cryptoresearch.ai.ui.components.EmptyState
import com.cryptoresearch.ai.ui.components.Muted
import com.cryptoresearch.ai.ui.components.Panel
import com.cryptoresearch.ai.ui.vm.AppViewModel
import com.cryptoresearch.ai.util.Fmt

@Composable
fun HistoryScreen(vm: AppViewModel, onOpen: (Long) -> Unit, onCompare: (Long, Long) -> Unit) {
    val rows by vm.reportRows.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }
    var coinFilter by rememberSaveable { mutableStateOf("All coins") }
    var dateFilter by rememberSaveable { mutableStateOf("All time") }
    var menuOpen by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(listOf<Long>()) }
    var deleteId by rememberSaveable { mutableStateOf<Long?>(null) }
    var confirmAll by rememberSaveable { mutableStateOf(false) }

    val coinNames = listOf("All coins") + rows.map { it.symbol }.distinct().sorted()
    val now = System.currentTimeMillis()
    val cutoff = when (dateFilter) {
        "7 days" -> now - 7 * 86_400_000L
        "30 days" -> now - 30 * 86_400_000L
        "90 days" -> now - 90 * 86_400_000L
        else -> 0L
    }
    val shown = rows.filter { r ->
        (coinFilter == "All coins" || r.symbol == coinFilter) &&
            r.createdAt >= cutoff &&
            (query.isBlank() || r.name.contains(query, true) || r.symbol.contains(query, true) || Classification.fromName(r.classification).label.contains(query, true))
    }

    LazyColumn(
        Modifier.fillMaxWidth(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Panel {
                Text("Research History", style = androidx.compose.material3.MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                OutlinedTextField(
                    value = query, onValueChange = { query = it }, singleLine = true, placeholder = { Text("Search reports") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Filled.Clear, contentDescription = "Clear") } },
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.foundation.layout.Box(Modifier.weight(1f)) {
                        OutlinedButton(onClick = { menuOpen = true }, modifier = Modifier.fillMaxWidth()) { Text("Coin: $coinFilter") }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            for (n in coinNames) DropdownMenuItem(text = { Text(n) }, onClick = { coinFilter = n; menuOpen = false })
                        }
                    }
                }
                ChoiceRow(listOf("All time", "7 days", "30 days", "90 days"), dateFilter, { it }, { dateFilter = it })
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { if (selected.size == 2) onCompare(selected[0], selected[1]) },
                        enabled = selected.size == 2, modifier = Modifier.weight(1f)
                    ) { Text(if (selected.size == 2) "Compare selected" else "Select 2 to compare (${selected.size}/2)") }
                    OutlinedButton(onClick = { confirmAll = true }, enabled = rows.isNotEmpty()) { Text("Delete all") }
                }
            }
        }
        if (shown.isEmpty()) item { EmptyState(if (rows.isEmpty()) "No saved reports yet. Run an analysis in AI Research." else "No reports match your filters.") }
        items(shown, key = { it.id }) { r ->
            Panel(Modifier.clickable { onOpen(r.id) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = r.id in selected,
                        onCheckedChange = { on ->
                            selected = if (on) (if (selected.size >= 2) selected.drop(1) + r.id else selected + r.id) else selected - r.id
                        }
                    )
                    Column(Modifier.weight(1f)) {
                        Text("${r.name} (${r.symbol})", fontWeight = FontWeight.SemiBold)
                        Muted("${Fmt.dateTime(r.createdAt)} · confidence ${r.confidencePct}% · ${r.volatility} volatility")
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        ClassTag(Classification.fromName(r.classification))
                        IconButton(onClick = { deleteId = r.id }) { Icon(Icons.Filled.Delete, contentDescription = "Delete") }
                    }
                }
            }
        }
        item { Disclaimer() }
    }

    if (deleteId != null) {
        AlertDialog(
            onDismissRequest = { deleteId = null },
            title = { Text("Delete this report?") },
            text = { Text("The saved report will be permanently removed from this device.") },
            confirmButton = { TextButton(onClick = { deleteId?.let { id -> vm.deleteReport(id); selected = selected - id }; deleteId = null }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { deleteId = null }) { Text("Cancel") } }
        )
    }
    if (confirmAll) {
        AlertDialog(
            onDismissRequest = { confirmAll = false },
            title = { Text("Delete all reports?") },
            text = { Text("All ${rows.size} saved reports will be permanently removed.") },
            confirmButton = { TextButton(onClick = { vm.deleteAllReports(); selected = emptyList(); confirmAll = false }) { Text("Delete all") } },
            dismissButton = { TextButton(onClick = { confirmAll = false }) { Text("Cancel") } }
        )
    }
}
