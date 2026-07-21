package com.familyhub.display.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.familyhub.display.data.model.CalendarEvent
import com.familyhub.display.data.model.EventRecurrence
import com.familyhub.display.data.model.EventType
import com.familyhub.display.ui.viewmodel.CalendarViewModel
import com.familyhub.display.util.eventTypeColor
import com.familyhub.display.util.eventTypeLabel
import com.familyhub.display.util.formatDayLabel
import com.familyhub.display.util.formatEventTime
import com.familyhub.display.util.formatMonthYear
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(
    viewModel: CalendarViewModel,
    onOpenSettings: () -> Unit,
    onSync: () -> Unit,
    onUserInteraction: () -> Unit,
    syncMessage: String?,
    onDismissSyncMessage: () -> Unit,
    isSyncing: Boolean,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(syncMessage) {
        syncMessage?.let {
            snackbarHostState.showSnackbar(it)
            onDismissSyncMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Family Calendar") },
                actions = {
                    IconButton(onClick = {
                        onUserInteraction()
                        onSync()
                    }) {
                        Icon(Icons.Default.CloudSync, contentDescription = "Sync")
                    }
                    IconButton(onClick = {
                        onUserInteraction()
                        onOpenSettings()
                    }) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                },
            )
        },
        floatingActionButton = {
            FilledTonalButton(
                onClick = {
                    onUserInteraction()
                    viewModel.showAddDialog()
                },
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Add event")
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .clickable(enabled = false, onClick = {}),
        ) {
            Column(
                modifier = Modifier
                    .weight(1.4f)
                    .fillMaxHeight()
                    .padding(16.dp),
            ) {
                MonthHeader(
                    month = uiState.visibleMonth,
                    onPrevious = {
                        onUserInteraction()
                        viewModel.goToPreviousMonth()
                    },
                    onNext = {
                        onUserInteraction()
                        viewModel.goToNextMonth()
                    },
                    onToday = {
                        onUserInteraction()
                        viewModel.goToToday()
                    },
                )
                Spacer(Modifier.height(12.dp))
                MonthGrid(
                    visibleMonth = uiState.visibleMonth,
                    selectedDay = uiState.selectedDay,
                    monthEvents = uiState.monthEvents,
                    onDaySelected = {
                        onUserInteraction()
                        viewModel.selectDay(it)
                    },
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(16.dp),
            ) {
                Text(
                    text = formatDayLabel(uiState.selectedDay),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(12.dp))
                DayEventsPanel(
                    events = uiState.dayEvents,
                    onEventClick = {
                        onUserInteraction()
                        viewModel.showEditDialog(it)
                    },
                )
                Spacer(Modifier.height(20.dp))
                Text(
                    text = "Upcoming",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(8.dp))
                UpcomingEventsPanel(
                    events = uiState.upcomingEvents,
                    onEventClick = {
                        onUserInteraction()
                        viewModel.showEditDialog(it)
                    },
                )
            }
        }
    }

    if (uiState.showAddDialog) {
        EventEditorDialog(
            initialEvent = uiState.editingEvent,
            selectedDay = uiState.selectedDay,
            onDismiss = viewModel::dismissDialog,
            onSave = viewModel::saveEvent,
            onDelete = viewModel::deleteEvent,
        )
    }

    if (isSyncing) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Text("Syncing…", style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun MonthHeader(
    month: LocalDate,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToday: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onPrevious) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous month")
            }
            IconButton(onClick = onNext) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next month")
            }
            Text(
                text = formatMonthYear(month),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
        }
        TextButton(onClick = onToday) {
            Text("Today")
        }
    }
}

@Composable
private fun MonthGrid(
    visibleMonth: LocalDate,
    selectedDay: LocalDate,
    monthEvents: List<CalendarEvent>,
    onDaySelected: (LocalDate) -> Unit,
) {
    val firstDayOfMonth = visibleMonth.withDayOfMonth(1)
    val daysInMonth = visibleMonth.lengthOfMonth()
    val leadingEmptyCells = (firstDayOfMonth.dayOfWeek.value % 7)

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth()) {
            listOf(
                DayOfWeek.SUNDAY,
                DayOfWeek.MONDAY,
                DayOfWeek.TUESDAY,
                DayOfWeek.WEDNESDAY,
                DayOfWeek.THURSDAY,
                DayOfWeek.FRIDAY,
                DayOfWeek.SATURDAY,
            ).forEach { day ->
                Text(
                    text = day.name.take(3),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
        Spacer(Modifier.height(8.dp))

        val totalCells = leadingEmptyCells + daysInMonth
        val rows = (totalCells + 6) / 7
        var dayCounter = 1

        for (row in 0 until rows) {
            Row(modifier = Modifier.fillMaxWidth()) {
                for (column in 0 until 7) {
                    val cellIndex = row * 7 + column
                    if (cellIndex < leadingEmptyCells || dayCounter > daysInMonth) {
                        Box(modifier = Modifier.weight(1f).aspectRatio(1f))
                    } else {
                        val date = visibleMonth.withDayOfMonth(dayCounter)
                        val eventsForDay = monthEvents.filter { event ->
                            val eventDay = java.time.Instant.ofEpochMilli(event.startEpochMillis)
                                .atZone(java.time.ZoneId.systemDefault())
                                .toLocalDate()
                            eventDay == date
                        }
                        DayCell(
                            day = dayCounter,
                            isSelected = date == selectedDay,
                            isToday = date == LocalDate.now(),
                            eventCount = eventsForDay.size,
                            dominantColor = eventsForDay.firstOrNull()?.let {
                                eventTypeColor(it.type, it.colorArgb)
                            },
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .clickable { onDaySelected(date) },
                        )
                        dayCounter++
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    day: Int,
    isSelected: Boolean,
    isToday: Boolean,
    eventCount: Int,
    dominantColor: androidx.compose.ui.graphics.Color?,
    modifier: Modifier = Modifier,
) {
    val background = when {
        isSelected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
        isToday -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f)
        else -> MaterialTheme.colorScheme.surface
    }

    Box(
        modifier = modifier
            .padding(2.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(background)
            .padding(6.dp),
    ) {
        Column {
            Text(
                text = day.toString(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
            )
            if (eventCount > 0 && dominantColor != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    repeat(minOf(eventCount, 3)) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(dominantColor),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DayEventsPanel(
    events: List<CalendarEvent>,
    onEventClick: (CalendarEvent) -> Unit,
) {
    if (events.isEmpty()) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Text(
                text = "No events for this day",
                modifier = Modifier.padding(16.dp),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        return
    }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(events, key = { "${it.id}-${it.startEpochMillis}" }) { event ->
            EventCard(event = event, onClick = { onEventClick(event) })
        }
    }
}

@Composable
private fun UpcomingEventsPanel(
    events: List<CalendarEvent>,
    onEventClick: (CalendarEvent) -> Unit,
) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(events, key = { "upcoming-${it.id}-${it.startEpochMillis}" }) { event ->
            EventCard(event = event, compact = true, onClick = { onEventClick(event) })
        }
    }
}

@Composable
private fun EventCard(
    event: CalendarEvent,
    compact: Boolean = false,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(if (compact) 10.dp else 14.dp)
                    .clip(CircleShape)
                    .background(eventTypeColor(event.type, event.colorArgb)),
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = event.title,
                    style = if (compact) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = buildString {
                        append(eventTypeLabel(event.type))
                        append(" • ")
                        append(formatEventTime(event.startEpochMillis, event.allDay))
                        if (event.recurrence != EventRecurrence.NONE) {
                            append(" • ")
                            append(event.recurrence.name.lowercase())
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (!compact && event.notes.isNotBlank()) {
                    Text(
                        text = event.notes,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EventEditorDialog(
    initialEvent: CalendarEvent?,
    selectedDay: LocalDate,
    onDismiss: () -> Unit,
    onSave: (CalendarEvent) -> Unit,
    onDelete: (Long) -> Unit,
) {
    val zoneId = java.time.ZoneId.systemDefault()
    val defaultStart = selectedDay.atTime(9, 0).atZone(zoneId).toInstant().toEpochMilli()

    var title by remember(initialEvent) { mutableStateOf(initialEvent?.title.orEmpty()) }
    var notes by remember(initialEvent) { mutableStateOf(initialEvent?.notes.orEmpty()) }
    var type by remember(initialEvent) { mutableStateOf(initialEvent?.type ?: EventType.EVENT) }
    var allDay by remember(initialEvent) { mutableStateOf(initialEvent?.allDay ?: false) }
    var recurrence by remember(initialEvent) { mutableStateOf(initialEvent?.recurrence ?: EventRecurrence.NONE) }
    var typeExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initialEvent == null) "Add event" else "Edit event") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes") },
                    modifier = Modifier.fillMaxWidth(),
                )
                ExposedDropdownMenuBox(expanded = typeExpanded, onExpandedChange = { typeExpanded = it }) {
                    OutlinedTextField(
                        value = eventTypeLabel(type),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Type") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = typeExpanded) },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth(),
                    )
                    ExposedDropdownMenu(expanded = typeExpanded, onDismissRequest = { typeExpanded = false }) {
                        EventType.entries.forEach { eventType ->
                            DropdownMenuItem(
                                text = { Text(eventTypeLabel(eventType)) },
                                onClick = {
                                    type = eventType
                                    typeExpanded = false
                                },
                            )
                        }
                    }
                }
                FilterChip(
                    selected = allDay,
                    onClick = { allDay = !allDay },
                    label = { Text("All day") },
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    EventRecurrence.entries.forEach { option ->
                        FilterChip(
                            selected = recurrence == option,
                            onClick = { recurrence = option },
                            label = { Text(option.name.lowercase().replaceFirstChar { it.titlecase() }) },
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isBlank()) return@Button
                    onSave(
                        CalendarEvent(
                            id = initialEvent?.id ?: 0L,
                            title = title.trim(),
                            notes = notes.trim(),
                            type = type,
                            startEpochMillis = initialEvent?.startEpochMillis ?: defaultStart,
                            endEpochMillis = initialEvent?.endEpochMillis,
                            allDay = allDay,
                            recurrence = recurrence,
                            source = initialEvent?.source ?: com.familyhub.display.data.model.ContentSource.LOCAL,
                            remoteId = initialEvent?.remoteId,
                            colorArgb = initialEvent?.colorArgb,
                        ),
                    )
                },
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            Row {
                if (initialEvent != null) {
                    TextButton(onClick = { onDelete(initialEvent.id) }) {
                        Text("Delete")
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        },
    )
}
