package com.cryptoresearch.ai.notify

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.cryptoresearch.ai.CryptoApp
import com.cryptoresearch.ai.analysis.SentimentLexicon
import com.cryptoresearch.ai.data.AppSettings
import com.cryptoresearch.ai.util.Fmt
import java.util.concurrent.TimeUnit
import kotlin.math.abs

/**
 * Lightweight periodic job. It exists only to deliver the optional notifications the user enabled
 * and is scheduled only when at least one such notification is on (see [Scheduler]).
 */
class RefreshWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as CryptoApp
        val s = app.settings.value
        val prefs = applicationContext.getSharedPreferences("worker_state", Context.MODE_PRIVATE)

        val r = app.market.refreshCoins()
        if (r.isFailure) {
            if (s.notifyErrors) {
                val last = prefs.getLong("lastErrorAt", 0L)
                val now = System.currentTimeMillis()
                if (now - last > 6 * 3_600_000L) {
                    prefs.edit().putLong("lastErrorAt", now).apply()
                    Notifier.show(applicationContext, Notifier.CH_ERRORS, 3001, "Market data update failed", Fmt.friendlyError(r.exceptionOrNull()))
                }
            }
            return Result.success()
        }

        val coins = app.market.allCoins()
        try {
            app.portfolio.updatePrices(coins)
        } catch (e: Exception) {
            // non-fatal
        }

        if (s.notifyMoves) {
            val watchIds = app.db.watchDao().all().map { it.coinId }.toSet()
            val pool = if (watchIds.isNotEmpty()) coins.filter { it.id in watchIds } else coins.take(10)
            val dayKey = System.currentTimeMillis() / 86_400_000L
            val edit = prefs.edit()
            var count = 0
            for (c in pool) {
                val ch = c.change24h ?: continue
                if (abs(ch) >= s.moveThresholdPct && count < 3) {
                    val key = "move:${c.id}"
                    if (prefs.getLong(key, -1L) != dayKey) {
                        edit.putLong(key, dayKey)
                        Notifier.show(
                            applicationContext, Notifier.CH_MOVES, 4000 + c.rank,
                            "${c.symbol} moved ${Fmt.pct(ch, 1)} in 24h",
                            "${c.name} is at ${Fmt.price(c.price)} (source: ${c.source}). Informational only."
                        )
                        count++
                    }
                }
            }
            edit.apply()
        }

        if (s.notifyNews) {
            val n: List<com.cryptoresearch.ai.data.NewsItem> = app.market.news(null, force = true).getOrNull()?.data ?: emptyList()
            val top = n.firstOrNull()
            if (top != null && prefs.getString("lastNewsId", null) != top.id) {
                prefs.edit().putString("lastNewsId", top.id).apply()
                val important = n.firstOrNull { SentimentLexicon.isImportant(it.title) }
                if (important != null) {
                    Notifier.show(applicationContext, Notifier.CH_NEWS, 5001, "Crypto news: ${important.source}", important.title)
                }
            }
        }
        return Result.success()
    }
}

object Scheduler {
    private const val WORK_NAME = "periodic_refresh"

    fun needed(s: AppSettings): Boolean =
        s.refreshMinutes > 0 && (s.notifyMoves || s.notifyNews || s.notifyErrors)

    fun apply(ctx: Context, s: AppSettings) {
        val wm = WorkManager.getInstance(ctx)
        if (!needed(s)) {
            wm.cancelUniqueWork(WORK_NAME)
            return
        }
        val minutes = maxOf(15, s.refreshMinutes).toLong()
        val req = PeriodicWorkRequestBuilder<RefreshWorker>(minutes, TimeUnit.MINUTES)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .setRequiresBatteryNotLow(true)
                    .build()
            )
            .build()
        wm.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, req)
    }
}
