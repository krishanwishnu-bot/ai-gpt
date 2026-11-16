package com.cryptoresearch.ai.ui.vm

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.cryptoresearch.ai.CryptoApp
import com.cryptoresearch.ai.analysis.BacktestParams
import com.cryptoresearch.ai.analysis.BacktestResult
import com.cryptoresearch.ai.analysis.Backtester
import com.cryptoresearch.ai.analysis.TechnicalAnalyzer
import com.cryptoresearch.ai.analysis.TfSnapshot
import com.cryptoresearch.ai.data.AppSettings
import com.cryptoresearch.ai.data.Candle
import com.cryptoresearch.ai.data.Coin
import com.cryptoresearch.ai.data.FearGreed
import com.cryptoresearch.ai.data.Fetched
import com.cryptoresearch.ai.data.GlobalStats
import com.cryptoresearch.ai.data.NewsItem
import com.cryptoresearch.ai.data.ResearchReport
import com.cryptoresearch.ai.data.Timeframe
import com.cryptoresearch.ai.data.db.PortfolioSnapshot
import com.cryptoresearch.ai.data.db.Position
import com.cryptoresearch.ai.data.db.ReportRow
import com.cryptoresearch.ai.data.db.VirtualAccount
import com.cryptoresearch.ai.data.db.VirtualTx
import com.cryptoresearch.ai.data.db.WatchEntry
import com.cryptoresearch.ai.notify.Notifier
import com.cryptoresearch.ai.notify.Scheduler
import com.cryptoresearch.ai.util.Fmt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class Status(val loading: Boolean = false, val error: String? = null, val source: String? = null)

data class ResearchUi(
    val running: Boolean = false,
    val coinSymbol: String? = null,
    val report: ResearchReport? = null,
    val error: String? = null
)

data class TechUi(
    val loading: Boolean = false,
    val coinId: String? = null,
    val tf: Timeframe = Timeframe.D1,
    val snap: TfSnapshot? = null,
    val candles: List<Candle> = emptyList(),
    val error: String? = null,
    val fetchedAt: Long = 0L,
    val fromCache: Boolean = false,
    val stale: Boolean = false,
    val note: String? = null
)

data class NewsUi(
    val loading: Boolean = false,
    val symbol: String? = null,
    val items: List<NewsItem> = emptyList(),
    val error: String? = null,
    val fetchedAt: Long = 0L,
    val fromCache: Boolean = false,
    val stale: Boolean = false,
    val note: String? = null
)

data class BtUi(
    val loading: Boolean = false,
    val result: BacktestResult? = null,
    val error: String? = null,
    val note: String? = null,
    val coinSymbol: String? = null,
    val source: String? = null
)

data class CompareUi(val a: ResearchReport? = null, val b: ResearchReport? = null, val error: String? = null)

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val c = app as CryptoApp
    private val market = c.market
    private val reportsRepo = c.reports
    private val portfolioRepo = c.portfolio
    private val orchestrator = c.orchestrator

    val settingsStore = c.settings
    val settings: StateFlow<AppSettings> = c.settings.state
    val agentStates = c.monitor.states

    val coins: StateFlow<List<Coin>> = market.coins.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val watchlist: StateFlow<List<WatchEntry>> = market.watchlist.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val reportRows: StateFlow<List<ReportRow>> = reportsRepo.rows.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val positions: StateFlow<List<Position>> = portfolioRepo.positions.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val transactions: StateFlow<List<VirtualTx>> = portfolioRepo.transactions.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val snapshots: StateFlow<List<PortfolioSnapshot>> = portfolioRepo.snapshots.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val account: StateFlow<VirtualAccount?> = portfolioRepo.account.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val coinsStatus = MutableStateFlow(Status())
    val global = MutableStateFlow<Fetched<GlobalStats>?>(null)
    val fearGreed = MutableStateFlow<Fetched<FearGreed>?>(null)
    val extrasError = MutableStateFlow<String?>(null)

    val selectedCoinId = MutableStateFlow<String?>(null)
    val research = MutableStateFlow(ResearchUi())
    val tech = MutableStateFlow(TechUi())
    val news = MutableStateFlow(NewsUi())
    val backtest = MutableStateFlow(BtUi())
    val openReport = MutableStateFlow<ResearchReport?>(null)
    val compare = MutableStateFlow(CompareUi())
    val message = MutableStateFlow<String?>(null)

    private var researchJob: Job? = null
    private var techJob: Job? = null
    private var newsJob: Job? = null
    private var btJob: Job? = null

    init {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                portfolioRepo.ensureAccount()
            } catch (e: Exception) {
                message.value = "Database error: " + (e.message ?: "unknown")
            }
            val list = market.allCoins()
            val refreshMs = settings.value.refreshMinutes * 60_000L
            val stale = list.isEmpty() || (refreshMs > 0 && System.currentTimeMillis() - list.maxOf { it.fetchedAt } > refreshMs)
            if (stale) refreshCoins() else loadExtras(false)
        }
    }

    fun clearMessage() {
        message.value = null
    }

    /** Called periodically by the activity while it is visible. */
    fun autoRefreshTick() {
        val ms = settings.value.refreshMinutes * 60_000L
        if (ms <= 0 || coinsStatus.value.loading) return
        val newest = coins.value.maxOfOrNull { it.fetchedAt } ?: 0L
        if (System.currentTimeMillis() - newest > ms) refreshCoins()
    }

    fun refreshCoins() {
        if (coinsStatus.value.loading) return
        coinsStatus.value = coinsStatus.value.copy(loading = true, error = null)
        viewModelScope.launch(Dispatchers.IO) {
            val r = market.refreshCoins()
            if (r.isSuccess) {
                coinsStatus.value = Status(false, null, r.getOrNull())
                try {
                    val list = market.allCoins()
                    portfolioRepo.updatePrices(list)
                    val acc = portfolioRepo.account.first()
                    val pos = portfolioRepo.positions.first()
                    if (acc != null) portfolioRepo.recordSnapshot(acc.cash + pos.sumOf { p -> p.quantity * p.lastPrice })
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // portfolio refresh is best-effort
                }
            } else {
                val msg = Fmt.friendlyError(r.exceptionOrNull())
                coinsStatus.value = Status(false, msg, null)
                if (settings.value.notifyErrors) {
                    Notifier.show(getApplication(), Notifier.CH_ERRORS, 3002, "Market data update failed", msg)
                }
            }
            loadExtras(true)
        }
    }

    private suspend fun loadExtras(force: Boolean) {
        val errs = ArrayList<String>()
        val g = market.global(force)
        if (g.isSuccess) global.value = g.getOrNull() else errs.add("Global stats: " + Fmt.friendlyError(g.exceptionOrNull()))
        val f = market.fearGreed(force)
        if (f.isSuccess) fearGreed.value = f.getOrNull() else errs.add("Fear & Greed: " + Fmt.friendlyError(f.exceptionOrNull()))
        extrasError.value = if (errs.isEmpty()) null else errs.joinToString(" · ")
    }

    fun toggleWatch(coin: Coin) {
        viewModelScope.launch(Dispatchers.IO) { market.toggleWatch(coin) }
    }

    fun select(coinId: String) {
        selectedCoinId.value = coinId
    }

    fun coinOf(id: String?): Coin? = if (id == null) null else coins.value.firstOrNull { it.id == id }

    // ------------------------------------------------------------------ research
    fun startResearch(coin: Coin) {
        if (researchJob?.isActive == true) return
        research.value = ResearchUi(running = true, coinSymbol = coin.symbol)
        researchJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val r = orchestrator.research(coin)
                if (r.isSuccess) {
                    val rep = r.getOrNull()
                    research.value = ResearchUi(false, coin.symbol, rep, null)
                    if (rep != null && settings.value.notifyReports) {
                        Notifier.show(
                            getApplication(), Notifier.CH_REPORTS, 2000 + coin.rank,
                            "Research report ready: ${coin.symbol}",
                            "${rep.classification.label} · confidence ${rep.confidencePct}%. Educational research only."
                        )
                    }
                } else {
                    val msg = r.exceptionOrNull()?.message ?: "Analysis failed."
                    research.value = ResearchUi(false, coin.symbol, null, msg)
                    if (settings.value.notifyErrors) Notifier.show(getApplication(), Notifier.CH_ERRORS, 3003, "Analysis failed: ${coin.symbol}", msg)
                }
            } catch (e: CancellationException) {
                research.value = ResearchUi(false, coin.symbol, null, "Analysis cancelled.")
                throw e
            } catch (e: Exception) {
                research.value = ResearchUi(false, coin.symbol, null, Fmt.friendlyError(e))
            }
        }
    }

    fun cancelResearch() {
        researchJob?.cancel()
    }

    // ------------------------------------------------------------------ technical
    fun loadTech(coin: Coin, tf: Timeframe, force: Boolean = false) {
        techJob?.cancel()
        tech.value = TechUi(loading = true, coinId = coin.id, tf = tf)
        techJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val r = market.candles(coin, tf, 500, force)
                if (r.isSuccess) {
                    val f = r.getOrNull()!!
                    val snap = TechnicalAnalyzer.analyze(f, tf, settings.value.includeApproxConcepts)
                    tech.value = TechUi(false, coin.id, tf, snap, f.data.candles, null, f.fetchedAt, f.fromCache, f.stale, f.note)
                } else {
                    tech.value = TechUi(false, coin.id, tf, null, emptyList(), Fmt.friendlyError(r.exceptionOrNull()))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                tech.value = TechUi(false, coin.id, tf, null, emptyList(), Fmt.friendlyError(e))
            }
        }
    }

    // ------------------------------------------------------------------ news
    fun loadNews(symbol: String?, force: Boolean = false) {
        newsJob?.cancel()
        news.value = NewsUi(loading = true, symbol = symbol, items = if (news.value.symbol == symbol) news.value.items else emptyList())
        newsJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val r = market.news(symbol, force)
                if (r.isSuccess) {
                    val f = r.getOrNull()!!
                    news.value = NewsUi(false, symbol, f.data, null, f.fetchedAt, f.fromCache, f.stale, f.note)
                } else {
                    news.value = NewsUi(false, symbol, emptyList(), Fmt.friendlyError(r.exceptionOrNull()))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                news.value = NewsUi(false, symbol, emptyList(), Fmt.friendlyError(e))
            }
        }
    }

    // ------------------------------------------------------------------ backtest
    fun runBacktest(coin: Coin, tf: Timeframe, days: Int, horizon: Int, feePct: Double, slipPct: Double) {
        btJob?.cancel()
        backtest.value = BtUi(loading = true, coinSymbol = coin.symbol)
        btJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val need = (Backtester.WARMUP + horizon + days * tf.candlesPerDay + 5).coerceAtMost(6000)
                val r = market.candles(coin, tf, need)
                if (r.isFailure) {
                    backtest.value = BtUi(false, null, Fmt.friendlyError(r.exceptionOrNull()), null, coin.symbol)
                    return@launch
                }
                val f = r.getOrNull()!!
                val res = Backtester.run(f.data.candles, BacktestParams(tf, days, horizon, feePct, slipPct))
                if (res == null) {
                    backtest.value = BtUi(
                        false, null,
                        "Not enough history: ${f.data.candles.size} candles available from ${f.data.source}; at least ${Backtester.WARMUP + horizon + 20} are needed (200 for indicator warm-up).",
                        null, coin.symbol, f.data.source
                    )
                } else {
                    var note: String? = null
                    if (f.data.approximate) note = "Candles are approximated from a price series (fallback source); results are less reliable."
                    if (f.stale) note = (note ?: "") + " Cached data used because refresh failed."
                    backtest.value = BtUi(false, res, null, note, coin.symbol, f.data.source)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                backtest.value = BtUi(false, null, Fmt.friendlyError(e), null, coin.symbol)
            }
        }
    }

    fun cancelBacktest() {
        btJob?.cancel()
        backtest.value = backtest.value.copy(loading = false, error = "Backtest cancelled.")
    }

    // ------------------------------------------------------------------ history
    fun openReport(id: Long) {
        openReport.value = null
        viewModelScope.launch(Dispatchers.IO) {
            val r = reportsRepo.get(id)
            if (r == null) message.value = "Could not load that report." else openReport.value = r
        }
    }

    fun loadCompare(idA: Long, idB: Long) {
        compare.value = CompareUi()
        viewModelScope.launch(Dispatchers.IO) {
            val a = reportsRepo.get(idA)
            val b = reportsRepo.get(idB)
            compare.value = if (a == null || b == null) CompareUi(a, b, "One of the reports could not be loaded.") else CompareUi(a, b, null)
        }
    }

    fun deleteReport(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                reportsRepo.delete(id)
            } catch (e: Exception) {
                message.value = "Database error: " + (e.message ?: "unknown")
            }
        }
    }

    fun deleteAllReports() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                reportsRepo.deleteAll()
                message.value = "All saved reports deleted."
            } catch (e: Exception) {
                message.value = "Database error: " + (e.message ?: "unknown")
            }
        }
    }

    // ------------------------------------------------------------------ portfolio
    fun addHolding(coin: Coin, usd: Double) {
        viewModelScope.launch(Dispatchers.IO) {
            val r = portfolioRepo.addHolding(coin, usd)
            message.value = if (r.isSuccess) r.getOrNull() else (r.exceptionOrNull()?.message ?: "Failed")
        }
    }

    fun reduceHolding(p: Position, fraction: Double) {
        viewModelScope.launch(Dispatchers.IO) {
            val price = coinOf(p.coinId)?.price ?: p.lastPrice
            val r = portfolioRepo.reduceHolding(p.coinId, fraction, price)
            message.value = if (r.isSuccess) r.getOrNull() else (r.exceptionOrNull()?.message ?: "Failed")
        }
    }

    fun resetPortfolio(balance: Double) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                portfolioRepo.reset(balance)
                message.value = "Virtual portfolio reset."
            } catch (e: Exception) {
                message.value = "Database error: " + (e.message ?: "unknown")
            }
        }
    }

    // ------------------------------------------------------------------ settings / storage
    fun updateSettings(block: (AppSettings) -> AppSettings) {
        settingsStore.update(block)
        Scheduler.apply(getApplication(), settingsStore.value)
    }

    fun saveApiKey(key: String) {
        val ok = settingsStore.setApiKey(key)
        message.value = if (ok) (if (key.isBlank()) "API key removed." else "API key saved securely on this device.") else "Secure storage is unavailable on this device; the key was not saved."
    }

    fun clearCache() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                market.clearCache()
                message.value = "Cached data cleared."
            } catch (e: Exception) {
                message.value = "Database error: " + (e.message ?: "unknown")
            }
        }
    }

    val storageInfo = MutableStateFlow("")

    fun refreshStorageInfo() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val items = market.cacheCount()
                val kb = market.cacheSizeChars() / 1024
                val reports = reportsRepo.count()
                val dbFile = getApplication<Application>().getDatabasePath(com.cryptoresearch.ai.data.db.AppDatabase.FILE_NAME)
                val dbKb = if (dbFile.exists()) dbFile.length() / 1024 else 0L
                storageInfo.value = "$items cached items (~$kb KB) · $reports saved reports · database file ${dbKb} KB"
            } catch (e: Exception) {
                storageInfo.value = "Storage information unavailable."
            }
        }
    }
}
