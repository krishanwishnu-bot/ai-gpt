package com.cryptoresearch.ai.data.net

import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.net.UnknownHostException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class ApiException(
    val kind: Kind,
    message: String,
    val retryAfterMs: Long? = null
) : Exception(message) {
    enum class Kind { NO_NETWORK, RATE_LIMIT, UNAVAILABLE, BAD_RESPONSE, HTTP }
}

/** Serialises requests to one host with a minimum spacing (simple client-side rate limiting). */
internal class RateGate(private val minIntervalMs: Long) {
    private val mutex = Mutex()
    private var lastMs = 0L

    suspend fun await() {
        mutex.withLock {
            val now = System.nanoTime() / 1_000_000L
            val wait = lastMs + minIntervalMs - now
            if (wait > 0) delay(wait)
            lastMs = System.nanoTime() / 1_000_000L
        }
    }
}

object Http {
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .callTimeout(40, TimeUnit.SECONDS)
        .build()

    private val gates = ConcurrentHashMap<String, RateGate>()

    private suspend fun Call.await(): Response = suspendCancellableCoroutine { cont ->
        cont.invokeOnCancellation {
            try {
                cancel()
            } catch (_: Throwable) {
            }
        }
        enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (cont.isActive) cont.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                if (cont.isActive) cont.resume(response) else response.close()
            }
        })
    }

    /**
     * HTTPS GET with retry (exponential back-off), Retry-After handling for HTTP 429 and
     * optional per-host rate gating. Throws [ApiException] on failure.
     */
    suspend fun getString(
        url: String,
        headers: Map<String, String> = emptyMap(),
        gateKey: String? = null,
        gateMs: Long = 0L,
        maxAttempts: Int = 3
    ): String {
        require(url.startsWith("https://")) { "Only HTTPS requests are allowed" }
        val builder = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .header("User-Agent", "CryptoResearchAI/1.0 (Android)")
        for ((k, v) in headers) builder.header(k, v)
        val request = builder.build()
        val gate: RateGate? = if (gateKey != null) gates.getOrPut(gateKey) { RateGate(gateMs) } else null

        var lastError: ApiException? = null
        for (attempt in 1..maxAttempts) {
            gate?.await()
            try {
                val response = client.newCall(request).await()
                val code = response.code
                val retryAfter = response.header("Retry-After")?.toLongOrNull()?.times(1000L)
                val body: String? = response.use { it.body?.string() }
                if (code in 200..299) {
                    if (body.isNullOrBlank()) {
                        lastError = ApiException(ApiException.Kind.BAD_RESPONSE, "Empty response")
                    } else {
                        return body
                    }
                } else if (code == 429) {
                    lastError = ApiException(ApiException.Kind.RATE_LIMIT, "Rate limit reached (HTTP 429)", retryAfter)
                } else if (code in 500..599) {
                    lastError = ApiException(ApiException.Kind.UNAVAILABLE, "Server error (HTTP $code)")
                } else {
                    throw ApiException(ApiException.Kind.HTTP, "HTTP $code")
                }
            } catch (e: UnknownHostException) {
                throw ApiException(ApiException.Kind.NO_NETWORK, "No network / DNS failure")
            } catch (e: IOException) {
                lastError = ApiException(ApiException.Kind.UNAVAILABLE, "Network error: " + (e.message ?: "I/O"))
            }
            if (attempt < maxAttempts) {
                val wait = lastError?.retryAfterMs ?: (1500L * attempt)
                delay(minOf(wait, 20_000L))
            }
        }
        throw lastError ?: ApiException(ApiException.Kind.UNAVAILABLE, "Request failed")
    }
}
