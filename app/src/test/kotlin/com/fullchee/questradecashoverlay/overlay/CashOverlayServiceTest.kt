package com.fullchee.questradecashoverlay.overlay

import android.graphics.Color
import android.graphics.Rect
import android.graphics.Typeface
import android.view.Gravity
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
        service.render(content("CAD cash: $1.00"))
        service.render(content("USD cash: $2.00"))

        val view = windows.views.single() as TextView
        assertEquals("USD cash: $2.00", view.text.toString())
        assertEquals(
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            (view.layoutParams as WindowManager.LayoutParams).type
        )
    }

    @Test
    fun `short cash turns the text red and bold, and enough cash turns it back`() {
        service.render(content("CAD cash: $1.00", cashShort = true))
        val view = windows.views.single() as TextView
        assertEquals(Typeface.BOLD, view.typeface.style)
        assertEquals(Color.rgb(255, 82, 82), view.currentTextColor)

        service.render(content("CAD cash: $1.00"))

        assertEquals(Typeface.NORMAL, view.typeface.style)
        assertEquals(Color.WHITE, view.currentTextColor)
    }

    @Test
    fun `the overlay's top right lines up inside the Total amount box`() {
        val width = service.resources.displayMetrics.widthPixels
        val density = service.resources.displayMetrics.density
        service.render(content("CAD cash: $1.00", totalBox = Rect(42, 706, width - 42, 880)))

        val layout = windows.views.single().layoutParams as WindowManager.LayoutParams
        assertEquals(Gravity.TOP or Gravity.END, layout.gravity)
        assertEquals(42 + (8 * density).toInt(), layout.x)
        assertEquals(706 + (4 * density).toInt(), layout.y)
    }

    @Test
    fun `the overlay follows the Total amount box when it moves`() {
        service.render(content("CAD cash: $1.00", totalBox = Rect(42, 706, 1038, 880)))
        service.render(content("CAD cash: $1.00", totalBox = Rect(42, 506, 1038, 680)))

        val layout = windows.views.single().layoutParams as WindowManager.LayoutParams
        assertEquals(506 + (4 * service.resources.displayMetrics.density).toInt(), layout.y)
    }

    @Test
    fun `null removes the overlay`() {
        service.render(content("CAD cash: $1.00"))
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
        service.render(content("CAD cash: $1.00"))

        controller.destroy()

        assertEquals(0, windows.views.size)
    }

    private fun content(text: String, cashShort: Boolean = false, totalBox: Rect? = null) =
        OverlayContent(text, cashShort, totalBox)
}
