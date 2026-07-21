package com.familyhub.display.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface CalendarEventDao {
    @Query("SELECT * FROM calendar_events ORDER BY startEpochMillis ASC")
    fun observeAll(): Flow<List<CalendarEventEntity>>

    @Query(
        """
        SELECT * FROM calendar_events
        WHERE startEpochMillis >= :rangeStart AND startEpochMillis < :rangeEnd
        ORDER BY startEpochMillis ASC
        """,
    )
    fun observeInRange(rangeStart: Long, rangeEnd: Long): Flow<List<CalendarEventEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: CalendarEventEntity): Long

    @Update
    suspend fun update(entity: CalendarEventEntity)

    @Query("DELETE FROM calendar_events WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM calendar_events WHERE source = 'CLOUD'")
    suspend fun deleteCloudEvents()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<CalendarEventEntity>)
}

@Dao
interface PhotoItemDao {
    @Query("SELECT * FROM photo_items ORDER BY sortOrder ASC, id ASC")
    fun observeAll(): Flow<List<PhotoItemEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: PhotoItemEntity): Long

    @Update
    suspend fun update(entity: PhotoItemEntity)

    @Query("DELETE FROM photo_items WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM photo_items WHERE source = 'CLOUD'")
    suspend fun deleteCloudPhotos()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<PhotoItemEntity>)
}
