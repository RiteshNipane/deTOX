package com.detox.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/** One reel/short seen. [dayKey] is local-date "yyyy-MM-dd". */
@Entity(tableName = "reel_event")
data class ReelEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val appId: String,
    val timestampMs: Long,
    val dayKey: String,
    val contentHash: Int,
)

/** Aggregated per day and per app, so the dashboard never scans raw events. */
@Entity(tableName = "daily_stat", primaryKeys = ["dayKey", "appId"])
data class DailyStat(
    val dayKey: String,
    val appId: String,
    val reelCount: Int = 0,
    val blockedCount: Int = 0,
)
