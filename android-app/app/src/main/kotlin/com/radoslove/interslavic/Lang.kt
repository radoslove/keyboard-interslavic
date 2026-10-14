package com.radoslove.interslavic

import android.content.Context
import android.view.inputmethod.InputMethodInfo
import android.view.inputmethod.InputMethodManager
import android.view.inputmethod.InputMethodSubtype

/**
 * The language the keyboard types: Interslavic (`isv`), Polish (`pl`),
 * Norwegian bokmål (`nb`) or English (`en`).
 *
 * iOS lists each language as its own keyboard (`isv 0.2.1`, `pl 0.2.1`,
 * 7801f4b). The Android equivalent is one input-method SUBTYPE per language
 * (res/xml/method.xml): they appear in the system keyboard list and picker, the
 * system's own Languages page ticks them on and off, and 🌐 steps through the
 * ticked ones. So the SYSTEM is the one place languages are chosen - 3.5.8 kept
 * its own list inside the app, which the system list never showed (owner
 * 2026-10-14: "all ticked at install, still no Polish").
 *
 * Each language loads its dictionary only the first time it is used.
 * Per language: layout ([Layout]), dictionary ([Dictionary]), the user's own
 * words ([UserWords]) and adaptive counts ([Usage]). Cyrillic and Glagolitic,
 * the new-word collector and the popularity ranking are Interslavic only - a
 * Polish word must never reach the Interslavic database.
 */
object Lang {

    const val ISV = "isv"
    const val ISC = "isc"
    const val PL = "pl"
    const val NB = "nb"
    const val EN = "en"

    /** This app's own language ([App.MAIN]): `isv` in Interslove, `isc` in Mellomskandinavisk. */
    const val MAIN = App.MAIN

    /** Every language the app knows, in the order of method.xml. */
    val ALL = App.LANGS

    private const val PREFS = "isv_collector"
    private const val KEY_CURRENT = "language"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Our language from a subtype's `lang=` extra value, or null. */
    fun of(subtype: InputMethodSubtype?): String? =
        subtype?.extraValue?.removePrefix("lang=")?.takeIf { it in ALL }

    /** This keyboard as the system knows it, or null if it is not installed as one. */
    fun imi(context: Context): InputMethodInfo? {
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        return imm.inputMethodList.firstOrNull { it.packageName == context.packageName }
    }

    /** The languages ticked in the system keyboard settings, in [ALL] order; never empty. */
    fun enabled(context: Context): List<String> {
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        val on = imi(context)?.let { imm.getEnabledInputMethodSubtypeList(it, true) }
            .orEmpty().mapNotNull { of(it) }
        return ALL.filter { it in on }.ifEmpty { listOf(MAIN) }
    }

    /** The language now typed: the one the system last switched us to. */
    fun current(context: Context): String =
        prefs(context).getString(KEY_CURRENT, MAIN)?.takeIf { it in ALL } ?: MAIN

    /** Called by the service whenever the system reports the active subtype. */
    fun setCurrent(context: Context, lang: String) {
        prefs(context).edit().putString(KEY_CURRENT, lang).apply()
    }

    /** The project's own language is typed: only then do the collector and the
     *  ranking export apply, so each app's data serves its own project. */
    fun isMain(context: Context) = current(context) == MAIN

    /** The phone's language mapped to one of [ALL], or null. Norwegian nynorsk
     *  and the bare `no` both get bokmål - the only Norwegian we have. */
    fun deviceLang(): String? = when (java.util.Locale.getDefault().language) {
        "pl" -> PL
        "nb", "no", "nn" -> NB
        "en" -> EN
        else -> null
    }

    /** Shown on the space bar when more than one language is ticked. */
    fun label(lang: String): String = when (lang) {
        PL -> "Polski"
        NB -> "Norsk"
        EN -> "English"
        ISV -> "Medžuslovjansky"
        ISC -> "isc-no"
        else -> App.MAIN_NAME
    }
}
