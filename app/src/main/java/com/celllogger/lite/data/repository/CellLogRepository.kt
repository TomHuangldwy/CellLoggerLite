package com.celllogger.lite.data.repository

import com.celllogger.lite.data.local.CellSampleDao
import com.celllogger.lite.data.local.CellSampleEntity
import com.celllogger.lite.data.local.NetworkTypeStat
import com.celllogger.lite.data.local.RsrpSummary
import com.celllogger.lite.data.local.SessionSummary
import kotlinx.coroutines.flow.Flow

class CellLogRepository(private val dao: CellSampleDao) {

    val recentSamples: Flow<List<CellSampleEntity>> = dao.observeRecent(500)
    val sessionSummaries: Flow<List<SessionSummary>> = dao.observeSessionSummaries()
    val networkTypeStats: Flow<List<NetworkTypeStat>> = dao.observeNetworkTypeStats()
    val rsrpSummary: Flow<RsrpSummary?> = dao.observeRsrpSummary()

    suspend fun insert(sample: CellSampleEntity) = dao.insert(sample)

    suspend fun getAllSamples(): List<CellSampleEntity> = dao.getAllSamples()

    suspend fun getSessionSamples(sessionId: String): List<CellSampleEntity> =
        dao.getSessionSamples(sessionId)

    suspend fun clearAll() = dao.clearAll()
}
