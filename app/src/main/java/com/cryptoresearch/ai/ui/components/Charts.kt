package com.cryptoresearch.ai.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.cryptoresearch.ai.analysis.ProfileBin
import com.cryptoresearch.ai.data.Candle
import com.cryptoresearch.ai.ui.theme.MarketColors

/** Lightweight line chart drawn on a Canvas (no charting library needed). */
@Composable
fun LineChart(
    values: List<Double>,
    modifier: Modifier = Modifier,
    color: Color = MarketColors.Accent,
    height: Dp = 140.dp,
    fill: Boolean = true,
    baseline: Double? = null
) {
    val grid = MaterialTheme.colorScheme.outline
    Canvas(modifier.fillMaxWidth().height(height)) {
        if (values.size < 2) return@Canvas
        var lo = values.min()
        var hi = values.max()
        if (baseline != null) {
            lo = minOf(lo, baseline)
            hi = maxOf(hi, baseline)
        }
        if (hi - lo < 1e-12) {
            hi += 1.0
            lo -= 1.0
        }
        val pad = (hi - lo) * 0.08
        lo -= pad
        hi += pad
        val w = size.width
        val h = size.height
        fun x(i: Int) = w * i / (values.size - 1)
        fun y(v: Double) = (h * (1.0 - (v - lo) / (hi - lo))).toFloat()
        for (g in 0..3) {
            val gy = h * g / 3f
            drawLine(grid.copy(alpha = 0.5f), Offset(0f, gy), Offset(w, gy), 1f)
        }
        if (baseline != null) {
            drawLine(grid, Offset(0f, y(baseline)), Offset(w, y(baseline)), 1.5f)
        }
        val path = Path()
        values.forEachIndexed { i, v ->
            if (i == 0) path.moveTo(x(i), y(v)) else path.lineTo(x(i), y(v))
        }
        if (fill) {
            val fillPath = Path()
            fillPath.addPath(path)
            fillPath.lineTo(w, h)
            fillPath.lineTo(0f, h)
            fillPath.close()
            drawPath(fillPath, color.copy(alpha = 0.12f))
        }
        drawPath(path, color, style = Stroke(width = 3f))
    }
}

/** Candlestick chart for the most recent candles, with optional line overlays (e.g. EMAs) and a volume strip. */
@Composable
fun CandleChart(
    candles: List<Candle>,
    overlays: List<Pair<DoubleArray, Color>>,
    showVolume: Boolean,
    modifier: Modifier = Modifier,
    height: Dp = 260.dp,
    maxCandles: Int = 80
) {
    val grid = MaterialTheme.colorScheme.outline
    Canvas(modifier.fillMaxWidth().height(height)) {
        val n = candles.size
        if (n < 2) return@Canvas
        val from = maxOf(0, n - maxCandles)
        val visible = candles.subList(from, n)
        var lo = visible.minOf { it.low }
        var hi = visible.maxOf { it.high }
        if (hi - lo < 1e-12) {
            hi += 1.0
            lo -= 1.0
        }
        val pad = (hi - lo) * 0.05
        lo -= pad
        hi += pad
        val volH = if (showVolume) size.height * 0.18f else 0f
        val priceH = size.height - volH - 4f
        val slot = size.width / visible.size
        val bodyW = (slot * 0.62f).coerceAtLeast(1.5f)
        fun y(v: Double) = (priceH * (1.0 - (v - lo) / (hi - lo))).toFloat()
        for (g in 0..4) {
            val gy = priceH * g / 4f
            drawLine(grid.copy(alpha = 0.45f), Offset(0f, gy), Offset(size.width, gy), 1f)
        }
        val maxVol = if (showVolume) visible.maxOf { it.volume } else 0.0
        visible.forEachIndexed { i, k ->
            val cx = slot * i + slot / 2f
            val up = k.close >= k.open
            val col = if (up) MarketColors.Up else MarketColors.Down
            drawLine(col, Offset(cx, y(k.high)), Offset(cx, y(k.low)), 1.5f)
            val top = y(maxOf(k.open, k.close))
            val bot = y(minOf(k.open, k.close))
            drawRect(col, Offset(cx - bodyW / 2f, top), Size(bodyW, maxOf(1.5f, bot - top)))
            if (showVolume && maxVol > 0) {
                val vh = (volH * (k.volume / maxVol)).toFloat()
                drawRect(col.copy(alpha = 0.45f), Offset(cx - bodyW / 2f, size.height - vh), Size(bodyW, vh))
            }
        }
        for ((arr, color) in overlays) {
            val path = Path()
            var started = false
            for (i in from until n) {
                if (i >= arr.size) break
                val v = arr[i]
                if (v.isNaN()) continue
                val px = slot * (i - from) + slot / 2f
                if (!started) {
                    path.moveTo(px, y(v))
                    started = true
                } else {
                    path.lineTo(px, y(v))
                }
            }
            if (started) drawPath(path, color, style = Stroke(width = 2.5f))
        }
    }
}

/** Horizontal volume-by-price bars (volume profile). */
@Composable
fun ProfileChart(bins: List<ProfileBin>, poc: Double?, modifier: Modifier = Modifier, height: Dp = 160.dp) {
    Canvas(modifier.fillMaxWidth().height(height)) {
        if (bins.isEmpty()) return@Canvas
        val maxV = bins.maxOf { it.volume }
        if (maxV <= 0) return@Canvas
        val rowH = size.height / bins.size
        bins.asReversed().forEachIndexed { i, b ->
            val frac = (b.volume / maxV).toFloat()
            val isPoc = poc != null && poc >= b.low && poc <= b.high
            val col = if (isPoc) MarketColors.Warn else MarketColors.Accent.copy(alpha = 0.6f)
            drawRect(col, Offset(0f, i * rowH + 1f), Size(size.width * frac, maxOf(1f, rowH - 2f)))
        }
    }
}
