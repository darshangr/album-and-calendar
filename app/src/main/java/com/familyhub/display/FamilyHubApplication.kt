package com.familyhub.display

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
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
            .crossfade(true)
            .build()
    }
}
