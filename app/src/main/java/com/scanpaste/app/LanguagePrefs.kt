package com.scanpaste.app

import android.content.Context
import android.content.SharedPreferences

/**
 * Stores the UI language chosen from the title menu.
 *
 * The app ships with English as the default resource set, so "en" is also the
 * fallback when nothing has been stored yet.
 *
 * Applying the locale happens in each Activity's attachBaseContext via
 * applyOverrideConfiguration, which AppCompat honours more reliably than a
 * createConfigurationContext wrapper.
 */
object LanguagePrefs {
    private const val PREFS_NAME = "scanpaste_settings"
    private const val KEY_LANGUAGE = "app_language"

    const val LANG_EN = "en"
    const val LANG_ZH = "zh"

    private const val DEFAULT_LANGUAGE = LANG_EN

    fun get(context: Context): String =
        prefs(context).getString(KEY_LANGUAGE, DEFAULT_LANGUAGE) ?: DEFAULT_LANGUAGE

    fun set(context: Context, lang: String) {
        prefs(context).edit().putString(KEY_LANGUAGE, lang).apply()
    }

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
