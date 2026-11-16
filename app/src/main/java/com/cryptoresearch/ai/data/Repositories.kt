package com.cryptoresearch.ai.data

import androidx.room.withTransaction
import com.cryptoresearch.ai.data.db.AppDatabase
import com.cryptoresearch.ai.data.db.CacheEntry
import com.cryptoresearch.ai.data.db.PortfolioSnapshot
import com.cryptoresearch.ai.data.db.Position
import com.cryptoresearch.ai.data.db.ReportEntity
import com.cryptoresearch.ai.data.db.ReportRow
import com.cryptoresearch.ai.data.db.VirtualAccount
import com.cryptoresearch.ai.data.db.VirtualTx
import com.cryptoresearch.ai.data.db.WatchEntry
import com.cryptoresearch.ai.data.net.ApiException
import com.cryptoresearch.ai.data.net.MarketApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import org.json.JSONArray
import org.json.JSONObject

// ---------------------------------------------------------------------------------------------
// Cache codecs (compact, dependency-free)
// ---------------------------------------------------------------------------------------------
internal object Codec {
    fun encodeSeries(s: CandleSeries): String {
        val sb = StringBuilder(s.candles.size * 60 + 64)
        sb.append(s.source).append('|')
            .append(if (s.approximate) 1 else 0).append('|')
            .append(if (s.hasVolume) 1 else 0).append('|')
            .append(if (s.hasDelta) 1 else 0).append('\n')
        for (c in s.candles) {
            sb.append(c.time).append(',').append(c.open).append(',').append(c.high).append(',')
                .append(c.low).append(',').append(c.close).append(',').append(c.volume).append(',')
            if (c.takerBuyVolume != null) sb.append(c.takerBuyVolume)
            sb.append('\n')
        }
        return sb.toString()
    }

    fun decodeSeries(text: String): CandleSeries? {
        val lines = text.split('\n')
        if (lines.isEmpty()) return null
        val h = lines[0].split('|')
        if (h.size < 4) return null
        val out = ArrayList<Candle>(lines.size)
        for (i in 1 until lines.size) {
            val l = lines[i]
            if (l.isEmpty()) continue
            val p = l.split(',')
            if (p.size < 6) return null
            val tb = if (p.size > 6 && p[6].isNotEmpty()) p[6].toDoubleOrNull() else null
            out.add(
                Candle(
                    p[0].toLong(), p[1].toDouble(), p[2].toDouble(), p[3].toDouble(),
                    p[4].toDouble(), p[5].toDouble(), tb
                )
            )
        }
        return CandleSeries(out, h[0], h[1] == "1", h[2] == "1", h[3] == "1")
    }

    fun encodeNews(list: List<NewsItem>): String {
        val a = JSONArray()
        for (n in list) {
            val o = JSONObject()
            o.put("id", n.id)
            o.put("title", n.title)
            o.put("summary", n.summary)
            o.put("url", n.url)
            o.put("source", n.source)
            o.put("at", n.publishedAt)
            o.put("cats", JSONArray(n.categories))
            o.put("sent", n.sentiment.name)
            a.put(o)
        }
        return a.toString()
    }

    fun decodeNews(text: String): List<NewsItem> {
        val a = JSONArray(text)
        val out = ArrayList<NewsItem>(a.length())
        for (i in 0 until a.length()) {
            val o = a.getJSONObject(i)
            val cats = ArrayList<String>()
            val ca = o.optJSONArray("cats")
            if (ca != null) for (j in 0 until ca.length()) cats.add(ca.optString(j))
            out.add(
                NewsItem(
                    o.optString("id"), o.optString("title"), o.optString("summary"), o.optString("url"),
                    o.optString("source"), o.optLong("at"), cats,
                    Sentiment.values().firstOrNull { it.name == o.optString("sent") } ?: Sentiment.NEUTRAL
                )
            )
        }
        return out
    }

    private fun JSONObject.nullableDouble(k: String): Double? =
        if (!has(k) || isNull(k)) null else optDouble(k)

    fun encodeGlobal(g: GlobalStats): String {
        val o = JSONObject()
        o.put("cap", g.totalMarketCap ?: JSONObject.NULL)
        o.put("vol", g.totalVolume ?: JSONObject.NULL)
        o.put("btc", g.btcDominance ?: JSONObject.NULL)
        o.put("eth", g.ethDominance ?: JSONObject.NULL)
        o.put("chg", g.marketCapChange24h ?: JSONObject.NULL)
        return o.toString()
    }

    fun decodeGlobal(t: String): GlobalStats {
        val o = JSONObject(t)
        return GlobalStats(
            o.nullableDouble("cap"), o.nullableDouble("vol"), o.nullableDouble("btc"),
            o.nullableDouble("eth"), o.nullableDouble("chg")
        )
    }

    fun encodeFng(f: FearGreed): String = JSONObject().put("v", f.value).put("l", f.label).toString()

    fun decodeFng(t: String): FearGreed {
        val o = JSONObject(t)
        return FearGreed(o.getInt("v"), o.optString("l"))
    }
}

// ---------------------------------------------------------------------------------------------
// Market repository: coins, candles, news, global stats, watchlist
// ---------------------------------------------------------------------------------------------
class MarketRepository(
    private val db: AppDatabase,
    private val api: MarketApi,
    private val settings: SettingsStore
) {
    val coins: Flow<List<Coin>> = db.coinDao().observeAll()
    val watchlist: Flow<List<WatchEntry>> = db.watchDao().observe()

    suspend fun coinById(id: String): Coin? = db.coinDao().byId(id)
    suspend fun allCoins(): List<Coin> = db.coinDao().all()

    /** Refresh the Top 50 list. Returns the provider name on success. */
    suspend fun refreshCoins(): Result<String> {
        var firstError: Throwable? = null
        var list: List<Coin>? = null
        try {
            list = api.geckoMarkets(50)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            firstError = e
        }
        if (list == null && settings.value.allowFallback) {
            try {
                list = api.coinloreMarkets(50)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // keep the first (primary) error for the message
            }
        }
        if (list == null) return Result.failure(firstError ?: ApiException(ApiException.Kind.UNAVAILABLE, "No data"))
        return try {
            db.coinDao().replaceAll(list)
            Result.success(list.first().source)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(Exception("Database error: " + (e.message ?: "unknown")))
        }
    }

    suspend fun toggleWatch(coin: Coin) {
        if (db.watchDao().count(coin.id) > 0) {
            db.watchDao().delete(coin.id)
        } else {
            db.watchDao().insert(WatchEntry(coin.id, coin.symbol, coin.name, System.currentTimeMillis()))
        }
    }

    suspend fun removeWatch(coinId: String) = db.watchDao().delete(coinId)

    private suspend fun <T> cachedFetch(
        key: String,
        ttlMs: Long,
        force: Boolean,
        enc: (T) -> String,
        dec: (String) -> T?,
        fetch: suspend () -> T
    ): Result<Fetched<T>> {
        val now = System.currentTimeMillis()
        val hit = try {
            db.cacheDao().get(key)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
        val decoded: T? = if (hit != null) {
            try {
                dec(hit.value)
            } catch (e: Exception) {
                null
            }
        } else null

        if (!force && hit != null && decoded != null && now - hit.fetchedAt < ttlMs) {
            return Result.success(Fetched(decoded, hit.fetchedAt, fromCache = true, stale = false))
        }
        return try {
            val fresh = fetch()
            try {
                db.cacheDao().put(CacheEntry(key, enc(fresh), now))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // cache write failure is non-fatal
            }
            Result.success(Fetched(fresh, now, fromCache = false, stale = false))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (hit != null && decoded != null) {
                Result.success(Fetched(decoded, hit.fetchedAt, fromCache = true, stale = true, note = com.cryptoresearch.ai.util.Fmt.friendlyError(e)))
            } else {
                Result.failure(e)
            }
        }
    }

    private fun candleTtl(tf: Timeframe): Long = when (tf) {
        Timeframe.M15 -> 2 * 60_000L
        Timeframe.H1 -> 5 * 60_000L
        Timeframe.H4 -> 15 * 60_000L
        Timeframe.D1 -> 30 * 60_000L
    }

    /**
     * Historical OHLCV candles (closed candles only). Primary source: Binance public market data.
     * Fallback: approximate candles derived from the CoinGecko price series (no per-candle volume).
     */
    suspend fun candles(coin: Coin, tf: Timeframe, count: Int = 500, force: Boolean = false): Result<Fetched<CandleSeries>> {
        val key = "candles:${coin.id}:${tf.label}:$count"
        return cachedFetch(
            key, candleTtl(tf), force,
            enc = { s: CandleSeries -> Codec.encodeSeries(s) },
            dec = { t: String -> Codec.decodeSeries(t) },
            fetch = {
                val now = System.currentTimeMillis()
                var series: CandleSeries? = null
                var primaryError: Exception? = null
                try {
                    val raw = api.binanceKlines(coin.symbol, tf, count).filter { it.time + tf.millis <= now }
                    if (raw.size >= 30) {
                        series = CandleSeries(raw, "Binance (spot ${coin.symbol}/USDT)", approximate = false, hasVolume = true, hasDelta = raw.all { it.takerBuyVolume != null })
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    primaryError = e
                }
                if (series == null && settings.value.allowFallback) {
                    try {
                        val raw = api.geckoDerivedCandles(coin.id, tf).filter { it.time + tf.millis <= now }
                        if (raw.size >= 30) {
                            series = CandleSeries(raw, "CoinGecko price series (derived candles)", approximate = true, hasVolume = false, hasDelta = false)
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        if (primaryError == null) primaryError = e
                    }
                }
                series ?: throw (primaryError ?: ApiException(ApiException.Kind.BAD_RESPONSE, "Not enough historical data for ${coin.symbol} (${tf.label})"))
            }
        )
    }

    suspend fun news(symbol: String?, force: Boolean = false): Result<Fetched<List<NewsItem>>> =
        cachedFetch(
            "news:${symbol?.uppercase() ?: "ALL"}", 10 * 60_000L, force,
            enc = { l: List<NewsItem> -> Codec.encodeNews(l) },
            dec = { t: String -> Codec.decodeNews(t) },
            fetch = { api.news(symbol) }
        )

    suspend fun global(force: Boolean = false): Result<Fetched<GlobalStats>> =
        cachedFetch(
            "global", 10 * 60_000L, force,
            enc = { g: GlobalStats -> Codec.encodeGlobal(g) },
            dec = { t: String -> Codec.decodeGlobal(t) },
            fetch = { api.geckoGlobal() }
        )

    suspend fun fearGreed(force: Boolean = false): Result<Fetched<FearGreed>> =
        cachedFetch(
            "fng", 30 * 60_000L, force,
            enc = { f: FearGreed -> Codec.encodeFng(f) },
            dec = { t: String -> Codec.decodeFng(t) },
            fetch = { api.fearGreed() }
        )

    suspend fun clearCache() = db.cacheDao().clear()
    suspend fun cacheCount(): Int = db.cacheDao().count()
    suspend fun cacheSizeChars(): Long = db.cacheDao().sizeChars()
}

// ---------------------------------------------------------------------------------------------
// Research reports
// ---------------------------------------------------------------------------------------------
class ReportRepository(private val db: AppDatabase) {
    val rows: Flow<List<ReportRow>> = db.reportDao().observeRows()

    suspend fun save(r: ResearchReport): Long =
        db.reportDao().insert(
            ReportEntity(
                coinId = r.coinId, symbol = r.symbol, name = r.name, createdAt = r.createdAt,
                classification = r.classification.name, strength = r.strength.name,
                confidencePct = r.confidencePct, volatility = r.volatility,
                json = ReportJson.encode(r)
            )
        )

    suspend fun get(id: Long): ResearchReport? {
        val e = db.reportDao().byId(id) ?: return null
        return try {
            ReportJson.decode(e.json)
        } catch (ex: Exception) {
            null
        }
    }

    suspend fun delete(id: Long) = db.reportDao().delete(id)
    suspend fun deleteAll() = db.reportDao().deleteAll()
    suspend fun count(): Int = db.reportDao().count()
}

// ---------------------------------------------------------------------------------------------
// Virtual (simulated) portfolio. No real money, no exchange connection.
// ---------------------------------------------------------------------------------------------
class PortfolioRepository(private val db: AppDatabase) {
    private val dao get() = db.portfolioDao()

    val positions: Flow<List<Position>> = db.portfolioDao().observePositions()
    val transactions: Flow<List<VirtualTx>> = db.portfolioDao().observeTx()
    val snapshots: Flow<List<PortfolioSnapshot>> = db.portfolioDao().observeSnapshots()
    val account: Flow<VirtualAccount?> = db.portfolioDao().observeAccount()

    suspend fun ensureAccount() {
        if (dao.account() == null) dao.upsertAccount(VirtualAccount(1, 10_000.0, 10_000.0))
    }

    /** Adds a hypothetical holding worth [usd] at the coin's current price. */
    suspend fun addHolding(coin: Coin, usd: Double): Result<String> {
        if (usd.isNaN() || usd <= 0.0) return Result.failure(Exception("Enter an amount greater than 0."))
        if (coin.price <= 0.0) return Result.failure(Exception("Price unavailable for ${coin.symbol}."))
        return try {
            db.withTransaction {
                ensureAccount()
                val acc = dao.account()!!
                if (usd > acc.cash + 1e-9) throw Exception("Not enough virtual cash (available ${String.format("%.2f", acc.cash)}).")
                val qty = usd / coin.price
                val existing = dao.position(coin.id)
                val newQty = (existing?.quantity ?: 0.0) + qty
                val newCost = ((existing?.quantity ?: 0.0) * (existing?.avgCost ?: 0.0) + usd) / newQty
                dao.upsertPosition(Position(coin.id, coin.symbol, coin.name, newQty, newCost, coin.price, System.currentTimeMillis()))
                dao.upsertAccount(acc.copy(cash = acc.cash - usd))
                dao.insertTx(VirtualTx(time = System.currentTimeMillis(), coinId = coin.id, symbol = coin.symbol, action = "ADD", quantity = qty, price = coin.price))
            }
            Result.success("Added hypothetical ${coin.symbol} holding.")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Removes [fraction] (0..1] of a hypothetical holding at [price]. */
    suspend fun reduceHolding(coinId: String, fraction: Double, price: Double): Result<String> {
        if (price <= 0.0) return Result.failure(Exception("Price unavailable."))
        return try {
            db.withTransaction {
                val pos = dao.position(coinId) ?: throw Exception("Holding not found.")
                val f = fraction.coerceIn(0.0, 1.0)
                val qty = pos.quantity * f
                val proceeds = qty * price
                val acc = dao.account() ?: throw Exception("Account missing.")
                dao.upsertAccount(acc.copy(cash = acc.cash + proceeds))
                if (f >= 0.9999) dao.deletePosition(coinId) else dao.upsertPosition(pos.copy(quantity = pos.quantity - qty, lastPrice = price, lastPriceAt = System.currentTimeMillis()))
                dao.insertTx(VirtualTx(time = System.currentTimeMillis(), coinId = coinId, symbol = pos.symbol, action = "REMOVE", quantity = qty, price = price))
            }
            Result.success("Holding updated.")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updatePrices(coins: List<Coin>) {
        val map = coins.associateBy { it.id }
        for (p in dao.positions()) {
            val c = map[p.coinId] ?: continue
            if (c.price > 0.0) dao.upsertPosition(p.copy(lastPrice = c.price, lastPriceAt = c.fetchedAt))
        }
    }

    suspend fun recordSnapshot(value: Double) {
        if (value.isNaN() || value <= 0.0) return
        val last = dao.lastSnapshot()
        val now = System.currentTimeMillis()
        if (last == null || now - last.time >= 30 * 60_000L) dao.insertSnapshot(PortfolioSnapshot(now, value))
    }

    suspend fun reset(startBalance: Double) {
        db.withTransaction {
            dao.deleteAllPositions()
            dao.deleteAllTx()
            dao.deleteAllSnapshots()
            dao.upsertAccount(VirtualAccount(1, startBalance, startBalance))
        }
    }
}
