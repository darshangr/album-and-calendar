package com.familyhub.display.util

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
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

private const val DOUBLE_TAP_TIMEOUT_MS = 350L

@Composable
fun Modifier.detectDoubleTap(onDoubleTap: () -> Unit): Modifier {
    var lastTapEpochMillis by remember { mutableLongStateOf(0L) }

    return pointerInput(Unit) {
        detectTapGestures(
            onTap = {
                val now = System.currentTimeMillis()
                if (now - lastTapEpochMillis <= DOUBLE_TAP_TIMEOUT_MS) {
                    onDoubleTap()
                    lastTapEpochMillis = 0L
                } else {
                    lastTapEpochMillis = now
                }
            },
        )
    }
}

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
