package com.trandz123.hotronguoikhiemthi.data.history

import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HistoryRepository @Inject constructor(
    private val dao: ScanHistoryDao,
) {
    fun observeRecent(): Flow<List<ScanHistoryEntity>> = dao.observeRecent()

    suspend fun record(type: ScanType, spokenText: String) {
        dao.insert(ScanHistoryEntity(type = type, spokenText = spokenText, timestampMs = System.currentTimeMillis()))
        dao.trimToLatest20()
    }

    suspend fun clear() = dao.clear()
}
