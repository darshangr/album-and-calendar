package com.familyhub.display

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.familyhub.display.data.AppContainer
import com.familyhub.display.data.google.GooglePhotosAuthInterceptor

class FamilyHubApplication : Application(), ImageLoaderFactory {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .okHttpClient {
                okhttp3.OkHttpClient.Builder()
                    .addInterceptor(GooglePhotosAuthInterceptor(container.googleAuthManager))
                    .build()
            }
            // Bound in-memory bitmaps so a long slideshow can't OOM the app.
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.20)
                    .build()
            }
            // Bound the on-disk decoded cache used for remote URLs.
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(200L * 1024 * 1024)
                    .build()
            }
            // Photos have no alpha; RGB_565 halves bitmap memory.
            .allowRgb565(true)
            .crossfade(true)
            .build()
    }
}
