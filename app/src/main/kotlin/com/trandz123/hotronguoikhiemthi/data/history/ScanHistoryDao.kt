package com.trandz123.hotronguoikhiemthi.data.history

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ScanHistoryDao {

    /** 20 ban ghi gan nhat, sort moi → cu. */
    @Query("SELECT * FROM scan_history ORDER BY timestampMs DESC LIMIT 20")
    fun observeRecent(): Flow<List<ScanHistoryEntity>>

    @Insert
    suspend fun insert(entity: ScanHistoryEntity): Long

    /** Don ban ghi cu, chi giu 20 gan nhat. Goi sau moi insert. */
    @Query(
        """
        DELETE FROM scan_history
        WHERE id NOT IN (SELECT id FROM scan_history ORDER BY timestampMs DESC LIMIT 20)
        """
    )
    suspend fun trimToLatest20()

    @Query("DELETE FROM scan_history")
    suspend fun clear()
}
