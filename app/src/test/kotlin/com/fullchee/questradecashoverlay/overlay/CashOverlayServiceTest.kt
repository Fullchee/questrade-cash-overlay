package com.fullchee.questradecashoverlay.overlay

import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowLooper
import org.robolectric.shadows.ShadowWindowManagerImpl

@RunWith(AndroidJUnit4::class)
class CashOverlayServiceTest {
    private val controller = Robolectric.buildService(CashOverlayService::class.java).create()
    private val service = controller.get()
    private val windows: ShadowWindowManagerImpl =
        Shadow.extract(service.getSystemService(WindowManager::class.java))

    @Test
    fun `text shows one accessibility overlay that later text updates`() {
        service.render("CAD cash: $1.00")
        service.render("USD cash: $2.00")

        val view = windows.views.single() as TextView
        assertEquals("USD cash: $2.00", view.text.toString())
        assertEquals(
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            (view.layoutParams as WindowManager.LayoutParams).type
        )
    }

    @Test
    fun `null removes the overlay`() {
        service.render("CAD cash: $1.00")
        service.render(null)

        assertEquals(0, windows.views.size)
    }

    @Test
    fun `hiding with no overlay shown does nothing`() {
        service.render(null)

        assertEquals(0, windows.views.size)
    }

    @Test
    fun `a connected service with no active window shows nothing, even after an event`() {
        service.onServiceConnected()
        service.onAccessibilityEvent(AccessibilityEvent())
        ShadowLooper.idleMainLooper()

        assertEquals(0, windows.views.size)
    }

    @Test
    fun `destroying the service removes the overlay`() {
        service.onServiceConnected()
        service.render("CAD cash: $1.00")

        controller.destroy()

        assertEquals(0, windows.views.size)
    }
}
