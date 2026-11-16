package com.cryptoresearch.ai

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.cryptoresearch.ai.ui.screens.BacktestScreen
import com.cryptoresearch.ai.ui.screens.CoinsScreen
import com.cryptoresearch.ai.ui.screens.DashboardScreen
import com.cryptoresearch.ai.ui.screens.HistoryScreen
import com.cryptoresearch.ai.ui.screens.MonitorScreen
import com.cryptoresearch.ai.ui.screens.NewsScreen
import com.cryptoresearch.ai.ui.screens.PortfolioScreen
import com.cryptoresearch.ai.ui.screens.ReportDetailScreen
import com.cryptoresearch.ai.ui.screens.CompareScreen
import com.cryptoresearch.ai.ui.screens.ResearchScreen
import com.cryptoresearch.ai.ui.screens.SettingsScreen
import com.cryptoresearch.ai.ui.screens.TechnicalScreen
import com.cryptoresearch.ai.ui.theme.CryptoTheme
import com.cryptoresearch.ai.ui.vm.AppViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private data class Dest(val route: String, val title: String)

private val DESTS = listOf(
    Dest("dashboard", "Dashboard"),
    Dest("coins", "Top 50 Coins"),
    Dest("research", "AI Research"),
    Dest("technical", "Technical Analysis"),
    Dest("news", "News & Sentiment"),
    Dest("backtest", "Backtesting"),
    Dest("portfolio", "Virtual Portfolio"),
    Dest("monitor", "Agent Monitor"),
    Dest("history", "Research History"),
    Dest("settings", "Settings")
)

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Foreground-only auto refresh; stops automatically when the app is not visible.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) {
                    delay(60_000L)
                    vm.autoRefreshTick()
                }
            }
        }
        setContent {
            val s by vm.settings.collectAsState()
            CryptoTheme(s.theme) { AppRoot(vm) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppRoot(vm: AppViewModel) {
    val nav = rememberNavController()
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val snack = remember { SnackbarHostState() }
    val msg by vm.message.collectAsState()
    val backEntry by nav.currentBackStackEntryAsState()
    val route = backEntry?.destination?.route ?: "dashboard"

    LaunchedEffect(msg) {
        val m = msg
        if (m != null) {
            snack.showSnackbar(m)
            vm.clearMessage()
        }
    }

    fun go(r: String) {
        nav.navigate(r) {
            popUpTo("dashboard") { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    val isTop = DESTS.any { it.route == route }
    val title = when {
        route.startsWith("report/") -> "Research Report"
        route.startsWith("compare/") -> "Compare Reports"
        else -> DESTS.firstOrNull { it.route == route }?.title ?: "Crypto Research AI"
    }

    ModalNavigationDrawer(
        drawerState = drawer,
        gesturesEnabled = isTop,
        drawerContent = {
            ModalDrawerSheet(Modifier.fillMaxWidth(0.82f)) {
                Column(Modifier.verticalScroll(rememberScrollState()).statusBarsPadding().padding(vertical = 8.dp)) {
                    Text("Crypto Research AI", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp))
                    DESTS.forEachIndexed { i, d ->
                        NavigationDrawerItem(
                            label = { Text("${i + 1}. ${d.title}") },
                            selected = route == d.route,
                            onClick = {
                                scope.launch { drawer.close() }
                                go(d.route)
                            },
                            modifier = Modifier.padding(horizontal = 10.dp)
                        )
                    }
                    Text(
                        "Educational research only — not financial advice.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(20.dp)
                    )
                }
            }
        }
    ) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            snackbarHost = { SnackbarHost(snack) },
            topBar = {
                TopAppBar(
                    title = { Text(title, fontWeight = FontWeight.SemiBold) },
                    navigationIcon = {
                        if (isTop) {
                            IconButton(onClick = { scope.launch { drawer.open() } }) { Icon(Icons.Filled.Menu, contentDescription = "Menu") }
                        } else {
                            IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back") }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface,
                        navigationIconContentColor = MaterialTheme.colorScheme.onSurface
                    )
                )
            }
        ) { pad ->
            Column(Modifier.fillMaxSize().padding(pad)) {
                NavHost(nav, startDestination = "dashboard", modifier = Modifier.fillMaxSize()) {
                    composable("dashboard") {
                        DashboardScreen(
                            vm,
                            onOpenCoin = { id -> vm.select(id); go("research") },
                            onNav = { go(it) },
                            onOpenReport = { id -> nav.navigate("report/$id") }
                        )
                    }
                    composable("coins") {
                        CoinsScreen(vm) { target, id -> vm.select(id); go(target) }
                    }
                    composable("research") { ResearchScreen(vm) }
                    composable("technical") { TechnicalScreen(vm) }
                    composable("news") { NewsScreen(vm) }
                    composable("backtest") { BacktestScreen(vm) }
                    composable("portfolio") { PortfolioScreen(vm) }
                    composable("monitor") { MonitorScreen(vm) }
                    composable("history") {
                        HistoryScreen(
                            vm,
                            onOpen = { id -> nav.navigate("report/$id") },
                            onCompare = { a, b -> nav.navigate("compare/$a/$b") }
                        )
                    }
                    composable("settings") { SettingsScreen(vm) }
                    composable("report/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { e ->
                        ReportDetailScreen(vm, e.arguments?.getLong("id") ?: -1L)
                    }
                    composable(
                        "compare/{a}/{b}",
                        arguments = listOf(navArgument("a") { type = NavType.LongType }, navArgument("b") { type = NavType.LongType })
                    ) { e ->
                        CompareScreen(vm, e.arguments?.getLong("a") ?: -1L, e.arguments?.getLong("b") ?: -1L)
                    }
                }
            }
        }
    }
}
