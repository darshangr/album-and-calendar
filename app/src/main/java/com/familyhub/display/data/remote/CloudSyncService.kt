package com.familyhub.display.data.remote

import com.familyhub.display.data.model.CalendarEvent
import com.familyhub.display.data.model.ContentSource
import com.familyhub.display.data.model.EventRecurrence
import com.familyhub.display.data.model.EventType
import com.familyhub.display.data.model.PhotoItem
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Header
import java.util.concurrent.TimeUnit

@JsonClass(generateAdapter = true)
data class RemoteCalendarEventDto(
    @Json(name = "id") val id: String,
    @Json(name = "title") val title: String,
    @Json(name = "notes") val notes: String? = null,
    @Json(name = "type") val type: String,
    @Json(name = "start_epoch_millis") val startEpochMillis: Long,
    @Json(name = "end_epoch_millis") val endEpochMillis: Long? = null,
    @Json(name = "all_day") val allDay: Boolean = false,
    @Json(name = "recurrence") val recurrence: String = "NONE",
    @Json(name = "color_argb") val colorArgb: Int? = null,
)

@JsonClass(generateAdapter = true)
data class RemotePhotoDto(
    @Json(name = "id") val id: String,
    @Json(name = "url") val url: String,
    @Json(name = "caption") val caption: String? = null,
    @Json(name = "display_duration_seconds") val displayDurationSeconds: Int = 10,
    @Json(name = "sort_order") val sortOrder: Int = 0,
)

@JsonClass(generateAdapter = true)
data class RemoteSyncPayload(
    @Json(name = "events") val events: List<RemoteCalendarEventDto> = emptyList(),
    @Json(name = "photos") val photos: List<RemotePhotoDto> = emptyList(),
)

interface FamilyHubApi {
    @GET("sync")
    suspend fun fetchSyncPayload(
        @Header("Authorization") authorization: String,
    ): RemoteSyncPayload
}

class CloudSyncService(
    private val baseUrl: String,
    private val apiKey: String,
) {
    private val api: FamilyHubApi by lazy {
        val authInterceptor = Interceptor { chain ->
            val request = chain.request().newBuilder()
                .header("Authorization", "Bearer $apiKey")
                .build()
            chain.proceed(request)
        }

        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }

        val client = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(authInterceptor)
            .addInterceptor(logging)
            .build()

        val moshi = Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .build()

        Retrofit.Builder()
            .baseUrl(normalizeBaseUrl(baseUrl))
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(FamilyHubApi::class.java)
    }

    suspend fun fetchRemoteContent(): Pair<List<CalendarEvent>, List<PhotoItem>> {
        if (baseUrl.isBlank() || apiKey.isBlank()) {
            throw IllegalStateException("Cloud sync is not configured")
        }

        val payload = api.fetchSyncPayload("Bearer $apiKey")
        val events = payload.events.map { it.toDomain() }
        val photos = payload.photos.map { it.toDomain() }
        return events to photos
    }

    private fun normalizeBaseUrl(url: String): String {
        val trimmed = url.trim()
        return if (trimmed.endsWith("/")) trimmed else "$trimmed/"
    }
}

private fun RemoteCalendarEventDto.toDomain(): CalendarEvent {
    return CalendarEvent(
        title = title,
        notes = notes.orEmpty(),
        type = runCatching { EventType.valueOf(type.uppercase()) }.getOrDefault(EventType.OTHER),
        startEpochMillis = startEpochMillis,
        endEpochMillis = endEpochMillis,
        allDay = allDay,
        recurrence = runCatching { EventRecurrence.valueOf(recurrence.uppercase()) }
            .getOrDefault(EventRecurrence.NONE),
        source = ContentSource.CLOUD,
        remoteId = id,
        colorArgb = colorArgb,
    )
}

private fun RemotePhotoDto.toDomain(): PhotoItem {
    return PhotoItem(
        uri = url,
        caption = caption.orEmpty(),
        displayDurationSeconds = displayDurationSeconds,
        sortOrder = sortOrder,
        source = ContentSource.CLOUD,
        remoteId = id,
    )
}
