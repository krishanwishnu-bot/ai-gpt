package com.cryptoresearch.ai.analysis

import com.cryptoresearch.ai.data.Candle
import kotlin.math.abs

data class Swing(val index: Int, val price: Double, val isHigh: Boolean)
data class Level(val price: Double, val touches: Int)
data class Zone(val kind: String, val low: Double, val high: Double, val index: Int, val time: Long, val open: Boolean)
data class Structure(val label: String, val detail: String, val bias: Int)
data class ElliottResult(val label: String, val detail: String)

/** Price-structure heuristics. FVG / order blocks / Elliott are approximations, not exact science. */
object Patterns {

    /** Fractal swing points: a high (low) that is the extreme of [k] candles on each side. */
    fun swings(c: List<Candle>, k: Int = 3): List<Swing> {
        val out = ArrayList<Swing>()
        if (c.size < 2 * k + 1) return out
        for (i in k until c.size - k) {
            var isH = true
            var isL = true
            for (j in 1..k) {
                if (c[i].high <= c[i - j].high || c[i].high <= c[i + j].high) isH = false
                if (c[i].low >= c[i - j].low || c[i].low >= c[i + j].low) isL = false
            }
            if (isH) out.add(Swing(i, c[i].high, true))
            if (isL) out.add(Swing(i, c[i].low, false))
        }
        return out
    }

    /** Clusters swing prices into horizontal levels and splits them into supports / resistances. */
    fun levels(sw: List<Swing>, last: Double, tol: Double = 0.01): Pair<List<Level>, List<Level>> {
        if (sw.isEmpty()) return Pair(emptyList(), emptyList())
        val prices = sw.map { it.price }.sorted()
        val clusters = ArrayList<MutableList<Double>>()
        for (p in prices) {
            val cur = clusters.lastOrNull()
            if (cur != null && abs(p - cur.average()) / cur.average() <= tol) cur.add(p) else clusters.add(mutableListOf(p))
        }
        val levels = clusters.filter { it.size >= 2 }.map { Level(it.average(), it.size) }
        val supports = levels.filter { it.price < last }.sortedByDescending { it.price }.take(3)
        val resistances = levels.filter { it.price > last }.sortedBy { it.price }.take(3)
        return Pair(supports, resistances)
    }

    fun marketStructure(c: List<Candle>, sw: List<Swing>): Structure {
        val highs = sw.filter { it.isHigh }
        val lows = sw.filter { !it.isHigh }
        if (highs.size < 2 || lows.size < 2 || c.isEmpty()) {
            return Structure("Insufficient swing points", "Need at least two swing highs and two swing lows.", 0)
        }
        val h1 = highs[highs.size - 2].price
        val h2 = highs[highs.size - 1].price
        val l1 = lows[lows.size - 2].price
        val l2 = lows[lows.size - 1].price
        val hh = h2 > h1
        val hl = l2 > l1
        val lastClose = c.last().close
        var label: String
        var bias: Int
        var detail: String
        if (hh && hl) {
            label = "Uptrend structure (higher highs / higher lows)"; bias = 1
            detail = "Last swing highs ${fmt(h1)} → ${fmt(h2)}, swing lows ${fmt(l1)} → ${fmt(l2)}."
        } else if (!hh && !hl) {
            label = "Downtrend structure (lower highs / lower lows)"; bias = -1
            detail = "Last swing highs ${fmt(h1)} → ${fmt(h2)}, swing lows ${fmt(l1)} → ${fmt(l2)}."
        } else {
            label = "Mixed / ranging structure"; bias = 0
            detail = "Swing highs ${fmt(h1)} → ${fmt(h2)}, swing lows ${fmt(l1)} → ${fmt(l2)} do not agree."
        }
        if (lastClose > h2) {
            detail += " Latest close is above the last swing high (bullish break of structure)."
        } else if (lastClose < l2) {
            detail += " Latest close is below the last swing low (bearish break of structure)."
        }
        return Structure(label, detail, bias)
    }

    private fun fmt(v: Double): String = com.cryptoresearch.ai.util.Fmt.price(v)

    /** Fair Value Gaps: 3-candle imbalances. Only gaps that price has not re-entered are returned. */
    fun fvgs(c: List<Candle>, maxResults: Int = 4): List<Zone> {
        val out = ArrayList<Zone>()
        for (i in 2 until c.size) {
            val a = c[i - 2]
            val b = c[i]
            if (b.low > a.high && (b.low - a.high) / b.close >= 0.001) {
                var open = true
                for (j in i + 1 until c.size) {
                    if (c[j].low <= b.low) {
                        open = false
                        break
                    }
                }
                if (open) out.add(Zone("Bullish FVG", a.high, b.low, i, b.time, true))
            } else if (b.high < a.low && (a.low - b.high) / b.close >= 0.001) {
                var open = true
                for (j in i + 1 until c.size) {
                    if (c[j].high >= b.high) {
                        open = false
                        break
                    }
                }
                if (open) out.add(Zone("Bearish FVG", b.high, a.low, i, b.time, true))
            }
        }
        return out.takeLast(maxResults)
    }

    /**
     * Approximate order blocks: the last opposite-coloured candle before an impulsive move
     * (> 1.5 ATR within 3 candles). Zones invalidated by a later close through them are dropped.
     */
    fun orderBlocks(c: List<Candle>, atr: DoubleArray, maxResults: Int = 3): List<Zone> {
        val out = ArrayList<Zone>()
        if (c.size < 8) return out
        for (i in 1 until c.size - 3) {
            val a = atr[i]
            if (a.isNaN()) continue
            val k = c[i]
            val maxClose = maxOf(c[i + 1].close, c[i + 2].close, c[i + 3].close)
            val minClose = minOf(c[i + 1].close, c[i + 2].close, c[i + 3].close)
            if (k.close < k.open && maxClose - k.high > 1.5 * a) {
                var valid = true
                for (j in i + 4 until c.size) {
                    if (c[j].close < k.low) {
                        valid = false
                        break
                    }
                }
                if (valid) out.add(Zone("Bullish OB", k.low, k.high, i, k.time, true))
            } else if (k.close > k.open && k.low - minClose > 1.5 * a) {
                var valid = true
                for (j in i + 4 until c.size) {
                    if (c[j].close > k.high) {
                        valid = false
                        break
                    }
                }
                if (valid) out.add(Zone("Bearish OB", k.low, k.high, i, k.time, true))
            }
        }
        return out.takeLast(maxResults)
    }

    /** Structural check of the last 6 alternating pivots against basic impulse-wave rules. Always low confidence. */
    fun elliott(sw: List<Swing>): ElliottResult {
        val piv = ArrayList<Swing>()
        for (s in sw.sortedBy { it.index }) {
            val last = piv.lastOrNull()
            if (last == null || last.isHigh != s.isHigh) {
                piv.add(s)
            } else if ((s.isHigh && s.price > last.price) || (!s.isHigh && s.price < last.price)) {
                piv[piv.size - 1] = s
            }
        }
        if (piv.size < 6) {
            return ElliottResult("Insufficient data", "Fewer than 6 alternating pivots are available for a wave count.")
        }
        val p = piv.takeLast(6)
        val up = !p[0].isHigh
        val w1 = abs(p[1].price - p[0].price)
        val w3 = abs(p[3].price - p[2].price)
        val w5 = abs(p[5].price - p[4].price)
        val ok = if (up) {
            p[2].price > p[0].price && p[3].price > p[1].price && p[4].price > p[1].price && w3 >= minOf(w1, w5)
        } else {
            p[2].price < p[0].price && p[3].price < p[1].price && p[4].price < p[1].price && w3 >= minOf(w1, w5)
        }
        return if (ok) {
            ElliottResult(
                "Possible 5-wave impulse (${if (up) "up" else "down"}) — approximate, low confidence",
                "The last 6 pivots satisfy basic impulse rules (wave 2 holds, wave 3 not the shortest, wave 4 no overlap). Wave counts are subjective."
            )
        } else {
            ElliottResult("No valid impulse count found", "The last 6 pivots violate at least one basic impulse-wave rule.")
        }
    }
}
