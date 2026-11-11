package com.radoslove.interslavic

import android.content.Context
import android.view.inputmethod.InputMethodInfo
import android.view.inputmethod.InputMethodManager
import android.view.inputmethod.InputMethodSubtype

/**
 * The language the keyboard types: Interslavic (`isv`), Interscandinavian
 * (`isc`), Polish (`pl`), Czech (`cs`), Slovak (`sk`), Slovenian (`sl`),
 * Croatian (`hr`), Serbian in Latin script (`sr`), Norwegian bokmål (`nb`)
 * or English (`en`).
 *
 * ONE keyboard in the system, the languages chosen INSIDE it (owner
 * 2026-11-11, parity with iOS 0.6.3): "jedna klawiatura Interslove". From
 * 3.5.9 to 3.8.16 every language was its own input-method subtype ticked on
 * the system's Languages page - at sixteen languages that list was long, and a
 * language added by a later build arrived unticked and invisible (Russian,
 * Ukrainian, Bulgarian and Proto-Slavic on the test phone, 2026-11-11). The
 * list lives in this keyboard's preferences now ([enabled]), ticked in the
 * in-keyboard settings panel and in the app; space and 🌐 switch between the
 * ticked ones without the system. The 3.5.8 trouble ("all ticked at install,
 * still no Polish", 2026-10-14) came from TWO lists, ours and the system's;
 * there is one again, so it cannot come back. On the first start the ticks
 * the system had are adopted once ([migrate]), so nobody loses a language.
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
    const val CS = "cs"
    const val SK = "sk"
    const val SL = "sl"
    const val HR = "hr"
    /** Serbian in LATIN script; the code stays `sr`, the subtype says `sr-Latn`. */
    const val SR = "sr"

    /** Bosnian: the same Latin alphabet as Croatian and Serbian Latin, its own
     *  wordlist. Latin only here - Cyrillic is co-official in Bosnia but is not
     *  what the language is typed in. */
    const val BS = "bs"

    /** Macedonian: CYRILLIC only, and the first language here with no Latin
     *  orthography of its own. Its wordlist is transliterated into the
     *  keyboard's internal Latin when it is built, so everything inside -
     *  decoder, ranking, learned offset - works unchanged. */
    const val MK = "mk"

    /** Russian: Cyrillic only, on the PHONETIC layout (owner 2026-11-11), the
     *  arrangement Russian users know as ЯШЕРТЫ - every letter on the Latin key
     *  that sounds or looks like it, the rest held. Its wordlist is in the
     *  internal Latin of tools/build_cyrillic_wordlist.py, like Macedonian's. */
    const val RU = "ru"

    /** Ukrainian: Cyrillic only, on the HOMOPHONIC layout (xkeyboard-config
     *  `ua(homophonic)`, owner 2026-11-11): я q, ш w, и y, і i, ґ g, г h, х x,
     *  ц c, в v, the rest held. The apostrophe inside a word (`п'ять`) is part
     *  of the word but never part of a glide. Internal Latin like Russian's. */
    const val UK = "uk"

    /** Bulgarian: Cyrillic only, on the traditional phonetic layout ЯВЕРТЪ
     *  (xkeyboard-config `bg(phonetic)`, owner 2026-11-11): я q, в w, ъ y, ь x,
     *  ж v, х h, ц c; ш щ held on s, ч c, ю u, ѝ i. */
    const val BG = "bg"

    /** Proto-Slavic in the Wiktionary notation (tools/build_sla_wordlist.py):
     *  Latin letters, but the yers `ъ` `ь` have keys of their own (`q` `f`), so
     *  it goes through the same one-mode Cyrillic table as Russian. */
    const val SLA = "sla"

    /** Belarusian: Cyrillic only, official orthography, on the published
     *  phonetic layout xkeyboard-config `by(phonetic)` (owner 2026-11-11): я q,
     *  в w, ы y, і i, х h, ь x, ж v; ш ч ў ю э ё held. Like Ukrainian, the
     *  apostrophe inside a word (`сям'я`) is part of it. */
    const val BE = "be"

    /** Languages whose every key writes ONE letter, so the Interslavic digraph
     *  keys and routes (`lj` `nj` over `q` `w`) stay off (iOS `singleLetterKeys`). */
    val SINGLE_LETTER_KEYS = setOf(MK, RU, UK, BG, SLA, BE)

    /** Languages that write an apostrophe INSIDE words (`п'ять`, `сям'я`). */
    val APOSTROPHE_IN_WORD = setOf(UK, BE)

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

    /** NOT `languages`: 3.5.8 kept its own list under that name as a STRING
     *  SET, and phones that had 3.5.8 still carry it - reading it as a string
     *  threw on every keyboard open in 3.8.17 (the test phone, 2026-11-11; Samsung then
     *  offered "Clear cache"). That old set is stale and is not read. */
    private const val KEY_ENABLED = "enabled_langs"

    /** The ticked languages, in [ALL] order; never empty. */
    fun enabled(context: Context): List<String> {
        val stored = try { prefs(context).getString(KEY_ENABLED, null) } catch (_: ClassCastException) { null }
            ?: return migrate(context)
        val on = stored.split(',').toSet()
        return ALL.filter { it in on }.ifEmpty { listOf(MAIN) }
    }

    /** Tick [langs]; at least one always stays (the caller keeps the last one). */
    fun setEnabled(context: Context, langs: Collection<String>) {
        val list = ALL.filter { it in langs }.ifEmpty { listOf(MAIN) }
        prefs(context).edit().putString(KEY_ENABLED, list.joinToString(",")).apply()
        if (current(context) !in list) setCurrent(context, list.first())
    }

    /**
     * First start with the list inside: the app's own language plus EVERY
     * language set on the phone that this keyboard has (Settings → languages,
     * all of them, not only the first).
     *
     * The old system ticks of 3.5.9-3.8.16 CANNOT be read back - measured on
     * the test phone 2026-11-11: the subtypes are gone from method.xml, so the
     * IME API lists only `isv`, and the raw setting `enabled_input_methods`,
     * which still names them by id, throws SecurityException for an app with
     * targetSdkVersion above 33 ("only readable to apps with targetSdkVersion
     * lower than or equal to: 33"). The phone's languages are what Android
     * itself switched on implicitly back then, so this is the nearest guess;
     * anything else is one tap in the panel.
     */
    private fun migrate(context: Context): List<String> {
        val start = listOf(MAIN) + phoneLangs()
        setEnabled(context, start)
        return ALL.filter { it in start }.ifEmpty { listOf(MAIN) }
    }

    /** The phone's languages, in its own order, mapped to ours. */
    fun phoneLangs(): List<String> {
        val list = android.os.LocaleList.getDefault()
        return (0 until list.size()).mapNotNull { fromLocale(list[it].language) }
            .filter { it in ALL }.distinct()
    }

    private fun fromLocale(code: String): String? = when (code) {
        "pl" -> PL; "cs" -> CS; "sk" -> SK; "sl" -> SL; "hr" -> HR
        "sr" -> SR; "bs" -> BS; "mk" -> MK; "ru" -> RU; "uk" -> UK; "bg" -> BG; "be" -> BE
        "nb", "no", "nn" -> NB
        "en" -> EN
        else -> null
    }

    /** The language now typed; always one of the ticked ones. */
    fun current(context: Context): String =
        prefs(context).getString(KEY_CURRENT, MAIN)?.takeIf { it in ALL } ?: MAIN

    /** Set by the space bar, 🌐 and the settings - no longer by the system. */
    fun setCurrent(context: Context, lang: String) {
        prefs(context).edit().putString(KEY_CURRENT, lang).apply()
    }

    /** The ticked language after (or, [back], before) the current one, round the list. */
    fun step(context: Context, back: Boolean = false): String {
        val on = enabled(context)
        val i = on.indexOf(current(context)).coerceAtLeast(0)
        return on[(i + if (back) on.size - 1 else 1) % on.size]
    }

    /** The project's own language is typed: only then do the collector and the
     *  ranking export apply, so each app's data serves its own project. */
    fun isMain(context: Context) = current(context) == MAIN

    /** The phone's language mapped to one of [ALL], or null. Norwegian nynorsk
     *  and the bare `no` both get bokmål - the only Norwegian we have. */
    fun deviceLang(): String? = when (java.util.Locale.getDefault().language) {
        "pl" -> PL
        "cs" -> CS
        "nb", "no", "nn" -> NB
        "en" -> EN
        else -> null
    }

    /** Shown on the space bar when more than one language is ticked. */
    fun label(lang: String): String = when (lang) {
        PL -> "Polski"
        NB -> "Norsk"
        EN -> "English"
        CS -> "Čeština"
        // Added with the languages themselves (2026-11-10): without a row here
        // the space bar fell back to the app's own name, so a phone typing
        // Serbian said "Medžuslovjansky" on the space bar.
        SK -> "Slovenčina"
        SL -> "Slovenščina"
        HR -> "Hrvatski"
        SR -> "Srpski"
        BS -> "Bosanski"
        MK -> "\u041c\u0430\u043a\u0435\u0434\u043e\u043d\u0441\u043a\u0438"
        RU -> "Русский"
        UK -> "Українська"
        BG -> "Български"
        BE -> "Беларуская"
        // No living speaker to name it in its own tongue: the Interslavic name.
        SLA -> "Praslovjansky"
        ISV -> "Medžuslovjansky"
        ISC -> "Mellomskandinavisk"
        else -> App.MAIN_NAME
    }
}
