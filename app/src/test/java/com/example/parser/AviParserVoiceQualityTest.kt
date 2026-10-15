package com.example.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regresiones de dictado operativo, fusión y correcciones puntuales de placas.
 * Pruebas puras: no requieren micrófono, red ni dispositivo Android.
 */
class AviParserVoiceQualityTest {
    private val vias = setOf(101, 102)

    @Test
    fun `a targeted last digit correction preserves action and lane`() {
        val initial = AviParser.parse("fuga vía 101 placa I1L111", vias)
        assertTrue(initial.valido)

        val corrected = AviParser.mergeCorrection(
            initial,
            "corrige último dígito negativo",
            vias
        )
        assertTrue(corrected.errores.joinToString(), corrected.valido)
        assertEquals("I1L110", corrected.placa)
        assertEquals(101, corrected.via)
        assertEquals("FUGA", corrected.accion)
    }

    @Test
    fun `a targeted prefix correction accepts a spoken NATO letter`() {
        val initial = AviParser.parse("liberado vía 102 placa I1L110", vias)
        assertTrue(initial.valido)

        val corrected = AviParser.mergeCorrection(
            initial,
            "cambia segundo carácter alfa",
            vias
        )
        assertTrue(corrected.errores.joinToString(), corrected.valido)
        assertEquals("IAL110", corrected.placa)
        assertEquals(102, corrected.via)
        assertEquals("LIBERADO", corrected.accion)
    }

    @Test
    fun `invalid character correction does not silently accept the old plate`() {
        val initial = AviParser.parse("derivado vía 101 placa I1L110", vias)
        val corrected = AviParser.mergeCorrection(
            initial,
            "corrige último dígito lima",
            vias
        )
        assertFalse(corrected.valido)
        assertEquals("I1L110", corrected.placa)
        assertTrue(corrected.errores.any { it.contains("carácter") })
    }

    @Test
    fun `short plate hypotheses do not contaminate votes for a full plate`() {
        val result = AviParser.fuseHypotheses(
            candidates = listOf(
                "fuga vía 101 placa I1L11",
                "fuga vía 101 placa I1L110"
            ),
            allowedVias = vias
        )
        assertTrue(result.parsed.errores.joinToString(), result.parsed.valido)
        assertEquals("I1L110", result.parsed.placa)
    }

    @Test
    fun `conflicting final digits show reduced consensus instead of false certainty`() {
        val result = AviParser.fuseHypotheses(
            candidates = listOf(
                "fuga vía 101 placa I1L110",
                "fuga vía 101 placa I1L111"
            ),
            allowedVias = vias
        )
        assertTrue(AviParser.isValidPeruPlate(result.parsed.placa))
        assertEquals(6, result.platePositionConfidence.size)
        assertTrue(result.platePositionConfidence[5] < 0.60f)
        assertTrue(result.platePositionConfidence[0] >= 0.99f)
    }

    @Test
    fun `full plate correction still replaces all six characters`() {
        val initial = AviParser.parse("fuga vía 101 placa I1L111", vias)
        val corrected = AviParser.mergeCorrection(
            initial, "placa alfa primero lima primero primero negativo", vias
        )
        assertTrue(corrected.errores.joinToString(), corrected.valido)
        assertEquals("A1L110", corrected.placa)
    }

    @Test
    fun `number before placa is classified as via even without action`() {
        val result = AviParser.parse("105 placa ABC123", setOf(105))
        assertEquals(105, result.via)
        assertEquals("ABC123", result.placa)
        assertFalse("Missing action must still require review", result.valido)
        assertTrue(result.errores.any { it.contains("acción") })
        assertFalse(result.errores.any { it.contains("VÍA") })
    }

    @Test
    fun `inferred via and explicit via produce same valid fields`() {
        val allowed = setOf(105)
        val inferred = AviParser.parse("fuga 105 placa ABC123", allowed)
        val explicit = AviParser.parse("fuga vía 105 placa ABC123", allowed)

        assertTrue(inferred.errores.joinToString(), inferred.valido)
        assertTrue(explicit.errores.joinToString(), explicit.valido)
        assertEquals(explicit.via, inferred.via)
        assertEquals(explicit.placa, inferred.placa)
        assertEquals(explicit.accion, inferred.accion)
        assertEquals("fuga via 105 placa abc123",
            AviParser.normalize("fuga vía 105 placa ABC123"))
    }

    @Test
    fun `only immediately preceding number becomes via`() {
        val parsed = AviParser.parse(
            "fuga 101 105 placa ABC123",
            allowedVias = setOf(101, 105)
        )
        assertTrue(parsed.errores.joinToString(), parsed.valido)
        assertEquals(105, parsed.via)
        assertEquals("ABC123", parsed.placa)
    }

    @Test
    fun `missing placa or intervening words do not infer via`() {
        val withoutPlaca = AviParser.parse("fuga 105 ABC123", setOf(105))
        val interveningWord = AviParser.parse(
            "fuga 105 vehiculo placa ABC123", setOf(105)
        )
        assertEquals(null, withoutPlaca.via)
        assertEquals(null, interveningWord.via)
        assertFalse(withoutPlaca.valido)
        assertFalse(interveningWord.valido)
    }

    @Test
    fun `inferred via is still checked against plaza permissions`() {
        val parsed = AviParser.parse(
            "derivado 105 placa ABC123", setOf(101)
        )
        assertEquals(105, parsed.via)
        assertFalse(parsed.valido)
        assertTrue(parsed.errores.any { it.contains("no está habilitada") })
    }

    @Test
    fun `explicit Q code via remains intact`() {
        val parsed = AviParser.parse(
            "liberado vía primero negativo quinto placa ABC123", setOf(105)
        )
        assertTrue(parsed.errores.joinToString(), parsed.valido)
        assertEquals(105, parsed.via)
        assertEquals("ABC123", parsed.placa)
    }

    @Test
    fun `hypothesis fusion keeps inferred via and explicit via compatible`() {
        val result = AviParser.fuseHypotheses(
            listOf(
                "fuga 105 placa ABC123",
                "fuga vía 105 placa ABC123"
            ),
            allowedVias = setOf(105)
        )
        assertEquals(105, result.parsed.via)
        assertEquals("ABC123", result.parsed.placa)
        assertTrue(result.parsed.errores.joinToString(), result.parsed.valido)
    }

    @Test
    fun `a partial correction can infer missing via before placa`() {
        val previous = AviParser.parse("fuga vía 101 placa ABC123", setOf(101, 105))
        val corrected = AviParser.mergeCorrection(
            previous,
            "105 placa ABC123",
            setOf(101, 105)
        )
        assertTrue(corrected.errores.joinToString(), corrected.valido)
        assertEquals(105, corrected.via)
        assertEquals("ABC123", corrected.placa)
        assertEquals("FUGA", corrected.accion)
    }
}
