package com.fullchee.questradecashoverlay

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.fullchee.questradecashoverlay.refresh.TOKEN_REFRESH_WORK
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CashOverlayAppTest {
    @Test
    fun `starting the app schedules the daily token refresh`() {
        val app = ApplicationProvider.getApplicationContext<Context>()

        val job = WorkManager.getInstance(app).getWorkInfosForUniqueWork(TOKEN_REFRESH_WORK).get()

        assertEquals(listOf(WorkInfo.State.ENQUEUED), job.map { it.state })
    }
}
