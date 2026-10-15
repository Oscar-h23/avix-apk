package com.example.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Sufijos de vía de la serie 100: solo se expanden contra
 * las vías habilitadas de la plaza, nunca por adivinación.
 */
class AviParserShortViaTest {
    private val vias = setOf(101, 102, 151, 152)

    @Test
    fun `numeric short forms resolve to actual lanes`() {
        val via51 = AviParser.parse("fuga vía 51 placa ABC123", vias)
        val via52 = AviParser.parse("derivado vía 52 placa ABC123", vias)

        assertTrue(via51.errores.joinToString(), via51.valido)
        assertTrue(via52.errores.joinToString(), via52.valido)
        assertEquals(151, via51.via)
        assertEquals(152, via52.via)
    }

    @Test
    fun `spoken cardinals and Q code also resolve short lanes`() {
        val cardinal = AviParser.parse("fuga vía cincuenta y uno placa ABC123", vias)
        val qCode = AviParser.parse("liberado vía quinto segundo placa ABC123", vias)

        assertTrue(cardinal.errores.joinToString(), cardinal.valido)
        assertTrue(qCode.errores.joinToString(), qCode.valido)
        assertEquals(151, cardinal.via)
        assertEquals(52 + 100, qCode.via)
    }

    @Test
    fun `a two digit number directly before placa is expanded`() {
        val result = AviParser.parse("fuga 51 placa ABC123", vias)
        assertTrue(result.errores.joinToString(), result.valido)
        assertEquals(151, result.via)
        assertEquals("ABC123", result.placa)
    }

    @Test
    fun `full lane numbers remain unchanged`() {
        val parsed = AviParser.parse("fuga vía 151 placa ABC123", vias)
        assertTrue(parsed.errores.joinToString(), parsed.valido)
        assertEquals(151, parsed.via)
    }

    @Test
    fun `a real short lane always wins over expansion`() {
        val parsed = AviParser.parse("fuga vía 51 placa ABC123", setOf(51, 151))
        assertTrue(parsed.errores.joinToString(), parsed.valido)
        assertEquals(51, parsed.via)
    }

    @Test
    fun `unavailable expansion stays invalid and is not reassigned`() {
        val parsed = AviParser.parse("derivado vía 52 placa ABC123", setOf(151))
        assertFalse(parsed.valido)
        assertEquals(52, parsed.via)
        assertTrue(parsed.errores.any { it.contains("no está habilitada") })
    }

    @Test
    fun `without a lane catalog do not guess one hundred series`() {
        val parsed = AviParser.parse("fuga vía 51 placa ABC123")
        assertEquals(51, parsed.via)
    }

    @Test
    fun `single digit does not automatically expand`() {
        val parsed = AviParser.parse("fuga vía 1 placa ABC123", vias)
        assertFalse(parsed.valido)
        assertEquals(1, parsed.via)
    }

    @Test
    fun `leading zero and negative first Q code resolve 101 and 102`() {
        val numeric = AviParser.parse("fuga vía 01 placa ABC123", vias)
        val qCode = AviParser.parse("fuga vía negativo segundo placa ABC123", vias)

        assertTrue(numeric.errores.joinToString(), numeric.valido)
        assertTrue(qCode.errores.joinToString(), qCode.valido)
        assertEquals(101, numeric.via)
        assertEquals(102, qCode.via)
    }

    @Test
    fun `hypothesis fusion uses expanded lane`() {
        val result = AviParser.fuseHypotheses(
            candidates = listOf(
                "fuga vía 51 placa ABC123",
                "fuga vía 151 placa ABC123"
            ),
            allowedVias = vias
        )
        assertTrue(result.parsed.errores.joinToString(), result.parsed.valido)
        assertEquals(151, result.parsed.via)
    }

    @Test
    fun `spoken lane correction also uses enabled short suffix`() {
        val initial = AviParser.parse("fuga vía 151 placa ABC123", vias)
        val corrected = AviParser.mergeCorrection(initial, "vía 52", vias)

        assertTrue(corrected.errores.joinToString(), corrected.valido)
        assertEquals(152, corrected.via)
    }

    @Test
    fun `bubble normalizes a recognized numeric lane after plaza catalog arrives`() {
        val vias = setOf(101, 105, 151, 152)
        // Antes de cargar la plaza el dictado ya contiene un número.
        assertEquals(51, AviParser.normalizeViaNumber("51", emptySet()))

        // Al cargar AVIX se convierte sin modificar acción ni placa.
        assertEquals(151, AviParser.normalizeViaNumber("51", vias))
        assertEquals(105, AviParser.normalizeViaNumber("105", vias))
        assertEquals(101, AviParser.normalizeViaNumber("101", vias))
        assertEquals(102, AviParser.normalizeViaNumber("02", setOf(101, 102)))
    }

    @Test
    fun `bubble cannot normalize an invalid or unauthorized lane`() {
        val vias = setOf(101, 105, 151)
        assertEquals(null, AviParser.normalizeViaNumber("", vias))
        assertEquals(null, AviParser.normalizeViaNumber("vía 51", vias))
        assertEquals(null, AviParser.normalizeViaNumber("ABC123", vias))
        assertEquals(52, AviParser.normalizeViaNumber("52", vias))
        assertFalse(AviParser.normalizeViaNumber("52", vias) in vias)
    }

    @Test
    fun `speech biasing contains short and full lane phrases`() {
        val phrases = AviParser.recognitionBiasingPhrases(vias)
        assertTrue("vía 151" in phrases)
        assertTrue("vía 51" in phrases)
        assertTrue("vía quinto primero" in phrases)
        assertTrue("vía 01" in phrases)
    }
}
