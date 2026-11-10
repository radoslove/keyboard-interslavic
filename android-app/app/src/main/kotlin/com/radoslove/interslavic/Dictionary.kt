package com.radoslove.interslavic

import android.content.Context
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * The prediction model (android_PLAN.md M2). Loads `assets/main_<lang>.combined`
 * — the same wordlist that feeds HeliBoard's swipe dictionary, 248 840 forms in
 * the format `word=X,f=N` — into memory and answers prefix queries ranked by
 * frequency.
 *
 * Android has no keyboard memory cap the way an iOS extension does, so this
 * loads the whole list on purpose: M2 is where we learn how large a model is
 * actually *useful* before iOS forces a budget on us. Loading happens once, on
 * a background thread; until it finishes, [suggest] simply returns nothing.
 *
 * The list is standard orthography only (č š ž ě), matching every other surface.
 */
object Dictionary {

    private class Entry(val word: String, val freq: Int)

    /** Asset per language; see [Lang]. */
    private fun asset(lang: String) = "main_$lang.combined"

    /** Every language loaded so far. A language is read the first time it is
     *  used and then kept, so 🌐 switches back without a second load. */
    private val loaded = HashMap<String, Array<Entry>>()
    private val loading = HashSet<String>()

    @Volatile private var current = Lang.MAIN
    @Volatile private var ready = false
    @Volatile private var entries: Array<Entry> = emptyArray()

    /** Idempotent; makes the keyboard's current language the active list and
     *  kicks off its background load the first time. */
    fun ensureLoaded(context: Context) = use(context, Lang.current(context))

    /** Switch the active list to [lang]; until it has loaded, [suggest] is empty. */
    @Synchronized
    fun use(context: Context, lang: String) {
        current = lang
        val have = loaded[lang]
        if (have != null) { entries = have; ready = true; return }
        ready = false
        entries = emptyArray()
        if (!loading.add(lang)) return
        val app = context.applicationContext
        Thread({ load(app, lang) }, "dict-load-$lang").apply { isDaemon = true; start() }
    }

    private fun load(context: Context, lang: String) {
        var arr: Array<Entry>? = null
        try {
            val list = ArrayList<Entry>(if (lang == Lang.PL) 410_000 else 250_000)
            context.assets.open(asset(lang)).use { stream ->
                BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { br ->
                    var line = br.readLine()
                    while (line != null) {
                        val s = line.trim()
                        if (s.startsWith("word=")) {
                            val fi = s.indexOf(",f=")
                            if (fi > 5) {
                                val w = s.substring(5, fi)
                                val f = s.substring(fi + 3).toIntOrNull() ?: 0
                                if (w.isNotEmpty()) list.add(Entry(w, f))
                            }
                        }
                        line = br.readLine()
                    }
                }
            }
            list.sortBy { it.word }
            val known = HashSet<String>(list.size * 2)
            for (e in list) known.add(e.word)
            for (w in UserWords.all(context, lang)) {
                if (known.add(w)) list.add(Entry(w, USER_FREQ))
            }
            list.sortBy { it.word }
            arr = list.toTypedArray()
        } catch (_: Throwable) {
            // A missing/broken asset just means no predictions — never crash typing.
        }
        synchronized(this) {
            loading.remove(lang)
            if (arr != null) {
                loaded[lang] = arr
                if (current == lang) { entries = arr; ready = true }
            }
        }
    }

    /**
     * Up to [n] words that start with [prefix] (lowercased by the caller),
     * highest frequency first. Empty while the model is still loading.
     *
     * DIACRITICS DO NOT HAVE TO BE TYPED (owner 2026-11-07: "czasami nie jestem
     * pewnym co powinno byc"): `verojetno` finds `věrojetno`, because every
     * letter is compared through [foldBase] - the same fold the glide decoder
     * uses, so the two can never disagree about which letters share a key. It
     * matters more than it sounds: 45 % of the Interslavic forms and 61 % of the
     * Polish ones carry a mark, and `ě` sorts after every ASCII letter, so a
     * literal search for `ve` did not even reach `věrojetno` - it broke out at
     * the first word that was not `ve…` and showed nothing.
     *
     * A WORD IS OFFERED FROM THE FIRST LETTER. The old scan stopped after 6 000
     * entries, which on a one-letter prefix is a slice of the ALPHABET, not of
     * the likely words: `p` in the Polish list opens 90 663 forms, so everything
     * from about `pod…` on was invisible until enough letters had been typed to
     * narrow the range by hand. The budget is now larger than the largest range
     * any single letter opens (measured: isv `p` 39 260, pl `p` 90 663), so the
     * ranking really sees every candidate and the ordering - frequency, then the
     * user's own usage through [boost] - decides what shows.
     */
    fun suggest(prefix: String, n: Int, boost: (String) -> Int = { 0 }): List<String> {
        if (!ready || prefix.isEmpty()) return emptyList()
        val arr = entries
        val topWords = arrayOfNulls<String>(n)
        val topScore = IntArray(n) { Int.MIN_VALUE }
        var budget = SCAN_BUDGET
        for (start in searchPrefixes(prefix)) {
            var i = lowerBound(arr, start)
            while (i < arr.size && budget > 0) {
                val e = arr[i]
                if (!e.word.startsWith(start)) break
                i++
                budget--
                if (!foldedStartsWith(e.word, prefix)) continue
                // Rank by a composite: frequency dominates, but a whole inflected
                // paradigm shares ONE frequency (the lemma's), so ties are common -
                // and breaking them alphabetically buried the everyday form (možemo)
                // under an aorist (možehmo). Prefer the SHORTER form on a tie; the
                // common present-tense forms are shorter than the aorist/imperfect
                // ones. (64 > any word length, so frequency still wins outright.)
                // + adaptive usage: words the user actually uses climb the ranking.
                var score = e.freq * 64 - e.word.length + boost(e.word)
                if (score > topScore[n - 1] && isArchaicPast(e.word)) score -= ARCHAIC_PENALTY
                if (score > topScore[n - 1]) {
                    var j = n - 1
                    while (j > 0 && topScore[j - 1] < score) {
                        topScore[j] = topScore[j - 1]
                        topWords[j] = topWords[j - 1]
                        j--
                    }
                    topScore[j] = score
                    topWords[j] = e.word
                }
            }
        }
        val out = ArrayList<String>(n)
        for (w in topWords) if (w != null) out.add(w)
        return out
    }

    /** An aorist/imperfect form is ranked as if it were 30 frequency points
     *  rarer than it is - enough to sink behind its own everyday siblings
     *  (which share the lemma's frequency), far from enough to hide it when
     *  nothing else matches the letters. */
    private const val ARCHAIC_PENALTY = 30 * 64

    /** Larger than the biggest range one letter opens in any of the six lists
     *  (pl `p` = 90 663), so a short prefix is never cut off alphabetically. */
    private const val SCAN_BUDGET = 250_000

    /** Does [word] begin with [typed], reading both through [foldBase]? */
    private fun foldedStartsWith(word: String, typed: String): Boolean {
        if (word.length < typed.length) return false
        for (k in typed.indices) if (foldBase(word[k]) != foldBase(typed[k])) return false
        return true
    }

    /** Every letter that shares a key with [c], [c] itself first. Built FROM
     *  [foldBase], so adding a letter to the glide fold adds it to search too. */
    private val sameKey: Map<Char, String> by lazy {
        val byBase = HashMap<Char, StringBuilder>()
        for (c in "abcdefghijklmnopqrstuvwxyzæøčćšśžżźěęéèêąàáłńňóòôíýúůřťďäö") {
            byBase.getOrPut(foldBase(c)) { StringBuilder() }.append(c)
        }
        val out = HashMap<Char, String>()
        for ((_, b) in byBase) for (c in b) out[c] = b.toString()
        out
    }

    /** Literal prefixes to binary-search for, covering the spellings [prefix]
     *  could have. Only the first letters are expanded - enough to narrow the
     *  range; the rest of the word is filtered by [foldedStartsWith]. The cap
     *  keeps a word of nothing but vowels from exploding into hundreds of
     *  searches (`ee` alone would be 36). */
    private fun searchPrefixes(prefix: String): List<String> {
        var out = listOf("")
        for (k in 0 until minOf(prefix.length, 3)) {
            val cls = sameKey[prefix[k]] ?: prefix[k].toString()
            if (out.size * cls.length > 24) break
            val next = ArrayList<String>(out.size * cls.length)
            for (p in out) for (c in cls) next.add(p + c)
            out = next
        }
        return if (out.size == 1 && out[0].isEmpty()) listOf(prefix.take(1)) else out
    }

    /**
     * The most frequent REAL word that is a proper beginning of [word], at
     * least [min] letters long, or null. Used after a glide: the path that
     * draws `myslimo` draws `myslim` on the way, so the shorter form must be
     * offerable even when the decoder preferred the longer one. Costs one
     * binary search per length, nothing measurable.
     */
    fun bestPrefix(word: String, min: Int = 3): String? {
        if (!ready || word.length <= min) return null
        val arr = entries
        var best: String? = null
        var bestFreq = -1
        for (k in min until word.length) {
            val p = word.substring(0, k)
            val i = lowerBound(arr, p)
            if (i < arr.size && arr[i].word == p && arr[i].freq > bestFreq) {
                bestFreq = arr[i].freq
                best = p
            }
        }
        return best
    }

    /**
     * Endings of the AORIST and IMPERFECT - the two past tenses nobody writes
     * in everyday Interslavic (owner 2026-11-08: "aoryst moze zostac tylko
     * dalej w kolejce"). Kept typeable, pushed down the queue.
     */
    private val PAST_ENDS = arrayOf(
        "hmo", "hu", "ste", "sta", "hte", "še", "šę", "h",
        "ahmo", "ahu", "aste", "aše", "ah",
        "ěhmo", "ěhu", "ěste", "ěše", "ěh",
        "jahmo", "jahu", "jaste", "jaše", "jah",
    )

    /**
     * Is [word] an aorist or imperfect form? Decided by the ending PLUS the
     * infinitive it would come from actually existing in this list, because
     * the ending alone lies: `vyše`, `uspěh`, `duh`, `naših`, `jeste` and
     * `bogah` all end like a past tense and are ordinary words (measured on
     * the list, 2026-11-08 - 2 150 of the 19 497 pattern matches are six
     * letters or shorter and that is where the collisions sit).
     *
     * This is why the forms are RANKED DOWN and not deleted: a homograph that
     * is also someone's aorist still has to be typeable, and the penalty only
     * reorders it against its own siblings - with nothing else competing for
     * the same letters it still comes first.
     *
     * Called only for a word that is about to enter the top of a ranking, so
     * the handful of binary searches costs nothing.
     */
    fun isArchaicPast(word: String): Boolean {
        // EIGHT letters, measured: every collision found on the list is seven
        // or shorter (`vyše` `uspěh` `duh` `naših` `jeste` `bogah` `směhu`
        // `strahu` `glavah`), because nouns are short and a past tense of a
        // real verb is long. The threshold keeps 14 569 of the 19 354 pattern
        // matches - including `myslihmo`, the one that started this - and
        // leaves the short aorists (`myslih`, `mysliše`) ranked as before,
        // which is the price of not touching a single everyday word.
        if (!ready || word.length < 8) return false
        for (e in PAST_ENDS) {
            if (!word.endsWith(e)) continue
            val stem = word.substring(0, word.length - e.length)
            if (stem.length < 2) continue
            if (hasWord(stem + "ti") || hasWord(stem + "ći")) return true
        }
        return false
    }

    /** Exact lookup, for [isArchaicPast]. */
    private fun hasWord(w: String): Boolean {
        val arr = entries
        val i = lowerBound(arr, w)
        return i < arr.size && arr[i].word == w
    }

    /** Whether the background load has finished. */
    fun isReady(): Boolean = ready

    // ---- Swipe decoding (M4) --------------------------------------------

    /** Fold a held letter to the key it lives on: č->c ž->z ě->e, and the
     *  Polish ą ć ę ł ń ó ś ź ż likewise - a glide over `żółw` crosses z o l w.
     *  Public because KeyboardView needs the same answer to measure the touch
     *  lift ([Lift]): which KEY the first and last letter of a glided word sat on. */
    fun foldBase(c: Char): Char = when (c) {
        'č', 'ć' -> 'c'; 'š', 'ś' -> 's'; 'ž', 'ż', 'ź' -> 'z'; 'ě', 'ę', 'é', 'è', 'ê' -> 'e'
        'ą', 'à', 'á' -> 'a'; 'ł', 'ľ', 'ĺ' -> 'l'; 'ń', 'ň' -> 'n'; 'ó', 'ò', 'ô' -> 'o'
        // Czech: the length marks and háčeks sit on their base key, like every other
        // held letter. Missing even one of these kills the glide for every word
        // that contains it - the decoder would look for a key that is not there.
        'í' -> 'i'; 'ý' -> 'y'; 'ú', 'ů' -> 'u'; 'ř', 'ŕ' -> 'r'; 'ť' -> 't'; 'ď' -> 'd'
        // Croatian, Serbian: `đ` is not `d` plus a mark to Unicode, like `ł`.
        'đ' -> 'd'
        // Norwegian: ä and ö are held on æ and ø, which are keys of their own.
        // Everywhere else (Slovak `ä`) they sit on a and o - folding Slovak `ä`
        // onto a key the keyboard does not have took every such word out of the
        // glide.
        'ä' -> if (current == Lang.NB || current == Lang.ISC) 'æ' else 'a'
        'ö' -> if (current == Lang.NB || current == Lang.ISC) 'ø' else 'o'
        else -> c
    }

    /** In Cyrillic and Glagolitic the `q` and `w` keys carry the `lj` and `nj`
     *  digraphs (`Layout.keyText`: `љ`/`њ`, `ⰾⱐ`/`ⱀⱐ`), so a
     *  glide over one of them stands for the PAIR `lj` / `nj`, and a word that
     *  contains the pair is glided over ONE key, not two. Set from the current
     *  script before decoding; the wordlist itself stays Latin. */
    @Volatile
    var digraphKeys: Boolean = false

    /** The key a digraph at [i] folds to, or null if there is no digraph there. */
    private fun pairKey(w: String, i: Int): Char? {
        if (!digraphKeys || i + 1 >= w.length) return null
        val first = w[i].lowercaseChar()
        val second = w[i + 1].lowercaseChar()
        return when {
            second == 'j' && foldBase(first) == 'l' -> 'q'
            second == 'j' && foldBase(first) == 'n' -> 'w'
            // Serbian Cyrillic only: `dž` is the single letter `џ` on the `x`
            // key. The second letter is matched UNFOLDED on purpose - folding
            // would also catch a plain `z`, and `d` + `z` meet across a prefix
            // (`odzvanjati`), where they are two letters and no `џ`.
            second == 'ž' && first == 'd' && current == Lang.SR -> 'x'
            else -> null
        }
    }

    /** The sequence of KEYS a word is glided over. */
    fun foldWord(w: String): String {
        val sb = StringBuilder(w.length)
        var i = 0
        while (i < w.length) {
            val pair = pairKey(w, i)
            if (pair != null) { sb.append(pair); i += 2 } else { sb.append(foldBase(w[i])); i++ }
        }
        return sb.toString()
    }

    /** Per-char fold only: the route over `l` and `j`, which are still keys of
     *  their own. A word with `lj` therefore has TWO routes in Cyrillic - over
     *  `љ`, or over the two letters - and both have to decode (owner 2026-11-08:
     *  "zeby lapalo wyrazy i po swipowaniu przez љ albo њ i przez osobno l i j").
     *  The better route scores; this one costs nothing for a word without a pair,
     *  because then it equals [foldWord] and is never computed twice. */
    private fun foldPlain(w: String): String {
        val sb = StringBuilder(w.length)
        for (c in w) sb.append(foldBase(c))
        return sb.toString()
    }

    /** First and last key of a word, without building the whole folded string -
     *  these two run over EVERY entry on every decode, so they stay allocation free. */
    fun foldFirst(w: String): Char = pairKey(w, 0) ?: foldBase(w.first())

    fun foldLast(w: String): Char =
        if (w.length >= 2) pairKey(w, w.length - 2) ?: foldBase(w.last()) else foldBase(w.last())

    /**
     * Geometry glide decoder. The caller (KeyboardView, which has the key
     * positions) passes a [score] function that, for a folded candidate word,
     * returns how well its letters lie along the finger's actual path — higher
     * is better, Double.NEGATIVE_INFINITY means "a letter never came near the
     * path, reject". This replaces the brittle ordered-key-crossing model:
     * on a long diagonal the finger crosses keys OUT of the word's letter order,
     * so order can't be trusted — distance-to-path can.
     *
     * We still anchor the FIRST letter to the start of the path to keep the
     * candidate set small, but to the few keys NEAREST that start rather than
     * to one. Anchoring to a single key silently excluded the right word:
     * `p` sits directly above `l`, so a glide for `pisati` begun a few pixels
     * low anchored on `l` and only `l...` words were ever scored - `pisati`
     * did not lose to `ležati`, it never entered the race. Geometry still
     * decides between the survivors. Frequency is only a tiebreak between words
     * that fit the path about equally well. Accents/digraphs match on their base
     * letters; the real accented word is returned.
     */
    fun decodeSwipeGeo(
        firstKeys: Set<Char>,
        lastKeys: Set<Char>,
        maxLen: Int,
        n: Int,
        coarse: (String) -> Double,
        fine: (String) -> Double,
        boost: (String) -> Double = { 0.0 },
    ): List<String> {
        if (!ready) return emptyList()

        // PASS 1 - cheap. Every word that survives the letter gates gets a
        // low-resolution shape comparison. This is where the volume is: a glide
        // for `pisati` crosses fifteen keys, so the length gate admits words up
        // to seventeen letters and roughly sixteen THOUSAND candidates reach
        // scoring. Running the full comparison on all of them took ~0.5 s - and
        // it runs again every 80 ms while the finger is still moving, which is
        // exactly the lag felt on a slower phone.
        val kept = arrayOfNulls<String>(SHORTLIST)
        val keptScore = DoubleArray(SHORTLIST) { Double.NEGATIVE_INFINITY }
        for (e in entries) {
            val w = e.word
            if (w.length < 2 || w.length > maxLen) continue
            // Both gates admit EITHER route of a Cyrillic digraph word: the
            // glide may have started on `љ` or on `l`. Still allocation free -
            // these two lines run over every entry on every decode.
            if (foldFirst(w) !in firstKeys &&
                (!digraphKeys || foldBase(w.first()) !in firstKeys)) continue
            // Cheap gate before the costly geometry: a glide ends ON its last
            // letter, so a word ending elsewhere cannot be what was drawn.
            if (lastKeys.isNotEmpty() && foldLast(w) !in lastKeys &&
                (!digraphKeys || foldBase(w.last()) !in lastKeys)) continue
            val folded = foldWord(w)
            var geo = coarse(folded)
            if (digraphKeys) {
                val plain = foldPlain(w)
                if (plain != folded) {
                    val alt = coarse(plain)
                    if (alt > geo) geo = alt
                }
            }
            if (geo == Double.NEGATIVE_INFINITY) continue
            val s = geo + freqTerm(e.freq)
            if (s > keptScore[SHORTLIST - 1]) {
                var j = SHORTLIST - 1
                while (j > 0 && keptScore[j - 1] < s) {
                    keptScore[j] = keptScore[j - 1]; kept[j] = kept[j - 1]; j--
                }
                keptScore[j] = s; kept[j] = w
            }
        }

        // PASS 2 - the real comparison, on the shortlist only. Measured on
        // synthetic glides: identical top-1 and top-3 against scoring everything,
        // and about eight times faster.
        val topWords = arrayOfNulls<String>(n)
        val topScore = DoubleArray(n) { Double.NEGATIVE_INFINITY }
        for (w in kept) {
            if (w == null) continue
            var folded = foldWord(w)
            var geo = fine(folded)
            if (digraphKeys) {
                val plain = foldPlain(w)
                if (plain != folded) {
                    val alt = fine(plain)
                    // The route that fits better is also the one whose doubled
                    // letters the penalty below should count.
                    if (alt > geo) { geo = alt; folded = plain }
                }
            }
            if (geo == Double.NEGATIVE_INFINITY) continue
            // geo is negative px (0 = letters sit exactly on the path); a small
            // frequency nudge breaks ties, and adaptive usage nudges harder so a
            // word you keep swiping wins the near-ties geometry cannot resolve.
            val freq = freqOf(w)
            val s = geo + freqTerm(freq) + boost(w) -
                doubles(folded) * DOUBLE_W
            if (s > topScore[n - 1]) {
                var j = n - 1
                while (j > 0 && topScore[j - 1] < s) {
                    topScore[j] = topScore[j - 1]; topWords[j] = topWords[j - 1]; j--
                }
                topScore[j] = s; topWords[j] = w
            }
        }
        return topWords.filterNotNull()
    }

    /**
     * Lists built by log-scaling corpus counts into `f` (FrequencyWords: isc, nb,
     * en) pull LINEARLY in `f`: taking log(f) again flattened them, and everyday
     * `her` (f=217) beat a rare three-letter word (f=60) by only ~4.5 px, so `her`
     * and `vi` lost glides to rarer neighbours (tester report, ISC test 0.1.3).
     * FREQ_LIN * f gives that pair ~18 px, with FREQ_W's log as the floor.
     * The Interslavic, Polish and Czech lists keep the log: their bytes are
     * taken from the isv distribution by quantile, not scaled from a count.
     */
    private const val FREQ_LIN = 0.15
    // Polish and Czech joined 2026-11-04. Their byte is a RANK (quantile-matched
    // onto the isv distribution), so taking the log again left the whole signal
    // worth ~5 px of path deviation - 5 % of a key - and `semtex` outranked the
    // median of the dictionary. Measured on the same synthetic glides: on the
    // words people actually type, Polish top-1 68 % -> 74 %; on a sample drawn
    // uniformly from 412 k forms it is a wash (61 % both ways) and on the rare
    // half it costs nothing, so the gain is real but narrow - it buys the
    // frequent words, which is what running text is made of.
    private val LINEAR_FREQ = setOf(Lang.ISC, Lang.NB, Lang.EN, Lang.PL, Lang.CS)

    private fun freqTerm(f: Int): Double {
        val log = Math.log((f + 1).toDouble()) * FREQ_W
        return if (current in LINEAR_FREQ) maxOf(log, f * FREQ_LIN) else log
    }

    /**
     * A doubled letter is invisible in a glide: `žže` and `že` draw the same
     * line, so geometry ties and the decision falls to frequency and usage.
     * One kept `žže` (usage +8) outweighed the whole frequency gap (že 204,
     * žže 74 = +3.5), and `žže` then came first every time (owner 2026-10-05).
     * Each double therefore costs a fixed penalty: the double form stays in the
     * strip, one tap away, but needs real repeated use to lead.
     */
    private const val DOUBLE_W = 10.0

    private fun doubles(folded: String): Int {
        var n = 0
        for (i in 1 until folded.length) if (folded[i] == folded[i - 1]) n++
        return n
    }

    /** How many words survive pass 1. Measured: 100, 200 and 400 all score the
     *  same, so this sits in the middle rather than at the edge of what works. */
    private const val SHORTLIST = 200

    /** How hard a word's own frequency pulls. Measured on synthetic glides:
     *  3.5 is the peak - a sloppy gesture gains two points of top-1 over 2.5,
     *  a tidy one is unchanged, and past 3.5 it starts costing. This is the
     *  "round towards the word people actually use" knob. */
    private const val FREQ_W = 3.5

    private fun freqOf(word: String): Int {
        val arr = entries
        val i = lowerBound(arr, word)
        return if (i < arr.size && arr[i].word == word) arr[i].freq else 0
    }

    /**
     * A word the user added themselves ([UserWords]). Middling frequency: it has
     * to be offered and glidable, but must not outrank everyday words that share
     * its prefix. Measured shipped frequencies run to roughly 250.
     */
    private const val USER_FREQ = 120

    /** Drop [lang]'s loaded list, so the next use reads it again - after a
     *  user word was removed in the settings. */
    @Synchronized
    fun forget(lang: String) {
        loaded.remove(lang)
        if (current == lang) { ready = false; entries = emptyArray() }
    }

    /** Insert a user-added word into the live list; no-op if known or not loaded. */
    @Synchronized
    fun addUserWord(word: String) {
        if (!ready || contains(word)) return
        val arr = entries
        val i = lowerBound(arr, word)
        val out = arrayOfNulls<Entry>(arr.size + 1)
        System.arraycopy(arr, 0, out, 0, i)
        out[i] = Entry(word, USER_FREQ)
        System.arraycopy(arr, i, out, i + 1, arr.size - i)
        @Suppress("UNCHECKED_CAST")
        entries = out as Array<Entry>
        loaded[current] = entries
    }

    /**
     * Is [prefix] the beginning of any word in the list?
     *
     * Asked when a letter is tapped right after a glide, to tell "the glided
     * word is being EXTENDED" from "the next word is starting". That used to be
     * decided by a clock alone, so whether two words came out glued depended on
     * how fast the user typed: a letter tapped within 1.2 s of a glide to begin
     * the next word landed as `wyjscs` (owner 2026-11-04). The list can answer
     * the real question - can the word still grow this way? - and it includes
     * the user's own words, so a word they added behaves like any other.
     */
    fun hasPrefix(prefix: String): Boolean {
        if (!ready || prefix.isEmpty()) return false
        val arr = entries
        val i = lowerBound(arr, prefix)
        return i < arr.size && arr[i].word.startsWith(prefix)
    }

    /** Exact membership test (binary search). Used to decide MISS for M3. */
    fun contains(word: String): Boolean {
        if (!ready) return false
        val arr = entries
        val i = lowerBound(arr, word)
        return i < arr.size && arr[i].word == word
    }

    /** First index whose word is >= prefix (binary search). */
    private fun lowerBound(arr: Array<Entry>, prefix: String): Int {
        var lo = 0
        var hi = arr.size
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (arr[mid].word < prefix) lo = mid + 1 else hi = mid
        }
        return lo
    }
}
