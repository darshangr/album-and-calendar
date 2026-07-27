package com.familyhub.display.util

import com.familyhub.display.data.model.EventType
import com.familyhub.display.ui.theme.EventBirthday
import com.familyhub.display.ui.theme.EventClass
import com.familyhub.display.ui.theme.EventDefault
import com.familyhub.display.ui.theme.EventOther
import com.familyhub.display.ui.theme.EventParty
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

fun formatEventTime(epochMillis: Long, allDay: Boolean, zoneId: ZoneId = ZoneId.systemDefault()): String {
    if (allDay) {
        return "All day"
    }
    val formatter = DateTimeFormatter.ofPattern("h:mm a")
    return Instant.ofEpochMilli(epochMillis).atZone(zoneId).format(formatter)
}

fun formatMonthYear(month: LocalDate): String {
    return month.month.getDisplayName(TextStyle.FULL, Locale.getDefault()) + " " + month.year
}

fun formatWeekRange(weekStart: LocalDate): String {
    val weekEnd = weekStart.plusDays(6)
    val startFmt = DateTimeFormatter.ofPattern("MMM d")
    return if (weekStart.month == weekEnd.month) {
        "${weekStart.format(startFmt)} – ${weekEnd.dayOfMonth}, ${weekEnd.year}"
    } else {
        "${weekStart.format(startFmt)} – ${weekEnd.format(startFmt)}, ${weekEnd.year}"
    }
}

fun eventColorArgb(memberId: Long?, memberColors: Map<Long, Int>): Int {
    return memberId?.let { memberColors[it] } ?: com.familyhub.display.ui.theme.GeneralEventColor
}

fun formatDayLabel(day: LocalDate): String {
    val formatter = DateTimeFormatter.ofPattern("EEEE, MMM d")
    return day.format(formatter)
}

fun eventTypeColor(type: EventType, customColor: Int? = null): androidx.compose.ui.graphics.Color {
    if (customColor != null) {
        return androidx.compose.ui.graphics.Color(customColor)
    }
    return when (type) {
        EventType.BIRTHDAY -> EventBirthday
        EventType.PARTY -> EventParty
        EventType.EVENT -> EventDefault
        EventType.KIDS_CLASS -> EventClass
        EventType.OTHER -> EventOther
    }
}

fun eventTypeLabel(type: EventType): String {
    return when (type) {
        EventType.BIRTHDAY -> "Birthday"
        EventType.PARTY -> "Party"
        EventType.EVENT -> "Event"
        EventType.KIDS_CLASS -> "Kids class"
        EventType.OTHER -> "Other"
    }
}
