package com.cryptoresearch.ai.analysis

import kotlin.math.ln
import kotlin.math.max
import kotlin.math.sqrt

/** Classic indicator maths. All functions are causal: value[i] only depends on data[0..i]. */
object Ind {
    private val NAN = Double.NaN

    fun sma(v: DoubleArray, p: Int): DoubleArray {
        val out = DoubleArray(v.size) { NAN }
        if (p <= 0 || v.size < p) return out
        var sum = 0.0
        for (i in v.indices) {
            sum += v[i]
            if (i >= p) sum -= v[i - p]
            if (i >= p - 1) out[i] = sum / p
        }
        return out
    }

    /** EMA seeded with the SMA of the first [p] valid values; leading NaNs are skipped. */
    fun ema(v: DoubleArray, p: Int): DoubleArray {
        val out = DoubleArray(v.size) { NAN }
        if (p <= 0) return out
        val start = v.indexOfFirst { !it.isNaN() }
        if (start < 0 || v.size - start < p) return out
        val k = 2.0 / (p + 1)
        var sum = 0.0
        for (i in start until start + p) sum += v[i]
        var prev = sum / p
        out[start + p - 1] = prev
        for (i in start + p until v.size) {
            prev = v[i] * k + prev * (1 - k)
            out[i] = prev
        }
        return out
    }

    /** Wilder's RSI. */
    fun rsi(c: DoubleArray, p: Int = 14): DoubleArray {
        val out = DoubleArray(c.size) { NAN }
        if (c.size <= p) return out
        var gain = 0.0
        var loss = 0.0
        for (i in 1..p) {
            val d = c[i] - c[i - 1]
            if (d >= 0) gain += d else loss -= d
        }
        var ag = gain / p
        var al = loss / p
        out[p] = if (al == 0.0) 100.0 else 100.0 - 100.0 / (1.0 + ag / al)
        for (i in p + 1 until c.size) {
            val d = c[i] - c[i - 1]
            val g = if (d > 0) d else 0.0
            val l = if (d < 0) -d else 0.0
            ag = (ag * (p - 1) + g) / p
            al = (al * (p - 1) + l) / p
            out[i] = if (al == 0.0) 100.0 else 100.0 - 100.0 / (1.0 + ag / al)
        }
        return out
    }

    class Macd(val line: DoubleArray, val signal: DoubleArray, val hist: DoubleArray)

    fun macd(c: DoubleArray, fast: Int = 12, slow: Int = 26, sig: Int = 9): Macd {
        val f = ema(c, fast)
        val s = ema(c, slow)
        val line = DoubleArray(c.size) { i -> if (f[i].isNaN() || s[i].isNaN()) NAN else f[i] - s[i] }
        val signal = ema(line, sig)
        val hist = DoubleArray(c.size) { i -> if (line[i].isNaN() || signal[i].isNaN()) NAN else line[i] - signal[i] }
        return Macd(line, signal, hist)
    }

    class Bands(val mid: DoubleArray, val upper: DoubleArray, val lower: DoubleArray)

    fun bollinger(c: DoubleArray, p: Int = 20, k: Double = 2.0): Bands {
        val mid = sma(c, p)
        val up = DoubleArray(c.size) { NAN }
        val lo = DoubleArray(c.size) { NAN }
        for (i in p - 1 until c.size) {
            val m = mid[i]
            if (m.isNaN()) continue
            var s = 0.0
            for (j in i - p + 1..i) {
                val d = c[j] - m
                s += d * d
            }
            val sd = sqrt(s / p)
            up[i] = m + k * sd
            lo[i] = m - k * sd
        }
        return Bands(mid, up, lo)
    }

    /** Wilder's Average True Range. */
    fun atr(h: DoubleArray, l: DoubleArray, c: DoubleArray, p: Int = 14): DoubleArray {
        require(h.size == l.size && l.size == c.size) { "High, low and close arrays must have equal lengths" }
        val n = c.size
        val out = DoubleArray(n) { NAN }
        if (p <= 0 || n < p) return out
        val tr = DoubleArray(n)
        tr[0] = h[0] - l[0]
        for (i in 1 until n) {
            tr[i] = max(h[i] - l[i], max(kotlin.math.abs(h[i] - c[i - 1]), kotlin.math.abs(l[i] - c[i - 1])))
        }
        var sum = 0.0
        for (i in 0 until p) sum += tr[i]
        var prev = sum / p
        out[p - 1] = prev
        for (i in p until n) {
            prev = (prev * (p - 1) + tr[i]) / p
            out[i] = prev
        }
        return out
    }

    /** Standard deviation of log returns over the last [lookback] returns (per-candle). */
    fun logReturnStd(c: DoubleArray, lookback: Int): Double {
        if (c.size < 3) return NAN
        val start = max(1, c.size - lookback)
        val r = ArrayList<Double>()
        for (i in start until c.size) {
            if (c[i] > 0 && c[i - 1] > 0) r.add(ln(c[i] / c[i - 1]))
        }
        if (r.size < 2) return NAN
        val mean = r.average()
        var s = 0.0
        for (x in r) s += (x - mean) * (x - mean)
        return sqrt(s / (r.size - 1))
    }

    fun last(a: DoubleArray): Double? {
        if (a.isEmpty()) return null
        val v = a[a.size - 1]
        return if (v.isNaN()) null else v
    }

    fun at(a: DoubleArray, i: Int): Double? {
        if (i < 0 || i >= a.size) return null
        val v = a[i]
        return if (v.isNaN()) null else v
    }
}
