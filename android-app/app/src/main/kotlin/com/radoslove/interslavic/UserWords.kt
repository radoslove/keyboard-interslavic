package com.radoslove.interslavic

import android.content.Context
import java.io.File

/**
 * Words the user added with the "＋ word" chip — the personal dictionary.
 *
 * The wordlist carries about a third of the forms the database knows, so a
 * correct word the keyboard has never seen is common (`prišlji`, the imperative
 * of `prislati`, was the first one reported). Without this the keyboard kept
 * offering to "fix" it and could never glide it.
 *
 * Separate from [Collector] on purpose: collecting a MISS for review is opt-in
 * and leaves the phone only by a deliberate export, while this is the user
 * telling their own keyboard "this is a word". Adding one also offers it to the
 * collector, which still decides by its own opt-in.
 *
 * On-device only. Storage: `filesDir/user_words.txt` (Interslavic) and
 * `user_words_<lang>.txt` for every other language, one lowercase word a line -
 * a Polish word saved on the Polish keyboard must not turn up in Interslavic.
 */
object UserWords {

    private fun file(context: Context, lang: String) =
        File(context.filesDir, if (lang == Lang.MAIN) "user_words.txt" else "user_words_$lang.txt")

    /** Every saved word of [lang]; empty when there is no file or it cannot be read. */
    @Synchronized
    fun all(context: Context, lang: String = Lang.current(context)): List<String> = try {
        val f = file(context, lang)
        if (f.exists()) f.readLines().map { it.trim() }.filter { it.isNotEmpty() } else emptyList()
    } catch (_: Throwable) {
        emptyList()
    }

    /** Save [word] (lowercased) once and make it known to [Dictionary] at once. */
    @Synchronized
    fun add(context: Context, word: String) {
        val w = word.lowercase()
        if (w.isEmpty() || !w.all { it.isLetter() }) return
        val lang = Lang.current(context)
        if (w !in all(context, lang)) {
            try {
                file(context, lang).appendText(w + "\n")
            } catch (_: Throwable) {
                // Losing a saved word is bad; crashing the keyboard is worse.
            }
        }
        Dictionary.addUserWord(w)
    }
}
