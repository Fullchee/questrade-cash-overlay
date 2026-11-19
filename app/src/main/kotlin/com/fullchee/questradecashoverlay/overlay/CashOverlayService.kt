package com.fullchee.questradecashoverlay.overlay

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent

// Stub so onboarding can enable it; #8 adds the overlay.
class CashOverlayService : AccessibilityService() {
    override fun onAccessibilityEvent(event: AccessibilityEvent) = Unit

    override fun onInterrupt() = Unit
}
