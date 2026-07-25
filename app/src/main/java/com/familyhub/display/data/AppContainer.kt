package com.familyhub.display.data

import android.content.Context
import com.familyhub.display.data.local.FamilyHubDatabase
import com.familyhub.display.data.model.CalendarEvent
import com.familyhub.display.data.model.ContentSource
import com.familyhub.display.data.model.EventRecurrence
import com.familyhub.display.data.model.EventType
import com.familyhub.display.data.model.PhotoItem
import com.familyhub.display.data.google.GoogleAuthManager
import com.familyhub.display.data.google.GoogleCalendarSyncService
import com.familyhub.display.data.google.GoogleDriveSyncService
import com.familyhub.display.data.repository.CalendarRepository
import com.familyhub.display.data.repository.PhotoRepository
import com.familyhub.display.data.repository.SyncRepository
import com.familyhub.display.data.settings.SettingsRepository
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.ZoneId

class AppContainer(context: Context) {
    private val database = FamilyHubDatabase.getInstance(context)

    private val appContext = context.applicationContext

    val settingsRepository = SettingsRepository(context)
    val googleAuthManager = GoogleAuthManager(context)
    val googleCalendarSyncService = GoogleCalendarSyncService(googleAuthManager)
    val googleDriveSyncService = GoogleDriveSyncService(appContext, googleAuthManager)
    val calendarRepository = CalendarRepository(database.calendarEventDao())
    val photoRepository = PhotoRepository(database.photoItemDao())
    val syncRepository = SyncRepository(
        calendarRepository = calendarRepository,
        photoRepository = photoRepository,
        settingsRepository = settingsRepository,
        googleAuthManager = googleAuthManager,
        googleCalendarSyncService = googleCalendarSyncService,
        googleDriveSyncService = googleDriveSyncService,
    )

    suspend fun seedSampleDataIfEmpty() {
        val existingEvents = calendarRepository.observeAllEvents().first()
        if (existingEvents.isNotEmpty()) {
            return
        }

        val zoneId = ZoneId.systemDefault()
        val today = LocalDate.now(zoneId)
        val sampleEvents = listOf(
            CalendarEvent(
                title = "Mom's Birthday",
                notes = "Buy flowers",
                type = EventType.BIRTHDAY,
                startEpochMillis = today.plusDays(12).atTime(0, 0).atZone(zoneId).toInstant().toEpochMilli(),
                allDay = true,
                recurrence = EventRecurrence.YEARLY,
            ),
            CalendarEvent(
                title = "Emma - Piano",
                notes = "Bring sheet music",
                type = EventType.KIDS_CLASS,
                startEpochMillis = today.with(java.time.DayOfWeek.TUESDAY).atTime(16, 0).atZone(zoneId).toInstant().toEpochMilli(),
                endEpochMillis = today.with(java.time.DayOfWeek.TUESDAY).atTime(17, 0).atZone(zoneId).toInstant().toEpochMilli(),
                recurrence = EventRecurrence.WEEKLY,
            ),
            CalendarEvent(
                title = "Neighborhood BBQ",
                type = EventType.PARTY,
                startEpochMillis = today.plusDays(5).atTime(18, 0).atZone(zoneId).toInstant().toEpochMilli(),
                endEpochMillis = today.plusDays(5).atTime(21, 0).atZone(zoneId).toInstant().toEpochMilli(),
            ),
            CalendarEvent(
                title = "Dentist appointment",
                type = EventType.EVENT,
                startEpochMillis = today.plusDays(2).atTime(10, 30).atZone(zoneId).toInstant().toEpochMilli(),
                endEpochMillis = today.plusDays(2).atTime(11, 30).atZone(zoneId).toInstant().toEpochMilli(),
            ),
            CalendarEvent(
                title = "Trash day",
                type = EventType.OTHER,
                startEpochMillis = today.with(java.time.DayOfWeek.THURSDAY).atTime(7, 0).atZone(zoneId).toInstant().toEpochMilli(),
                allDay = true,
                recurrence = EventRecurrence.WEEKLY,
            ),
        )

        sampleEvents.forEach { calendarRepository.upsert(it) }

        val samplePhotos = listOf(
            PhotoItem(
                uri = "https://picsum.photos/seed/family1/1600/1200",
                caption = "Family hike",
                displayDurationSeconds = 8,
                sortOrder = 0,
                source = ContentSource.LOCAL,
            ),
            PhotoItem(
                uri = "https://picsum.photos/seed/family2/1600/1200",
                caption = "Backyard fun",
                displayDurationSeconds = 12,
                sortOrder = 1,
                source = ContentSource.LOCAL,
            ),
            PhotoItem(
                uri = "https://picsum.photos/seed/family3/1600/1200",
                caption = "Holiday memories",
                displayDurationSeconds = 10,
                sortOrder = 2,
                source = ContentSource.LOCAL,
            ),
        )

        samplePhotos.forEach { photoRepository.upsert(it) }
    }
}
