package com.trandz123.hotronguoikhiemthi.data.history

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 1 ban ghi lich su quet. Dung chung cho ca Doc tien va Doc menu — phan biet qua [type].
 *
 *  - type=MONEY: spokenText = "Hai tram nghin dong"
 *  - type=MENU:  spokenText = "12 mon: Pho bo nam muoi nghin, Bun cha bay muoi nghin..."
 *                (tom tat 1 doan ngan thay vi luu toan bo menu)
 */
@Entity(tableName = "scan_history")
data class ScanHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: ScanType,
    val spokenText: String,
    val timestampMs: Long,
)

enum class ScanType { MONEY, MENU }
