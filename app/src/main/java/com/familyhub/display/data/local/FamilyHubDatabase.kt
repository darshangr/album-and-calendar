package com.familyhub.display.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [CalendarEventEntity::class, PhotoItemEntity::class, FamilyMemberEntity::class],
    version = 2,
    exportSchema = false,
)
abstract class FamilyHubDatabase : RoomDatabase() {
    abstract fun calendarEventDao(): CalendarEventDao
    abstract fun photoItemDao(): PhotoItemDao
    abstract fun familyMemberDao(): FamilyMemberDao

    companion object {
        @Volatile
        private var instance: FamilyHubDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE calendar_events ADD COLUMN memberId INTEGER")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS family_members (" +
                        "id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, " +
                        "name TEXT NOT NULL, " +
                        "colorArgb INTEGER NOT NULL, " +
                        "sortOrder INTEGER NOT NULL)",
                )
            }
        }

        fun getInstance(context: Context): FamilyHubDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    FamilyHubDatabase::class.java,
                    "family_hub.db",
                ).addMigrations(MIGRATION_1_2)
                    .build().also { instance = it }
            }
        }
    }
}
