package com.familyhub.display.data.model

enum class EventType {
    BIRTHDAY,
    PARTY,
    EVENT,
    KIDS_CLASS,
    OTHER,
}

enum class EventRecurrence {
    NONE,
    WEEKLY,
    MONTHLY,
    YEARLY,
}

enum class ContentSource {
    LOCAL,
    CLOUD,
    GOOGLE,
}

data class CalendarEvent(
    val id: Long = 0,
    val title: String,
    val notes: String = "",
    val type: EventType,
    val startEpochMillis: Long,
    val endEpochMillis: Long? = null,
    val allDay: Boolean = false,
    val recurrence: EventRecurrence = EventRecurrence.NONE,
    val source: ContentSource = ContentSource.LOCAL,
    val remoteId: String? = null,
    val colorArgb: Int? = null,
    // Null = general / family-wide event.
    val memberId: Long? = null,
)

data class FamilyMember(
    val id: Long = 0,
    val name: String,
    val colorArgb: Int,
    val sortOrder: Int = 0,
)

data class PhotoItem(
    val id: Long = 0,
    val uri: String,
    val caption: String = "",
    val displayDurationSeconds: Int = 10,
    val sortOrder: Int = 0,
    val source: ContentSource = ContentSource.LOCAL,
    val remoteId: String? = null,
)
