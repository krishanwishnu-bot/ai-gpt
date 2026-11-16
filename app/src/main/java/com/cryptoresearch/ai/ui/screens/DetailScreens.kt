package com.cryptoresearch.ai.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cryptoresearch.ai.ui.components.ErrorBanner
import com.cryptoresearch.ai.ui.vm.AppViewModel

@Composable
fun ReportDetailScreen(vm: AppViewModel, id: Long) {
    val rep by vm.openReport.collectAsState()
    LaunchedEffect(id) { vm.openReport(id) }
    LazyColumn(
        Modifier.fillMaxWidth(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        val r = rep
        if (r == null) {
            item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) { CircularProgressIndicator() } }
        } else {
            reportItems(r)
        }
    }
}

@Composable
fun CompareScreen(vm: AppViewModel, idA: Long, idB: Long) {
    val ui by vm.compare.collectAsState()
    LaunchedEffect(idA, idB) { vm.loadCompare(idA, idB) }
    LazyColumn(
        Modifier.fillMaxWidth(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        val a = ui.a
        val b = ui.b
        ui.error?.let { e -> item { ErrorBanner(e) } }
        if (a != null && b != null) {
            compareItems(a, b)
        } else if (ui.error == null) {
            item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) { CircularProgressIndicator() } }
        }
    }
}
