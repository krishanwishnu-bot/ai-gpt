package com.cryptoresearch.ai.analysis

import com.cryptoresearch.ai.data.Sentiment

/**
 * Very small keyword-based sentiment estimator for news headlines.
 * This is a transparent heuristic (not a trained model) and is labelled as such in the UI.
 */
object SentimentLexicon {
    private val positive = setOf(
        "surge", "surges", "surged", "rally", "rallies", "rallied", "soar", "soars", "soared",
        "jump", "jumps", "jumped", "gain", "gains", "gained", "bullish", "approval", "approved",
        "approves", "adoption", "record", "breakout", "upgrade", "upgrades", "partnership",
        "inflow", "inflows", "rebound", "rebounds", "recovers", "recovery", "optimism", "optimistic",
        "accumulate", "accumulation", "growth", "boost", "boosts", "rise", "rises", "rising",
        "climbs", "climb", "milestone", "highs", "outperforms", "wins", "win"
    )
    private val negative = setOf(
        "crash", "crashes", "crashed", "plunge", "plunges", "plunged", "drop", "drops", "dropped",
        "fall", "falls", "fell", "slump", "slumps", "bearish", "hack", "hacked", "hacks", "exploit",
        "exploited", "lawsuit", "sue", "sues", "sued", "ban", "bans", "banned", "fraud", "scam",
        "liquidation", "liquidations", "liquidated", "outflow", "outflows", "selloff", "dump", "dumps",
        "fear", "warning", "warns", "investigation", "probe", "delist", "delists", "collapse",
        "bankruptcy", "decline", "declines", "loses", "losses", "loss", "rug", "stolen", "theft",
        "fine", "fined", "penalty", "tumble", "tumbles", "sinks", "slides"
    )

    private val important = listOf(
        "hack", "exploit", "etf", "sec ", "regulat", "lawsuit", "ban", "approval", "approved",
        "delist", "listing", "halving", "upgrade", "fork", "bankruptcy", "liquidation", "stolen",
        "investigation", "partnership", "fed ", "interest rate", "inflation"
    )

    fun score(text: String): Int {
        var s = 0
        val tokens = text.lowercase().split(Regex("[^a-z]+"))
        for (t in tokens) {
            if (t.isEmpty()) continue
            if (t in positive) s++
            if (t in negative) s--
        }
        return s
    }

    fun classify(text: String): Sentiment {
        val s = score(text)
        return when {
            s >= 1 -> Sentiment.BULLISH
            s <= -1 -> Sentiment.BEARISH
            else -> Sentiment.NEUTRAL
        }
    }

    fun isImportant(text: String): Boolean {
        val l = " " + text.lowercase() + " "
        return important.any { l.contains(it) }
    }
}
