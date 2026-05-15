package com.anantva.tether

import android.app.Application
import com.anantva.tether.data.repository.SyncOrchestrator
import com.anantva.tether.lifecycle.AppForegroundTracker
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class TetherApplication : Application() {

    @Inject lateinit var appForegroundTracker: AppForegroundTracker
    @Inject lateinit var syncOrchestrator: SyncOrchestrator

    override fun onCreate() {
        super.onCreate()
        appForegroundTracker.start()
        syncOrchestrator.start()
    }
}
