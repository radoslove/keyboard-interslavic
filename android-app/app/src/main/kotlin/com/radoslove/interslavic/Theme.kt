package com.radoslove.interslavic

import android.content.Context
import android.content.res.Configuration

/**
 * Light or dark keyboard, chosen in the settings (owner 2026-10-14): automatic
 * (follows the phone's dark mode - the default), always light, or always dark.
 */
object Theme {

    const val AUTO = "auto"
    const val LIGHT = "light"
    const val DARK = "dark"
    val ALL = listOf(AUTO, LIGHT, DARK)

    private const val PREFS = "isv_collector"
    private const val KEY = "theme"

    fun choice(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, AUTO)
            ?.takeIf { it in ALL } ?: AUTO

    fun set(context: Context, value: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, value).apply()
    }

    /** Whether the keyboard draws dark now. */
    fun isDark(context: Context): Boolean = when (choice(context)) {
        DARK -> true
        LIGHT -> false
        else -> (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
    }
}
