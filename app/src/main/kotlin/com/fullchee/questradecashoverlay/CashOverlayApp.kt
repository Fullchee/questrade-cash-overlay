package com.fullchee.questradecashoverlay

import android.app.Application
import androidx.work.Configuration
import com.fullchee.questradecashoverlay.refresh.scheduleTokenRefresh

class CashOverlayApp :
    Application(),
    Configuration.Provider {
    // Providing the configuration here lets WorkManager start on first use instead of from a content provider.
    override val workManagerConfiguration: Configuration = Configuration.Builder().build()

    override fun onCreate() {
        super.onCreate()
        scheduleTokenRefresh(this)
    }
}
