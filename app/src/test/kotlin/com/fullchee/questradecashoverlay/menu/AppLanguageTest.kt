package com.fullchee.questradecashoverlay.menu

import android.app.LocaleManager
import android.content.Context
import android.os.LocaleList
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppLanguageTest {
    private val app = ApplicationProvider.getApplicationContext<Context>()
    private val locales = app.getSystemService(LocaleManager::class.java)

    @Test
    fun aChosenLanguageIsReadBack() {
        setLanguage(app, AppLanguage.ENGLISH)

        assertEquals(AppLanguage.ENGLISH, currentLanguage(app))
        assertEquals("en", locales.applicationLocales.toLanguageTags())
    }

    @Test
    fun androidsOwnSpellingOfSimplifiedChineseCountsAsIt() {
        locales.applicationLocales = LocaleList.forLanguageTags("zh-Hans-CN")

        assertEquals(AppLanguage.SIMPLIFIED_CHINESE, currentLanguage(app))
    }

    @Test
    fun noChosenLanguageMeansChineseLikeTheAppStartsIn() {
        locales.applicationLocales = LocaleList.getEmptyLocaleList()

        assertEquals(AppLanguage.SIMPLIFIED_CHINESE, currentLanguage(app))
    }
}
