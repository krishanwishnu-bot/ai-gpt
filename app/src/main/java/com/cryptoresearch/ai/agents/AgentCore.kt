package com.cryptoresearch.ai.agents

import com.cryptoresearch.ai.analysis.Signal
import com.cryptoresearch.ai.analysis.TfSnapshot
import com.cryptoresearch.ai.data.AgentReport
import com.cryptoresearch.ai.data.AgentState
import com.cryptoresearch.ai.data.AgentStatus
import com.cryptoresearch.ai.data.CandleSeries
import com.cryptoresearch.ai.data.Coin
import com.cryptoresearch.ai.data.AppSettings
import com.cryptoresearch.ai.data.FearGreed
import com.cryptoresearch.ai.data.Fetched
import com.cryptoresearch.ai.data.GlobalStats
import com.cryptoresearch.ai.data.MarketRepository
import com.cryptoresearch.ai.data.NewsItem
import com.cryptoresearch.ai.data.ResearchReport
import com.cryptoresearch.ai.data.Timeframe
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Thrown by an agent when it cannot produce a meaningful result (shown as Failed in the monitor). */
class AgentFailure(message: String) : Exception(message)

/** Shared, mutable working state for one research run. Agents run sequentially, so no locking is needed. */
class ResearchContext(
    val coin: Coin,
    val repo: MarketRepository,
    val settings: AppSettings
) {
    var coinNow: Coin = coin
    var coinFetchNote: String? = null

    val series = LinkedHashMap<Timeframe, Fetched<CandleSeries>>()
    val seriesErrors = LinkedHashMap<Timeframe, String>()
    val snapshots = LinkedHashMap<Timeframe, TfSnapshot>()

    var global: Fetched<GlobalStats>? = null
    var globalError: String? = null
    var fng: Fetched<FearGreed>? = null
    var fngError: String? = null

    var news: List<NewsItem> = emptyList()
    var newsFetchedAt: Long? = null
    var newsFromCache: Boolean = false
    var newsScope: String = "none"
    var newsError: String? = null

    val signals = ArrayList<Signal>()
    val reports = LinkedHashMap<Int, AgentReport>()
    var finalReport: ResearchReport? = null

    var currentAgent: Int = 0
    var onStatus: (Int, AgentStatus, String) -> Unit = { _, _, _ -> }

    fun waiting(msg: String) = onStatus(currentAgent, AgentStatus.WAITING, msg)
    fun running(msg: String) = onStatus(currentAgent, AgentStatus.RUNNING, msg)
}

interface ResearchAgent {
    val id: Int
    val name: String
    val role: String
    suspend fun run(ctx: ResearchContext): AgentReport
}

/** Holds the live status of all nine research agents for the Agent Monitor screen. */
class AgentMonitor {
    private val _states = MutableStateFlow(defaultStates())
    val states: StateFlow<List<AgentState>> = _states

    fun resetForRun() {
        _states.value = _states.value.map { it.copy(status = AgentStatus.IDLE, processing = "Queued", error = null) }
    }

    fun update(id: Int, block: (AgentState) -> AgentState) {
        _states.value = _states.value.map { if (it.id == id) block(it) else it }
    }

    companion object {
        val AGENT_INFO: List<Pair<String, String>> = listOf(
            "Market Agent" to "Collects live market data, tracks price changes and volatility.",
            "Technical Analysis Agent" to "RSI, MACD, EMA/SMA, Bollinger, support/resistance, structure, multi-timeframe trends.",
            "Volume Analysis Agent" to "Volume spikes, trends, volume profile and volume delta (when available).",
            "News Research Agent" to "Collects and summarises relevant crypto news with source links.",
            "Sentiment Analysis Agent" to "News tone (keyword heuristic) and Fear & Greed Index.",
            "Bullish Evidence Agent" to "Collects bullish indicators with strength and uncertainty.",
            "Bearish Evidence Agent" to "Collects bearish indicators with strength and uncertainty.",
            "Elliott Wave & NEoWave Agent" to "Checks multi-timeframe swing structure, basic impulse rules, Fibonacci context and invalidation levels; labels uncertain counts.",
            "Risk & Signal Engine Agent" to "Validates evidence and data quality, resolves conflicts, classifies the research and builds the final report."
        )

        fun defaultStates(): List<AgentState> =
            AGENT_INFO.mapIndexed { i, p -> AgentState(id = i + 1, name = p.first, role = p.second) }
    }
}
