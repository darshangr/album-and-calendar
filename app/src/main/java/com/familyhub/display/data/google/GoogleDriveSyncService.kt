package com.familyhub.display.data.google

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
        maxPhotos: Int = 2000,
        maxFolders: Int = 500,
    ): List<PhotoItem> = withContext(Dispatchers.IO) {
        val folderIds = parseFolderIds(folderInput)
        if (folderIds.isEmpty()) {
            throw GoogleDriveException("Enter at least one Google Drive folder link or ID")
        }
        val token = authManager.getAccessToken()
            ?: throw GoogleDriveException("Not signed in to Google")

        val cacheDir = File(context.filesDir, "drive_photos").apply { mkdirs() }

        // Walk the given folders and their subfolders (breadth-first), collecting
        // images until we hit the caps. `visited` prevents cycles / duplicates.
        val driveFiles = mutableListOf<DriveFile>()
        val seenFileIds = mutableSetOf<String>()
        val visitedFolders = mutableSetOf<String>()
        val queue = ArrayDeque(folderIds)

        while (queue.isNotEmpty() && driveFiles.size < maxPhotos && visitedFolders.size < maxFolders) {
            val folderId = queue.removeFirst()
            if (!visitedFolders.add(folderId)) continue

            val (images, subfolders) = listChildren(folderId, token, maxPhotos - driveFiles.size)
            images.forEach { if (seenFileIds.add(it.id)) driveFiles += it }
            subfolders.forEach { if (it !in visitedFolders) queue.addLast(it) }
        }

        val keepNames = mutableSetOf<String>()
        val photos = mutableListOf<PhotoItem>()

        driveFiles.take(maxPhotos).forEachIndexed { index, file ->
            // Images are re-encoded to downscaled JPEG on download, so use a
            // uniform .jpg cache name regardless of the source format.
            val localName = "${file.id}.jpg"
            keepNames += localName
            val localFile = File(cacheDir, localName)

            // Re-download if missing, empty, or a previously cached file is corrupt
            // (e.g. a truncated download) so the cache self-heals.
            if (!localFile.exists() || localFile.length() == 0L || !isDecodable(localFile)) {
                localFile.delete()
                runCatching { downloadAndDownscale(file.id, token, localFile) }
                    .onFailure {
                        Log.e(TAG, "Failed to download ${file.name}: ${it.message}")
                        localFile.delete()
                    }
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

    /**
     * Lists the direct children of a folder, returning image files and the ids of
     * any subfolders (so the caller can recurse). Includes items from shared
     * drives.
     */
    private fun listChildren(
        folderId: String,
        token: String,
        remaining: Int,
    ): Pair<List<DriveFile>, List<String>> {
        val images = mutableListOf<DriveFile>()
        val subfolders = mutableListOf<String>()
        var pageToken: String? = null

        do {
            val urlBuilder = "https://www.googleapis.com/drive/v3/files".toHttpUrl().newBuilder()
                .addQueryParameter(
                    "q",
                    "'$folderId' in parents and trashed = false and " +
                        "(mimeType contains 'image/' or mimeType = '$FOLDER_MIME')",
                )
                .addQueryParameter("fields", "files(id,name,mimeType,modifiedTime),nextPageToken")
                .addQueryParameter("pageSize", "100")
                .addQueryParameter("orderBy", "folder,name")
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
                parsed?.files?.forEach { file ->
                    if (file.mimeType == FOLDER_MIME) {
                        subfolders += file.id
                    } else {
                        images += file
                    }
                }
                pageToken = parsed?.nextPageToken
            }
        } while (pageToken != null && images.size < remaining)

        return images.take(remaining) to subfolders
    }

    /**
     * Downloads a Drive image to a temp file, then decodes it **downsampled**
     * (never loading the full-resolution bitmap into memory) and re-encodes it as
     * a screen-sized JPEG. This bounds both device storage and the bitmap memory
     * the slideshow decodes later, avoiding OOM on large photo libraries.
     */
    private fun downloadAndDownscale(fileId: String, token: String, dest: File) {
        val request = Request.Builder()
            .url("https://www.googleapis.com/drive/v3/files/$fileId?alt=media&supportsAllDrives=true")
            .addHeader("Authorization", "Bearer $token")
            .get()
            .build()

        // Unique temp file per download so concurrent/retried downloads never
        // clobber each other's partial files.
        val tmp = File.createTempFile("dl_", ".tmp", dest.parentFile)
        try {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw GoogleDriveException("Download failed (${response.code})")
                }
                val body = response.body ?: throw GoogleDriveException("Empty response for $fileId")
                tmp.outputStream().use { out ->
                    body.byteStream().copyTo(out)
                }
            }

            if (!tmp.exists() || tmp.length() == 0L) {
                throw GoogleDriveException("Downloaded 0 bytes for $fileId")
            }

            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(tmp.absolutePath, bounds)
            val srcW = bounds.outWidth
            val srcH = bounds.outHeight

            if (srcW <= 0 || srcH <= 0) {
                // Not a decodable image (or unsupported); keep the original bytes.
                tmp.copyTo(dest, overwrite = true)
                return
            }

            var sample = 1
            while (srcW / (sample * 2) >= MAX_IMAGE_DIMEN || srcH / (sample * 2) >= MAX_IMAGE_DIMEN) {
                sample *= 2
            }

            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.RGB_565
            }
            val bitmap = BitmapFactory.decodeFile(tmp.absolutePath, decodeOptions)
            if (bitmap == null) {
                tmp.copyTo(dest, overwrite = true)
                return
            }

            try {
                dest.outputStream().use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
                }
            } finally {
                bitmap.recycle()
            }
        } finally {
            tmp.delete()
        }
    }

    private fun isDecodable(file: File): Boolean {
        return try {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, options)
            options.outWidth > 0 && options.outHeight > 0
        } catch (e: Exception) {
            false
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
        private const val FOLDER_MIME = "application/vnd.google-apps.folder"
        private const val MAX_IMAGE_DIMEN = 2560
        private const val JPEG_QUALITY = 82

        fun parseFolderId(input: String): String? {
            val trimmed = input.trim()
            if (trimmed.isEmpty()) return null
            Regex("/folders/([a-zA-Z0-9_-]+)").find(trimmed)?.let { return it.groupValues[1] }
            Regex("[?&]id=([a-zA-Z0-9_-]+)").find(trimmed)?.let { return it.groupValues[1] }
            if (Regex("^[a-zA-Z0-9_-]+$").matches(trimmed)) return trimmed
            return null
        }

        /** Parses multiple folder links/IDs separated by newlines, commas, or spaces. */
        fun parseFolderIds(input: String): List<String> {
            return input.split('\n', ',', ' ', '\t')
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .mapNotNull { parseFolderId(it) }
                .distinct()
        }
    }
}
