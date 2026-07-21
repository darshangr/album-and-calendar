package com.familyhub.display

import android.app.Application
import com.familyhub.display.data.AppContainer

class FamilyHubApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
