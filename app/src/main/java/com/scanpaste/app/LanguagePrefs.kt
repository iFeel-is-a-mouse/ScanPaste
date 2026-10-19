package com.scanpaste.app

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import java.util.Locale

/**
 * Stores the UI language chosen from the title menu.
 *
 * The app ships with English as the default resource set, so "en" is also the
 * fallback when nothing has been stored yet.
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

    /**
     * Returns a Context whose resources use the stored language.
     *
     * Called from each Activity's attachBaseContext so that the chosen locale
     * applies before the layout is inflated.
     */
    fun wrap(base: Context): Context {
        val locale = Locale(get(base))
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        return base.createConfigurationContext(config)
    }

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
