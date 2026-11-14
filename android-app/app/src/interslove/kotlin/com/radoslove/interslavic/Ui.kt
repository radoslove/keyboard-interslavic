package com.radoslove.interslavic

import android.content.Context

/**
 * The language of the app's own screen: Interslavic with an English gloss under
 * each paragraph (`ms`, the original), or English, Polish or Norwegian alone -
 * so a tester who reads none of the others can still find their way, and
 * testers can be gathered in any of the four. Defaults to the phone's language
 * when the app has it, otherwise Interslavic.
 *
 * The keyboard itself has no words to translate; this is only the settings app.
 * MS texts are checked against the medzuslove dictionary (see SetupActivity).
 */
object Ui {

    const val MS = "ms"
    val ALL = listOf(MS, "en", "pl", "nb")

    /** Heading of the screen-language choice, in every offered language. */
    const val LANG_HEAD = "Jezyk · Language · Język · Språk"

    /** Self-names, shown as the choices whatever the current language is. */
    val NAMES = mapOf(MS to "Medžuslovjansky", "en" to "English", "pl" to "Polski", "nb" to "Norsk")

    private const val PREFS = "isv_collector"
    private const val KEY = "ui_language"

    /** The screen follows the PHONE's language: the 15 languages of
     *  `tools/ui_texts.json` have their own text (iOS 0.6.11, 2026-11-13), any
     *  other language gets Interslavic with the English gloss. A choice saved
     *  by the old four-language picker (removed in 3.9.7) is ignored. */
    fun current(context: Context): String {
        val loc = java.util.Locale.getDefault()
        val code = when (val l = loc.language) {
            "no", "nn" -> "nb"
            // Serbian: Latin when the phone says so, otherwise Cyrillic
            // (iOS `Ui.swift`: `sr-Latn` -> sr, `sr`/`sr-Cyrl` -> Cyrillic).
            "sr" -> if (loc.script == "Latn") "sr" else "sr-Cyrl"
            else -> l
        }
        return if (code != MS && code in UiTexts.LANGUAGES) code else MS
    }

    fun set(context: Context, lang: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, lang).apply()
    }

    /** A keyboard language's own name, glossed in the screen's language [ui]. */
    fun langName(ui: String, lang: String): String {
        val own = when (lang) {
            Lang.ISV -> "Medžuslovjansky"; Lang.ISC -> "Mellomskandinavisk"; Lang.PL -> "Polski"
            Lang.NB -> "Norsk bokmål"; Lang.CS -> "Čeština"; Lang.SK -> "Slovenčina"; Lang.SL -> "Slovenščina"; Lang.HR -> "Hrvatski"; Lang.SR -> "Srpski"; Lang.BS -> "Bosanski"; Lang.MK -> "\u041c\u0430\u043a\u0435\u0434\u043e\u043d\u0441\u043a\u0438"; Lang.RU -> "Русский"; Lang.UK -> "Українська"; Lang.BG -> "Български"; Lang.BE -> "Беларуская"; Lang.SLA -> "Praslovjansky"; else -> "English"
        }
        val gloss = mapOf(
            Lang.ISV to mapOf("pl" to "międzysłowiański", "nb" to "interslavisk", "en" to "Interslavic"),
            Lang.ISC to mapOf(MS to "medžuskandinavsky", "pl" to "międzyskandynawski", "nb" to "mellomskandinavisk", "en" to "Interscandinavian"),
            Lang.PL to mapOf(MS to "poljsky", "nb" to "polsk", "en" to "Polish"),
            Lang.NB to mapOf(MS to "norvežsky", "pl" to "norweski", "en" to "Norwegian"),
            Lang.EN to mapOf(MS to "anglijsky", "pl" to "angielski", "nb" to "engelsk"),
            Lang.CS to mapOf(MS to "češsky", "pl" to "czeski", "nb" to "tsjekkisk", "en" to "Czech"),
            Lang.SK to mapOf(MS to "slovačsky", "pl" to "słowacki", "nb" to "slovakisk", "en" to "Slovak"),
            Lang.SL to mapOf(MS to "slovenečsky", "pl" to "słoweński", "nb" to "slovensk", "en" to "Slovenian"),
            Lang.HR to mapOf(MS to "hrvatsky", "pl" to "chorwacki", "nb" to "kroatisk", "en" to "Croatian"),
            Lang.SR to mapOf(MS to "srbsky", "pl" to "serbski", "nb" to "serbisk", "en" to "Serbian"),
            Lang.BS to mapOf(MS to "bosnijsky", "pl" to "bośniacki", "nb" to "bosnisk", "en" to "Bosnian"),
            Lang.MK to mapOf(MS to "makedonsky", "pl" to "macedoński", "nb" to "makedonsk", "en" to "Macedonian"),
            Lang.RU to mapOf(MS to "russky", "pl" to "rosyjski", "nb" to "russisk", "en" to "Russian"),
            Lang.UK to mapOf(MS to "ukrajinsky", "pl" to "ukraiński", "nb" to "ukrainsk", "en" to "Ukrainian"),
            Lang.BG to mapOf(MS to "bulgarsky", "pl" to "bułgarski", "nb" to "bulgarsk", "en" to "Bulgarian"),
            Lang.BE to mapOf(MS to "bělorussky", "pl" to "białoruski", "nb" to "belarusisk", "en" to "Belarusian"),
            Lang.SLA to mapOf("pl" to "prasłowiański", "nb" to "urslavisk", "en" to "Proto-Slavic"),
        )[lang]?.get(ui)
        return if (gloss == null || gloss == own) own else "$own ($gloss)"
    }

    /** [key] in [lang]; `ms` falls back to English for the few keys it lacks. */
    fun t(lang: String, key: String): String =
        (TEXT[key]?.let { it[lang] ?: it["en"] } ?: key)
            .replace("{n}", (Lang.ALL.size - 1).toString())

    /** The English gloss under an MS paragraph, or null outside `ms`. */
    fun gloss(lang: String, key: String): String? =
        if (lang == MS) TEXT[key]?.get("en")?.replace("{n}", (Lang.ALL.size - 1).toString()) else null

    /** Every screen text lives in `tools/ui_texts.json` (one source for
     *  Android and iOS, 15 languages); `tools/build_ui_texts.py --android`
     *  writes it into [UiTexts]. Change a text there, never here. */
    private val TEXT: Map<String, Map<String, String>> = UiTexts.TEXT
}
