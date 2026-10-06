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
 * On-device only. Storage: `filesDir/user_words.txt`, one lowercase word a line.
 */
object UserWords {

    private const val FILE = "user_words.txt"

    private fun file(context: Context) = File(context.filesDir, FILE)

    /** Every saved word; empty when there is no file or it cannot be read. */
    @Synchronized
    fun all(context: Context): List<String> = try {
        val f = file(context)
        if (f.exists()) f.readLines().map { it.trim() }.filter { it.isNotEmpty() } else emptyList()
    } catch (_: Throwable) {
        emptyList()
    }

    /** Save [word] (lowercased) once and make it known to [Dictionary] at once. */
    @Synchronized
    fun add(context: Context, word: String) {
        val w = word.lowercase()
        if (w.isEmpty() || !w.all { it.isLetter() }) return
        if (w !in all(context)) {
            try {
                file(context).appendText(w + "\n")
            } catch (_: Throwable) {
                // Losing a saved word is bad; crashing the keyboard is worse.
            }
        }
        Dictionary.addUserWord(w)
    }
}
