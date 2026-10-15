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

    fun current(context: Context): String {
        val saved = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
        if (saved != null && saved in ALL) return saved
        return when (Lang.deviceLang()) {
            Lang.PL -> "pl"
            Lang.NB -> "nb"
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
            Lang.ISV to mapOf("pl" to "międzysłowiański", "nb" to "interslavisk", "en" to "Interslavic"),
            Lang.ISC to mapOf("pl" to "międzyskandynawski", "nb" to "mellomskandinavisk", "en" to "Interscandinavian"),
            Lang.PL to mapOf("nb" to "polsk", "en" to "Polish"),
            Lang.NB to mapOf("pl" to "norweski", "en" to "Norwegian"),
            Lang.EN to mapOf("pl" to "angielski", "nb" to "engelsk"),
        )[lang]?.get(if (ui == MS) "en" else ui)
        return if (gloss == null || gloss == own) own else "$own ($gloss)"
    }

    /** [key] in [lang]; `ms` falls back to English for the few keys it lacks. */
    fun t(lang: String, key: String): String =
        TEXT[key]?.let { it[lang] ?: it["en"] } ?: key

    /** The English gloss under an MS paragraph, or null outside `ms`. */
    fun gloss(lang: String, key: String): String? = if (lang == MS) TEXT[key]?.get("en") else null

    private fun row(ms: String?, en: String, pl: String, nb: String) =
        buildMap { if (ms != null) put(MS, ms); put("en", en); put("pl", pl); put("nb", nb) }

    private val TEXT: Map<String, Map<String, String>> = mapOf(
        "title" to row("Medžuslovjansky", "Interslavic keyboard", "Klawiatura międzysłowiańska", "Interslavisk tastatur"),
        "intro" to row(
            "Medžuslovjanska klaviatura. Standardne bukvy (č š ž ě) na dolgom " +
                "pritisku i mahu vgoru; pas prědloženij iz slovnika. Takože jezyky: " +
                "medžuskandinavsky, poljsky, norvežsky i anglijsky.",
            "Interslavic keyboard. Standard letters (č š ž ě) on long-press and " +
                "an upward flick; a suggestion bar from the dictionary. Also Interscandinavian, " +
                "Polish, Norwegian and English.",
            "Klawiatura międzysłowiańska. Litery č š ž ě pod przytrzymaniem i ruchem " +
                "w górę, podpowiedzi ze słownika. Można też pisać po międzyskandynawsku, polsku, " +
                "norwesku i angielsku.",
            "Interslavisk tastatur. Bokstavene č š ž ě ligger under langt trykk og et " +
                "sveip oppover, med forslag fra ordboka. Du kan også skrive mellomskandinavisk, " +
                "norsk, polsk og engelsk.",
        ),
        "ui_lang" to row(null, "Language of this screen", "Język tego ekranu", "Språk på denne skjermen"),
        "langs_head" to row("Jezyky klaviatury", "Keyboard languages", "Języki klawiatury", "Tastaturspråk"),
        "langs_body" to row(
            "Izberi jezyky:",
            "Each language is its own entry in the phone's keyboard list. Interslavic is " +
                "on; add the others with the button below. 🌐 switches between them (held, it opens the " +
                "keyboard list). A language's dictionary loads only once you use it. " +
                "Cyrillic and Glagolitic are for Interslavic.",
            "Każdy język to osobna pozycja na systemowej liście klawiatur. Międzysłowiański " +
                "jest włączony, pozostałe dodasz przyciskiem poniżej. 🌐 przełącza między nimi " +
                "(przytrzymany otwiera listę klawiatur). Słownik języka wczytuje się dopiero " +
                "przy pierwszym użyciu. Cyrylica i głagolica są dla międzysłowiańskiego.",
            "Hvert språk er en egen oppføring i telefonens tastaturliste. Interslavisk er " +
                "på; legg til de andre med knappen nedenfor. 🌐 bytter mellom dem (holdt inne åpner den " +
                "tastaturlisten). Ordboka for et språk lastes først når du bruker det. " +
                "Kyrillisk og glagolittisk gjelder interslavisk.",
        ),
        "langs_now" to row(null, "On now", "Włączone teraz", "På nå"),
        "btn_langs" to row("Izberi jezyky", "Choose languages", "Wybierz języki", "Velg språk"),
        "script_head" to row("Latinica, kirilica ili glagolica", "Latin, Cyrillic or Glagolitic",
            "Łacinka, cyrylica czy głagolica", "Latinsk, kyrillisk eller glagolittisk"),
        "script_body" to row(
            "Izberi:",
            "The Interslavic keyboard writes in the chosen script. The letters stay on " +
                "the same keys, and the dictionary and swipe work the same.",
            "Klawiatura międzysłowiańska pisze wybranym alfabetem. Litery zostają na tych " +
                "samych klawiszach, słownik i swipe działają tak samo.",
            "Det interslaviske tastaturet skriver med det valgte alfabetet. Bokstavene " +
                "blir på de samme tastene, og ordbok og sveip virker likt.",
        ),
        "s_latin" to row("Latinica", "Latin", "Łacinka", "Latinsk"),
        "s_cyrillic" to row("Kirilica", "Cyrillic", "Cyrylica", "Kyrillisk"),
        "s_glagolitic" to row("Glagolica", "Glagolitic", "Głagolica", "Glagolittisk"),
        "collect_head" to row("Sbiranje novyh slov (opcionalno)", "Collecting new words (optional)",
            "Zbieranie nowych słów (opcjonalnie)", "Innsamling av nye ord (valgfritt)"),
        "collect_body" to row(
            "Kogda je vključeno, klaviatura lokalno zapisyvaje slova, ktoryh nema v " +
                "slovniku, da by pozdněje šli do prěgleda (nikogda ne avtomatično " +
                "popravjene). Ničto se ne posyla — izvoz je fajl, ktory sam prědaješ " +
                "dalje. Standardno izključeno.",
            "When on, the keyboard saves Interslavic words NOT in the dictionary LOCALLY, " +
                "to later go to review (never auto-corrected). Nothing is sent — the " +
                "export is a file you pass on yourself. Off by default.",
            "Gdy włączone, klawiatura zapisuje LOKALNIE słowa międzysłowiańskie, których " +
                "nie ma w słowniku, żeby później trafiły do przeglądu (nigdy nie są " +
                "poprawiane automatycznie). Nic nie jest wysyłane — eksport to plik, który " +
                "sam przekazujesz dalej. Domyślnie wyłączone.",
            "Når dette er på, lagrer tastaturet LOKALT interslaviske ord som ikke står i " +
                "ordboka, slik at de kan gjennomgås senere (de rettes aldri automatisk). " +
                "Ingenting sendes — eksporten er en fil du selv sender videre. Av som standard.",
        ),
        "collect_sw" to row("Sbiraj nove slova", "Collect new words", "Zbieraj nowe słowa", "Samle nye ord"),
        "pop_head" to row("Poredok popularnosti (opcionalno)", "Popularity ranking (optional)",
            "Ranking popularności (opcjonalnie)", "Popularitetsrangering (valgfritt)"),
        "pop_body" to row(
            "Kogda je vključeno, slova, ktore potvrdiš pri pisanju — svajpom, " +
                "izborom iz pasa, dokončenjem znanogo slova — sut lokalno čisljene za " +
                "spis najpopularnějših medžuslovjanskih slov. Psevdonimno " +
                "(identifikator telefona niže), bez personalnyh danyh. Ničto ne odhodi " +
                "avtomatično — do bazy dojde jedino pri tvojem izvozu. Standardno " +
                "izključeno.",
            "When on, Interslavic words you CONFIRM while typing — swipe, picking from the " +
                "bar, finishing a known word — are counted LOCALLY to feed a ranking " +
                "of the most popular Interslavic words. Pseudonymously (device id " +
                "below), no personal data. Nothing leaves automatically — it reaches " +
                "the database only on your export. Off by default.",
            "Gdy włączone, słowa międzysłowiańskie, które ZATWIERDZASZ przy pisaniu — " +
                "swipe'em, wyborem z paska, dokończeniem znanego słowa — są liczone " +
                "LOKALNIE do rankingu najpopularniejszych słów. Pseudonimowo (identyfikator " +
                "urządzenia niżej), bez danych osobowych. Nic nie wychodzi samo — trafia do " +
                "bazy tylko przy Twoim eksporcie. Domyślnie wyłączone.",
            "Når dette er på, telles interslaviske ord du BEKREFTER mens du skriver — med " +
                "sveip, valg fra linjen eller ved å fullføre et kjent ord — LOKALT til en " +
                "rangering av de mest brukte ordene. Pseudonymt (enhets-ID nedenfor), uten " +
                "personopplysninger. Ingenting sendes automatisk — det når databasen bare " +
                "når du eksporterer. Av som standard.",
        ),
        "pop_sw" to row("Vklad do poredka popularnosti", "Contribute to the popularity ranking",
            "Dokładaj do rankingu popularności", "Bidra til popularitetsrangeringen"),
        "words_head" to row("Moje slova", "My words", "Moje słowa", "Mine ord"),
        "words_body" to row(
            "Slova dodane čipom ＋ v pasu prědloženij, za každy jezyk. Odstrani slovo i " +
                "klaviatura go prěstane prědlagati.",
            "Words you added with the ＋ chip in the suggestion bar, per language. Remove a " +
                "word and the keyboard stops suggesting it.",
            "Słowa dodane chipem ＋ na pasku podpowiedzi, osobno dla każdego języka. Usuń słowo, " +
                "a klawiatura przestanie je podpowiadać.",
            "Ord du har lagt til med ＋ i forslagslinjen, for hvert språk. Fjern et ord, så " +
                "slutter tastaturet å foreslå det.",
        ),
        "words_none" to row("Ješče ničto ne jest dodano.", "No words added yet.", "Nie dodano jeszcze słów.", "Ingen ord lagt til ennå."),
        "words_remove" to row("Odstrani", "Remove", "Usuń", "Fjern"),
        "words_removed" to row("Odstranjeno", "Removed", "Usunięto", "Fjernet"),
        "theme_head" to row("Tema klaviatury", "Keyboard theme", "Motyw klawiatury", "Tastaturtema"),
        "theme_auto" to row("Avtomatično, kako telefon", "Automatic, as the phone", "Automatycznie, jak telefon", "Automatisk, som telefonen"),
        "theme_light" to row("Světla", "Light", "Jasny", "Lyst"),
        "theme_dark" to row("Temna", "Dark", "Ciemny", "Mørkt"),
        "sym_head" to row("Znaky", "Marks", "Znaki", "Tegn"),
        "sym_body" to row(
            null,
            "How often you type each mark (? , . „ € and the rest), counted on this phone only, " +
                "without the text around it. It decides where the keys go: the more used, the " +
                "nearer the thumb. Shared only in the export, and only with the ranking on.",
            "Jak często wpisujesz każdy znak (? , . „ € i reszta), liczone tylko na tym telefonie, " +
                "bez tekstu dookoła. Na tej podstawie układamy klawisze: im częściej używany, tym " +
                "bliżej kciuka. Wychodzi tylko w eksporcie i tylko przy włączonym rankingu.",
            "Hvor ofte du skriver hvert tegn (? , . „ € og resten), telt bare på denne telefonen, " +
                "uten teksten rundt. Det avgjør hvor tastene står: jo mer brukt, jo nærmere tommelen. " +
                "Deles bare i eksporten, og bare med rangeringen på.",
        ),
        "sym_none" to row(null, "Nothing counted yet.", "Jeszcze nic nie policzono.", "Ingenting telt ennå."),
        "sound_head" to row("Zvuk i vibracija (opcionalno)", "Sound and vibration (optional)",
            "Dźwięk i wibracja (opcjonalnie)", "Lyd og vibrasjon (valgfritt)"),
        "sound_body" to row(
            "Kratky zvuk i vibracija pri každoj klaviši. Standardno izključeno.",
            "A short sound and vibration on each key. Off by default.",
            "Krótki dźwięk i wibracja przy każdym klawiszu. Domyślnie wyłączone.",
            "En kort lyd og vibrasjon for hver tast. Av som standard.",
        ),
        "sound_sw" to row("Zvuk i vibracija klaviatury", "Key sound and vibration",
            "Dźwięk i wibracja klawiszy", "Tastelyd og vibrasjon"),
        "space_head" to row("Odstup medžu slovami", "Spacing between words",
            "Odstęp między słowami", "Mellomrom mellom ord"),
        "space_body" to row(
            "Standardno odstup jest dodavany PRĚD slědujuče slovo, tako že " +
                "interpunkcija se drži slova bez odstupa. Izključi, ako hočeš " +
                "stary sposob: odstup ide směsta po slovu.",
            "By default the space is added BEFORE the next word, so punctuation " +
                "stays tight against the word. Turn this off for the classic way: " +
                "the space follows the word immediately.",
            "Domyślnie spacja trafia PRZED następne słowo, więc interpunkcja przylega do " +
                "słowa. Wyłącz, jeśli wolisz klasycznie: spacja zaraz po słowie.",
            "Som standard settes mellomrommet inn FØR neste ord, slik at tegnsettingen " +
                "står tett inntil ordet. Slå av for den klassiske måten: mellomrom rett etter ordet.",
        ),
        "space_sw" to row("Umny odstup", "Smart space", "Inteligentna spacja", "Smart mellomrom"),
        "btn_batch" to row("Pošli do bazy (grupa JSON)", "Send to the database (JSON batch)",
            "Wyślij do bazy (paczka JSON)", "Send til databasen (JSON-pakke)"),
        "btn_export" to row("Izvezi do prěgleda (spis)", "Export for review (list)",
            "Eksportuj do przeglądu (lista)", "Eksporter til gjennomgang (liste)"),
        "btn_clear" to row("Očisti red", "Clear the queue", "Wyczyść kolejkę", "Tøm køen"),
        "t_cleared" to row(null, "Queue cleared", "Kolejka wyczyszczona", "Køen er tømt"),
        "t_empty" to row(null, "The queue is empty", "Kolejka jest pusta", "Køen er tom"),
        "st_new" to row(null, "New words", "Nowe słowa", "Nye ord"),
        "st_on" to row(null, "on", "włączone", "på"),
        "st_off" to row(null, "off", "wyłączone", "av"),
        "st_queue" to row(null, "queued", "w kolejce", "i kø"),
        "st_rank" to row(null, "ranking", "ranking", "rangering"),
        "sub_batch" to row(null, "Interslavic — batch for the database", "medžuslovjansky — paczka do bazy",
            "interslavisk — pakke til databasen"),
        "sub_review" to row(null, "Interslavic — words for review", "medžuslovjansky — słowa do przeglądu",
            "interslavisk — ord til gjennomgang"),
        "chooser" to row(null, "Send", "Wyślij", "Send"),
    )
}
