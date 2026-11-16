package com.cryptoresearch.ai.agents

import com.cryptoresearch.ai.analysis.SentimentLexicon
import com.cryptoresearch.ai.analysis.Signal
import com.cryptoresearch.ai.analysis.TechnicalAnalyzer
import com.cryptoresearch.ai.data.AgentReport
import com.cryptoresearch.ai.data.NewsItem
import com.cryptoresearch.ai.data.Sentiment
import com.cryptoresearch.ai.data.Timeframe
import com.cryptoresearch.ai.util.Fmt

// ------------------------------------------------------------------------------------------
// Agent 1 - Market
// ------------------------------------------------------------------------------------------
class MarketAgent : ResearchAgent {
    override val id = 1
    override val name = "Market Agent"
    override val role = AgentMonitor.AGENT_INFO[0].second

    override suspend fun run(ctx: ResearchContext): AgentReport {
        ctx.waiting("Refreshing market data")
        val now = System.currentTimeMillis()
        val cached = ctx.repo.coinById(ctx.coin.id)
        if (cached == null || now - cached.fetchedAt > 120_000L) {
            val r = ctx.repo.refreshCoins()
            if (r.isFailure) {
                ctx.coinFetchNote = "Market refresh failed (" + Fmt.friendlyError(r.exceptionOrNull()) + "); using cached data."
            }
        }
        val coin = ctx.repo.coinById(ctx.coin.id) ?: ctx.coin
        ctx.coinNow = coin

        ctx.waiting("Fetching global market statistics")
        val g = ctx.repo.global()
        if (g.isSuccess) ctx.global = g.getOrNull() else ctx.globalError = Fmt.friendlyError(g.exceptionOrNull())

        ctx.running("Analysing market conditions")
        val src = "Market (${coin.source})"
        val details = ArrayList<String>()
        details.add("${coin.name} (${coin.symbol}) · rank #${coin.rank}")
        details.add("Price ${Fmt.price(coin.price)} · 24h ${Fmt.pct(coin.change24h)} · 7d ${Fmt.pct(coin.change7d)}")
        details.add("Market cap ${Fmt.compact(coin.marketCap)} · 24h volume ${Fmt.compact(coin.volume24h)}")
        if (coin.marketCap > 0) {
            details.add("24h turnover (volume / market cap): ${Fmt.plainPct(coin.volume24h / coin.marketCap * 100.0, 2)}")
        }
        val hi = coin.high24h
        val lo = coin.low24h
        if (hi != null && lo != null && hi > lo && coin.price > 0) {
            val rangePct = (hi - lo) / coin.price * 100.0
            val pos = ((coin.price - lo) / (hi - lo) * 100.0).coerceIn(0.0, 100.0)
            details.add("24h range ${Fmt.price(lo)} – ${Fmt.price(hi)} (${Fmt.plainPct(rangePct, 2)} of price); price sits at ${Fmt.plainPct(pos, 0)} of the range")
        } else {
            details.add("24h high/low not provided by ${coin.source}.")
        }
        val gs = ctx.global?.data
        if (gs != null) {
            details.add("Total market cap ${Fmt.compact(gs.totalMarketCap)} (24h ${Fmt.pct(gs.marketCapChange24h)}) · BTC dominance ${Fmt.plainPct(gs.btcDominance)}")
        } else {
            details.add("Global market statistics unavailable: ${ctx.globalError ?: "unknown"}")
        }
        details.add("Data from ${coin.source}, fetched ${Fmt.ago(coin.fetchedAt)}")
        ctx.coinFetchNote?.let { details.add("Warning: $it") }

        val ch24 = coin.change24h
        if (ch24 != null) {
            if (ch24 >= 3.0) ctx.signals.add(Signal("24h price change ${Fmt.pct(ch24)}", 1, 1, src))
            if (ch24 <= -3.0) ctx.signals.add(Signal("24h price change ${Fmt.pct(ch24)}", -1, 1, src))
        }
        val ch7 = coin.change7d
        if (ch7 != null) {
            if (ch7 >= 10.0) ctx.signals.add(Signal("7-day price change ${Fmt.pct(ch7)}", 1, 1, src))
            if (ch7 <= -10.0) ctx.signals.add(Signal("7-day price change ${Fmt.pct(ch7)}", -1, 1, src))
        }
        val gch = gs?.marketCapChange24h
        if (gch != null) {
            if (gch >= 2.0) ctx.signals.add(Signal("Total crypto market cap ${Fmt.pct(gch)} in 24h", 1, 1, "Market (CoinGecko global)"))
            if (gch <= -2.0) ctx.signals.add(Signal("Total crypto market cap ${Fmt.pct(gch)} in 24h", -1, 1, "Market (CoinGecko global)"))
        }

        val summary = "${coin.symbol} at ${Fmt.price(coin.price)} (24h ${Fmt.pct(coin.change24h)}, 7d ${Fmt.pct(coin.change7d)}); rank #${coin.rank}."
        val sources = arrayListOf("${coin.source} market data")
        if (gs != null) sources.add("CoinGecko global")
        return AgentReport(id, name, summary, details, sources)
    }
}

// ------------------------------------------------------------------------------------------
// Agent 2 - Technical analysis (fetches candles for all four timeframes)
// ------------------------------------------------------------------------------------------
class TechnicalAgent : ResearchAgent {
    override val id = 2
    override val name = "Technical Analysis Agent"
    override val role = AgentMonitor.AGENT_INFO[1].second

    override suspend fun run(ctx: ResearchContext): AgentReport {
        for (tf in Timeframe.values()) {
            ctx.waiting("Fetching ${tf.label} candles")
            val r = ctx.repo.candles(ctx.coinNow, tf, 500)
            if (r.isSuccess) {
                r.getOrNull()?.let { ctx.series[tf] = it }
            } else {
                ctx.seriesErrors[tf] = Fmt.friendlyError(r.exceptionOrNull())
            }
        }
        ctx.running("Computing indicators and price structure")
        for ((tf, f) in ctx.series) {
            val s = TechnicalAnalyzer.analyze(f, tf, ctx.settings.includeApproxConcepts)
            ctx.snapshots[tf] = s
            if (s.ok) ctx.signals.addAll(s.signals)
        }
        val ok = ctx.snapshots.values.filter { it.ok }
        if (ok.isEmpty()) {
            val why = if (ctx.seriesErrors.isNotEmpty()) ctx.seriesErrors.values.first() else "not enough candles"
            throw AgentFailure("No usable historical candle data ($why)")
        }
        val details = ArrayList<String>()
        for (tf in Timeframe.values().reversed()) {
            val s = ctx.snapshots[tf]
            if (s == null) {
                details.add("${tf.label}: unavailable (${ctx.seriesErrors[tf] ?: "no data"})")
                continue
            }
            if (!s.ok) {
                details.add("${tf.label}: ${s.note}")
                continue
            }
            details.add("${tf.label}: ${s.trend} (net ${s.net}) · close ${Fmt.price(s.lastClose)} · RSI ${Fmt.num(s.rsi, 1)} · MACD hist ${Fmt.num(s.macdHist, 4)}")
            details.add("    EMA20 ${Fmt.price(s.ema20)} · EMA50 ${Fmt.price(s.ema50)} · EMA200 ${Fmt.price(s.ema200)} · BB %B ${Fmt.num(s.percentB, 2)}")
            details.add("    ${s.structure.label}")
            val sup = s.supports.firstOrNull()
            val res = s.resistances.firstOrNull()
            if (sup != null || res != null) {
                details.add("    Nearest support ${if (sup != null) Fmt.price(sup.price) else "n/a"} · nearest resistance ${if (res != null) Fmt.price(res.price) else "n/a"}")
            }
            if (s.approximate) details.add("    Note: candles approximated from price series.")
        }
        val bullTf = ok.count { it.trend == "Bullish" }
        val bearTf = ok.count { it.trend == "Bearish" }
        val summary = "Analysed ${ok.size}/4 timeframes: $bullTf bullish, $bearTf bearish, ${ok.size - bullTf - bearTf} mixed."
        val sources = ctx.series.values.map { it.data.source }.distinct().toMutableList()
        return AgentReport(id, name, summary, details, sources)
    }
}

// ------------------------------------------------------------------------------------------
// Agent 3 - Volume
// ------------------------------------------------------------------------------------------
class VolumeAgent : ResearchAgent {
    override val id = 3
    override val name = "Volume Analysis Agent"
    override val role = AgentMonitor.AGENT_INFO[2].second

    override suspend fun run(ctx: ResearchContext): AgentReport {
        ctx.running("Analysing volume")
        val ok = ctx.snapshots.values.filter { it.ok }
        if (ok.isEmpty()) throw AgentFailure("No candle data available from the Technical Analysis Agent")
        val details = ArrayList<String>()
        var anyVolume = false
        for (tf in Timeframe.values().reversed()) {
            val s = ctx.snapshots[tf] ?: continue
            if (!s.ok) continue
            val v = s.volume
            if (v == null || !v.available) {
                details.add("${tf.label}: ${v?.note ?: "volume unavailable"}")
                continue
            }
            anyVolume = true
            val vsAvg = v.lastVsAvg
            details.add("${tf.label}: last candle volume ${if (vsAvg != null) Fmt.num(vsAvg, 2) + "× the 20-candle average" else "n/a"} · spikes ${v.spikes}/${v.spikeWindow} recent candles · trend ${v.trend}")
            if (v.poc != null) {
                details.add("    Volume profile (approximate, last 100 candles): POC ${Fmt.price(v.poc)} · value area ${Fmt.price(v.vaLow)} – ${Fmt.price(v.vaHigh)}")
            }
            details.add("    " + (if (v.deltaAvailable && v.deltaPct20 != null) "Volume delta (20 candles): ${Fmt.pct(v.deltaPct20, 1)} of volume (positive = net taker buying)" else (v.deltaNote ?: "Volume delta unavailable")))

            if (tf == Timeframe.D1 || tf == Timeframe.H4 || tf == Timeframe.H1) {
                val src = "Volume (${tf.label})"
                if (vsAvg != null && vsAvg >= 2.0 && v.lastCandleUp != null) {
                    if (v.lastCandleUp) {
                        ctx.signals.add(Signal("[${tf.label}] Volume spike (${Fmt.num(vsAvg, 1)}×) on an up candle", 1, 1, src))
                    } else {
                        ctx.signals.add(Signal("[${tf.label}] Volume spike (${Fmt.num(vsAvg, 1)}×) on a down candle", -1, 1, src))
                    }
                }
                val d = v.deltaPct20
                if (v.deltaAvailable && d != null) {
                    if (d >= 10.0) ctx.signals.add(Signal("[${tf.label}] Net taker buying over 20 candles (${Fmt.pct(d, 1)})", 1, 1, src))
                    if (d <= -10.0) ctx.signals.add(Signal("[${tf.label}] Net taker selling over 20 candles (${Fmt.pct(d, 1)})", -1, 1, src))
                }
            }
        }
        val summary = if (anyVolume) {
            val d1 = ctx.snapshots[Timeframe.D1]?.volume
            "Volume analysed on ${ok.count { it.volume?.available == true }} timeframe(s)." + (if (d1 != null && d1.available) " 1d volume trend: ${d1.trend}." else "")
        } else {
            "Per-candle volume is unavailable from the data source; volume analysis skipped."
        }
        val sources = ok.filter { it.volume?.available == true }.map { it.source }.distinct().toMutableList()
        return AgentReport(id, name, summary, details, sources)
    }
}

// ------------------------------------------------------------------------------------------
// Agent 4 - News
// ------------------------------------------------------------------------------------------
class NewsAgent : ResearchAgent {
    override val id = 4
    override val name = "News Research Agent"
    override val role = AgentMonitor.AGENT_INFO[3].second

    private fun mentions(n: NewsItem, name: String, symbol: String): Boolean {
        val text = n.title + " " + n.summary
        if (text.contains(name, ignoreCase = true)) return true
        if (symbol.length >= 3) {
            return Regex("\\b" + Regex.escape(symbol) + "\\b", RegexOption.IGNORE_CASE).containsMatchIn(text)
        }
        return false
    }

    override suspend fun run(ctx: ResearchContext): AgentReport {
        val coin = ctx.coinNow
        ctx.waiting("Fetching ${coin.symbol} news")
        val specific = ctx.repo.news(coin.symbol)
        var items: List<NewsItem> = specific.getOrNull()?.data ?: emptyList()
        var fetchedAt: Long? = specific.getOrNull()?.fetchedAt
        var fromCache = specific.getOrNull()?.fromCache ?: false
        var scope = if (items.size >= 3) "coin-specific" else "none"
        var lastError: String? = if (specific.isFailure) Fmt.friendlyError(specific.exceptionOrNull()) else null

        if (items.size < 3) {
            ctx.waiting("Fetching general market headlines")
            val general = ctx.repo.news(null)
            val gl = general.getOrNull()?.data ?: emptyList()
            if (general.isFailure) lastError = Fmt.friendlyError(general.exceptionOrNull())
            if (general.isSuccess) {
                fetchedAt = general.getOrNull()?.fetchedAt
                fromCache = general.getOrNull()?.fromCache ?: false
            }
            val mentioned = gl.filter { mentions(it, coin.name, coin.symbol) }
            val merged = (items + mentioned).distinctBy { it.id }
            if (merged.isNotEmpty()) {
                items = merged
                scope = "mentions"
            } else if (gl.isNotEmpty()) {
                items = gl.take(10)
                scope = "general"
            }
        }
        if (items.isEmpty()) {
            ctx.newsError = lastError ?: "No articles returned"
            throw AgentFailure("News unavailable: ${ctx.newsError}")
        }
        ctx.running("Summarising news")
        val sorted = items.sortedByDescending { it.publishedAt }.take(ctx.settings.newsCount.coerceIn(5, 30))
        ctx.news = sorted
        ctx.newsScope = scope
        ctx.newsFetchedAt = fetchedAt
        ctx.newsFromCache = fromCache

        val details = ArrayList<String>()
        details.add(
            when (scope) {
                "coin-specific" -> "Showing ${sorted.size} articles tagged ${coin.symbol}."
                "mentions" -> "Few ${coin.symbol}-tagged articles; including general headlines that mention ${coin.name}."
                else -> "No ${coin.name}-specific articles found; showing general market headlines (weaker relevance)."
            }
        )
        for (n in sorted.take(8)) {
            details.add("[${Fmt.shortDateTime(n.publishedAt)}] ${n.title} — ${n.source} (${n.sentiment.label})")
        }
        val important = sorted.filter { SentimentLexicon.isImportant(it.title) }.take(4)
        if (important.isNotEmpty()) {
            details.add("Potentially market-relevant developments:")
            for (n in important) details.add("⚑ ${n.title} — ${n.source}")
        }
        val summary = "${sorted.size} articles ($scope); ${important.size} flagged as potentially market-relevant. Latest ${Fmt.ago(sorted.first().publishedAt)}."
        val sources = arrayListOf("CryptoCompare news API")
        sources.addAll(sorted.map { it.source }.distinct().take(4))
        return AgentReport(id, name, summary, details, sources)
    }
}

// ------------------------------------------------------------------------------------------
// Agent 5 - Sentiment
// ------------------------------------------------------------------------------------------
class SentimentAgent : ResearchAgent {
    override val id = 5
    override val name = "Sentiment Analysis Agent"
    override val role = AgentMonitor.AGENT_INFO[4].second

    override suspend fun run(ctx: ResearchContext): AgentReport {
        ctx.waiting("Fetching Fear & Greed Index")
        val f = ctx.repo.fearGreed()
        if (f.isSuccess) ctx.fng = f.getOrNull() else ctx.fngError = Fmt.friendlyError(f.exceptionOrNull())

        ctx.running("Classifying news sentiment")
        val details = ArrayList<String>()
        val sources = ArrayList<String>()
        val news = ctx.news
        if (news.isEmpty() && ctx.fng == null) {
            throw AgentFailure("No sentiment data available (news: ${ctx.newsError ?: "none"}; Fear & Greed: ${ctx.fngError ?: "none"})")
        }
        var summary = ""
        if (news.isNotEmpty()) {
            val bull = news.count { it.sentiment == Sentiment.BULLISH }
            val bear = news.count { it.sentiment == Sentiment.BEARISH }
            val neu = news.size - bull - bear
            val net = (bull - bear).toDouble() / news.size
            details.add("News tone (${ctx.newsScope}): $bull bullish · $bear bearish · $neu neutral out of ${news.size} articles.")
            details.add("Method: keyword-based classification of headlines/snippets — a transparent heuristic, not a trained model.")
            if (news.size < 5) details.add("Small sample (${news.size} articles): treat as low reliability.")
            if (ctx.newsScope == "general") details.add("Articles are general market news, not specific to ${ctx.coinNow.symbol}.")
            val src = "Sentiment (news)"
            if (ctx.newsScope != "general" && news.size >= 5) {
                if (net >= 0.25) ctx.signals.add(Signal("News tone is net positive (${bull} bullish vs ${bear} bearish headlines)", 1, 1, src))
                if (net <= -0.25) ctx.signals.add(Signal("News tone is net negative (${bear} bearish vs ${bull} bullish headlines)", -1, 1, src))
            }
            summary = "News tone: $bull bullish / $bear bearish / $neu neutral."
            sources.add("CryptoCompare news API")
        } else {
            details.add("No news articles available for sentiment scoring (${ctx.newsError ?: "unknown reason"}).")
        }
        val fg = ctx.fng?.data
        if (fg != null) {
            details.add("Fear & Greed Index (market-wide, not coin-specific): ${fg.value} — ${fg.label}.")
            val src = "Sentiment (Fear & Greed)"
            if (fg.value >= 60) ctx.signals.add(Signal("Market-wide sentiment is greedy (Fear & Greed ${fg.value})", 1, 1, src))
            if (fg.value <= 40) ctx.signals.add(Signal("Market-wide sentiment is fearful (Fear & Greed ${fg.value})", -1, 1, src))
            summary += (if (summary.isEmpty()) "" else " ") + "Fear & Greed ${fg.value} (${fg.label})."
            sources.add("alternative.me Fear & Greed Index")
        } else {
            details.add("Fear & Greed Index unavailable: ${ctx.fngError ?: "unknown"}.")
        }
        details.add("Social-media sentiment is not included: no reliable free source is integrated, so no values are invented.")
        return AgentReport(id, name, summary, details, sources)
    }
}
