package app.lazydex.util

import org.junit.Assert.assertEquals
import org.junit.Test

class TitleNormalizerTest {

    @Test
    fun normalize_handlesBlankOrNullTitles() {
        assertEquals("", TitleNormalizer.normalize(null))
        assertEquals("", TitleNormalizer.normalize("   "))
    }

    @Test
    fun normalize_lowercasesAndStripsBrackets() {
        assertEquals("attack on titan", TitleNormalizer.normalize("Attack on Titan (TV)"))
        assertEquals("frieren beyond journeys end", TitleNormalizer.normalize("[BD] Frieren: Beyond Journey's End!"))
    }

    @Test
    fun normalize_collapsesWhitespaceAndRemovesSpecialSymbols() {
        assertEquals("solo leveling", TitleNormalizer.normalize("  Solo   Leveling!!!  "))
    }
}
