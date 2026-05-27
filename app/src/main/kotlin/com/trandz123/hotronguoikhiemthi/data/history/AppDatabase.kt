package com.trandz123.hotronguoikhiemthi.data.history

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters

@Database(
    entities = [ScanHistoryEntity::class],
    version = 1,
    exportSchema = false,
)
@TypeConverters(ScanTypeConverter::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun scanHistoryDao(): ScanHistoryDao
}

class ScanTypeConverter {
    @TypeConverter
    fun toType(name: String): ScanType = ScanType.valueOf(name)

    @TypeConverter
    fun fromType(type: ScanType): String = type.name
}
