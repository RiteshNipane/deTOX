package com.detox.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface ReelDao {
    @Insert
    suspend fun insertEvent(event: ReelEvent)

    @Query("INSERT OR IGNORE INTO daily_stat (dayKey, appId, reelCount, blockedCount) VALUES (:dayKey, :appId, 0, 0)")
    suspend fun ensureStat(dayKey: String, appId: String)

    @Query("UPDATE daily_stat SET reelCount = reelCount + 1 WHERE dayKey = :dayKey AND appId = :appId")
    suspend fun bumpCount(dayKey: String, appId: String)

    @Query("UPDATE daily_stat SET blockedCount = blockedCount + 1 WHERE dayKey = :dayKey AND appId = :appId")
    suspend fun bumpBlocked(dayKey: String, appId: String)

    @Transaction
    suspend fun recordReel(event: ReelEvent) {
        insertEvent(event)
        ensureStat(event.dayKey, event.appId)
        bumpCount(event.dayKey, event.appId)
    }

    @Transaction
    suspend fun recordBlocked(dayKey: String, appId: String) {
        ensureStat(dayKey, appId)
        bumpBlocked(dayKey, appId)
    }

    @Query("SELECT COALESCE(SUM(reelCount), 0) FROM daily_stat WHERE dayKey = :dayKey")
    suspend fun totalForDay(dayKey: String): Int

    @Query("SELECT COALESCE(SUM(reelCount), 0) FROM daily_stat WHERE dayKey = :dayKey")
    fun observeTotalForDay(dayKey: String): Flow<Int>

    @Query("SELECT * FROM daily_stat WHERE dayKey >= :fromDayKey ORDER BY dayKey")
    fun observeSince(fromDayKey: String): Flow<List<DailyStat>>
}
