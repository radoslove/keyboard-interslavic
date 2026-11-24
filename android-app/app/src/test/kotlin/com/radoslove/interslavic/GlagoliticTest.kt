package com.radoslove.interslavic

import org.junit.Assert.assertEquals
import org.junit.Test

/** Glagolitic layout 261124 (iOS 0593919, 151c9e6): the shared pl/isv glyphs,
 *  dzelo, ja = Ⱑ read back by context, the titlo and the numbers. */
class GlagoliticTest {
    private val G = Script.Mode.GLAGOLITIC

    private fun isv(s: String) = Script.convert(s, G, Lang.ISV)
    private fun isvBack(s: String) = Script.back(s, G, Lang.ISV)
    private fun pl(s: String) = Script.convert(s, G, Lang.PL)

    @Test fun isvLetters() {
        assertEquals("ⱑⰱⰾⱁⰽⱁ", isv("jabloko"))          // ja = Ⱑ (was ⱝ)
        assertEquals("ⱚⰻⰾⰿ".replace('ⰿ', 'ⱞ'), isv("film"))  // f = ⱚ, m = ⱞ
        assertEquals("ⰱⱏⰹⱅⰻ", isv("byti"))              // y = ⱏⰹ
        assertEquals("ⱂⱁⰷⰵⰿⱀⱏⰹ".replace('ⰿ', 'ⱞ'), isv("podzemny"))   // every d + z = ⰷ
        assertEquals("ⱂⱁⰴⰸⰵⰿⱀⱏⰹ".replace('ⰿ', 'ⱞ'), isv("poḍzemny"))  // ḍ keeps them apart
        assertEquals("ⰾⱐ", isv("lj"))
        assertEquals("ⱝ", isv("ȧ"))
        assertEquals("ⱐⱏ", isv("ʹʺ"))
    }

    @Test fun isvBackByContext() {
        assertEquals("jabloko", isvBack(isv("jabloko")))   // Ⱑ at the start = ja
        assertEquals("moja", isvBack(isv("moja")))         // after a vowel = ja
        assertEquals("město", isvBack(isv("město")))       // after a consonant = ě
        assertEquals("podzemny", isvBack(isv("podzemny")))
        assertEquals("film", isvBack("ⱇⰻⰾⱞ"))              // old ⱇ still reads back
        assertEquals("byti", isvBack("ⰱⱐⰹⱅⰻ"))             // old ⱐⰹ still reads back
    }

    @Test fun polish() {
        assertEquals("ⱈⰾⰵⰱ", pl("chleb"))                  // ch joins
        assertEquals("ⱒⰰⰾⰰ", pl("hala"))                   // h stays ⱒ
        assertEquals("ⱞⰰⰿⰰ".replace('ⰿ', 'ⱞ'), pl("mama"))   // m = ⱞ
        assertEquals("ⱖ", pl("ǒ"))
        assertEquals("ⰷ", pl("ẑ"))
        assertEquals("mama", Script.back("ⰿⰰⰿⰰ", G, Lang.PL))   // old ⰿ reads back
    }

    @Test fun titlo() {
        assertEquals("ⰰ҃", Titlo.over("ⰰ"))
        assertEquals("ⰰ︮ⰱ︦ⰲ︯", Titlo.over("ⰰⰱⰲ"))
        assertEquals("ⰰ︮ⰱ︯ ⰲ҃", Titlo.over("ⰰⰱ ⰲ"))
        assertEquals("ⰰⰱ", Titlo.strip(Titlo.over("ⰰⰱ")))
        // a letter with an acute is one letter: ⱁ́ + ⱄ
        assertEquals("ⱁ́︮ⱄ︯", Titlo.over("ⱁ́ⱄ"))
        assertEquals("ⰾ︮ⱐ︯", Titlo.apply("ⰾⱐ", Titlo.PLAIN))
        assertEquals("ⰾ︮ⱐ︮", Titlo.apply("ⰾⱐ", Titlo.OPEN))
        assertEquals("ⰰ҃", Titlo.apply("ⰰ", Titlo.PLAIN))
        assertEquals("ⰱ︮ⰰⰲ", Titlo.openRun("ⰰ ⰱ︮ⰰⰲ"))
        assertEquals(null, Titlo.openRun("ⰱ҃ ⰰ"))
        assertEquals("ja" to "҃", Titlo.split("ja҃"))
    }

    @Test fun numerals() {
        assertEquals("ⰓⰋⰂ", Titlo.numeral(123))
        assertEquals("ⰗⰑⰂ", Titlo.numeral(583))
        assertEquals("ⰚⰎⰄ", Titlo.numeral(855))
        assertEquals("Ⱋ", Titlo.numeral(900))
        assertEquals("ⰀⰉ", Titlo.numeral(11))
        assertEquals(null, Titlo.numeral(0))
        assertEquals(null, Titlo.numeral(1000))
    }

    @Test fun layout() {
        assertEquals("ja", Layout.keyText('q', G, Lang.ISV))
        assertEquals("v", Layout.keyText('w', G, Lang.ISV))
        assertEquals(Script.SOFT_YER_MARK, Layout.keyText('v', G, Lang.ISV))
        assertEquals("ẑ", Layout.keyText('x', G, Lang.ISV))
        assertEquals("ẑ", Layout.keyText('x', G, Lang.PL))
        assertEquals("lj", Layout.keyText('q', Script.Mode.CYRILLIC, Lang.ISV))   // Cyrillic unchanged
        assertEquals(listOf("qwertyuiop", "asdfghjkl", "zxcvbnm"), Layout.letterRows(Lang.ISV, G))
        assertEquals(listOf("ẑ", "dž", "ḍ", "d҃", "d︮", "d︯"),
            Layout.variants('d', false, Lang.ISV, latin = false, titlo = true))
        assertEquals(listOf("nj", "n҃", "n︮", "n︯"),
            Layout.variants('n', false, Lang.ISV, latin = false, titlo = true))
        assertEquals(listOf("jo", "ju", "ȧ", "ja҃", "ja︮", "ja︯"),
            Layout.variants('q', false, Lang.ISV, latin = false, titlo = true))
        assertEquals(listOf(Script.HARD_YER_MARK),
            Layout.variants('v', false, Lang.ISV, latin = false, titlo = true))
        assertEquals(listOf("ǒ", "ju", "ję", "ją", "ja҃", "ja︮", "ja︯"),
            Layout.variants('q', false, Lang.PL, latin = false, titlo = true))
    }
}
