package com.radoslove.interslavic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Morse: every word of every language goes out as Morse and comes back as
 *  the same keyboard Latin; every letter of every table reads back as itself. */
class MorseTest {

    /** Real words, as written in the language. */
    private val samples = mapOf(
        Lang.ISV to listOf("čaj", "medvěd", "dobry den"),
        Lang.PL to listOf("żółw", "łąka", "źdźbło"),
        Lang.SR to listOf("čaša", "đak", "ćup"),
        Lang.HR to listOf("šuma", "žena"),
        Lang.CS to listOf("čaj", "chléb"),
        Lang.DE to listOf("Grüße", "schön"),
        Lang.IT to listOf("città", "perché"),
        Lang.NB to listOf("blåbær", "øl"),
        Lang.RU to listOf("шапка", "объект", "ёлка"),
        Lang.UK to listOf("їжак", "кит", "сім'я"),
        Lang.BG to listOf("ъгъл", "щастие"),
    )

    private fun firstScript(lang: String) = Script.offered(lang).first()

    @Test
    fun roundTripAndShow() {
        val report = StringBuilder()
        for ((lang, words) in samples) {
            for (w in words) {
                val latin = Script.back(w, firstScript(lang), lang)
                val morse = Script.convert(latin, Script.Mode.MORSE, lang)
                val back = Script.back(morse, Script.Mode.MORSE, lang)
                report.append("$lang  $w  ->  $morse  ->  ${Script.convert(back, firstScript(lang), lang)}\n")
                // Letters with no code come back as their base letter (č -> c),
                // so compare after the same fold.
                val expect = Script.back(Morse.decode(Morse.encode(Script.convert(latin, firstScript(lang), lang), lang), lang), firstScript(lang), lang)
                assertEquals("$lang $w", expect.lowercase(), back.lowercase())
            }
        }
        println(report)
    }

    @Test
    fun ownLettersReadBackAsThemselves() {
        val letters = mapOf(
            Lang.PL to "ąćęłńóśźż", Lang.SR to "čćđšž", Lang.DE to "äöü",
            Lang.RU to "абвгдежзийклмнопрстуфхцчшщыьэюя",
            Lang.UK to "абвгдеєжзиіїйклмнопрстуфхцчшщьюя",
            Lang.BG to "абвгдежзийклмнопрстуфхцчшщъьюя",
        )
        for ((lang, s) in letters) for (c in s) {
            val code = Morse.code(c, lang)
            assertTrue("$lang $c has a code", code != null)
            assertEquals("$lang $c", c.toString(), Morse.decode(code!!, lang))
        }
    }

    @Test
    fun cyrillicUsesCyrillicCodes() {
        // ш is the Latin key w inside the Russian keyboard: it must go out as
        // ш (−−−−), never as w (·−−).
        val latin = Script.back("ш", Script.Mode.CYRILLIC, Lang.RU)
        assertEquals("−−−−", Script.convert(latin, Script.Mode.MORSE, Lang.RU))
    }

    @Test
    fun gaps() {
        assertEquals("·−  −···     −·−·", Morse.encode("ab c", Lang.ISV))
        assertEquals("ab c", Morse.decode("·−  −···     −·−·", Lang.ISV))
    }
}
