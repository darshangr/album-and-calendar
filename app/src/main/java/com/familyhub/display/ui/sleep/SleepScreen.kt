package com.familyhub.display.ui.sleep

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import kotlinx.coroutines.delay
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * Full-black night screen shown during the sleep window. Tapping wakes the
 * display temporarily (via [onWake]).
 */
@Composable
fun SleepScreen(
    wakeHour: Int,
    onWake: () -> Unit,
) {
    var now by remember { mutableStateOf(LocalTime.now()) }

    LaunchedEffect(Unit) {
        while (true) {
            now = LocalTime.now()
            delay(30_000L)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTapGestures(onTap = { onWake() })
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = now.format(DateTimeFormatter.ofPattern("h:mm")),
                color = Color(0xFF303030),
                fontSize = 64.sp,
                textAlign = TextAlign.Center,
            )
            Text(
                text = "Sleeping until %02d:00 — tap to wake".format(wakeHour % 24),
                color = Color(0xFF262626),
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
    }
}

/** True when [now] falls inside the sleep window, correctly handling windows that cross midnight. */
fun isWithinSleepWindow(now: LocalTime, sleepStartHour: Int, wakeHour: Int): Boolean {
    val start = LocalTime.of(sleepStartHour % 24, 0)
    val end = LocalTime.of(wakeHour % 24, 0)
    return if (start <= end) {
        now >= start && now < end
    } else {
        now >= start || now < end
    }
}
