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

class CalendarRepository(
    private val dao: CalendarEventDao,
) {
    fun observeAllEvents(): Flow<List<CalendarEvent>> =
        dao.observeAll().map { entities -> entities.map { it.toDomain() } }

    /**
     * Returns all event occurrences (recurrences expanded) whose day falls within
     * [rangeStart, rangeEndExclusive). Fetches all events and expands them, which
     * correctly surfaces weekly/yearly recurrences regardless of their base date.
     */
    fun observeEventsForRange(
        rangeStart: LocalDate,
        rangeEndExclusive: LocalDate,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): Flow<List<CalendarEvent>> {
        return dao.observeAll().map { entities ->
            val events = entities.map { it.toDomain() }
            expandIntoRange(events, rangeStart, rangeEndExclusive, zoneId)
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

    private fun expandIntoRange(
        events: List<CalendarEvent>,
        rangeStart: LocalDate,
        rangeEndExclusive: LocalDate,
        zoneId: ZoneId,
    ): List<CalendarEvent> {
        val result = mutableListOf<CalendarEvent>()
        var date = rangeStart
        while (date.isBefore(rangeEndExclusive)) {
            for (event in events) {
                if (occursOn(event, date, zoneId)) {
                    result += shiftEventToDate(event, date, zoneId)
                }
            }
            date = date.plusDays(1)
        }
        return result.sortedBy { it.startEpochMillis }
    }

    private fun occursOn(event: CalendarEvent, date: LocalDate, zoneId: ZoneId): Boolean {
        val base = Instant.ofEpochMilli(event.startEpochMillis).atZone(zoneId).toLocalDate()
        return when (event.recurrence) {
            com.familyhub.display.data.model.EventRecurrence.NONE -> base == date
            com.familyhub.display.data.model.EventRecurrence.WEEKLY ->
                base.dayOfWeek == date.dayOfWeek && !date.isBefore(base)
            com.familyhub.display.data.model.EventRecurrence.MONTHLY ->
                effectiveDayOfMonth(base, date) == date.dayOfMonth && !date.isBefore(base.withDayOfMonth(1))
            com.familyhub.display.data.model.EventRecurrence.YEARLY ->
                base.monthValue == date.monthValue && base.dayOfMonth == date.dayOfMonth
        }
    }

    // Clamps the base day-of-month to months that are shorter (e.g. a 31st
    // recurrence lands on the last day of shorter months).
    private fun effectiveDayOfMonth(base: LocalDate, date: LocalDate): Int {
        return minOf(base.dayOfMonth, date.lengthOfMonth())
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
}

class MemberRepository(
    private val dao: com.familyhub.display.data.local.FamilyMemberDao,
) {
    fun observeMembers(): Flow<List<com.familyhub.display.data.model.FamilyMember>> =
        dao.observeAll().map { entities -> entities.map { it.toDomain() } }

    suspend fun upsert(member: com.familyhub.display.data.model.FamilyMember): Long {
        val entity = member.toEntity()
        return if (member.id == 0L) {
            dao.insert(entity)
        } else {
            dao.update(entity)
            member.id
        }
    }

    suspend fun delete(memberId: Long) {
        dao.clearMemberFromEvents(memberId)
        dao.deleteById(memberId)
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
