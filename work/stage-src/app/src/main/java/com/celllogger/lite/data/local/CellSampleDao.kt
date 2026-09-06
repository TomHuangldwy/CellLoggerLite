package com.celllogger.lite.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CellSampleDao {

    @Insert
    suspend fun insert(sample: CellSampleEntity): Long

    @Query(
        """
        SELECT * FROM cell_samples
        ORDER BY utcTimestampMs DESC
        LIMIT :limit
        """
    )
    fun observeRecent(limit: Int): Flow<List<CellSampleEntity>>

    @Query("SELECT * FROM cell_samples ORDER BY utcTimestampMs DESC")
    fun observeAll(): Flow<List<CellSampleEntity>>

    @Query(
        """
        SELECT sessionId AS sessionId,
               experimentId AS experimentId,
               COUNT(*) AS sampleCount,
               MIN(utcTimestampMs) AS firstUtcMs,
               MAX(utcTimestampMs) AS lastUtcMs
        FROM cell_samples
        GROUP BY sessionId
        ORDER BY lastUtcMs DESC
        """
    )
    fun observeSessionSummaries(): Flow<List<SessionSummary>>

    @Query(
        """
        SELECT dataNetworkType AS networkType,
               COUNT(*) AS sampleCount
        FROM cell_samples
        WHERE dataNetworkType IS NOT NULL
        GROUP BY dataNetworkType
        ORDER BY sampleCount DESC
        """
    )
    fun observeNetworkTypeStats(): Flow<List<NetworkTypeStat>>

    @Query(
        """
        SELECT AVG(rsrpDbm) AS avgRsrpDbm,
               COUNT(*) AS sampleCount
        FROM cell_samples
        WHERE rsrpDbm IS NOT NULL
        """
    )
    fun observeRsrpSummary(): Flow<RsrpSummary?>

    @Query("SELECT * FROM cell_samples ORDER BY utcTimestampMs DESC")
    suspend fun getAllSamples(): List<CellSampleEntity>

    @Query(
        "SELECT * FROM cell_samples WHERE sessionId = :sessionId ORDER BY utcTimestampMs ASC"
    )
    suspend fun getSessionSamples(sessionId: String): List<CellSampleEntity>

    @Query("DELETE FROM cell_samples")
    suspend fun clearAll()
}

data class SessionSummary(
    val sessionId: String,
    val experimentId: String?,
    val sampleCount: Int,
    val firstUtcMs: Long,
    val lastUtcMs: Long
)

data class NetworkTypeStat(
    val networkType: String?,
    val sampleCount: Int
)

data class RsrpSummary(
    val avgRsrpDbm: Double?,
    val sampleCount: Int
)
