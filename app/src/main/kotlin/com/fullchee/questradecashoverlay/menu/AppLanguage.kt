package com.fullchee.questradecashoverlay.menu

import android.app.LocaleManager
import android.content.Context
import android.os.LocaleList
import androidx.annotation.StringRes
import com.fullchee.questradecashoverlay.R
import java.util.Locale

/** The languages in locales_config.xml. */
enum class AppLanguage(val tag: String, @param:StringRes val label: Int) {
    ENGLISH("en", R.string.language_english),
    SIMPLIFIED_CHINESE("zh-CN", R.string.language_simplified_chinese)
}

fun currentLanguage(context: Context): AppLanguage {
    val chosen = context.getSystemService(LocaleManager::class.java).applicationLocales
    // Android's per-app language page may store another spelling (zh-Hans-CN) or none; with none the app runs in Chinese.
    val language = if (chosen.isEmpty) null else chosen[0].language
    return AppLanguage.entries.firstOrNull { Locale.forLanguageTag(it.tag).language == language }
        ?: AppLanguage.SIMPLIFIED_CHINESE
}

fun setLanguage(context: Context, language: AppLanguage) {
    context.getSystemService(LocaleManager::class.java).applicationLocales =
        LocaleList.forLanguageTags(language.tag)
}
