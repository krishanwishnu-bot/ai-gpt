package com.cryptoresearch.ai.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class AppSettings(
    /** navy | black | light */
    val theme: String = "navy",
    /** 0 = manual refresh only */
    val refreshMinutes: Int = 15,
    val allowFallback: Boolean = true,
    val defaultTimeframe: String = "1d",
    val includeApproxConcepts: Boolean = true,
    val newsCount: Int = 15,
    val notifyMoves: Boolean = false,
    val moveThresholdPct: Float = 8f,
    val notifyReports: Boolean = false,
    val notifyNews: Boolean = false,
    val notifyErrors: Boolean = false
)

class SettingsStore(private val context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(load())
    val state: StateFlow<AppSettings> = _state
    val value: AppSettings get() = _state.value

    @Volatile
    private var cachedKey: String? = null
    @Volatile
    private var keyLoaded = false

    private val securePrefs: SharedPreferences? by lazy {
        try {
            val alias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
            EncryptedSharedPreferences.create(
                "secure_prefs",
                alias,
                context,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun load(): AppSettings {
        val d = AppSettings()
        return AppSettings(
            theme = prefs.getString("theme", d.theme) ?: d.theme,
            refreshMinutes = prefs.getInt("refreshMinutes", d.refreshMinutes),
            allowFallback = prefs.getBoolean("allowFallback", d.allowFallback),
            defaultTimeframe = prefs.getString("defaultTimeframe", d.defaultTimeframe) ?: d.defaultTimeframe,
            includeApproxConcepts = prefs.getBoolean("includeApprox", d.includeApproxConcepts),
            newsCount = prefs.getInt("newsCount", d.newsCount),
            notifyMoves = prefs.getBoolean("notifyMoves", d.notifyMoves),
            moveThresholdPct = prefs.getFloat("moveThreshold", d.moveThresholdPct),
            notifyReports = prefs.getBoolean("notifyReports", d.notifyReports),
            notifyNews = prefs.getBoolean("notifyNews", d.notifyNews),
            notifyErrors = prefs.getBoolean("notifyErrors", d.notifyErrors)
        )
    }

    fun update(block: (AppSettings) -> AppSettings) {
        val n = block(_state.value)
        _state.value = n
        prefs.edit()
            .putString("theme", n.theme)
            .putInt("refreshMinutes", n.refreshMinutes)
            .putBoolean("allowFallback", n.allowFallback)
            .putString("defaultTimeframe", n.defaultTimeframe)
            .putBoolean("includeApprox", n.includeApproxConcepts)
            .putInt("newsCount", n.newsCount)
            .putBoolean("notifyMoves", n.notifyMoves)
            .putFloat("moveThreshold", n.moveThresholdPct)
            .putBoolean("notifyReports", n.notifyReports)
            .putBoolean("notifyNews", n.notifyNews)
            .putBoolean("notifyErrors", n.notifyErrors)
            .apply()
    }

    /** Optional CoinGecko demo API key, stored in EncryptedSharedPreferences (never in source code). */
    fun apiKey(): String? {
        if (!keyLoaded) {
            cachedKey = try {
                securePrefs?.getString("coingecko_key", null)
            } catch (e: Exception) {
                null
            }
            keyLoaded = true
        }
        return cachedKey
    }

    /** Returns false when secure storage is unavailable on this device. */
    fun setApiKey(key: String?): Boolean {
        val sp = securePrefs ?: return false
        return try {
            val clean = key?.trim().takeUnless { it.isNullOrEmpty() }
            if (clean == null) sp.edit().remove("coingecko_key").apply() else sp.edit().putString("coingecko_key", clean).apply()
            cachedKey = clean
            keyLoaded = true
            true
        } catch (e: Exception) {
            false
        }
    }

    fun secureStorageAvailable(): Boolean = securePrefs != null
}
