package com.familyhub.display.data.google

import com.familyhub.display.data.model.ContentSource
import com.familyhub.display.data.model.PhotoItem
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

@JsonClass(generateAdapter = true)
data class PhotosSearchRequest(
    @Json(name = "pageSize") val pageSize: Int = 100,
    @Json(name = "pageToken") val pageToken: String? = null,
    @Json(name = "filters") val filters: PhotosFilters = PhotosFilters(),
)

@JsonClass(generateAdapter = true)
data class PhotosFilters(
    @Json(name = "mediaTypeFilter") val mediaTypeFilter: MediaTypeFilter = MediaTypeFilter(),
)

@JsonClass(generateAdapter = true)
data class MediaTypeFilter(
    @Json(name = "mediaTypes") val mediaTypes: List<String> = listOf("PHOTO"),
)

@JsonClass(generateAdapter = true)
data class PhotosSearchResponse(
    @Json(name = "mediaItems") val mediaItems: List<GoogleMediaItem>? = null,
    @Json(name = "nextPageToken") val nextPageToken: String? = null,
)

@JsonClass(generateAdapter = true)
data class GoogleMediaItem(
    @Json(name = "id") val id: String,
    @Json(name = "baseUrl") val baseUrl: String,
    @Json(name = "filename") val filename: String? = null,
    @Json(name = "description") val description: String? = null,
)

class GooglePhotosSyncService(
    private val authManager: GoogleAuthManager,
) {
    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val searchRequestAdapter = moshi.adapter(PhotosSearchRequest::class.java)
    private val searchResponseAdapter = moshi.adapter(PhotosSearchResponse::class.java)

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun fetchPhotos(
        maxPhotos: Int = 100,
        defaultDurationSeconds: Int = 10,
    ): List<PhotoItem> = withContext(Dispatchers.IO) {
        val accessToken = authManager.getAccessToken()
            ?: throw IllegalStateException("Not signed in to Google")

        val photos = mutableListOf<PhotoItem>()
        var pageToken: String? = null
        var sortOrder = 0

        while (photos.size < maxPhotos) {
            val requestBody = searchRequestAdapter.toJson(
                PhotosSearchRequest(pageSize = 100, pageToken = pageToken),
            ).toRequestBody("application/json".toMediaType())

            val request = Request.Builder()
                .url("https://photoslibrary.googleapis.com/v1/mediaItems:search")
                .addHeader("Authorization", "Bearer $accessToken")
                .post(requestBody)
                .build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                throw IllegalStateException("Google Photos sync failed (${response.code})")
            }

            val body = response.body?.string().orEmpty()
            val parsed = searchResponseAdapter.fromJson(body)
                ?: throw IllegalStateException("Invalid Google Photos response")

            parsed.mediaItems.orEmpty().forEach { item ->
                if (photos.size >= maxPhotos) return@forEach
                photos += PhotoItem(
                    uri = "${item.baseUrl}=w2048-h2048",
                    caption = item.description ?: item.filename.orEmpty(),
                    displayDurationSeconds = defaultDurationSeconds,
                    sortOrder = sortOrder++,
                    source = ContentSource.GOOGLE,
                    remoteId = "gphoto:${item.id}",
                )
            }

            pageToken = parsed.nextPageToken
            if (pageToken.isNullOrBlank()) break
        }

        return@withContext photos
    }
}
