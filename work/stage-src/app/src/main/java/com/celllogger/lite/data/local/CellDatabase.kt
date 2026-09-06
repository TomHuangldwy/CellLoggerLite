package com.celllogger.lite.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [CellSampleEntity::class],
    version = 1,
    exportSchema = false
)
abstract class CellDatabase : RoomDatabase() {
    abstract fun cellSampleDao(): CellSampleDao
}
