package com.familyhub.display.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.familyhub.display.data.model.ContentSource
import com.familyhub.display.data.model.EventRecurrence
import com.familyhub.display.data.model.EventType

@Entity(tableName = "calendar_events")
data class CalendarEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val notes: String,
    val type: String,
    val startEpochMillis: Long,
    val endEpochMillis: Long?,
    val allDay: Boolean,
    val recurrence: String,
    val source: String,
    val remoteId: String?,
    val colorArgb: Int?,
    val memberId: Long? = null,
)

@Entity(tableName = "family_members")
data class FamilyMemberEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val colorArgb: Int,
    val sortOrder: Int,
)

@Entity(tableName = "photo_items")
data class PhotoItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uri: String,
    val caption: String,
    val displayDurationSeconds: Int,
    val sortOrder: Int,
    val source: String,
    val remoteId: String?,
)

fun CalendarEventEntity.toDomain() = com.familyhub.display.data.model.CalendarEvent(
    id = id,
    title = title,
    notes = notes,
    type = EventType.valueOf(type),
    startEpochMillis = startEpochMillis,
    endEpochMillis = endEpochMillis,
    allDay = allDay,
    recurrence = EventRecurrence.valueOf(recurrence),
    source = ContentSource.valueOf(source),
    remoteId = remoteId,
    colorArgb = colorArgb,
    memberId = memberId,
)

fun com.familyhub.display.data.model.CalendarEvent.toEntity() = CalendarEventEntity(
    id = id,
    title = title,
    notes = notes,
    type = type.name,
    startEpochMillis = startEpochMillis,
    endEpochMillis = endEpochMillis,
    allDay = allDay,
    recurrence = recurrence.name,
    source = source.name,
    remoteId = remoteId,
    colorArgb = colorArgb,
    memberId = memberId,
)

fun FamilyMemberEntity.toDomain() = com.familyhub.display.data.model.FamilyMember(
    id = id,
    name = name,
    colorArgb = colorArgb,
    sortOrder = sortOrder,
)

fun com.familyhub.display.data.model.FamilyMember.toEntity() = FamilyMemberEntity(
    id = id,
    name = name,
    colorArgb = colorArgb,
    sortOrder = sortOrder,
)

fun PhotoItemEntity.toDomain() = com.familyhub.display.data.model.PhotoItem(
    id = id,
    uri = uri,
    caption = caption,
    displayDurationSeconds = displayDurationSeconds,
    sortOrder = sortOrder,
    source = ContentSource.valueOf(source),
    remoteId = remoteId,
)

fun com.familyhub.display.data.model.PhotoItem.toEntity() = PhotoItemEntity(
    id = id,
    uri = uri,
    caption = caption,
    displayDurationSeconds = displayDurationSeconds,
    sortOrder = sortOrder,
    source = source.name,
    remoteId = remoteId,
)
