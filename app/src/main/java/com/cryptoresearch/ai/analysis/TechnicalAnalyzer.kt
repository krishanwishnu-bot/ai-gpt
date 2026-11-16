package com.cryptoresearch.ai.analysis

import com.cryptoresearch.ai.data.CandleSeries
import com.cryptoresearch.ai.data.Fetched
import com.cryptoresearch.ai.data.Timeframe
import com.cryptoresearch.ai.util.Fmt

/** A single piece of directional evidence. direction: +1 bullish, -1 bearish. */
data class Signal(val text: String, val direction: Int, val weight: Int, val source: String)

data class TfSnapshot(
    val tf: Timeframe,
    val ok: Boolean,
    val note: String? = null,
    val candleCount: Int = 0,
    val lastClose: Double = 0.0,
    val lastTime: Long = 0L,
    val ema20: Double? = null,
    val ema50: Double? = null,
    val ema100: Double? = null,
    val ema200: Double? = null,
    val sma20: Double? = null,
    val sma50: Double? = null,
    val sma200: Double? = null,
    val rsi: Double? = null,
    val macd: Double? = null,
    val macdSignal: Double? = null,
    val macdHist: Double? = null,
    val bbUpper: Double? = null,
    val bbMid: Double? = null,
    val bbLower: Double? = null,
    val percentB: Double? = null,
    val atrPct: Double? = null,
    val realizedVolPct: Double? = null,
    val supports: List<Level> = emptyList(),
    val resistances: List<Level> = emptyList(),
    val structure: Structure = Structure("n/a", "", 0),
    val fvgs: List<Zone> = emptyList(),
    val orderBlocks: List<Zone> = emptyList(),
    val elliott: ElliottResult = ElliottResult("n/a", ""),
    val trend: String = "Insufficient",
    val net: Int = 0,
    val signals: List<Signal> = emptyList(),
    val history: List<String> = emptyList(),
    val volume: VolumeStats? = null,
    val gaps: List<String> = emptyList(),
    val source: String = "",
    val approximate: Boolean = false,
    val fetchedAt: Long = 0L,
    val fromCache: Boolean = false,
    val stale: Boolean = false
)

object TechnicalAnalyzer {

    fun analyze(f: Fetched<CandleSeries>, tf: Timeframe, includeApprox: Boolean): TfSnapshot {
        val series = f.data
        val c = series.candles
        val n = c.size
        if (n < 30) {
            return TfSnapshot(
                tf = tf, ok = false, note = "Only $n candles available (minimum 30 needed).",
                candleCount = n, source = series.source, approximate = series.approximate,
                fetchedAt = f.fetchedAt, fromCache = f.fromCache, stale = f.stale
            )
        }
        val close = DoubleArray(n) { c[it].close }
        val high = DoubleArray(n) { c[it].high }
        val low = DoubleArray(n) { c[it].low }
        val last = close[n - 1]

        val ema20 = Ind.last(Ind.ema(close, 20))
        val ema50 = Ind.last(Ind.ema(close, 50))
        val ema100 = Ind.last(Ind.ema(close, 100))
        val ema200 = Ind.last(Ind.ema(close, 200))
        val sma20 = Ind.last(Ind.sma(close, 20))
        val sma50 = Ind.last(Ind.sma(close, 50))
        val sma200 = Ind.last(Ind.sma(close, 200))
        val rsiArr = Ind.rsi(close, 14)
        val rsi = Ind.last(rsiArr)
        val m = Ind.macd(close)
        val macdV = Ind.last(m.line)
        val macdSig = Ind.last(m.signal)
        val macdHist = Ind.last(m.hist)
        val bb = Ind.bollinger(close, 20, 2.0)
        val bbU = Ind.last(bb.upper)
        val bbM = Ind.last(bb.mid)
        val bbL = Ind.last(bb.lower)
        val pctB = if (bbU != null && bbL != null && bbU > bbL) (last - bbL) / (bbU - bbL) else null
        val atrArr = Ind.atr(high, low, close, 14)
        val atr = Ind.last(atrArr)
        val atrPct = if (atr != null && last > 0) atr / last * 100.0 else null
        val rv = Ind.logReturnStd(close, 50)
        val realizedVol = if (rv.isNaN()) null else rv * 100.0

        val gaps = ArrayList<String>()
        fun need(name: String, period: Int, v: Double?) {
            if (v == null) gaps.add("$name unavailable (needs $period candles, have $n)")
        }
        need("EMA50", 50, ema50)
        need("EMA100", 100, ema100)
        need("EMA200", 200, ema200)
        need("SMA200", 200, sma200)

        val sw = Patterns.swings(c, 3)
        val lv = Patterns.levels(sw, last, 0.01)
        val structure = Patterns.marketStructure(c, sw)
        val fvgs = if (includeApprox) Patterns.fvgs(c, 4) else emptyList()
        val obs = if (includeApprox) Patterns.orderBlocks(c, atrArr, 3) else emptyList()
        val elliott = if (includeApprox) Patterns.elliott(sw) else ElliottResult("Disabled in settings", "")
        val vol = VolumeAnalysis.analyze(series)

        // ---- directional signals ----
        val w = if (tf == Timeframe.H4 || tf == Timeframe.D1) 2 else 1
        val tag = "[${tf.label}]"
        val src = "Technical (${tf.label})"
        val signals = ArrayList<Signal>()
        if (ema200 != null) {
            val up = last > ema200
            signals.add(Signal("$tag Close ${if (up) "above" else "below"} EMA200 (${Fmt.price(ema200)})", if (up) 1 else -1, w, src))
        }
        if (ema50 != null) {
            val up = last > ema50
            signals.add(Signal("$tag Close ${if (up) "above" else "below"} EMA50 (${Fmt.price(ema50)})", if (up) 1 else -1, w, src))
        }
        if (ema20 != null && ema50 != null) {
            val up = ema20 > ema50
            signals.add(Signal("$tag EMA20 ${if (up) "above" else "below"} EMA50 (short-term trend ${if (up) "up" else "down"})", if (up) 1 else -1, 1, src))
        }
        if (rsi != null) {
            when {
                rsi >= 70 -> signals.add(Signal("$tag RSI ${Fmt.num(rsi, 1)} is overbought (stretched; pullback risk)", -1, 1, src))
                rsi >= 55 -> signals.add(Signal("$tag RSI ${Fmt.num(rsi, 1)} shows positive momentum", 1, 1, src))
                rsi <= 30 -> signals.add(Signal("$tag RSI ${Fmt.num(rsi, 1)} is oversold (mean-reversion potential)", 1, 1, src))
                rsi <= 45 -> signals.add(Signal("$tag RSI ${Fmt.num(rsi, 1)} shows weak momentum", -1, 1, src))
            }
        }
        if (macdHist != null) {
            val up = macdHist > 0
            signals.add(Signal("$tag MACD histogram ${if (up) "positive" else "negative"} (${Fmt.num(macdHist, 4)})", if (up) 1 else -1, 1, src))
        }
        if (structure.bias != 0) {
            signals.add(Signal("$tag ${structure.label}", structure.bias, w, src))
        }

        // ---- historical context (1d only) ----
        val history = ArrayList<String>()
        if (tf == Timeframe.D1 && n >= 100) {
            val histSrc = "Historical (1d)"
            if (n > 91) {
                val r90 = (last / close[n - 91] - 1.0) * 100.0
                history.add("90-day return: ${Fmt.pct(r90, 1)}")
                if (r90 >= 15) signals.add(Signal("[1d] 90-day return is ${Fmt.pct(r90, 1)}", 1, 1, histSrc))
                if (r90 <= -15) signals.add(Signal("[1d] 90-day return is ${Fmt.pct(r90, 1)}", -1, 1, histSrc))
            }
            if (n > 31) {
                val r30 = (last / close[n - 31] - 1.0) * 100.0
                history.add("30-day return: ${Fmt.pct(r30, 1)}")
            }
            val periodHigh = close.max()
            val dd = (periodHigh - last) / periodHigh * 100.0
            history.add("Distance below highest close in loaded history ($n days): ${Fmt.plainPct(dd)}")
            if (dd >= 40) signals.add(Signal("[1d] Price is ${Fmt.plainPct(dd)} below its highest close in the loaded history", -1, 1, histSrc))
            if (dd <= 10) signals.add(Signal("[1d] Price is within ${Fmt.plainPct(dd)} of its highest close in the loaded history", 1, 1, histSrc))
            val e50 = Ind.ema(close, 50)
            var above = 0
            var counted = 0
            for (i in n - 90 until n) {
                if (i >= 0 && !e50[i].isNaN()) {
                    counted++
                    if (close[i] > e50[i]) above++
                }
            }
            if (counted >= 30) {
                val p = above * 100.0 / counted
                history.add("Closed above EMA50 on ${Fmt.plainPct(p, 0)} of the last $counted days")
                if (p >= 70) signals.add(Signal("[1d] Closed above EMA50 on ${Fmt.plainPct(p, 0)} of recent days", 1, 1, histSrc))
                if (p <= 30) signals.add(Signal("[1d] Closed above EMA50 on only ${Fmt.plainPct(p, 0)} of recent days", -1, 1, histSrc))
            }
        }

        val trendSignals = signals.filter { it.source.startsWith("Technical") }
        val net = trendSignals.sumOf { it.direction * it.weight }
        val trend = when {
            trendSignals.isEmpty() -> "Insufficient"
            net >= 3 -> "Bullish"
            net <= -3 -> "Bearish"
            else -> "Mixed"
        }

        return TfSnapshot(
            tf = tf, ok = true, candleCount = n, lastClose = last, lastTime = c.last().time,
            ema20 = ema20, ema50 = ema50, ema100 = ema100, ema200 = ema200,
            sma20 = sma20, sma50 = sma50, sma200 = sma200,
            rsi = rsi, macd = macdV, macdSignal = macdSig, macdHist = macdHist,
            bbUpper = bbU, bbMid = bbM, bbLower = bbL, percentB = pctB,
            atrPct = atrPct, realizedVolPct = realizedVol,
            supports = lv.first, resistances = lv.second,
            structure = structure, fvgs = fvgs, orderBlocks = obs, elliott = elliott,
            trend = trend, net = net, signals = signals, history = history,
            volume = vol, gaps = gaps,
            source = series.source, approximate = series.approximate,
            fetchedAt = f.fetchedAt, fromCache = f.fromCache, stale = f.stale
        )
    }

    /** Human-readable detail lines for one timeframe (used by the report and the Technical screen). */
    fun describe(s: TfSnapshot): List<String> {
        if (!s.ok) return listOf("${s.tf.label}: ${s.note ?: "analysis unavailable"}")
        val out = ArrayList<String>()
        out.add("${s.tf.label} · ${s.trend} (net score ${s.net}) · close ${Fmt.price(s.lastClose)} · ${s.candleCount} closed candles")
        out.add("RSI(14) ${Fmt.num(s.rsi, 1)} · MACD ${Fmt.num(s.macd, 4)} / signal ${Fmt.num(s.macdSignal, 4)} / hist ${Fmt.num(s.macdHist, 4)}")
        out.add("EMA20 ${Fmt.price(s.ema20)} · EMA50 ${Fmt.price(s.ema50)} · EMA100 ${Fmt.price(s.ema100)} · EMA200 ${Fmt.price(s.ema200)}")
        out.add("SMA20 ${Fmt.price(s.sma20)} · SMA50 ${Fmt.price(s.sma50)} · SMA200 ${Fmt.price(s.sma200)}")
        out.add("Bollinger(20,2): lower ${Fmt.price(s.bbLower)} · mid ${Fmt.price(s.bbMid)} · upper ${Fmt.price(s.bbUpper)} · %B ${Fmt.num(s.percentB, 2)}")
        out.add("ATR(14) ${Fmt.plainPct(s.atrPct, 2)} of price · realized volatility ${Fmt.plainPct(s.realizedVolPct, 2)} per candle")
        out.add("Support: " + (if (s.supports.isEmpty()) "none detected" else s.supports.joinToString { "${Fmt.price(it.price)} (${it.touches} touches)" }))
        out.add("Resistance: " + (if (s.resistances.isEmpty()) "none detected" else s.resistances.joinToString { "${Fmt.price(it.price)} (${it.touches} touches)" }))
        out.add("Structure: ${s.structure.label}. ${s.structure.detail}")
        if (s.fvgs.isNotEmpty()) out.add("Open FVGs (approximate): " + s.fvgs.joinToString { "${it.kind} ${Fmt.price(it.low)}–${Fmt.price(it.high)}" })
        if (s.orderBlocks.isNotEmpty()) out.add("Order blocks (approximate heuristic): " + s.orderBlocks.joinToString { "${it.kind} ${Fmt.price(it.low)}–${Fmt.price(it.high)}" })
        out.add("Elliott (approximate): ${s.elliott.label}")
        for (g in s.gaps) out.add("Note: $g")
        if (s.approximate) out.add("Note: candles are approximated from a price series; accuracy is reduced.")
        return out
    }
}
