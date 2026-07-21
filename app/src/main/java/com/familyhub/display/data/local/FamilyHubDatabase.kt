package com.familyhub.display.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [CalendarEventEntity::class, PhotoItemEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class FamilyHubDatabase : RoomDatabase() {
    abstract fun calendarEventDao(): CalendarEventDao
    abstract fun photoItemDao(): PhotoItemDao

    companion object {
        @Volatile
        private var instance: FamilyHubDatabase? = null

        fun getInstance(context: Context): FamilyHubDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    FamilyHubDatabase::class.java,
                    "family_hub.db",
                ).build().also { instance = it }
            }
        }
    }
}
