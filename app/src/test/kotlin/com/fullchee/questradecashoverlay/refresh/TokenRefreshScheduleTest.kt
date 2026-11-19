package com.fullchee.questradecashoverlay.refresh

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.NetworkType
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TokenRefreshScheduleTest {
    private val app = ApplicationProvider.getApplicationContext<Context>()

    @Before
    fun initWorkManager() {
        WorkManagerTestInitHelper.initializeTestWorkManager(app)
    }

    @Test
    fun `scheduling twice keeps one daily job that needs the network`() {
        scheduleTokenRefresh(app)
        scheduleTokenRefresh(app)

        val job = WorkManager.getInstance(app)
            .getWorkInfosForUniqueWork(TOKEN_REFRESH_WORK)
            .get()
            .single()
        assertEquals(TimeUnit.HOURS.toMillis(24), job.periodicityInfo?.repeatIntervalMillis)
        assertEquals(NetworkType.CONNECTED, job.constraints.requiredNetworkType)
        assertEquals(setOf(TokenRefreshWorker::class.java.name), job.tags)
    }
}
