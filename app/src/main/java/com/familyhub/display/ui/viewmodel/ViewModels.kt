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
            if (container.googleAuthManager.getSignedInAccount() != null) {
                syncNow()
            }
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
        viewModelScope.launch {
            _uiState.update { it.copy(isSyncing = true, syncMessage = null) }
            val result = container.syncRepository.syncNow()
            _uiState.update {
                it.copy(
                    isSyncing = false,
                    syncMessage = when {
                        result.isSuccess && result.getOrNull() == com.familyhub.display.data.repository.SyncSource.GOOGLE ->
                            "Google Calendar and Photos synced"
                        result.isSuccess -> "Sync completed"
                        else -> result.exceptionOrNull()?.message ?: "Sync failed. Check settings."
                    },
                )
            }
        }
    }

    fun signInWithGoogle() = container.googleAuthManager.getSignInIntent()

    fun handleGoogleSignInResult(data: android.content.Intent?) {
        container.googleAuthManager.handleSignInResult(data)
        viewModelScope.launch {
            syncNow()
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

data class CalendarUiState(
    val visibleMonth: LocalDate = LocalDate.now(),
    val selectedDay: LocalDate = LocalDate.now(),
    val monthEvents: List<CalendarEvent> = emptyList(),
    val dayEvents: List<CalendarEvent> = emptyList(),
    val upcomingEvents: List<CalendarEvent> = emptyList(),
    val showAddDialog: Boolean = false,
    val editingEvent: CalendarEvent? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
class CalendarViewModel(
    private val container: AppContainer,
) : ViewModel() {
    private val visibleMonth = MutableStateFlow(LocalDate.now())
    private val selectedDay = MutableStateFlow(LocalDate.now())
    private val dialogState = MutableStateFlow<Pair<Boolean, CalendarEvent?>>(false to null)

    val uiState: StateFlow<CalendarUiState> = combine(
        visibleMonth,
        selectedDay,
        dialogState,
    ) { month, day, dialog ->
        Triple(month, day, dialog)
    }.flatMapLatest { (month, day, dialog) ->
        combine(
            container.calendarRepository.observeEventsForMonth(month),
            container.calendarRepository.observeEventsForDay(day),
            container.calendarRepository.observeAllEvents().map { allEvents ->
                allEvents
                    .filter { it.startEpochMillis >= System.currentTimeMillis() }
                    .sortedBy { it.startEpochMillis }
                    .take(8)
            },
        ) { monthEvents, dayEvents, upcoming ->
            CalendarUiState(
                visibleMonth = month,
                selectedDay = day,
                monthEvents = monthEvents,
                dayEvents = dayEvents,
                upcomingEvents = upcoming,
                showAddDialog = dialog.first,
                editingEvent = dialog.second,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CalendarUiState())

    fun selectDay(day: LocalDate) {
        selectedDay.value = day
    }

    fun goToPreviousMonth() {
        visibleMonth.value = visibleMonth.value.minusMonths(1)
    }

    fun goToNextMonth() {
        visibleMonth.value = visibleMonth.value.plusMonths(1)
    }

    fun goToToday() {
        val today = LocalDate.now()
        visibleMonth.value = today
        selectedDay.value = today
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
