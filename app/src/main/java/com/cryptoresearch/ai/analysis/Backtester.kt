package com.cryptoresearch.ai.analysis

import com.cryptoresearch.ai.data.Candle
import com.cryptoresearch.ai.data.Classification
import com.cryptoresearch.ai.data.Timeframe
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

data class BacktestParams(
    val tf: Timeframe,
    val periodDays: Int,
    val horizonCandles: Int,
    val feePct: Double,
    val slippagePct: Double
)

data class BtTrade(
    val signalTime: Long,
    val entryTime: Long,
    val exitTime: Long,
    val classification: Classification,
    val grossMovePct: Double,
    val netReturnPct: Double,
    val hit: Boolean
)

data class BacktestResult(
    val params: BacktestParams,
    val windowStart: Long,
    val windowEnd: Long,
    val windowCandles: Int,
    val requestedCandles: Int,
    val distribution: Map<Classification, Int>,
    val trades: List<BtTrade>,
    val bullishTrades: Int,
    val bearishTrades: Int,
    val bullishHitRate: Double?,
    val bearishHitRate: Double?,
    val overallHitRate: Double?,
    val avgNetReturnPct: Double?,
    val compoundedNetPct: Double,
    val buyHoldPct: Double,
    val maxDrawdownPct: Double,
    val tradeStdDevPct: Double?,
    val annualizedVolPct: Double?,
    val equityCurve: List<Double>,
    val notes: List<String>
)

/**
 * Historical research evaluation.
 *
 * Classification at candle t uses only indicator values computed from candles 0..t (all indicators
 * are causal). The hypothetical entry is the OPEN of candle t+1 (never the close that produced the
 * signal) and the exit is the CLOSE of candle t+H. Trades do not overlap. This avoids look-ahead bias.
 */
object Backtester {
    const val WARMUP = 200

    fun classifyAt(
        i: Int, close: DoubleArray, ema20: DoubleArray, ema50: DoubleArray, ema200: DoubleArray,
        rsi: DoubleArray, hist: DoubleArray
    ): Classification? {
        if (ema50[i].isNaN() || ema20[i].isNaN() || rsi[i].isNaN() || hist[i].isNaN()) return null
        var s = 0
        s += if (close[i] > ema50[i]) 1 else -1
        s += if (ema20[i] > ema50[i]) 1 else -1
        s += if (rsi[i] > 55) 1 else if (rsi[i] < 45) -1 else 0
        s += if (hist[i] > 0) 1 else -1
        if (!ema200[i].isNaN()) s += if (close[i] > ema200[i]) 1 else -1
        return when {
            s >= 3 -> Classification.BULLISH
            s <= -3 -> Classification.BEARISH
            else -> Classification.NEUTRAL
        }
    }

    fun run(candles: List<Candle>, p: BacktestParams): BacktestResult? {
        val n = candles.size
        val h = p.horizonCandles
        if (n < WARMUP + h + 20) return null

        val close = DoubleArray(n) { candles[it].close }
        val ema20 = Ind.ema(close, 20)
        val ema50 = Ind.ema(close, 50)
        val ema200 = Ind.ema(close, 200)
        val rsi = Ind.rsi(close, 14)
        val hist = Ind.macd(close).hist

        val requested = p.periodDays * p.tf.candlesPerDay
        val start = max(WARMUP, n - requested)
        val lastSignalIdx = n - 1 - h

        val dist = LinkedHashMap<Classification, Int>()
        dist[Classification.BULLISH] = 0
        dist[Classification.BEARISH] = 0
        dist[Classification.NEUTRAL] = 0
        for (t in start..lastSignalIdx) {
            val cl = classifyAt(t, close, ema20, ema50, ema200, rsi, hist) ?: continue
            dist[cl] = (dist[cl] ?: 0) + 1
        }

        val fee = p.feePct / 100.0
        val slip = p.slippagePct / 100.0
        val trades = ArrayList<BtTrade>()
        val equity = ArrayList<Double>()
        equity.add(1.0)
        var eq = 1.0
        var peak = 1.0
        var maxDd = 0.0
        var t = start
        while (t <= lastSignalIdx) {
            val cl = classifyAt(t, close, ema20, ema50, ema200, rsi, hist)
            if (cl == null || cl == Classification.NEUTRAL) {
                t++
                continue
            }
            val entryC = candles[t + 1]
            val exitC = candles[t + h]
            val rawEntry = entryC.open
            val rawExit = exitC.close
            val gross = (rawExit / rawEntry - 1.0) * 100.0
            val net: Double
            val hit: Boolean
            if (cl == Classification.BULLISH) {
                val entry = rawEntry * (1 + slip)
                val exit = rawExit * (1 - slip)
                net = ((exit * (1 - fee)) / (entry * (1 + fee)) - 1.0) * 100.0
                hit = rawExit > rawEntry
            } else {
                // Inverse-direction hypothetical (no borrow / funding costs modelled).
                val entry = rawEntry * (1 - slip)
                val exit = rawExit * (1 + slip)
                net = ((entry * (1 - fee)) / (exit * (1 + fee)) - 1.0) * 100.0
                hit = rawExit < rawEntry
            }
            trades.add(BtTrade(candles[t].time, entryC.time, exitC.time, cl, gross, net, hit))
            eq *= (1.0 + net / 100.0)
            equity.add(eq)
            peak = max(peak, eq)
            maxDd = max(maxDd, (peak - eq) / peak * 100.0)
            t += h
        }

        val bull = trades.filter { it.classification == Classification.BULLISH }
        val bear = trades.filter { it.classification == Classification.BEARISH }
        val bullHit = if (bull.isEmpty()) null else bull.count { it.hit } * 100.0 / bull.size
        val bearHit = if (bear.isEmpty()) null else bear.count { it.hit } * 100.0 / bear.size
        val allHit = if (trades.isEmpty()) null else trades.count { it.hit } * 100.0 / trades.size
        val avgNet = if (trades.isEmpty()) null else trades.map { it.netReturnPct }.average()
        val sd: Double? = if (trades.size < 2) null else {
            val m = trades.map { it.netReturnPct }.average()
            sqrt(trades.sumOf { (it.netReturnPct - m).pow(2) } / (trades.size - 1))
        }
        val buyHold = (close[n - 1] / close[start] - 1.0) * 100.0
        val rv = Ind.logReturnStd(close.copyOfRange(start, n), n - start)
        val annVol = if (rv.isNaN()) null else rv * sqrt(p.tf.candlesPerDay * 365.0) * 100.0

        val notes = ArrayList<String>()
        notes.add("Classification at candle t uses only data up to and including t; the hypothetical entry is the open of candle t+1 (no look-ahead).")
        notes.add("Rule set: close vs EMA50/EMA200, EMA20 vs EMA50, RSI(14) vs 55/45 and MACD histogram sign. Score ≥ +3 = Bullish Evidence, ≤ −3 = Bearish Evidence, otherwise Neutral.")
        notes.add("Hypothetical holding period: ${h} candles (${p.tf.label}). Trades never overlap. Fee ${p.feePct}% and slippage ${p.slippagePct}% are applied on each side.")
        notes.add("Bearish classifications are evaluated as the inverse move without borrowing/funding costs, purely as an accuracy measure.")
        notes.add("Max drawdown is computed on the sequence of hypothetical trades (trade-level equity), not intra-trade.")
        notes.add("Historical results are educational only and do not predict or guarantee future performance.")
        if (n - start < requested) {
            notes.add("Requested ${p.periodDays} days but only ${min(n - start, requested)} candles were available after the 200-candle indicator warm-up.")
        }
        if (trades.size < 20) {
            notes.add("Only ${trades.size} non-overlapping samples — statistics are not reliable with so few observations.")
        }

        return BacktestResult(
            params = p,
            windowStart = candles[start].time,
            windowEnd = candles[n - 1].time,
            windowCandles = n - start,
            requestedCandles = requested,
            distribution = dist,
            trades = trades,
            bullishTrades = bull.size,
            bearishTrades = bear.size,
            bullishHitRate = bullHit,
            bearishHitRate = bearHit,
            overallHitRate = allHit,
            avgNetReturnPct = avgNet,
            compoundedNetPct = (eq - 1.0) * 100.0,
            buyHoldPct = buyHold,
            maxDrawdownPct = maxDd,
            tradeStdDevPct = sd,
            annualizedVolPct = annVol,
            equityCurve = equity,
            notes = notes
        )
    }
}
