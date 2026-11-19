package com.fullchee.questradecashoverlay

import android.app.Application
import android.app.LocaleManager
import android.os.LocaleList
import androidx.work.Configuration
import com.fullchee.questradecashoverlay.logins.LoginStore
import com.fullchee.questradecashoverlay.logins.createLoginStore
import com.fullchee.questradecashoverlay.refresh.scheduleTokenRefresh

class CashOverlayApp :
    Application(),
    Configuration.Provider {
    // Providing the configuration here lets WorkManager start on first use instead of from a content provider.
    override val workManagerConfiguration: Configuration = Configuration.Builder().build()

    val loginStore: LoginStore by lazy { createLoginStore(this) }

    override fun onCreate() {
        super.onCreate()
        useChineseUnlessLanguagePicked()
        scheduleTokenRefresh(this)
    }

    // The family member reads Simplified Chinese whatever the phone's language; English stays a choice.
    private fun useChineseUnlessLanguagePicked() {
        val localeManager = getSystemService(LocaleManager::class.java)
        if (localeManager.applicationLocales.isEmpty) {
            localeManager.applicationLocales = LocaleList.forLanguageTags("zh-CN")
        }
    }
}
