package com.focuslock

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent

class AppBlockerService : AccessibilityService() {
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}
}
