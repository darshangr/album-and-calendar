package com.familyhub.display.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.familyhub.display.data.AppContainer
import com.familyhub.display.data.model.CalendarEvent
import com.familyhub.display.data.model.PhotoItem
import com.familyhub.display.data.settings.AppSettings
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

enum class DisplayMode {
    CALENDAR,
    PHOTOS,
}

data class MainUiState(
    val mode: DisplayMode = DisplayMode.CALENDAR,
    val settings: AppSettings = AppSettings(),
    val syncMessage: String? = null,
    val isSyncing: Boolean = false,
    val googleAccount: com.familyhub.display.data.google.GoogleAccountState =
        com.familyhub.display.data.google.GoogleAccountState(),
    val pendingConsentIntent: android.content.Intent? = null,
    val members: List<com.familyhub.display.data.model.FamilyMember> = emptyList(),
)

class MainViewModel(
    private val container: AppContainer,
) : ViewModel() {
    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private var lastInteractionEpochMillis = System.currentTimeMillis()

    init {
        viewModelScope.launch {
            container.seedSampleDataIfEmpty()
        }

        viewModelScope.launch {
            container.settingsRepository.settings.collect { appSettings ->
                _uiState.update { it.copy(settings = appSettings) }
            }
        }

        viewModelScope.launch {
            container.googleAuthManager.accountState.collect { account ->
                _uiState.update { it.copy(googleAccount = account) }
            }
        }

        viewModelScope.launch {
            container.memberRepository.observeMembers().collect { members ->
                _uiState.update { it.copy(members = members) }
            }
        }

        viewModelScope.launch {
            if (container.googleAuthManager.getSignedInAccount() != null) {
                syncNow()
            }
        }
    }

    fun addMember(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            val existing = _uiState.value.members
            val color = com.familyhub.display.ui.theme.MemberColorPalette[
                existing.size % com.familyhub.display.ui.theme.MemberColorPalette.size,
            ]
            container.memberRepository.upsert(
                com.familyhub.display.data.model.FamilyMember(
                    name = trimmed,
                    colorArgb = color,
                    sortOrder = existing.size,
                ),
            )
        }
    }

    fun deleteMember(memberId: Long) {
        viewModelScope.launch {
            container.memberRepository.delete(memberId)
        }
    }

    fun updateMemberColor(member: com.familyhub.display.data.model.FamilyMember) {
        viewModelScope.launch {
            val palette = com.familyhub.display.ui.theme.MemberColorPalette
            val nextIndex = (palette.indexOf(member.colorArgb) + 1).mod(palette.size)
            container.memberRepository.upsert(member.copy(colorArgb = palette[nextIndex]))
        }
    }

    fun onUserInteraction() {
        lastInteractionEpochMillis = System.currentTimeMillis()
    }

    fun checkIdleTimeout(nowMillis: Long = System.currentTimeMillis()) {
        val timeoutMinutes = _uiState.value.settings.calendarIdleTimeoutMinutes
        if (_uiState.value.mode != DisplayMode.CALENDAR || timeoutMinutes <= 0) {
            return
        }

        val idleMillis = nowMillis - lastInteractionEpochMillis
        if (idleMillis >= timeoutMinutes * 60_000L) {
            switchToPhotos()
        }
    }

    fun switchToPhotos() {
        _uiState.update { it.copy(mode = DisplayMode.PHOTOS) }
    }

    fun switchToCalendarFromDoubleTap() {
        lastInteractionEpochMillis = System.currentTimeMillis()
        _uiState.update { it.copy(mode = DisplayMode.CALENDAR) }
    }

    fun syncNow() {
        if (_uiState.value.isSyncing) return
        viewModelScope.launch {
            _uiState.update { it.copy(isSyncing = true, syncMessage = null) }
            val result = container.syncRepository.syncNow()
            result.fold(
                onSuccess = { summary ->
                    _uiState.update {
                        it.copy(isSyncing = false, syncMessage = buildSyncMessage(summary))
                    }
                },
                onFailure = { error ->
                    if (error is com.familyhub.display.data.google.GoogleConsentRequiredException) {
                        _uiState.update {
                            it.copy(
                                isSyncing = false,
                                pendingConsentIntent = error.consentIntent,
                                syncMessage = "Approve Google Drive access to finish syncing photos.",
                            )
                        }
                    } else {
                        _uiState.update {
                            it.copy(
                                isSyncing = false,
                                syncMessage = error.message ?: "Sync failed. Check settings.",
                            )
                        }
                    }
                },
            )
        }
    }

    fun clearPendingConsent() {
        _uiState.update { it.copy(pendingConsentIntent = null) }
    }

    fun onConsentResult() {
        container.googleAuthManager.refreshAccountState()
        syncNow()
    }

    private fun buildSyncMessage(
        summary: com.familyhub.display.data.repository.SyncSummary,
    ): String {
        if (summary.source == com.familyhub.display.data.repository.SyncSource.CUSTOM_CLOUD) {
            return "Sync completed"
        }
        return buildString {
            append("Synced ${summary.eventCount} events")
            if (summary.photoCount > 0) append(" and ${summary.photoCount} photos")
            summary.calendarError?.let { append("  •  Calendar: $it") }
            summary.photosError?.let { append("  •  Photos: $it") }
        }
    }

    fun signInWithGoogle() = container.googleAuthManager.getSignInIntent()

    fun handleGoogleSignInResult(data: android.content.Intent?) {
        val result = container.googleAuthManager.handleSignInResult(data)
        val signedIn = result.getOrNull()?.isSignedIn == true
        if (signedIn) {
            syncNow()
        } else {
            val error = result.exceptionOrNull()
            val statusCode = (error as? com.google.android.gms.common.api.ApiException)?.statusCode
            _uiState.update {
                it.copy(
                    syncMessage = buildString {
                        append("Google sign-in failed")
                        if (statusCode != null) append(" (code $statusCode)")
                        append(". Check the Android OAuth client (SHA-1 + package), enabled APIs, and test user.")
                    },
                )
            }
        }
    }

    fun signOutGoogle() {
        viewModelScope.launch {
            container.googleAuthManager.signOut()
            container.calendarRepository.replaceGoogleEvents(emptyList())
            container.photoRepository.replaceGooglePhotos(emptyList())
        }
    }

    fun clearSyncMessage() {
        _uiState.update { it.copy(syncMessage = null) }
    }

    fun updateSettings(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch {
            container.settingsRepository.update(transform)
        }
    }
}

enum class CalendarViewMode { WEEK, MONTH }

data class CalendarUiState(
    val viewMode: CalendarViewMode = CalendarViewMode.WEEK,
    val anchorDate: LocalDate = LocalDate.now(),
    val selectedDay: LocalDate = LocalDate.now(),
    val rangeEvents: List<CalendarEvent> = emptyList(),
    val members: List<com.familyhub.display.data.model.FamilyMember> = emptyList(),
    val upcomingEvents: List<CalendarEvent> = emptyList(),
    val showAddDialog: Boolean = false,
    val editingEvent: CalendarEvent? = null,
)

private data class RangeSpec(val mode: CalendarViewMode, val anchor: LocalDate)

fun weekStart(date: LocalDate): LocalDate {
    // Sunday-first week to match the month grid.
    val offset = (date.dayOfWeek.value % 7).toLong()
    return date.minusDays(offset)
}

@OptIn(ExperimentalCoroutinesApi::class)
class CalendarViewModel(
    private val container: AppContainer,
) : ViewModel() {
    private val viewMode = MutableStateFlow(CalendarViewMode.WEEK)
    private val anchorDate = MutableStateFlow(LocalDate.now())
    private val selectedDay = MutableStateFlow(LocalDate.now())
    private val dialogState = MutableStateFlow<Pair<Boolean, CalendarEvent?>>(false to null)

    init {
        viewModelScope.launch {
            val settings = container.settingsRepository.settings.first()
            viewMode.value = if (settings.weeklyViewDefault) CalendarViewMode.WEEK else CalendarViewMode.MONTH
        }
    }

    private fun rangeFor(mode: CalendarViewMode, anchor: LocalDate): Pair<LocalDate, LocalDate> {
        return when (mode) {
            CalendarViewMode.WEEK -> {
                val start = weekStart(anchor)
                start to start.plusDays(7)
            }
            CalendarViewMode.MONTH -> {
                val start = anchor.withDayOfMonth(1)
                start to start.plusMonths(1)
            }
        }
    }

    private val rangeEventsFlow = combine(viewMode, anchorDate) { m, a -> RangeSpec(m, a) }
        .flatMapLatest { spec ->
            val (start, end) = rangeFor(spec.mode, spec.anchor)
            container.calendarRepository.observeEventsForRange(start, end)
                .map { spec to it }
        }

    private val upcomingFlow = container.calendarRepository
        .observeEventsForRange(LocalDate.now(), LocalDate.now().plusDays(60))
        .map { events ->
            events.filter { it.startEpochMillis >= System.currentTimeMillis() }
                .sortedBy { it.startEpochMillis }
                .take(8)
        }

    val uiState: StateFlow<CalendarUiState> = combine(
        rangeEventsFlow,
        selectedDay,
        dialogState,
        container.memberRepository.observeMembers(),
        upcomingFlow,
    ) { (spec, rangeEvents), day, dialog, members, upcoming ->
        CalendarUiState(
            viewMode = spec.mode,
            anchorDate = spec.anchor,
            selectedDay = day,
            rangeEvents = rangeEvents,
            members = members,
            upcomingEvents = upcoming,
            showAddDialog = dialog.first,
            editingEvent = dialog.second,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CalendarUiState())

    fun setViewMode(mode: CalendarViewMode) {
        viewMode.value = mode
    }

    fun selectDay(day: LocalDate) {
        selectedDay.value = day
    }

    fun goToPrevious() {
        val mode = viewMode.value
        anchorDate.value = if (mode == CalendarViewMode.WEEK) {
            anchorDate.value.minusWeeks(1)
        } else {
            anchorDate.value.minusMonths(1)
        }
        alignSelectedDayToRange()
    }

    fun goToNext() {
        val mode = viewMode.value
        anchorDate.value = if (mode == CalendarViewMode.WEEK) {
            anchorDate.value.plusWeeks(1)
        } else {
            anchorDate.value.plusMonths(1)
        }
        alignSelectedDayToRange()
    }

    fun goToToday() {
        val today = LocalDate.now()
        anchorDate.value = today
        selectedDay.value = today
    }

    private fun alignSelectedDayToRange() {
        val (start, end) = rangeFor(viewMode.value, anchorDate.value)
        if (selectedDay.value.isBefore(start) || !selectedDay.value.isBefore(end)) {
            selectedDay.value = start
        }
    }

    fun showAddDialog() {
        dialogState.value = true to null
    }

    fun showEditDialog(event: CalendarEvent) {
        dialogState.value = true to event
    }

    fun dismissDialog() {
        dialogState.value = false to null
    }

    fun saveEvent(event: CalendarEvent) {
        viewModelScope.launch {
            container.calendarRepository.upsert(event)
            dismissDialog()
        }
    }

    fun deleteEvent(eventId: Long) {
        viewModelScope.launch {
            container.calendarRepository.delete(eventId)
            dismissDialog()
        }
    }

    fun addMember(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            val existing = uiState.value.members
            val palette = com.familyhub.display.ui.theme.MemberColorPalette
            container.memberRepository.upsert(
                com.familyhub.display.data.model.FamilyMember(
                    name = trimmed,
                    colorArgb = palette[existing.size % palette.size],
                    sortOrder = existing.size,
                ),
            )
        }
    }
}

data class PhotoUiState(
    val photos: List<PhotoItem> = emptyList(),
    val currentIndex: Int = 0,
    val showAddDialog: Boolean = false,
    val editingPhoto: PhotoItem? = null,
)

class PhotoViewModel(
    private val container: AppContainer,
) : ViewModel() {
    private val _uiState = MutableStateFlow(PhotoUiState())
    val uiState: StateFlow<PhotoUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            container.photoRepository.observePhotos().collect { photos ->
                _uiState.update { current ->
                    val index = if (photos.isEmpty()) 0 else current.currentIndex.coerceAtMost(photos.lastIndex)
                    current.copy(photos = photos, currentIndex = index)
                }
            }
        }
    }

    fun advancePhoto() {
        val photos = _uiState.value.photos
        if (photos.isEmpty()) return
        val nextIndex = (_uiState.value.currentIndex + 1) % photos.size
        _uiState.update { it.copy(currentIndex = nextIndex) }
    }

    fun previousPhoto() {
        val photos = _uiState.value.photos
        if (photos.isEmpty()) return
        val prevIndex = (_uiState.value.currentIndex - 1 + photos.size) % photos.size
        _uiState.update { it.copy(currentIndex = prevIndex) }
    }

    fun showAddDialog() {
        _uiState.update { it.copy(showAddDialog = true, editingPhoto = null) }
    }

    fun dismissDialog() {
        _uiState.update { it.copy(showAddDialog = false, editingPhoto = null) }
    }

    fun savePhoto(photo: PhotoItem) {
        viewModelScope.launch {
            container.photoRepository.upsert(photo)
            dismissDialog()
        }
    }

    fun deletePhoto(photoId: Long) {
        viewModelScope.launch {
            container.photoRepository.delete(photoId)
            dismissDialog()
        }
    }
}

class ViewModelFactory(
    private val container: AppContainer,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return when {
            modelClass.isAssignableFrom(MainViewModel::class.java) -> MainViewModel(container) as T
            modelClass.isAssignableFrom(CalendarViewModel::class.java) -> CalendarViewModel(container) as T
            modelClass.isAssignableFrom(PhotoViewModel::class.java) -> PhotoViewModel(container) as T
            else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
