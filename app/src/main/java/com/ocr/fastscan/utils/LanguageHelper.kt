package com.ocr.fastscan.utils

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

object LanguageHelper {

    const val SYSTEM_LANGUAGE = "system"

    /**
     * Возвращает язык, который нужно применить
     */
    fun getCurrentLanguage(context: Context): String {
        val prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(context)
        return prefs.getString("language", SYSTEM_LANGUAGE) ?: SYSTEM_LANGUAGE
    }

    /**
     * Применяет выбранный язык
     */
    fun applyLanguage(context: Context, language: String): Context {
        val locale = when (language) {
            SYSTEM_LANGUAGE -> Locale.getDefault()           // язык системы
            else -> Locale(language)
        }

        Locale.setDefault(locale)

        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)

        return context.createConfigurationContext(config)
    }

    fun saveLanguage(context: Context, language: String) {
        val prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(context)
        prefs.edit().putString("language", language).apply()
    }
}