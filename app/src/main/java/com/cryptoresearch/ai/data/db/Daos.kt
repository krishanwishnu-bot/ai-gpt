package com.cryptoresearch.ai.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.cryptoresearch.ai.data.Coin
import kotlinx.coroutines.flow.Flow

@Dao
abstract class CoinDao {
    @Query("SELECT * FROM coins ORDER BY `rank` ASC")
    abstract fun observeAll(): Flow<List<Coin>>

    @Query("SELECT * FROM coins WHERE id = :id")
    abstract suspend fun byId(id: String): Coin?

    @Query("SELECT * FROM coins ORDER BY `rank` ASC")
    abstract suspend fun all(): List<Coin>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertAll(list: List<Coin>)

    @Query("DELETE FROM coins")
    abstract suspend fun clear()

    @Transaction
    open suspend fun replaceAll(list: List<Coin>) {
        clear()
        insertAll(list)
    }
}

@Dao
interface WatchDao {
    @Query("SELECT * FROM watchlist ORDER BY addedAt DESC")
    fun observe(): Flow<List<WatchEntry>>

    @Query("SELECT * FROM watchlist")
    suspend fun all(): List<WatchEntry>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: WatchEntry)

    @Query("DELETE FROM watchlist WHERE coinId = :id")
    suspend fun delete(id: String)

    @Query("SELECT COUNT(*) FROM watchlist WHERE coinId = :id")
    suspend fun count(id: String): Int
}

@Dao
interface CacheDao {
    @Query("SELECT * FROM cache WHERE `key` = :key")
    suspend fun get(key: String): CacheEntry?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(entry: CacheEntry)

    @Query("DELETE FROM cache")
    suspend fun clear()

    @Query("SELECT COUNT(*) FROM cache")
    suspend fun count(): Int

    @Query("SELECT COALESCE(SUM(LENGTH(value)), 0) FROM cache")
    suspend fun sizeChars(): Long
}

@Dao
interface ReportDao {
    @Query("SELECT id, coinId, symbol, name, createdAt, classification, strength, confidencePct, volatility FROM reports ORDER BY createdAt DESC")
    fun observeRows(): Flow<List<ReportRow>>

    @Query("SELECT * FROM reports WHERE id = :id")
    suspend fun byId(id: Long): ReportEntity?

    @Insert
    suspend fun insert(entity: ReportEntity): Long

    @Query("DELETE FROM reports WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM reports")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM reports")
    suspend fun count(): Int
}

@Dao
interface PortfolioDao {
    @Query("SELECT * FROM positions ORDER BY symbol ASC")
    fun observePositions(): Flow<List<Position>>

    @Query("SELECT * FROM positions")
    suspend fun positions(): List<Position>

    @Query("SELECT * FROM positions WHERE coinId = :id")
    suspend fun position(id: String): Position?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPosition(p: Position)

    @Query("DELETE FROM positions WHERE coinId = :id")
    suspend fun deletePosition(id: String)

    @Query("DELETE FROM positions")
    suspend fun deleteAllPositions()

    @Insert
    suspend fun insertTx(tx: VirtualTx)

    @Query("SELECT * FROM virtual_tx ORDER BY time DESC LIMIT 30")
    fun observeTx(): Flow<List<VirtualTx>>

    @Query("DELETE FROM virtual_tx")
    suspend fun deleteAllTx()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSnapshot(s: PortfolioSnapshot)

    @Query("SELECT * FROM snapshots ORDER BY time DESC LIMIT 300")
    fun observeSnapshots(): Flow<List<PortfolioSnapshot>>

    @Query("SELECT * FROM snapshots ORDER BY time DESC LIMIT 1")
    suspend fun lastSnapshot(): PortfolioSnapshot?

    @Query("DELETE FROM snapshots")
    suspend fun deleteAllSnapshots()

    @Query("SELECT * FROM account WHERE id = 1")
    fun observeAccount(): Flow<VirtualAccount?>

    @Query("SELECT * FROM account WHERE id = 1")
    suspend fun account(): VirtualAccount?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAccount(a: VirtualAccount)
}
