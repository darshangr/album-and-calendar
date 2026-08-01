package com.familyhub.display.data.google

import com.familyhub.display.data.model.CalendarEvent
import com.familyhub.display.data.model.ContentSource
import com.familyhub.display.data.model.EventRecurrence
import com.familyhub.display.data.model.EventType
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.client.util.DateTime
import com.google.api.services.calendar.Calendar
import com.google.api.services.calendar.model.Events
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

class GoogleCalendarSyncService(
    private val authManager: GoogleAuthManager,
) {
    suspend fun fetchEvents(
        daysBack: Long = 7,
        daysForward: Long = 365,
    ): List<CalendarEvent> = withContext(Dispatchers.IO) {
        val credential = authManager.getCredential()
            ?: throw IllegalStateException("Not signed in to Google")

        val transport = GoogleNetHttpTransport.newTrustedTransport()
        val calendarService = Calendar.Builder(transport, GsonFactory.getDefaultInstance(), credential)
            .setApplicationName("Family Hub")
            .build()

        val now = System.currentTimeMillis()
        val timeMin = DateTime(now - TimeUnit.DAYS.toMillis(daysBack))
        val timeMax = DateTime(now + TimeUnit.DAYS.toMillis(daysForward))

        val calendarList = calendarService.calendarList().list().execute()
        val events = mutableListOf<CalendarEvent>()

        for (calendar in calendarList.items.orEmpty()) {
            if (calendar.accessRole == "freeBusyReader") continue

            val calendarId = calendar.id ?: continue
            val calendarName = calendar.summary.orEmpty()
            var pageToken: String? = null

            do {
                val response: Events = calendarService.events().list(calendarId)
                    .setTimeMin(timeMin)
                    .setTimeMax(timeMax)
                    .setSingleEvents(true)
                    .setOrderBy("startTime")
                    .setMaxResults(250)
                    .apply { pageToken?.let { setPageToken(it) } }
                    .execute()

                response.items.orEmpty().forEach { item ->
                    val startMillis = item.start?.dateTime?.value
                        ?: item.start?.date?.value
                        ?: return@forEach
                    val endMillis = item.end?.dateTime?.value ?: item.end?.date?.value
                    val allDay = item.start?.dateTime == null && item.start?.date != null

                    events += CalendarEvent(
                        title = item.summary ?: "Untitled",
                        notes = item.description.orEmpty(),
                        type = inferEventType(item.summary, calendarName),
                        startEpochMillis = startMillis,
                        endEpochMillis = endMillis,
                        allDay = allDay,
                        recurrence = inferRecurrence(item.recurrence),
                        source = ContentSource.GOOGLE,
                        remoteId = "gcal:${item.id}",
                        colorArgb = calendar.backgroundColor?.let { parseColor(it) },
                    )
                }

                pageToken = response.nextPageToken
            } while (pageToken != null)
        }

        return@withContext events.distinctBy { it.remoteId }
    }

    private fun inferEventType(summary: String?, calendarName: String): EventType {
        val text = "${summary.orEmpty()} ${calendarName}".lowercase()
        return when {
            listOf("birthday", "bday", "born").any { it in text } -> EventType.BIRTHDAY
            listOf("party", "bbq", "celebration", "shower").any { it in text } -> EventType.PARTY
            listOf("class", "lesson", "practice", "soccer", "piano", "tutor", "school").any { it in text } ->
                EventType.KIDS_CLASS
            listOf("appointment", "doctor", "dentist", "meeting", "event").any { it in text } -> EventType.EVENT
            else -> EventType.OTHER
        }
    }

    private fun inferRecurrence(recurrenceRules: List<String>?): EventRecurrence {
        val rule = recurrenceRules?.firstOrNull()?.uppercase().orEmpty()
        return when {
            "FREQ=YEARLY" in rule -> EventRecurrence.YEARLY
            "FREQ=MONTHLY" in rule -> EventRecurrence.MONTHLY
            "FREQ=WEEKLY" in rule -> EventRecurrence.WEEKLY
            else -> EventRecurrence.NONE
        }
    }

    private fun parseColor(hex: String): Int? {
        return runCatching {
            android.graphics.Color.parseColor(hex)
        }.getOrNull()
    }
}
