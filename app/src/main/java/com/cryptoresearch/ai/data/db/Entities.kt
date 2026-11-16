package com.cryptoresearch.ai.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "watchlist")
data class WatchEntry(
    @PrimaryKey val coinId: String,
    val symbol: String,
    val name: String,
    val addedAt: Long
)

@Entity(tableName = "cache")
data class CacheEntry(
    @PrimaryKey val key: String,
    val value: String,
    val fetchedAt: Long
)

@Entity(tableName = "reports")
data class ReportEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val coinId: String,
    val symbol: String,
    val name: String,
    val createdAt: Long,
    val classification: String,
    val strength: String,
    val confidencePct: Int,
    val volatility: String,
    val json: String
)

/** Lightweight projection of [ReportEntity] without the large JSON column. */
data class ReportRow(
    val id: Long,
    val coinId: String,
    val symbol: String,
    val name: String,
    val createdAt: Long,
    val classification: String,
    val strength: String,
    val confidencePct: Int,
    val volatility: String
)

@Entity(tableName = "positions")
data class Position(
    @PrimaryKey val coinId: String,
    val symbol: String,
    val name: String,
    val quantity: Double,
    val avgCost: Double,
    val lastPrice: Double,
    val lastPriceAt: Long
)

@Entity(tableName = "virtual_tx")
data class VirtualTx(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val time: Long,
    val coinId: String,
    val symbol: String,
    val action: String,
    val quantity: Double,
    val price: Double
)

@Entity(tableName = "snapshots")
data class PortfolioSnapshot(
    @PrimaryKey val time: Long,
    val value: Double
)

@Entity(tableName = "account")
data class VirtualAccount(
    @PrimaryKey val id: Int = 1,
    val cash: Double,
    val startBalance: Double
)
