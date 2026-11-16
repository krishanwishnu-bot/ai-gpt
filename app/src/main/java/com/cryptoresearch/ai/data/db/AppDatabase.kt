package com.cryptoresearch.ai.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.cryptoresearch.ai.data.Coin

@Database(
    entities = [
        Coin::class,
        WatchEntry::class,
        CacheEntry::class,
        ReportEntity::class,
        Position::class,
        VirtualTx::class,
        PortfolioSnapshot::class,
        VirtualAccount::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun coinDao(): CoinDao
    abstract fun watchDao(): WatchDao
    abstract fun cacheDao(): CacheDao
    abstract fun reportDao(): ReportDao
    abstract fun portfolioDao(): PortfolioDao

    companion object {
        const val FILE_NAME = "crypto_research.db"

        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, FILE_NAME)
                .build()
    }
}
