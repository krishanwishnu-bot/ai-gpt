package com.cryptoresearch.ai.util

import com.cryptoresearch.ai.data.net.ApiException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

object Fmt {
    fun price(v: Double?): String {
        if (v == null || v.isNaN() || v.isInfinite()) return "—"
        val a = abs(v)
        return when {
            a >= 1.0 -> String.format(Locale.US, "\$%,.2f", v)
            a >= 0.01 -> String.format(Locale.US, "\$%.4f", v)
            else -> String.format(Locale.US, "\$%.8f", v)
        }
    }

    fun num(v: Double?, decimals: Int = 2): String {
        if (v == null || v.isNaN() || v.isInfinite()) return "—"
        return String.format(Locale.US, "%." + decimals + "f", v)
    }

    fun compact(v: Double?): String {
        if (v == null || v.isNaN() || v.isInfinite()) return "—"
        val a = abs(v)
        return when {
            a >= 1e12 -> String.format(Locale.US, "\$%.2fT", v / 1e12)
            a >= 1e9 -> String.format(Locale.US, "\$%.2fB", v / 1e9)
            a >= 1e6 -> String.format(Locale.US, "\$%.2fM", v / 1e6)
            a >= 1e3 -> String.format(Locale.US, "\$%.2fK", v / 1e3)
            else -> String.format(Locale.US, "\$%.2f", v)
        }
    }

    fun pct(v: Double?, decimals: Int = 2): String {
        if (v == null || v.isNaN() || v.isInfinite()) return "—"
        return String.format(Locale.US, "%+." + decimals + "f%%", v)
    }

    fun plainPct(v: Double?, decimals: Int = 1): String {
        if (v == null || v.isNaN() || v.isInfinite()) return "—"
        return String.format(Locale.US, "%." + decimals + "f%%", v)
    }

    fun dateTime(ms: Long): String =
        SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(Date(ms))

    fun shortDateTime(ms: Long): String =
        SimpleDateFormat("dd MMM HH:mm", Locale.getDefault()).format(Date(ms))

    fun date(ms: Long): String =
        SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(ms))

    fun ago(ms: Long, now: Long = System.currentTimeMillis()): String {
        val d = now - ms
        return when {
            d < 60_000L -> "just now"
            d < 3_600_000L -> "${d / 60_000L}m ago"
            d < 86_400_000L -> "${d / 3_600_000L}h ago"
            else -> "${d / 86_400_000L}d ago"
        }
    }

    fun friendlyError(e: Throwable?): String {
        if (e == null) return "Unknown error."
        if (e is ApiException) {
            return when (e.kind) {
                ApiException.Kind.NO_NETWORK -> "No internet connection. Check your network and retry."
                ApiException.Kind.RATE_LIMIT -> "API rate limit reached. Wait a minute and retry, or add a CoinGecko API key in Settings."
                ApiException.Kind.UNAVAILABLE -> "The data provider is unavailable right now. Retry shortly."
                ApiException.Kind.BAD_RESPONSE -> "The data provider returned an invalid response."
                ApiException.Kind.HTTP -> "Data request failed (" + (e.message ?: "HTTP error") + ")."
            }
        }
        return e.message ?: e.javaClass.simpleName
    }
}
