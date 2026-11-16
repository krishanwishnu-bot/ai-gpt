package com.cryptoresearch.ai.agents

import com.cryptoresearch.ai.data.AgentReport
import com.cryptoresearch.ai.data.Classification
import com.cryptoresearch.ai.data.FreshLevel
import com.cryptoresearch.ai.data.ReportSection
import com.cryptoresearch.ai.data.ResearchReport
import com.cryptoresearch.ai.data.Strength
import com.cryptoresearch.ai.data.Timeframe
import com.cryptoresearch.ai.data.freshnessOf
import com.cryptoresearch.ai.util.Fmt
import kotlin.math.abs

// Agent 9 - validates evidence and data, then assembles the final research report
class RiskAgent : ResearchAgent {
    override val id = 9
    override val name = "Risk & Signal Engine Agent"
    override val role = AgentMonitor.AGENT_INFO[8].second

    override suspend fun run(ctx: ResearchContext): AgentReport {
        ctx.running("Reviewing agent reports")
        val coin = ctx.coinNow
        val now = System.currentTimeMillis()
        val bull = ctx.signals.score(1)
        val bear = ctx.signals.score(-1)

        // ----- data gaps -----
        val gaps = ArrayList<String>()
        for (i in 1..8) {
            val st = ctx.reports[i]
            if (st == null) gaps.add("Agent $i produced no report (failed or skipped).")
        }
        for (tf in Timeframe.values()) {
            val s = ctx.snapshots[tf]
            if (s == null) gaps.add("${tf.label} candles unavailable: ${ctx.seriesErrors[tf] ?: "no data"}")
            else if (!s.ok) gaps.add("${tf.label}: ${s.note}")
            else {
                if (s.approximate) gaps.add("${tf.label}: candles approximated from price series; no volume.")
                s.gaps.forEach { gaps.add("${tf.label}: $it") }
            }
        }
        if (ctx.news.isEmpty()) gaps.add("News unavailable: ${ctx.newsError ?: "unknown"}")
        else {
            if (ctx.newsScope == "general") gaps.add("News is general market news, not coin-specific.")
            if (ctx.newsFetchedAt != null && now - ctx.newsFetchedAt!! > 24 * 60 * 60_000L) gaps.add("News data is older than 24 hours.")
            if (ctx.newsFromCache) gaps.add("News was served from cache; verify publication timestamps.")
        }
        if (ctx.fng == null) gaps.add("Fear & Greed unavailable: ${ctx.fngError ?: "unknown"}")
        if (ctx.global == null) gaps.add("Global market stats unavailable: ${ctx.globalError ?: "unknown"}")
        ctx.coinFetchNote?.let { gaps.add(it) }
        gaps.add("Social-media sentiment, order book, liquidation and institutional flow data are not available and were not estimated.")

        // ----- conflicts -----
        val conflicts = ArrayList<String>()
        val trends = Timeframe.values().mapNotNull { tf -> ctx.snapshots[tf]?.takeIf { it.ok }?.let { tf to it.trend } }
        val tBull = trends.filter { it.second == "Bullish" }.map { it.first.label }
        val tBear = trends.filter { it.second == "Bearish" }.map { it.first.label }
        if (tBull.isNotEmpty() && tBear.isNotEmpty()) {
            conflicts.add("Timeframes disagree: bullish on ${tBull.joinToString()}, bearish on ${tBear.joinToString()}.")
        }
        val ch24 = coin.change24h
        val d1 = ctx.snapshots[Timeframe.D1]?.takeIf { it.ok }
        if (ch24 != null && d1 != null) {
            if (ch24 > 3 && d1.trend == "Bearish") conflicts.add("Price is up ${Fmt.pct(ch24)} in 24h, but the daily trend structure is bearish.")
            if (ch24 < -3 && d1.trend == "Bullish") conflicts.add("Price is down ${Fmt.pct(ch24)} in 24h, but the daily trend structure is bullish.")
        }
        val newsBull = ctx.news.count { it.sentiment.name == "BULLISH" }
        val newsBear = ctx.news.count { it.sentiment.name == "BEARISH" }
        if (ctx.news.size >= 5 && ctx.newsScope != "general") {
            if (newsBull > newsBear * 2 && bear > bull) conflicts.add("News tone is positive while technical/market evidence leans bearish.")
            if (newsBear > newsBull * 2 && bull > bear) conflicts.add("News tone is negative while technical/market evidence leans bullish.")
        }
        val fg = ctx.fng?.data
        if (fg != null) {
            if (fg.value >= 75 && d1 != null && d1.rsi != null && d1.rsi >= 70) conflicts.add("Extreme greed (${fg.value}) with overbought daily RSI: bullish momentum but elevated reversal risk.")
            if (fg.value <= 25 && bull > bear) conflicts.add("Extreme fear (${fg.value}) while evidence leans bullish: sentiment and structure diverge.")
        }
        if (bull > 0 && bear > 0 && abs(bull - bear) <= 2) conflicts.add("Bullish (score $bull) and bearish (score $bear) evidence are closely balanced.")

        // ----- volatility -----
        val volTf = ctx.snapshots[Timeframe.D1]?.takeIf { it.ok && it.atrPct != null } ?: ctx.snapshots.values.firstOrNull { it.ok && it.tf != Timeframe.M15 && it.atrPct != null }
        val volLevel: String
        val volDetail: String
        if (volTf != null && volTf.atrPct != null) {
            val a: Double = volTf.atrPct ?: 0.0
            val th = when (volTf.tf) {
                Timeframe.D1 -> doubleArrayOf(2.5, 5.0, 8.0)
                Timeframe.H4 -> doubleArrayOf(1.2, 2.5, 4.0)
                Timeframe.H1 -> doubleArrayOf(0.6, 1.2, 2.0)
                Timeframe.M15 -> doubleArrayOf(0.3, 0.6, 1.0)
            }
            volLevel = when {
                a < th[0] -> "Low"
                a < th[1] -> "Moderate"
                a < th[2] -> "High"
                else -> "Very High"
            }
            volDetail = "ATR(14) on ${volTf.tf.label} is ${Fmt.plainPct(a, 2)} of price."
        } else {
            val r = ch24?.let { abs(it) }
            volLevel = when {
                r == null -> "Unknown"
                r < 2 -> "Low"
                r < 5 -> "Moderate"
                r < 10 -> "High"
                else -> "Very High"
            }
            volDetail = "Estimated from the 24h price change only (candle data unavailable)."
        }

        // ----- confidence -----
        var conf = 100
        val okCount = ctx.snapshots.values.count { it.ok }
        conf -= (4 - okCount) * 12
        if (ctx.news.isEmpty()) conf -= 8 else if (ctx.newsScope == "general") conf -= 4
        if (ctx.fng == null) conf -= 4 else if (ctx.fng!!.stale) conf -= 3
        if (ctx.global == null) conf -= 3 else if (ctx.global!!.stale) conf -= 3
        if (ctx.newsFetchedAt != null && now - ctx.newsFetchedAt!! > 24 * 60 * 60_000L) conf -= 4
        if (ctx.snapshots.values.any { it.ok && it.approximate }) conf -= 10
        if (ctx.snapshots.values.any { it.ok && it.stale }) conf -= 6
        if (ctx.coinFetchNote != null) conf -= 6
        conf -= minOf(12, conflicts.size * 4)
        conf -= (8 - ctx.reports.keys.count { it in 1..8 }).coerceAtLeast(0) * 5
        conf = conf.coerceIn(5, 95)
        val confLabel = if (conf >= 75) "High" else if (conf >= 50) "Medium" else "Low"

        // ----- classification -----
        val total = bull + bear
        val classification: Classification
        val strength: Strength
        if (okCount == 0 || total < 3) {
            classification = Classification.INSUFFICIENT
            strength = Strength.NONE
        } else {
            val diff = bull - bear
            val ratio = abs(diff).toDouble() / total
            if (ratio < 0.2) {
                classification = Classification.NEUTRAL
                strength = Strength.NONE
            } else {
                classification = if (diff > 0) Classification.BULLISH else Classification.BEARISH
                val mine = if (diff > 0) bull else bear
                val other = if (diff > 0) bear else bull
                var st = strengthOf(mine, other)
                if (conf < 50 && (st == Strength.STRONG || st == Strength.VERY_STRONG)) st = Strength.MEDIUM
                if (conf < 35 && st == Strength.MEDIUM) st = Strength.WEAK
                strength = st
            }
        }

        // ----- freshness -----
        val coinFresh = freshnessOf(coin.fetchedAt, false, false)
        val oldestCandle = ctx.series.values.minOfOrNull { it.fetchedAt }
        val candleFresh = oldestCandle?.let { freshnessOf(it, ctx.series.values.all { s -> s.fromCache }, ctx.series.values.any { s -> s.stale }, 60 * 60_000L) }
        val freshText = "Price: ${coinFresh.text}" + (if (candleFresh != null) " · Candles: ${candleFresh.text}" else " · Candles: unavailable") +
            (if (ctx.newsFetchedAt != null) " · News: ${Fmt.ago(ctx.newsFetchedAt!!)}" else "") +
            (if (ctx.fng?.stale == true) " · Fear & Greed: stale" else "") +
            (if (ctx.global?.stale == true) " · Global stats: stale" else "")
        val anyStale = coinFresh.level == FreshLevel.STALE || candleFresh?.level == FreshLevel.STALE

        // ----- sources -----
        val sources = LinkedHashSet<String>()
        for (r in ctx.reports.values) sources.addAll(r.sources)

        // ----- sections -----
        fun rep(i: Int): AgentReport? = ctx.reports[i]
        val sections = ArrayList<ReportSection>()
        sections.add(
            ReportSection(
                "1. Coin Overview",
                listOf(
                    "${coin.name} (${coin.symbol}) · market-cap rank #${coin.rank}",
                    "Market cap ${Fmt.compact(coin.marketCap)} · 24h volume ${Fmt.compact(coin.volume24h)}",
                    "Price data: ${coin.source}, fetched ${Fmt.dateTime(coin.fetchedAt)}"
                )
            )
        )
        sections.add(ReportSection("2. Current Market Conditions", rep(1)?.details ?: listOf("Market Agent produced no report.")))
        sections.add(ReportSection("3. Technical Analysis", (rep(2)?.details ?: listOf("Technical Analysis Agent produced no report.")) + ctx.snapshots[Timeframe.D1]?.takeIf { it.ok }?.history.orEmpty().map { "History: $it" }))
        sections.add(ReportSection("4. Volume Analysis", rep(3)?.details ?: listOf("Volume Analysis Agent produced no report.")))

        val mtf = ArrayList<String>()
        for (tf in tfOrder()) {
            val s = ctx.snapshots[tf]
            mtf.add(if (s == null) "${tf.label}: unavailable" else if (!s.ok) "${tf.label}: ${s.note}" else "${tf.label}: ${s.trend} (net score ${s.net}) — ${s.structure.label}")
        }
        mtf.add(if (trends.size >= 2) "Alignment: ${tBull.size} bullish, ${tBear.size} bearish, ${trends.size - tBull.size - tBear.size} mixed of ${trends.size} timeframes." else "Alignment: not enough timeframes for a multi-timeframe view.")
        rep(8)?.details?.forEach { mtf.add("Elliott / NEoWave: $it") }
        sections.add(ReportSection("5. Multi-Timeframe & Elliott Wave Analysis", mtf))

        sections.add(ReportSection("6. News Analysis", rep(4)?.details ?: listOf("News unavailable: ${ctx.newsError ?: "unknown"}")))
        sections.add(ReportSection("7. Sentiment Analysis", rep(5)?.details ?: listOf("Sentiment Agent produced no report.")))
        sections.add(ReportSection("8. Bullish Evidence", rep(6)?.details ?: listOf("No report.")))
        sections.add(ReportSection("9. Bearish Evidence", rep(7)?.details ?: listOf("No report.")))
        sections.add(
            ReportSection(
                "10. Risk Assessment",
                listOf(
                    "Volatility: $volLevel. $volDetail",
                    "Data confidence: $conf% ($confLabel).",
                    "Data freshness: $freshText" + if (anyStale) " (some data is stale)" else "",
                    "Crypto assets can move sharply and unpredictably; indicators lag and are not forecasts."
                ) + gaps.map { "Data gap: $it" }
            )
        )
        sections.add(ReportSection("11. Conflicting Evidence", if (conflicts.isEmpty()) listOf("No significant conflicts between the evidence groups were detected.") else conflicts))

        val summaryLines = ArrayList<String>()
        summaryLines.add("Research classification: ${classification.label}" + (if (strength != Strength.NONE) " · evidence strength ${strength.label}" else "") + " (bullish score $bull vs bearish score $bear).")
        summaryLines.add("Data confidence $conf% ($confLabel) · volatility $volLevel.")
        summaryLines.add(
            when (classification) {
                Classification.BULLISH -> "Available indicators lean positive, but this reflects past price behaviour only."
                Classification.BEARISH -> "Available indicators lean negative, but this reflects past price behaviour only."
                Classification.NEUTRAL -> "Evidence is balanced or mixed; no clear lean."
                Classification.INSUFFICIENT -> "Not enough reliable data for a classification."
            }
        )
        if (conflicts.isNotEmpty()) summaryLines.add("${conflicts.size} conflict(s) reduce reliability — see section 11.")
        summaryLines.add("Educational research only. This is NOT financial advice, a buy/sell signal, or a prediction.")
        sections.add(ReportSection("12. Final Research Summary", summaryLines))

        val report = ResearchReport(
            coinId = coin.id, symbol = coin.symbol, name = coin.name, createdAt = now,
            classification = classification, strength = strength,
            confidencePct = conf, confidenceLabel = confLabel, volatility = volLevel,
            freshness = freshText, bullScore = bull, bearScore = bear,
            sections = sections, sources = sources.toList(),
            agentReports = ctx.reports.values.toList()
        )
        ctx.finalReport = report

        val details = ArrayList<String>()
        details.add("Classification: ${classification.label} (${strength.label}) · bull $bull vs bear $bear")
        details.add("Volatility $volLevel · confidence $conf% ($confLabel)")
        details.add(if (conflicts.isEmpty()) "Conflicts: none detected" else "Conflicts (${conflicts.size}):")
        conflicts.forEach { details.add("• $it") }
        details.add("Data gaps (${gaps.size}):")
        gaps.forEach { details.add("• $it") }
        val summary = "${classification.label}" + (if (strength != Strength.NONE) " (${strength.label})" else "") + " · confidence $conf% · ${conflicts.size} conflicts · ${gaps.size} data gaps."
        return AgentReport(id, name, summary, details, sources.toList())
    }
}
