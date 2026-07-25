package com.familyhub.display.data.repository

import com.familyhub.display.data.local.CalendarEventDao
import com.familyhub.display.data.local.PhotoItemDao
import com.familyhub.display.data.local.toDomain
import com.familyhub.display.data.local.toEntity
import com.familyhub.display.data.model.CalendarEvent
import com.familyhub.display.data.model.PhotoItem
import com.familyhub.display.data.remote.CloudSyncService
import com.familyhub.display.data.settings.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

class CalendarRepository(
    private val dao: CalendarEventDao,
) {
    fun observeAllEvents(): Flow<List<CalendarEvent>> =
        dao.observeAll().map { entities -> entities.map { it.toDomain() } }

    fun observeEventsForMonth(month: LocalDate, zoneId: ZoneId = ZoneId.systemDefault()): Flow<List<CalendarEvent>> {
        val monthStart = month.withDayOfMonth(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
        val monthEnd = month.with(TemporalAdjusters.lastDayOfMonth())
            .plusDays(1)
            .atStartOfDay(zoneId)
            .toInstant()
            .toEpochMilli()
        return dao.observeInRange(monthStart, monthEnd).map { entities ->
            entities.flatMap { expandRecurringEvent(it.toDomain(), month, zoneId) }
                .sortedBy { it.startEpochMillis }
        }
    }

    fun observeEventsForDay(day: LocalDate, zoneId: ZoneId = ZoneId.systemDefault()): Flow<List<CalendarEvent>> {
        val dayStart = day.atStartOfDay(zoneId).toInstant().toEpochMilli()
        val dayEnd = day.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
        return dao.observeInRange(dayStart, dayEnd).map { entities ->
            entities.flatMap { expandRecurringEvent(it.toDomain(), day.withDayOfMonth(1), zoneId) }
                .filter { eventOccursOnDay(it, day, zoneId) }
                .sortedBy { it.startEpochMillis }
        }
    }

    suspend fun upsert(event: CalendarEvent): Long {
        return if (event.id == 0L) {
            dao.insert(event.toEntity())
        } else {
            dao.update(event.toEntity())
            event.id
        }
    }

    suspend fun delete(eventId: Long) {
        dao.deleteById(eventId)
    }

    suspend fun replaceCloudEvents(events: List<CalendarEvent>) {
        dao.deleteCloudEvents()
        dao.insertAll(events.map { it.toEntity() })
    }

    suspend fun replaceGoogleEvents(events: List<CalendarEvent>) {
        dao.deleteGoogleEvents()
        dao.insertAll(events.map { it.toEntity() })
    }

    private fun expandRecurringEvent(
        event: CalendarEvent,
        visibleMonth: LocalDate,
        zoneId: ZoneId,
    ): List<CalendarEvent> {
        if (event.recurrence == com.familyhub.display.data.model.EventRecurrence.NONE) {
            return listOf(event)
        }

        val eventDate = Instant.ofEpochMilli(event.startEpochMillis).atZone(zoneId).toLocalDate()
        val monthStart = visibleMonth.withDayOfMonth(1)
        val monthEnd = visibleMonth.with(TemporalAdjusters.lastDayOfMonth())

        return when (event.recurrence) {
            com.familyhub.display.data.model.EventRecurrence.WEEKLY -> {
                generateSequence(eventDate) { previous ->
                    previous.plusWeeks(1)
                }
                    .takeWhile { !it.isAfter(monthEnd.plusWeeks(1)) }
                    .filter { !it.isBefore(monthStart.minusWeeks(1)) }
                    .map { date -> shiftEventToDate(event, date, zoneId) }
                    .toList()
            }
            com.familyhub.display.data.model.EventRecurrence.YEARLY -> {
                val yearlyDate = eventDate.withYear(visibleMonth.year)
                if (!yearlyDate.isBefore(monthStart) && !yearlyDate.isAfter(monthEnd)) {
                    listOf(shiftEventToDate(event, yearlyDate, zoneId))
                } else {
                    emptyList()
                }
            }
            else -> listOf(event)
        }
    }

    private fun shiftEventToDate(
        event: CalendarEvent,
        date: LocalDate,
        zoneId: ZoneId,
    ): CalendarEvent {
        val original = Instant.ofEpochMilli(event.startEpochMillis).atZone(zoneId)
        val shiftedStart = date.atTime(original.toLocalTime()).atZone(zoneId).toInstant().toEpochMilli()
        val shiftedEnd = event.endEpochMillis?.let { end ->
            val duration = end - event.startEpochMillis
            shiftedStart + duration
        }
        return event.copy(startEpochMillis = shiftedStart, endEpochMillis = shiftedEnd)
    }

    private fun eventOccursOnDay(event: CalendarEvent, day: LocalDate, zoneId: ZoneId): Boolean {
        val eventDay = Instant.ofEpochMilli(event.startEpochMillis).atZone(zoneId).toLocalDate()
        return eventDay == day
    }
}

class PhotoRepository(
    private val dao: PhotoItemDao,
) {
    fun observePhotos(): Flow<List<PhotoItem>> =
        dao.observeAll().map { entities -> entities.map { it.toDomain() } }

    suspend fun upsert(photo: PhotoItem): Long {
        return if (photo.id == 0L) {
            dao.insert(photo.toEntity())
        } else {
            dao.update(photo.toEntity())
            photo.id
        }
    }

    suspend fun delete(photoId: Long) {
        dao.deleteById(photoId)
    }

    suspend fun replaceCloudPhotos(photos: List<PhotoItem>) {
        dao.deleteCloudPhotos()
        dao.insertAll(photos.map { it.toEntity() })
    }

    suspend fun replaceGooglePhotos(photos: List<PhotoItem>) {
        dao.deleteGooglePhotos()
        dao.insertAll(photos.map { it.toEntity() })
    }
}

class SyncRepository(
    private val calendarRepository: CalendarRepository,
    private val photoRepository: PhotoRepository,
    private val settingsRepository: SettingsRepository,
    private val googleAuthManager: com.familyhub.display.data.google.GoogleAuthManager,
    private val googleCalendarSyncService: com.familyhub.display.data.google.GoogleCalendarSyncService,
    private val googleDriveSyncService: com.familyhub.display.data.google.GoogleDriveSyncService,
) {
    // Ensures only one sync runs at a time (auto-sync on launch must not race a
    // manual "Sync now"), which otherwise corrupts the shared photo cache.
    private val syncMutex = Mutex()

    suspend fun syncNow(): Result<SyncSummary> = syncMutex.withLock {
        if (googleAuthManager.getSignedInAccount() != null) {
            syncFromGoogle()
        } else {
            syncFromCloud().map { SyncSummary(source = SyncSource.CUSTOM_CLOUD) }
        }
    }

    suspend fun syncFromGoogle(): Result<SyncSummary> {
        return runCatching {
            val settings = settingsRepository.settings.first()
            authManagerRequireSignedIn()

            var eventCount = 0
            var photoCount = 0
            var calendarError: String? = null
            var photosError: String? = null

            // Calendar and Photos are synced independently so one failing
            // (e.g. Google's 2025 Photos Library API restriction) does not
            // block the other.
            try {
                val events = googleCalendarSyncService.fetchEvents()
                calendarRepository.replaceGoogleEvents(events)
                eventCount = events.size
            } catch (e: com.familyhub.display.data.google.GoogleConsentRequiredException) {
                throw e
            } catch (e: Exception) {
                calendarError = e.message ?: "Calendar sync failed"
            }

            if (settings.driveFolderId.isBlank()) {
                photosError = "No Google Drive photo folder set. Add one in Settings."
            } else {
                try {
                    val photos = googleDriveSyncService.fetchPhotos(
                        folderInput = settings.driveFolderId,
                        defaultDurationSeconds = settings.defaultPhotoDurationSeconds,
                    )
                    photoRepository.replaceGooglePhotos(photos)
                    photoCount = photos.size
                } catch (e: com.familyhub.display.data.google.GoogleConsentRequiredException) {
                    throw e
                } catch (e: Exception) {
                    photosError = e.message ?: "Photos sync failed"
                }
            }

            if (calendarError != null && photosError != null) {
                throw IllegalStateException("Calendar: $calendarError  •  Photos: $photosError")
            }

            settingsRepository.update { current ->
                current.copy(lastSyncEpochMillis = System.currentTimeMillis())
            }

            SyncSummary(
                source = SyncSource.GOOGLE,
                eventCount = eventCount,
                photoCount = photoCount,
                calendarError = calendarError,
                photosError = photosError,
            )
        }
    }

    suspend fun syncFromCloud(): Result<Unit> {
        return runCatching {
            val settings = settingsRepository.settings.first()
            if (settings.cloudBaseUrl.isBlank() || settings.cloudApiKey.isBlank()) {
                throw IllegalStateException("Cloud sync is not configured")
            }

            val service = CloudSyncService(settings.cloudBaseUrl, settings.cloudApiKey)
            val (events, photos) = service.fetchRemoteContent()
            calendarRepository.replaceCloudEvents(events)
            photoRepository.replaceCloudPhotos(photos)

            settingsRepository.update { current ->
                current.copy(lastSyncEpochMillis = System.currentTimeMillis())
            }
        }
    }

    private fun authManagerRequireSignedIn() {
        if (googleAuthManager.getSignedInAccount() == null) {
            throw IllegalStateException("Not signed in to Google")
        }
    }
}

enum class SyncSource {
    GOOGLE,
    CUSTOM_CLOUD,
}

data class SyncSummary(
    val source: SyncSource,
    val eventCount: Int = 0,
    val photoCount: Int = 0,
    val calendarError: String? = null,
    val photosError: String? = null,
)
