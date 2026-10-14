package com.radoslove.interslavic

import android.content.Context

/**
 * The language of the app's own screen: Norwegian with an English gloss under
 * each paragraph (`isc`, the original), or English or Polish alone - so testers
 * can be gathered in any of them. Defaults to the phone's language when the app
 * has it, otherwise Norwegian.
 *
 * Same keys as the Interslavic app's Ui.kt; SetupActivity is shared.
 */
object Ui {

    /** The main screen language: Norwegian + English gloss. */
    const val MS = "isc"
    val ALL = listOf(MS, "en", "pl")

    /** Heading of the screen-language choice, in every offered language. */
    const val LANG_HEAD = "Språk · Language · Język"

    /** Self-names, shown as the choices whatever the current language is. */
    val NAMES = mapOf(MS to "Norsk", "en" to "English", "pl" to "Polski")

    private const val PREFS = "isv_collector"
    private const val KEY = "ui_language"

    fun current(context: Context): String {
        val saved = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
        if (saved != null && saved in ALL) return saved
        return when (Lang.deviceLang()) {
            Lang.PL -> "pl"
            Lang.EN -> "en"
            else -> MS
        }
    }

    fun set(context: Context, lang: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, lang).apply()
    }

    /** A keyboard language's own name, glossed in the screen's language [ui]. */
    fun langName(ui: String, lang: String): String {
        val own = when (lang) {
            Lang.ISV -> "Medžuslovjansky"; Lang.ISC -> "Mellomskandinavisk (isc-no)"; Lang.PL -> "Polski"
            Lang.NB -> "Norsk bokmål"; else -> "English"
        }
        val gloss = mapOf(
            Lang.ISC to mapOf("pl" to "międzyskandynawski", "en" to "Interscandinavian"),
            Lang.ISV to mapOf("pl" to "międzysłowiański", MS to "interslavisk", "en" to "Interslavic"),
            Lang.PL to mapOf("en" to "Polish"),
            Lang.NB to mapOf("pl" to "norweski", "en" to "Norwegian"),
            Lang.EN to mapOf("pl" to "angielski"),
        )[lang]?.let { it[ui] ?: it["en"] }
        return if (gloss == null || gloss == own) own else "$own ($gloss)"
    }

    /** [key] in [lang]; the main language falls back to English for keys it lacks. */
    fun t(lang: String, key: String): String =
        TEXT[key]?.let { it[lang] ?: it["en"] } ?: key

    /** The English gloss under a Norwegian paragraph, or null outside the main language. */
    fun gloss(lang: String, key: String): String? = if (lang == MS) TEXT[key]?.get("en") else null

    private fun row(nb: String?, en: String, pl: String) =
        buildMap { if (nb != null) put(MS, nb); put("en", en); put("pl", pl) }

    private val TEXT: Map<String, Map<String, String>> = mapOf(
        "title" to row("Mellomskandinavisk", "Interscandinavian keyboard", "Klawiatura międzyskandynawska"),
        "intro" to row(
            "Mellomskandinavisk tastatur. Norsk oppsett (æ ø å); ä ö é med langt " +
                "trykk på æ ø e; forslag fra ordboken. Du kan også skrive norsk, engelsk og polsk.",
            "Interscandinavian keyboard. Norwegian layout (æ ø å); ä ö é on " +
                "long-press of æ ø e; a suggestion bar from the dictionary. Norwegian, " +
                "English and Polish too.",
            "Klawiatura międzyskandynawska. Układ norweski (æ ø å); ä ö é pod " +
                "przytrzymaniem æ ø e; podpowiedzi ze słownika. Można też pisać po " +
                "norwesku, angielsku i polsku.",
        ),
        "langs_head" to row("Tastaturspråk", "Keyboard languages", "Języki klawiatury"),
        "langs_body" to row(
            "Hvert språk er en egen oppføring i telefonens tastaturliste. Mellomskandinavisk " +
                "er på; legg til de andre med knappen nedenfor. 🌐 bytter mellom dem (holdt " +
                "inne åpner den tastaturlisten). Ordboka for et språk lastes først når du bruker det.",
            "Each language is its own entry in the phone's keyboard list. Interscandinavian " +
                "is on; add the others with the button below. 🌐 switches between them (held, " +
                "it opens the keyboard list). A language's dictionary loads only once you use it.",
            "Każdy język to osobna pozycja na systemowej liście klawiatur. Międzyskandynawski " +
                "jest włączony, pozostałe dodasz przyciskiem poniżej. 🌐 przełącza między nimi " +
                "(przytrzymany otwiera listę klawiatur). Słownik języka wczytuje się dopiero " +
                "przy pierwszym użyciu.",
        ),
        "langs_now" to row("På nå", "On now", "Włączone teraz"),
        "btn_langs" to row("Velg språk", "Choose languages", "Wybierz języki"),
        "script_head" to row("Latinsk, kyrillisk eller glagolittisk (mellomslavisk)",
            "Latin, Cyrillic or Glagolitic (Interslavic)", "Łacinka, cyrylica czy głagolica (międzysłowiański)"),
        "script_body" to row(
            "Mellomslavisk skrives med det valgte alfabetet. Bokstavene blir på de samme tastene.",
            "Interslavic is written in the chosen script. The letters stay on the same keys.",
            "Międzysłowiański pisze się wybranym alfabetem. Litery zostają na tych samych klawiszach.",
        ),
        "s_latin" to row("Latinsk", "Latin", "Łacinka"),
        "s_cyrillic" to row("Kyrillisk", "Cyrillic", "Cyrylica"),
        "s_glagolitic" to row("Glagolittisk", "Glagolitic", "Głagolica"),
        "collect_head" to row("Samle nye ord (valgfritt)", "Collecting new words (optional)",
            "Zbieranie nowych słów (opcjonalnie)"),
        "collect_body" to row(
            "Når dette er slått på, lagrer tastaturet mellomskandinaviske ord som ikke finnes i " +
                "ordboken, lokalt på telefonen, slik at de kan gjennomgås senere (de " +
                "blir aldri autokorrigert). Ingenting sendes – eksporten er en fil du " +
                "selv sender videre. Av som standard.",
            "When on, the keyboard saves Interscandinavian words NOT in the dictionary " +
                "LOCALLY, to later go to review (never auto-corrected). Nothing is sent — " +
                "the export is a file you pass on yourself. Off by default.",
            "Gdy włączone, klawiatura zapisuje LOKALNIE słowa międzyskandynawskie, których " +
                "nie ma w słowniku, żeby później trafiły do przeglądu (nigdy nie są " +
                "poprawiane automatycznie). Nic nie jest wysyłane — eksport to plik, który " +
                "sam przekazujesz dalej. Domyślnie wyłączone.",
        ),
        "collect_sw" to row("Samle nye ord", "Collect new words", "Zbieraj nowe słowa"),
        "pop_head" to row("Popularitetsrangering (valgfritt)", "Popularity ranking (optional)",
            "Ranking popularności (opcjonalnie)"),
        "pop_body" to row(
            "Når dette er slått på, telles mellomskandinaviske ord du bekrefter mens du " +
                "skriver – med sveip, ved å velge fra forslagslinjen eller ved å fullføre et " +
                "kjent ord – lokalt, til en liste over de mest brukte ordene. Pseudonymt " +
                "(enhets-ID nedenfor), uten personopplysninger. Ingenting sendes " +
                "automatisk – det når databasen bare når du eksporterer. Av som standard.",
            "When on, Interscandinavian words you CONFIRM while typing — swipe, picking " +
                "from the bar, finishing a known word — are counted LOCALLY to feed a " +
                "ranking of the most popular words. Pseudonymously (device id below), no " +
                "personal data. Nothing leaves automatically — it reaches the database only " +
                "on your export. Off by default.",
            "Gdy włączone, słowa międzyskandynawskie, które ZATWIERDZASZ przy pisaniu — " +
                "swipe'em, wyborem z paska, dokończeniem znanego słowa — są liczone " +
                "LOKALNIE do rankingu najpopularniejszych słów. Pseudonimowo (identyfikator " +
                "urządzenia niżej), bez danych osobowych. Nic nie wychodzi samo — trafia do " +
                "bazy tylko przy Twoim eksporcie. Domyślnie wyłączone.",
        ),
        "pop_sw" to row("Bidra til popularitetsrangeringen", "Contribute to the popularity ranking",
            "Dokładaj do rankingu popularności"),
        "theme_head" to row("Tastaturtema", "Keyboard theme", "Motyw klawiatury"),
        "theme_auto" to row("Automatisk, som telefonen", "Automatic, as the phone", "Automatycznie, jak telefon"),
        "theme_light" to row("Lyst", "Light", "Jasny"),
        "theme_dark" to row("Mørkt", "Dark", "Ciemny"),
        "sym_head" to row("Tegn", "Marks", "Znaki"),
        "sym_body" to row(
            "Hvor ofte du skriver hvert tegn (? , . „ € og resten), telt bare på denne telefonen, " +
                "uten teksten rundt. Det avgjør hvor tastene står: jo mer brukt, jo nærmere tommelen. " +
                "Deles bare i eksporten, og bare med rangeringen på.",
            "How often you type each mark (? , . „ € and the rest), counted on this phone only, " +
                "without the text around it. It decides where the keys go: the more used, the " +
                "nearer the thumb. Shared only in the export, and only with the ranking on.",
            "Jak często wpisujesz każdy znak (? , . „ € i reszta), liczone tylko na tym telefonie, " +
                "bez tekstu dookoła. Na tej podstawie układamy klawisze: im częściej używany, tym " +
                "bliżej kciuka. Wychodzi tylko w eksporcie i tylko przy włączonym rankingu.",
        ),
        "sym_none" to row("Ingenting telt ennå.", "Nothing counted yet.", "Jeszcze nic nie policzono."),
        "sound_head" to row("Lyd og vibrasjon (valgfritt)", "Sound and vibration (optional)",
            "Dźwięk i wibracja (opcjonalnie)"),
        "sound_body" to row(
            "Kort lyd og vibrasjon ved hvert tastetrykk. Av som standard.",
            "A short sound and vibration on each key. Off by default.",
            "Krótki dźwięk i wibracja przy każdym klawiszu. Domyślnie wyłączone.",
        ),
        "sound_sw" to row("Lyd og vibrasjon på tastene", "Key sound and vibration",
            "Dźwięk i wibracja klawiszy"),
        "space_head" to row("Mellomrom mellom ord", "Spacing between words", "Odstęp między słowami"),
        "space_body" to row(
            "Som standard settes mellomrommet inn FØR neste ord, slik at " +
                "tegnsettingen står tett inntil ordet. Slå av for den klassiske " +
                "måten: mellomrommet kommer rett etter ordet.",
            "By default the space is added BEFORE the next word, so punctuation " +
                "stays tight against the word. Turn this off for the classic way: " +
                "the space follows the word immediately.",
            "Domyślnie spacja trafia PRZED następne słowo, więc interpunkcja przylega do " +
                "słowa. Wyłącz, jeśli wolisz klasycznie: spacja zaraz po słowie.",
        ),
        "space_sw" to row("Smart mellomrom", "Smart space", "Inteligentna spacja"),
        "btn_batch" to row("Send til databasen (JSON-pakke)", "Send to the database (JSON batch)",
            "Wyślij do bazy (paczka JSON)"),
        "btn_export" to row("Eksporter til gjennomgang (liste)", "Export for review (list)",
            "Eksportuj do przeglądu (lista)"),
        "btn_clear" to row("Tøm køen", "Clear the queue", "Wyczyść kolejkę"),
        "t_cleared" to row("Køen er tømt", "Queue cleared", "Kolejka wyczyszczona"),
        "t_empty" to row("Køen er tom", "The queue is empty", "Kolejka jest pusta"),
        "st_new" to row("Nye ord", "New words", "Nowe słowa"),
        "st_on" to row("på", "on", "włączone"),
        "st_off" to row("av", "off", "wyłączone"),
        "st_queue" to row("i kø", "queued", "w kolejce"),
        "st_rank" to row("rangering", "ranking", "ranking"),
        "sub_batch" to row("mellomskandinavisk — pakke til databasen",
            "Interscandinavian — batch for the database", "międzyskandynawski — paczka do bazy"),
        "sub_review" to row("mellomskandinavisk — ord til gjennomgang",
            "Interscandinavian — words for review", "międzyskandynawski — słowa do przeglądu"),
        "chooser" to row("Send", "Send", "Wyślij"),
    )
}
