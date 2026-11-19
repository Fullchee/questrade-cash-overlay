package com.fullchee.questradecashoverlay.lint

import com.android.tools.lint.client.api.IssueRegistry
import com.android.tools.lint.client.api.Vendor
import com.android.tools.lint.detector.api.CURRENT_API

class UiStringIssueRegistry : IssueRegistry() {
    override val issues = listOf(HardcodedUiTextDetector.ISSUE)

    override val api = CURRENT_API

    override val vendor = Vendor(vendorName = "questrade-cash-overlay")
}
