package com.familyhub.display.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Slideshow
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
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.familyhub.display.data.model.CalendarEvent
import com.familyhub.display.data.model.EventRecurrence
import com.familyhub.display.data.model.EventType
import com.familyhub.display.data.model.FamilyMember
import com.familyhub.display.ui.theme.GeneralEventColor
import com.familyhub.display.ui.viewmodel.CalendarViewMode
import com.familyhub.display.ui.viewmodel.CalendarViewModel
import com.familyhub.display.ui.viewmodel.weekStart
import com.familyhub.display.util.eventTypeLabel
import com.familyhub.display.util.formatDayLabel
import com.familyhub.display.util.formatEventTime
import com.familyhub.display.util.formatMonthYear
import com.familyhub.display.util.formatWeekRange
import android.app.TimePickerDialog
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val zone: ZoneId = ZoneId.systemDefault()

private fun eventDate(event: CalendarEvent): LocalDate =
    Instant.ofEpochMilli(event.startEpochMillis).atZone(zone).toLocalDate()

private fun colorForEvent(event: CalendarEvent, members: List<FamilyMember>): Color {
    val argb = members.firstOrNull { it.id == event.memberId }?.colorArgb
    return Color(argb ?: GeneralEventColor)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(
    viewModel: CalendarViewModel,
    onOpenSettings: () -> Unit,
    onSync: () -> Unit,
    onStartSlideshow: () -> Unit,
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

    val title = when (uiState.viewMode) {
        CalendarViewMode.WEEK -> formatWeekRange(weekStart(uiState.anchorDate))
        CalendarViewMode.MONTH -> formatMonthYear(uiState.anchorDate)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Family Calendar") },
                actions = {
                    IconButton(onClick = { onUserInteraction(); onStartSlideshow() }) {
                        Icon(Icons.Default.Slideshow, contentDescription = "Start photo slideshow")
                    }
                    IconButton(onClick = { onUserInteraction(); onSync() }) {
                        Icon(Icons.Default.CloudSync, contentDescription = "Sync")
                    }
                    IconButton(onClick = { onUserInteraction(); onOpenSettings() }) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                },
            )
        },
        floatingActionButton = {
            FilledTonalButton(onClick = { onUserInteraction(); viewModel.showAddDialog() }) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Add event")
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Row(modifier = Modifier.fillMaxSize().padding(padding)) {
            Column(
                modifier = Modifier
                    .weight(1.55f)
                    .fillMaxHeight()
                    .padding(16.dp),
            ) {
                CalendarHeader(
                    title = title,
                    viewMode = uiState.viewMode,
                    onPrevious = { onUserInteraction(); viewModel.goToPrevious() },
                    onNext = { onUserInteraction(); viewModel.goToNext() },
                    onToday = { onUserInteraction(); viewModel.goToToday() },
                    onSelectMode = { onUserInteraction(); viewModel.setViewMode(it) },
                )
                Spacer(Modifier.height(12.dp))
                when (uiState.viewMode) {
                    CalendarViewMode.WEEK -> WeekView(
                        weekStart = weekStart(uiState.anchorDate),
                        selectedDay = uiState.selectedDay,
                        events = uiState.rangeEvents,
                        members = uiState.members,
                        onSelectDay = { onUserInteraction(); viewModel.selectDay(it) },
                        onEventClick = { onUserInteraction(); viewModel.showEditDialog(it) },
                    )
                    CalendarViewMode.MONTH -> MonthGrid(
                        visibleMonth = uiState.anchorDate,
                        selectedDay = uiState.selectedDay,
                        monthEvents = uiState.rangeEvents,
                        members = uiState.members,
                        onDaySelected = { onUserInteraction(); viewModel.selectDay(it) },
                    )
                }
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
                MemberGroupedDay(
                    day = uiState.selectedDay,
                    events = uiState.rangeEvents,
                    members = uiState.members,
                    onEventClick = { onUserInteraction(); viewModel.showEditDialog(it) },
                )
                Spacer(Modifier.height(20.dp))
                Text("Upcoming", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(uiState.upcomingEvents, key = { "up-${it.id}-${it.startEpochMillis}" }) { event ->
                        EventRowCard(
                            event = event,
                            color = colorForEvent(event, uiState.members),
                            compact = true,
                            onClick = { onUserInteraction(); viewModel.showEditDialog(event) },
                        )
                    }
                }
            }
        }
    }

    if (uiState.showAddDialog) {
        EventEditorDialog(
            initialEvent = uiState.editingEvent,
            selectedDay = uiState.selectedDay,
            members = uiState.members,
            onAddMember = viewModel::addMember,
            onDismiss = viewModel::dismissDialog,
            onSave = viewModel::saveEvent,
            onDelete = viewModel::deleteEvent,
        )
    }

    if (isSyncing) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Syncing…", style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun CalendarHeader(
    title: String,
    viewMode: CalendarViewMode,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToday: () -> Unit,
    onSelectMode: (CalendarViewMode) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onPrevious) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous")
            }
            IconButton(onClick = onNext) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next")
            }
            Text(text = title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = viewMode == CalendarViewMode.WEEK,
                onClick = { onSelectMode(CalendarViewMode.WEEK) },
                label = { Text("Week") },
            )
            FilterChip(
                selected = viewMode == CalendarViewMode.MONTH,
                onClick = { onSelectMode(CalendarViewMode.MONTH) },
                label = { Text("Month") },
            )
            TextButton(onClick = onToday) { Text("Today") }
        }
    }
}

@Composable
private fun WeekView(
    weekStart: LocalDate,
    selectedDay: LocalDate,
    events: List<CalendarEvent>,
    members: List<FamilyMember>,
    onSelectDay: (LocalDate) -> Unit,
    onEventClick: (CalendarEvent) -> Unit,
) {
    Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        for (i in 0 until 7) {
            val date = weekStart.plusDays(i.toLong())
            val dayEvents = events.filter { eventDate(it) == date }
            DayColumn(
                date = date,
                isSelected = date == selectedDay,
                isToday = date == LocalDate.now(),
                events = dayEvents,
                members = members,
                onSelect = { onSelectDay(date) },
                onEventClick = onEventClick,
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
        }
    }
}

@Composable
private fun DayColumn(
    date: LocalDate,
    isSelected: Boolean,
    isToday: Boolean,
    events: List<CalendarEvent>,
    members: List<FamilyMember>,
    onSelect: () -> Unit,
    onEventClick: (CalendarEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val headerBg = when {
        isSelected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
        isToday -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f)
        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
    }
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                .background(headerBg)
                .clickable(onClick = onSelect)
                .padding(vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = date.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.getDefault()),
                style = MaterialTheme.typography.labelMedium,
            )
            Text(
                text = date.dayOfMonth.toString(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
            )
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            events.forEach { event ->
                EventChip(
                    event = event,
                    color = colorForEvent(event, members),
                    onClick = { onEventClick(event) },
                )
            }
        }
    }
}

@Composable
private fun EventChip(event: CalendarEvent, color: Color, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.16f))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(color))
            Spacer(Modifier.width(6.dp))
            Text(
                text = if (event.allDay) "All day" else formatEventTime(event.startEpochMillis, event.allDay),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = event.title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun MemberGroupedDay(
    day: LocalDate,
    events: List<CalendarEvent>,
    members: List<FamilyMember>,
    onEventClick: (CalendarEvent) -> Unit,
) {
    val dayEvents = events.filter { eventDate(it) == day }
    if (dayEvents.isEmpty()) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Text("No events for this day", modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.bodyLarge)
        }
        return
    }

    // Group: each member (with events that day) in order, then General/Family last.
    val byMember: Map<Long?, List<CalendarEvent>> = dayEvents.groupBy { it.memberId }
    val orderedGroups = buildList {
        members.forEach { member ->
            byMember[member.id]?.let { add(member to it) }
        }
        // General/family events, plus any whose member was removed.
        val generalEvents = dayEvents.filter { e -> members.none { it.id == e.memberId } }
        if (generalEvents.isNotEmpty()) add(null to generalEvents)
    }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        orderedGroups.forEach { (member, groupEvents) ->
            item(key = "hdr-${member?.id ?: "general"}") {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 4.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(Color(member?.colorArgb ?: GeneralEventColor)),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = member?.name ?: "General / Family",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            items(groupEvents, key = { "ev-${it.id}-${it.startEpochMillis}" }) { event ->
                EventRowCard(
                    event = event,
                    color = Color(member?.colorArgb ?: GeneralEventColor),
                    compact = false,
                    onClick = { onEventClick(event) },
                )
            }
        }
    }
}

@Composable
private fun EventRowCard(
    event: CalendarEvent,
    color: Color,
    compact: Boolean,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(if (compact) 10.dp else 14.dp).clip(CircleShape).background(color))
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

@Composable
private fun MonthGrid(
    visibleMonth: LocalDate,
    selectedDay: LocalDate,
    monthEvents: List<CalendarEvent>,
    members: List<FamilyMember>,
    onDaySelected: (LocalDate) -> Unit,
) {
    val firstDayOfMonth = visibleMonth.withDayOfMonth(1)
    val daysInMonth = visibleMonth.lengthOfMonth()
    val leadingEmptyCells = firstDayOfMonth.dayOfWeek.value % 7

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth()) {
            listOf(
                DayOfWeek.SUNDAY, DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
                DayOfWeek.THURSDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY,
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
                        val eventsForDay = monthEvents.filter { eventDate(it) == date }
                        MonthDayCell(
                            day = date.dayOfMonth,
                            isSelected = date == selectedDay,
                            isToday = date == LocalDate.now(),
                            dotColors = eventsForDay.take(4).map { colorForEvent(it, members) },
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
private fun MonthDayCell(
    day: Int,
    isSelected: Boolean,
    isToday: Boolean,
    dotColors: List<Color>,
    modifier: Modifier = Modifier,
) {
    val background = when {
        isSelected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
        isToday -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f)
        else -> MaterialTheme.colorScheme.surface
    }
    Box(modifier = modifier.padding(2.dp).clip(RoundedCornerShape(12.dp)).background(background).padding(6.dp)) {
        Column {
            Text(
                text = day.toString(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                dotColors.forEach { c ->
                    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(c))
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
    members: List<FamilyMember>,
    onAddMember: (String) -> Unit,
    onDismiss: () -> Unit,
    onSave: (CalendarEvent) -> Unit,
    onDelete: (Long) -> Unit,
) {
    val context = LocalContext.current
    val timeFormatter = remember { DateTimeFormatter.ofPattern("h:mm a") }

    // The event keeps its date; only the time-of-day is edited here.
    val baseDate = remember(initialEvent) {
        initialEvent?.let { Instant.ofEpochMilli(it.startEpochMillis).atZone(zone).toLocalDate() } ?: selectedDay
    }

    var title by remember(initialEvent) { mutableStateOf(initialEvent?.title.orEmpty()) }
    var notes by remember(initialEvent) { mutableStateOf(initialEvent?.notes.orEmpty()) }
    var type by remember(initialEvent) { mutableStateOf(initialEvent?.type ?: EventType.EVENT) }
    var allDay by remember(initialEvent) { mutableStateOf(initialEvent?.allDay ?: false) }
    var recurrence by remember(initialEvent) { mutableStateOf(initialEvent?.recurrence ?: EventRecurrence.NONE) }
    var memberId by remember(initialEvent) { mutableStateOf(initialEvent?.memberId) }
    var typeExpanded by remember { mutableStateOf(false) }

    var startTime by remember(initialEvent) {
        mutableStateOf(
            initialEvent?.let { Instant.ofEpochMilli(it.startEpochMillis).atZone(zone).toLocalTime() }
                ?: LocalTime.of(9, 0),
        )
    }
    var endTime by remember(initialEvent) {
        mutableStateOf(
            initialEvent?.endEpochMillis?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalTime() }
                ?: startTime.plusHours(1),
        )
    }

    // Inline "add member": remember the name to auto-select once it appears.
    var addingMember by remember { mutableStateOf(false) }
    var newMemberName by remember { mutableStateOf("") }
    var pendingSelectName by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(members) {
        pendingSelectName?.let { name ->
            members.firstOrNull { it.name.equals(name, ignoreCase = true) }?.let {
                memberId = it.id
                pendingSelectName = null
            }
        }
    }

    fun showTimePicker(initial: LocalTime, onPicked: (LocalTime) -> Unit) {
        TimePickerDialog(
            context,
            { _, hour, minute -> onPicked(LocalTime.of(hour, minute)) },
            initial.hour,
            initial.minute,
            false,
        ).show()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initialEvent == null) "Add event" else "Edit event") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) {
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

                FilterChip(selected = allDay, onClick = { allDay = !allDay }, label = { Text("All day") })
                if (!allDay) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(onClick = { showTimePicker(startTime) { startTime = it } }) {
                            Text("Start: ${startTime.format(timeFormatter)}")
                        }
                        OutlinedButton(onClick = { showTimePicker(endTime) { endTime = it } }) {
                            Text("End: ${endTime.format(timeFormatter)}")
                        }
                    }
                }

                Text("Who", style = MaterialTheme.typography.labelLarge)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    (listOf<FamilyMember?>(null) + members).chunked(3).forEach { rowItems ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            rowItems.forEach { member ->
                                FilterChip(
                                    selected = memberId == member?.id,
                                    onClick = { memberId = member?.id },
                                    leadingIcon = {
                                        Box(
                                            modifier = Modifier
                                                .size(12.dp)
                                                .clip(CircleShape)
                                                .background(Color(member?.colorArgb ?: GeneralEventColor)),
                                        )
                                    },
                                    label = { Text(member?.name ?: "General / Family") },
                                )
                            }
                        }
                    }
                }
                if (addingMember) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedTextField(
                            value = newMemberName,
                            onValueChange = { newMemberName = it },
                            label = { Text("New member name") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        Button(
                            onClick = {
                                val name = newMemberName.trim()
                                if (name.isNotEmpty()) {
                                    onAddMember(name)
                                    pendingSelectName = name
                                }
                                newMemberName = ""
                                addingMember = false
                            },
                            enabled = newMemberName.isNotBlank(),
                        ) { Text("Add") }
                    }
                } else {
                    TextButton(onClick = { addingMember = true }) { Text("+ Add family member") }
                }

                ExposedDropdownMenuBox(expanded = typeExpanded, onExpandedChange = { typeExpanded = it }) {
                    OutlinedTextField(
                        value = eventTypeLabel(type),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Type") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = typeExpanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth(),
                    )
                    ExposedDropdownMenu(expanded = typeExpanded, onDismissRequest = { typeExpanded = false }) {
                        EventType.entries.forEach { eventType ->
                            DropdownMenuItem(
                                text = { Text(eventTypeLabel(eventType)) },
                                onClick = { type = eventType; typeExpanded = false },
                            )
                        }
                    }
                }

                Text("Repeats", style = MaterialTheme.typography.labelLarge)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                ) {
                    EventRecurrence.entries.forEach { option ->
                        FilterChip(
                            selected = recurrence == option,
                            onClick = { recurrence = option },
                            leadingIcon = if (recurrence == option) {
                                { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                            } else {
                                null
                            },
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
                    val startMillis = if (allDay) {
                        baseDate.atStartOfDay(zone).toInstant().toEpochMilli()
                    } else {
                        baseDate.atTime(startTime).atZone(zone).toInstant().toEpochMilli()
                    }
                    val endMillis = if (allDay || !endTime.isAfter(startTime)) {
                        null
                    } else {
                        baseDate.atTime(endTime).atZone(zone).toInstant().toEpochMilli()
                    }
                    onSave(
                        CalendarEvent(
                            id = initialEvent?.id ?: 0L,
                            title = title.trim(),
                            notes = notes.trim(),
                            type = type,
                            startEpochMillis = startMillis,
                            endEpochMillis = endMillis,
                            allDay = allDay,
                            recurrence = recurrence,
                            source = initialEvent?.source ?: com.familyhub.display.data.model.ContentSource.LOCAL,
                            remoteId = initialEvent?.remoteId,
                            colorArgb = initialEvent?.colorArgb,
                            memberId = memberId,
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
                    TextButton(onClick = { onDelete(initialEvent.id) }) { Text("Delete") }
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}
