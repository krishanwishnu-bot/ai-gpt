package com.cryptoresearch.ai.analysis

import com.cryptoresearch.ai.data.CandleSeries
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

data class ProfileBin(val low: Double, val high: Double, val volume: Double)

data class VolumeStats(
    val available: Boolean,
    val note: String?,
    val lastVsAvg: Double?,
    val spikes: Int,
    val spikeWindow: Int,
    val trend: String?,
    val lastCandleUp: Boolean?,
    val poc: Double?,
    val vaLow: Double?,
    val vaHigh: Double?,
    val profile: List<ProfileBin>,
    val deltaAvailable: Boolean,
    val deltaPct20: Double?,
    val deltaNote: String?
)

object VolumeAnalysis {
    private const val BINS = 24

    fun analyze(series: CandleSeries): VolumeStats {
        val c = series.candles
        if (!series.hasVolume) {
            return VolumeStats(
                false, "Per-candle volume is not provided by ${series.source}; volume analysis unavailable.",
                null, 0, 0, null, null, null, null, null, emptyList(), false, null,
                "Volume delta unavailable (no volume data)."
            )
        }
        if (c.size < 30) {
            return VolumeStats(
                false, "Insufficient data (${c.size} candles; at least 30 needed).",
                null, 0, 0, null, null, null, null, null, emptyList(), false, null,
                "Volume delta unavailable (insufficient data)."
            )
        }
        val vol = DoubleArray(c.size) { c[it].volume }

        // Last candle vs. average of the previous 20
        val prev20 = vol.copyOfRange(vol.size - 21, vol.size - 1).average()
        val lastVsAvg = if (prev20 > 0) vol[vol.size - 1] / prev20 else null

        // Spikes in the last 50 candles: volume > mean + 2*std of the preceding 20 candles
        var spikes = 0
        val window = min(50, vol.size - 20)
        for (i in vol.size - window until vol.size) {
            val seg = vol.copyOfRange(i - 20, i)
            val m = seg.average()
            var s = 0.0
            for (x in seg) s += (x - m) * (x - m)
            val sd = sqrt(s / seg.size)
            if (sd > 0 && vol[i] > m + 2 * sd) spikes++
        }

        // Trend: average of last 10 vs. the 20 before them
        val recent = vol.copyOfRange(vol.size - 10, vol.size).average()
        val before = vol.copyOfRange(vol.size - 30, vol.size - 10).average()
        val trend = when {
            before <= 0 -> "Flat"
            recent / before > 1.15 -> "Rising"
            recent / before < 0.85 -> "Falling"
            else -> "Flat"
        }

        // Volume profile over the last 100 candles (volume allocated by typical price - approximation)
        val tail = c.takeLast(100)
        val lo = tail.minOf { it.low }
        val hi = tail.maxOf { it.high }
        val profile = ArrayList<ProfileBin>()
        var poc: Double? = null
        var vaLow: Double? = null
        var vaHigh: Double? = null
        if (hi > lo) {
            val step = (hi - lo) / BINS
            val bins = DoubleArray(BINS)
            for (k in tail) {
                val tp = (k.high + k.low + k.close) / 3.0
                val idx = min(BINS - 1, max(0, ((tp - lo) / step).toInt()))
                bins[idx] += k.volume
            }
            for (b in 0 until BINS) profile.add(ProfileBin(lo + b * step, lo + (b + 1) * step, bins[b]))
            val total = bins.sum()
            val pocIdx = bins.indices.maxByOrNull { bins[it] } ?: 0
            poc = lo + (pocIdx + 0.5) * step
            if (total > 0) {
                val order = bins.indices.sortedByDescending { bins[it] }
                var acc = 0.0
                var minB = BINS
                var maxB = -1
                for (b in order) {
                    acc += bins[b]
                    minB = min(minB, b)
                    maxB = max(maxB, b)
                    if (acc >= 0.7 * total) break
                }
                vaLow = lo + minB * step
                vaHigh = lo + (maxB + 1) * step
            }
        }

        // Volume delta from taker-buy volume (only when the exchange provides it for every candle)
        var deltaPct: Double? = null
        var deltaNote: String? = null
        if (series.hasDelta) {
            val last20 = c.takeLast(20)
            var delta = 0.0
            var tot = 0.0
            for (k in last20) {
                val tb = k.takerBuyVolume ?: 0.0
                delta += 2 * tb - k.volume
                tot += k.volume
            }
            if (tot > 0) deltaPct = delta / tot * 100.0
            deltaNote = "Delta = taker-buy volume minus taker-sell volume over the last 20 candles (spot, single exchange)."
        } else {
            deltaNote = "Volume delta unavailable: taker-buy data not provided by the data source."
        }

        val lastC = c.last()
        return VolumeStats(
            true, null, lastVsAvg, spikes, window, trend, lastC.close >= lastC.open,
            poc, vaLow, vaHigh, profile, series.hasDelta, deltaPct, deltaNote
        )
    }
}
