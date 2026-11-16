package com.cryptoresearch.ai.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "coins")
data class Coin(
    @PrimaryKey val id: String,
    val symbol: String,
    val name: String,
    val image: String?,
    val rank: Int,
    val price: Double,
    val marketCap: Double,
    val volume24h: Double,
    val change24h: Double?,
    val change7d: Double?,
    val high24h: Double?,
    val low24h: Double?,
    val fetchedAt: Long,
    val source: String
)

enum class Timeframe(val label: String, val binanceInterval: String, val millis: Long) {
    M15("15m", "15m", 900_000L),
    H1("1h", "1h", 3_600_000L),
    H4("4h", "4h", 14_400_000L),
    D1("1d", "1d", 86_400_000L);

    val candlesPerDay: Int get() = (86_400_000L / millis).toInt()

    companion object {
        fun fromLabel(label: String): Timeframe = values().firstOrNull { it.label == label } ?: D1
    }
}

data class Candle(
    val time: Long,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val volume: Double,
    /** Taker-buy base volume (only provided by some exchanges); null when unavailable. */
    val takerBuyVolume: Double?
)

data class CandleSeries(
    val candles: List<Candle>,
    val source: String,
    /** True when candles were derived/approximated from a lower-resolution price series. */
    val approximate: Boolean,
    val hasVolume: Boolean,
    val hasDelta: Boolean
)

enum class Sentiment(val label: String) {
    BULLISH("Bullish"), BEARISH("Bearish"), NEUTRAL("Neutral")
}

data class NewsItem(
    val id: String,
    val title: String,
    val summary: String,
    val url: String,
    val source: String,
    val publishedAt: Long,
    val categories: List<String>,
    val sentiment: Sentiment
)

data class GlobalStats(
    val totalMarketCap: Double?,
    val totalVolume: Double?,
    val btcDominance: Double?,
    val ethDominance: Double?,
    val marketCapChange24h: Double?
)

data class FearGreed(val value: Int, val label: String)

/** Wrapper that records where data came from and how fresh it is. */
data class Fetched<T>(
    val data: T,
    val fetchedAt: Long,
    val fromCache: Boolean,
    val stale: Boolean,
    /** Set when a refresh failed and cached data was served instead. */
    val note: String? = null
)

enum class FreshLevel { LIVE, CACHED, STALE }

data class Freshness(val level: FreshLevel, val text: String)

fun freshnessOf(
    fetchedAt: Long,
    fromCache: Boolean,
    stale: Boolean,
    staleAfterMs: Long = 15 * 60_000L,
    now: Long = System.currentTimeMillis()
): Freshness {
    val age = now - fetchedAt
    val ago = com.cryptoresearch.ai.util.Fmt.ago(fetchedAt, now)
    return when {
        stale || age > staleAfterMs -> Freshness(FreshLevel.STALE, "Stale · $ago")
        fromCache -> Freshness(FreshLevel.CACHED, "Cached · $ago")
        else -> Freshness(FreshLevel.LIVE, "Live · $ago")
    }
}

// ---------- Research report model ----------

enum class Classification(val label: String) {
    BULLISH("Bullish Evidence"),
    BEARISH("Bearish Evidence"),
    NEUTRAL("Neutral"),
    INSUFFICIENT("Insufficient Data");

    companion object {
        fun fromName(n: String): Classification = values().firstOrNull { it.name == n } ?: INSUFFICIENT
    }
}

enum class Strength(val label: String) {
    NONE("n/a"), WEAK("Weak"), MEDIUM("Medium"), STRONG("Strong"), VERY_STRONG("Very Strong");

    companion object {
        fun fromName(n: String): Strength = values().firstOrNull { it.name == n } ?: NONE
    }
}

enum class AgentStatus(val label: String) {
    IDLE("Idle"), RUNNING("Running"), COMPLETED("Completed"), FAILED("Failed"), WAITING("Waiting for Data")
}

data class Evidence(val text: String, val weight: Int, val source: String)

data class AgentReport(
    val agentId: Int,
    val agentName: String,
    val summary: String,
    val details: List<String>,
    val sources: List<String>,
    val evidences: List<Evidence> = emptyList(),
    val error: String? = null
)

data class ReportSection(val title: String, val lines: List<String>)

data class ResearchReport(
    val coinId: String,
    val symbol: String,
    val name: String,
    val createdAt: Long,
    val classification: Classification,
    val strength: Strength,
    val confidencePct: Int,
    val confidenceLabel: String,
    val volatility: String,
    val freshness: String,
    val bullScore: Int,
    val bearScore: Int,
    val sections: List<ReportSection>,
    val sources: List<String>,
    val agentReports: List<AgentReport>
)

data class AgentState(
    val id: Int,
    val name: String,
    val role: String,
    val status: AgentStatus = AgentStatus.IDLE,
    val lastRunAt: Long? = null,
    val sources: List<String> = emptyList(),
    val processing: String = "Not run yet",
    val summary: String = "",
    val details: List<String> = emptyList(),
    val error: String? = null
)
