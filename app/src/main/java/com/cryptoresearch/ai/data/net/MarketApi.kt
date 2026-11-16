package com.cryptoresearch.ai.data.net

import com.cryptoresearch.ai.analysis.SentimentLexicon
import com.cryptoresearch.ai.data.Candle
import com.cryptoresearch.ai.data.Coin
import com.cryptoresearch.ai.data.FearGreed
import com.cryptoresearch.ai.data.GlobalStats
import com.cryptoresearch.ai.data.NewsItem
import com.cryptoresearch.ai.data.Timeframe
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

private fun JSONObject.dbl(key: String): Double? {
    if (!has(key) || isNull(key)) return null
    val d = optDouble(key, Double.NaN)
    return if (d.isNaN() || d.isInfinite()) null else d
}

private fun parseJsonArray(text: String, provider: String): JSONArray =
    try {
        JSONArray(text)
    } catch (e: JSONException) {
        throw ApiException(ApiException.Kind.BAD_RESPONSE, "Invalid JSON from $provider")
    }

private fun parseJsonObject(text: String, provider: String): JSONObject =
    try {
        JSONObject(text)
    } catch (e: JSONException) {
        throw ApiException(ApiException.Kind.BAD_RESPONSE, "Invalid JSON from $provider")
    }

/**
 * All network data sources used by the app (HTTPS only, no private keys in source).
 *  - CoinGecko public API: market data (primary), global stats, fallback OHLC
 *  - CoinLore public API: market data fallback (no key required)
 *  - Binance public market-data endpoint: OHLCV candles (incl. taker-buy volume)
 *  - CryptoCompare public news API: crypto news
 *  - alternative.me: Fear & Greed Index
 */
class MarketApi(private val apiKeyProvider: () -> String?) {

    private val geckoBase = "https://api.coingecko.com/api/v3"
    private val binanceBase = "https://data-api.binance.vision/api/v3"

    private fun geckoHeaders(): Map<String, String> {
        val k = apiKeyProvider()
        return if (!k.isNullOrBlank()) mapOf("x-cg-demo-api-key" to k.trim()) else emptyMap()
    }

    suspend fun geckoMarkets(limit: Int = 50): List<Coin> {
        val url = "$geckoBase/coins/markets?vs_currency=usd&order=market_cap_desc&per_page=$limit&page=1&sparkline=false&price_change_percentage=24h,7d"
        val text = Http.getString(url, geckoHeaders(), "coingecko", 1200L)
        val arr = parseJsonArray(text, "CoinGecko")
        val now = System.currentTimeMillis()
        val out = ArrayList<Coin>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val id = o.optString("id", "")
            val price = o.dbl("current_price") ?: continue
            if (id.isEmpty() || price <= 0.0) continue
            var rank = o.optInt("market_cap_rank", 0)
            if (rank <= 0) rank = i + 1
            out.add(
                Coin(
                    id = id,
                    symbol = o.optString("symbol", "").uppercase(),
                    name = o.optString("name", id),
                    image = o.optString("image", "").ifEmpty { null },
                    rank = rank,
                    price = price,
                    marketCap = o.dbl("market_cap") ?: 0.0,
                    volume24h = o.dbl("total_volume") ?: 0.0,
                    change24h = o.dbl("price_change_percentage_24h"),
                    change7d = o.dbl("price_change_percentage_7d_in_currency"),
                    high24h = o.dbl("high_24h"),
                    low24h = o.dbl("low_24h"),
                    fetchedAt = now,
                    source = "CoinGecko"
                )
            )
        }
        if (out.size < 10) throw ApiException(ApiException.Kind.BAD_RESPONSE, "CoinGecko returned too few valid coins")
        return out.sortedBy { it.rank }.take(limit)
    }

    suspend fun coinloreMarkets(limit: Int = 50): List<Coin> {
        val url = "https://api.coinlore.net/api/tickers/?start=0&limit=$limit"
        val text = Http.getString(url, emptyMap(), "coinlore", 1000L)
        val root = parseJsonObject(text, "CoinLore")
        val arr = root.optJSONArray("data") ?: throw ApiException(ApiException.Kind.BAD_RESPONSE, "CoinLore: missing data")
        val now = System.currentTimeMillis()
        val out = ArrayList<Coin>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val price = o.dbl("price_usd") ?: continue
            if (price <= 0.0) continue
            val nameId = o.optString("nameid", "").lowercase()
            val id = if (nameId.isNotEmpty()) nameId else "coinlore-" + o.optString("id", i.toString())
            var rank = o.optInt("rank", 0)
            if (rank <= 0) rank = i + 1
            out.add(
                Coin(
                    id = id,
                    symbol = o.optString("symbol", "").uppercase(),
                    name = o.optString("name", id),
                    image = null,
                    rank = rank,
                    price = price,
                    marketCap = o.dbl("market_cap_usd") ?: 0.0,
                    volume24h = o.dbl("volume24") ?: 0.0,
                    change24h = o.dbl("percent_change_24h"),
                    change7d = o.dbl("percent_change_7d"),
                    high24h = null,
                    low24h = null,
                    fetchedAt = now,
                    source = "CoinLore"
                )
            )
        }
        if (out.size < 10) throw ApiException(ApiException.Kind.BAD_RESPONSE, "CoinLore returned too few valid coins")
        return out.sortedBy { it.rank }.take(limit)
    }

    suspend fun geckoGlobal(): GlobalStats {
        val text = Http.getString("$geckoBase/global", geckoHeaders(), "coingecko", 1200L)
        val data = parseJsonObject(text, "CoinGecko").optJSONObject("data")
            ?: throw ApiException(ApiException.Kind.BAD_RESPONSE, "CoinGecko global: missing data")
        val cap = data.optJSONObject("total_market_cap")
        val vol = data.optJSONObject("total_volume")
        val dom = data.optJSONObject("market_cap_percentage")
        val stats = GlobalStats(
            totalMarketCap = cap?.dbl("usd"),
            totalVolume = vol?.dbl("usd"),
            btcDominance = dom?.dbl("btc"),
            ethDominance = dom?.dbl("eth"),
            marketCapChange24h = data.dbl("market_cap_change_percentage_24h_usd")
        )
        if (stats.totalMarketCap == null) throw ApiException(ApiException.Kind.BAD_RESPONSE, "CoinGecko global: invalid data")
        return stats
    }

    suspend fun fearGreed(): FearGreed {
        val text = Http.getString("https://api.alternative.me/fng/?limit=1", emptyMap(), "altme", 800L)
        val arr = parseJsonObject(text, "alternative.me").optJSONArray("data")
        val o = arr?.optJSONObject(0) ?: throw ApiException(ApiException.Kind.BAD_RESPONSE, "Fear & Greed: no data")
        val v = o.optString("value", "").toIntOrNull()
            ?: throw ApiException(ApiException.Kind.BAD_RESPONSE, "Fear & Greed: invalid value")
        if (v < 0 || v > 100) throw ApiException(ApiException.Kind.BAD_RESPONSE, "Fear & Greed: out of range")
        return FearGreed(v, o.optString("value_classification", ""))
    }

    /** Binance spot klines, paginated backwards. Returns candles sorted ascending (may include the forming candle). */
    suspend fun binanceKlines(symbol: String, tf: Timeframe, total: Int): List<Candle> {
        val all = ArrayList<Candle>()
        var endTime: Long? = null
        var remaining = total
        while (remaining > 0) {
            val lim = minOf(1000, remaining)
            var url = "$binanceBase/klines?symbol=${symbol}USDT&interval=${tf.binanceInterval}&limit=$lim"
            if (endTime != null) url += "&endTime=$endTime"
            val text = Http.getString(url, emptyMap(), "binance", 200L)
            val arr = parseJsonArray(text, "Binance")
            if (arr.length() == 0) break
            val page = ArrayList<Candle>(arr.length())
            for (i in 0 until arr.length()) {
                val k = arr.optJSONArray(i) ?: continue
                if (k.length() < 10) continue
                val o = k.optString(1, "").toDoubleOrNull()
                val h = k.optString(2, "").toDoubleOrNull()
                val l = k.optString(3, "").toDoubleOrNull()
                val c = k.optString(4, "").toDoubleOrNull()
                val v = k.optString(5, "").toDoubleOrNull()
                val tb = k.optString(9, "").toDoubleOrNull()
                if (o == null || h == null || l == null || c == null || v == null) continue
                if (o <= 0.0 || h <= 0.0 || l <= 0.0 || c <= 0.0 || h < l) continue
                page.add(Candle(k.optLong(0), o, h, l, c, v, tb))
            }
            if (page.isEmpty()) break
            all.addAll(0, page)
            remaining -= arr.length()
            if (arr.length() < lim) break
            endTime = page.first().time - 1
        }
        return all.distinctBy { it.time }.sortedBy { it.time }
    }

    /**
     * Fallback: derive approximate candles from CoinGecko's price series. The source gives no
     * per-candle volume, so volume is reported as unavailable by the caller.
     */
    suspend fun geckoDerivedCandles(coinId: String, tf: Timeframe): List<Candle> {
        val days = when (tf) {
            Timeframe.M15 -> 1
            Timeframe.H1 -> 30
            Timeframe.H4 -> 90
            Timeframe.D1 -> 365
        }
        val url = "$geckoBase/coins/$coinId/market_chart?vs_currency=usd&days=$days"
        val text = Http.getString(url, geckoHeaders(), "coingecko", 1200L)
        val prices = parseJsonObject(text, "CoinGecko").optJSONArray("prices")
            ?: throw ApiException(ApiException.Kind.BAD_RESPONSE, "CoinGecko chart: missing prices")
        val buckets = LinkedHashMap<Long, DoubleArray>() // [open, high, low, close]
        for (i in 0 until prices.length()) {
            val p = prices.optJSONArray(i) ?: continue
            if (p.length() < 2) continue
            val ts = p.optLong(0)
            val px = p.optDouble(1, Double.NaN)
            if (px.isNaN() || px <= 0.0) continue
            val key = ts / tf.millis
            val b = buckets[key]
            if (b == null) {
                buckets[key] = doubleArrayOf(px, px, px, px)
            } else {
                if (px > b[1]) b[1] = px
                if (px < b[2]) b[2] = px
                b[3] = px
            }
        }
        return buckets.entries
            .sortedBy { it.key }
            .map { Candle(it.key * tf.millis, it.value[0], it.value[1], it.value[2], it.value[3], 0.0, null) }
    }

    suspend fun news(symbol: String?): List<NewsItem> {
        var url = "https://min-api.cryptocompare.com/data/v2/news/?lang=EN"
        if (!symbol.isNullOrBlank()) url += "&categories=" + symbol.uppercase()
        val text = Http.getString(url, emptyMap(), "cryptocompare", 800L)
        val root = parseJsonObject(text, "CryptoCompare")
        val arr = root.optJSONArray("Data") ?: throw ApiException(ApiException.Kind.BAD_RESPONSE, "News: missing data")
        val out = ArrayList<NewsItem>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val title = o.optString("title", "").trim()
            val link = o.optString("url", "")
            if (title.isEmpty() || !link.startsWith("https://")) continue
            val body = o.optString("body", "").replace(Regex("\\s+"), " ").trim()
            val summary = if (body.length > 180) body.substring(0, 177).trimEnd() + "…" else body
            val sourceName = o.optJSONObject("source_info")?.optString("name", "")?.ifEmpty { null }
                ?: o.optString("source", "unknown")
            val cats = o.optString("categories", "").split("|").map { it.trim() }.filter { it.isNotEmpty() }
            out.add(
                NewsItem(
                    id = o.optString("id", link),
                    title = title,
                    summary = summary,
                    url = link,
                    source = sourceName,
                    publishedAt = o.optLong("published_on", 0L) * 1000L,
                    categories = cats,
                    sentiment = SentimentLexicon.classify("$title $summary")
                )
            )
        }
        return out.sortedByDescending { it.publishedAt }
    }
}
