package com.familyhub.display.data.google

import android.content.Context
import android.net.Uri
import android.util.Log
import com.familyhub.display.data.model.ContentSource
import com.familyhub.display.data.model.PhotoItem
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

class GoogleDriveException(message: String) : Exception(message)

@JsonClass(generateAdapter = true)
data class DriveFileListResponse(
    @Json(name = "files") val files: List<DriveFile>? = null,
    @Json(name = "nextPageToken") val nextPageToken: String? = null,
)

@JsonClass(generateAdapter = true)
data class DriveFile(
    @Json(name = "id") val id: String,
    @Json(name = "name") val name: String,
    @Json(name = "mimeType") val mimeType: String? = null,
    @Json(name = "modifiedTime") val modifiedTime: String? = null,
)

/**
 * Syncs images from a shared Google Drive folder and caches them on device so the
 * slideshow keeps working offline. The family shares one Drive folder with the
 * signed-in account; new images added to that folder appear on the next sync.
 */
class GoogleDriveSyncService(
    private val context: Context,
    private val authManager: GoogleAuthManager,
) {
    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val listAdapter = moshi.adapter(DriveFileListResponse::class.java)

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    suspend fun fetchPhotos(
        folderInput: String,
        defaultDurationSeconds: Int = 10,
        maxPhotos: Int = 200,
    ): List<PhotoItem> = withContext(Dispatchers.IO) {
        val folderId = parseFolderId(folderInput)
            ?: throw GoogleDriveException("Enter a valid Google Drive folder link or ID")
        val token = authManager.getAccessToken()
            ?: throw GoogleDriveException("Not signed in to Google")

        val cacheDir = File(context.filesDir, "drive_photos").apply { mkdirs() }
        val driveFiles = listImageFiles(folderId, token, maxPhotos)

        val keepNames = mutableSetOf<String>()
        val photos = mutableListOf<PhotoItem>()

        driveFiles.forEachIndexed { index, file ->
            val ext = extensionFor(file.mimeType, file.name)
            val localName = "${file.id}.$ext"
            keepNames += localName
            val localFile = File(cacheDir, localName)

            if (!localFile.exists() || localFile.length() == 0L) {
                runCatching { downloadFile(file.id, token, localFile) }
                    .onFailure { Log.e(TAG, "Failed to download ${file.name}: ${it.message}") }
            }

            if (localFile.exists() && localFile.length() > 0L) {
                photos += PhotoItem(
                    uri = Uri.fromFile(localFile).toString(),
                    caption = file.name.substringBeforeLast('.'),
                    displayDurationSeconds = defaultDurationSeconds,
                    sortOrder = index,
                    source = ContentSource.GOOGLE,
                    remoteId = "gdrive:${file.id}",
                )
            }
        }

        // Remove cached files that are no longer in the Drive folder.
        cacheDir.listFiles()?.forEach { cached ->
            if (cached.name !in keepNames) cached.delete()
        }

        return@withContext photos
    }

    private fun listImageFiles(folderId: String, token: String, maxPhotos: Int): List<DriveFile> {
        val result = mutableListOf<DriveFile>()
        var pageToken: String? = null

        do {
            val urlBuilder = "https://www.googleapis.com/drive/v3/files".toHttpUrl().newBuilder()
                .addQueryParameter(
                    "q",
                    "'$folderId' in parents and mimeType contains 'image/' and trashed = false",
                )
                .addQueryParameter("fields", "files(id,name,mimeType,modifiedTime),nextPageToken")
                .addQueryParameter("pageSize", "100")
                .addQueryParameter("orderBy", "name")
                .addQueryParameter("supportsAllDrives", "true")
                .addQueryParameter("includeItemsFromAllDrives", "true")
            if (pageToken != null) urlBuilder.addQueryParameter("pageToken", pageToken)

            val request = Request.Builder()
                .url(urlBuilder.build())
                .addHeader("Authorization", "Bearer $token")
                .get()
                .build()

            httpClient.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    Log.e(TAG, "Drive list ${response.code}: $body")
                    throw GoogleDriveException(describeError(response.code, body))
                }
                val parsed = listAdapter.fromJson(body)
                parsed?.files?.let { result += it }
                pageToken = parsed?.nextPageToken
            }
        } while (pageToken != null && result.size < maxPhotos)

        return result.take(maxPhotos)
    }

    private fun downloadFile(fileId: String, token: String, dest: File) {
        val request = Request.Builder()
            .url("https://www.googleapis.com/drive/v3/files/$fileId?alt=media&supportsAllDrives=true")
            .addHeader("Authorization", "Bearer $token")
            .get()
            .build()

        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw GoogleDriveException("Download failed (${response.code})")
            }
            dest.outputStream().use { out ->
                response.body?.byteStream()?.copyTo(out)
            }
        }
    }

    private fun extensionFor(mimeType: String?, name: String): String {
        val fromName = name.substringAfterLast('.', "").lowercase()
        if (fromName.length in 1..5) return fromName
        return when (mimeType) {
            "image/png" -> "png"
            "image/webp" -> "webp"
            "image/gif" -> "gif"
            "image/heic" -> "heic"
            else -> "jpg"
        }
    }

    private fun describeError(code: Int, body: String): String {
        val lower = body.lowercase()
        return when {
            code == 403 && ("insufficient" in lower || "scope" in lower) ->
                "Drive access not granted. Sign out and sign in again to allow Google Drive."
            code == 403 && ("accessnotconfigured" in lower || "has not been used" in lower) ->
                "Google Drive API is not enabled for this project. Enable it in Google Cloud Console."
            code == 404 ->
                "Drive folder not found. Make sure it is shared with this account and the link/ID is correct."
            code == 401 -> "Google authorization expired (401). Sign out and sign in again."
            else -> "Google Drive sync failed ($code)."
        }
    }

    companion object {
        private const val TAG = "GoogleDriveSync"

        fun parseFolderId(input: String): String? {
            val trimmed = input.trim()
            if (trimmed.isEmpty()) return null
            Regex("/folders/([a-zA-Z0-9_-]+)").find(trimmed)?.let { return it.groupValues[1] }
            Regex("[?&]id=([a-zA-Z0-9_-]+)").find(trimmed)?.let { return it.groupValues[1] }
            if (Regex("^[a-zA-Z0-9_-]+$").matches(trimmed)) return trimmed
            return null
        }
    }
}
