package com.example.parser

import com.example.model.ParsedCommand
import java.text.Normalizer
import java.util.Locale

enum class AviCommandStage {
    ACCION,
    VIA,
    PLACA,
    COMPLETO
}

data class AviCommandAnalysis(
    val stage: AviCommandStage,
    val parsed: ParsedCommand,
    val actionDetected: Boolean,
    val viaMarkerDetected: Boolean,
    val plateMarkerDetected: Boolean,
    val platePositions: List<Boolean>,
    val prompt: String
)

data class HypothesisFusionResult(
    val text: String,
    val parsed: ParsedCommand,
    val fused: Boolean,
    val sourceCount: Int,
    val actionConfidence: Float,
    val viaConfidence: Float,
    val platePositionConfidence: List<Float>
)

object AviParser {

    private val phoneticAlphabet = mapOf(
        "alfa" to "A", "alpha" to "A",
        "bravo" to "B", "brabo" to "B",
        "charlie" to "C", "charli" to "C", "charly" to "C", "chali" to "C",
        "delta" to "D", "dhelta" to "D",
        "echo" to "E", "eco" to "E", "hecho" to "E",
        "foxtrot" to "F", "fox trot" to "F", "fox" to "F",
        "golf" to "G", "gol" to "G",
        "hotel" to "H",
        "india" to "I",
        "juliet" to "J", "julieta" to "J", "yuliet" to "J",
        "kilo" to "K", "quilo" to "K",
        "lima" to "L",
        "mike" to "M", "maik" to "M", "mic" to "M",
        "november" to "N", "noviembre" to "N",
        "oscar" to "O",
        "papa" to "P",
        "quebec" to "Q", "quebek" to "Q", "quebeck" to "Q",
        "kebe" to "Q", "kevel" to "Q",
        "romeo" to "R",
        "sierra" to "S",
        "tango" to "T",
        "uniform" to "U", "uniforme" to "U",
        "victor" to "V",
        "whiskey" to "W", "whisky" to "W", "wisky" to "W", "wiski" to "W",
        "xray" to "X", "x ray" to "X", "x rey" to "X",
        "exray" to "X", "ex rey" to "X",
        "equisray" to "X", "equis ray" to "X", "equis rey" to "X",
        "ray" to "X", "rey" to "X", "equis" to "X", "extra" to "X",
        "yankee" to "Y", "yanki" to "Y", "yanqui" to "Y",
        // Variantes que el ASR puede producir al escuchar "Zulu".
        // Se interpretan como Z únicamente dentro del bloque de placa.
        "zulu" to "Z", "sulu" to "Z", "tsuru" to "Z",
        "suru" to "Z", "zuru" to "Z",
        "a" to "A",
        "be" to "B", "ve grande" to "B", "be alta" to "B",
        "ce" to "C",
        "de" to "D",
        "e" to "E",
        "efe" to "F",
        "ge" to "G",
        "hache" to "H",
        "i" to "I", "i latina" to "I",
        "jota" to "J",
        "ka" to "K",
        "ele" to "L",
        "eme" to "M",
        "ene" to "N",
        "o" to "O",
        "pe" to "P",
        "cu" to "Q",
        "erre" to "R", "ere" to "R",
        "ese" to "S",
        "te" to "T",
        "u" to "U",
        "ve" to "V", "uve" to "V", "ve corta" to "V", "ve chica" to "V",
        "doble ve" to "W", "uve doble" to "W",
        "i griega" to "Y", "ye" to "Y",
        "zeta" to "Z", "ceta" to "Z"
    )

    // Código numérico operativo:
    // 0 NEGATIVO, 1 PRIMERO, 2 SEGUNDO, ... 9 NOVENO.
    private val singleDigits = mapOf(
        "negativo" to 0, "negativa" to 0,
        "primero" to 1, "primer" to 1, "primera" to 1,
        "segundo" to 2, "segunda" to 2,
        "tercero" to 3, "tercer" to 3, "tercera" to 3,
        "cuarto" to 4, "cuarta" to 4,
        "quinto" to 5, "quinta" to 5,
        "sexto" to 6, "sexta" to 6,
        "septimo" to 7, "septima" to 7,
        "octavo" to 8, "octava" to 8,
        "noveno" to 9, "novena" to 9,
        "cero" to 0,
        "uno" to 1, "un" to 1, "una" to 1,
        "dos" to 2,
        "tres" to 3,
        "cuatro" to 4,
        "cinco" to 5,
        "seis" to 6,
        "siete" to 7,
        "ocho" to 8,
        "nueve" to 9
    )

    private val spanishNumbers = mapOf(
        "cero" to 0, "uno" to 1, "un" to 1, "una" to 1, "dos" to 2, "tres" to 3,
        "cuatro" to 4, "cinco" to 5, "seis" to 6, "siete" to 7, "ocho" to 8, "nueve" to 9,
        "diez" to 10, "once" to 11, "doce" to 12, "trece" to 13, "catorce" to 14,
        "quince" to 15, "dieciseis" to 16, "diecisiete" to 17, "dieciocho" to 18, "diecinueve" to 19,
        "veinte" to 20, "veintiuno" to 21, "veintidos" to 22, "veintitres" to 23,
        "veinticuatro" to 24, "veinticinco" to 25, "veintiseis" to 26, "veintisiete" to 27,
        "veintiocho" to 28, "veintinueve" to 29,
        "treinta" to 30, "cuarenta" to 40, "cincuenta" to 50, "sesenta" to 60,
        "setenta" to 70, "ochenta" to 80, "noventa" to 90,
        "cien" to 100, "ciento" to 100, "doscientos" to 200, "trescientos" to 300,
        "cuatrocientos" to 400, "quinientos" to 500, "seiscientos" to 600,
        "setecientos" to 700, "ochocientos" to 800, "novecientos" to 900,
        "mil" to 1000
    )

    private val aliasFuga = listOf(
        "fuga", "fugado", "se dio a la fuga", "se fugo", "evasion"
    )
    private val aliasDerivado = listOf(
        "derivado", "derivar", "desvio", "desviado", "derivacion", "derivada"
    )
    private val aliasLiberado = listOf(
        "liberado", "liberar", "liberada", "libero", "liberacion"
    )

    private val viaKeywords = listOf("via ", "carril ", "pista ", "numero ")

    // Confusiones observables del ASR. No se reemplazan globalmente:
    // solo cuando la estructura del comando indica que AVIX está esperando ese marcador.
    private val viaContextAliases = setOf(
        "via", "habia", "avia", "bia", "dia", "guia", "ia"
    )

    private val viaContextPairAliases = setOf(
        "vi a",
        "y a"
    )

    private val placaContextAliases = setOf(
        "placa", "placas", "plata", "plaga", "flaca"
    )

    private val fugaContextAliases = setOf(
        "fuga", "juga"
    )

    private val canonicalPhoneticWords = listOf(
        "alfa", "bravo", "charlie", "delta", "echo", "foxtrot", "golf", "hotel",
        "india", "juliet", "kilo", "lima", "mike", "november", "oscar", "papa",
        "quebec", "romeo", "sierra", "tango", "uniform", "victor", "whiskey",
        "x ray", "yankee", "zulu"
    )

    private val canonicalQDigits = listOf(
        "negativo", "primero", "segundo", "tercero", "cuarto",
        "quinto", "sexto", "septimo", "octavo", "noveno"
    )

    private val canonicalPhoneticMap = mapOf(
        "alfa" to "A", "bravo" to "B", "charlie" to "C", "delta" to "D",
        "echo" to "E", "foxtrot" to "F", "golf" to "G", "hotel" to "H",
        "india" to "I", "juliet" to "J", "kilo" to "K", "lima" to "L",
        "mike" to "M", "november" to "N", "oscar" to "O", "papa" to "P",
        "quebec" to "Q", "romeo" to "R", "sierra" to "S", "tango" to "T",
        "uniform" to "U", "victor" to "V", "whiskey" to "W",
        "xray" to "X",
        "yankee" to "Y", "zulu" to "Z"
    )

    fun numberToQPhrase(value: Int): String {
        if (value < 0) return ""
        return value.toString()
            .mapNotNull { ch ->
                ch.digitToIntOrNull()?.let { digit -> canonicalQDigits[digit] }
            }
            .joinToString(" ")
    }

    /**
     * Vocabulario contextual entregado al SpeechRecognizer cuando la versión
     * de Android soporta biasing strings. Incluye dominio AVIX, código Q,
     * alfabeto fonético y las vías reales de la plaza.
     */
    fun recognitionBiasingPhrases(
        allowedVias: Set<Int> = emptySet()
    ): List<String> {
        val phrases = mutableListOf(
            "fuga",
            "derivado",
            "liberado",
            "fuga vía",
            "derivado vía",
            "liberado vía",
            "vía",
            "placa",
            "vía placa",
            "acción vía placa"
        )

        // Priorizar frases completas de vía para que el reconocedor use
        // el catálogo de la plaza como contexto, no solo como validación posterior.
        for (via in allowedVias.filter { it > 0 }.sorted()) {
            phrases += "vía $via"

            val qPhrase = numberToQPhrase(via)
            if (qPhrase.isNotBlank()) {
                phrases += "vía $qPhrase"
            }

            // También orientar al reconocedor con los dos últimos dígitos
            // de las vías 101..199 habilitadas en la plaza.
            if (via in 101..199) {
                val shortForm = (via - 100).toString().padStart(2, '0')
                phrases += "vía $shortForm"
                val shortQ = shortForm.map { digit ->
                    canonicalQDigits[digit.digitToInt()]
                }.joinToString(" ")
                phrases += "vía $shortQ"
            }
        }

        phrases += canonicalPhoneticWords
        phrases += canonicalPhoneticWords.map { "placa $it" }

        phrases += canonicalQDigits
        phrases += canonicalQDigits.map { "placa $it" }

        return phrases.distinct().take(100)
    }

    private fun basicNormalize(text: String): String {
        val withoutAccents = Normalizer.normalize(text, Normalizer.Form.NFD)
            .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")

        return withoutAccents
            .lowercase(Locale.ROOT)
            .replace(",", " ")
            .replace(".", " ")
            .replace(";", " ")
            .replace(":", " ")
            .replace("-", " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun isNumberContextToken(token: String): Boolean {
        return token.all { it.isDigit() } ||
            singleDigits.containsKey(token) ||
            spanishNumbers.containsKey(token)
    }

    private fun isPlateContextToken(
        tokens: List<String>,
        index: Int
    ): Boolean {
        val token = tokens.getOrNull(index) ?: return false

        if (singleDigits.containsKey(token)) return true
        if (phoneticAlphabet.containsKey(token)) return true
        if (token.length == 1 && token[0].isLetterOrDigit()) return true
        if (token.any { it.isDigit() } && token.all { it.isLetterOrDigit() }) return true

        if (index + 1 < tokens.size) {
            val pair = "$token ${tokens[index + 1]}"
            if (phoneticAlphabet.containsKey(pair)) return true
        }

        return false
    }

    private fun editDistance(
        a: String,
        b: String
    ): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length

        val previous = IntArray(b.length + 1) { it }
        val current = IntArray(b.length + 1)

        for (i in 1..a.length) {
            current[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                current[j] = minOf(
                    current[j - 1] + 1,
                    previous[j] + 1,
                    previous[j - 1] + cost
                )
            }
            for (j in previous.indices) {
                previous[j] = current[j]
            }
        }

        return previous[b.length]
    }

    private fun inferPhoneticLetter(token: String): String? {
        if (token.length !in 3..10) return null

        val ranked = canonicalPhoneticMap
            .map { (word, letter) ->
                Triple(word, letter, editDistance(token, word))
            }
            .groupBy { it.second }
            .mapNotNull { (_, candidates) ->
                candidates.minByOrNull { it.third }
            }
            .sortedBy { it.third }

        val best = ranked.firstOrNull() ?: return null
        val second = ranked.getOrNull(1)

        // Tolerancia según longitud. Además exigimos que la mejor coincidencia
        // sea claramente mejor que la segunda para evitar adivinanzas.
        val maxDistance = when {
            token.length <= 4 -> 1
            token.length <= 7 -> 2
            else -> 3
        }

        val isUniqueEnough =
            second == null || best.third + 1 < second.third

        return if (best.third <= maxDistance && isUniqueEnough) {
            best.second
        } else {
            null
        }
    }

    private fun resemblesVia(token: String): Boolean {
        if (token in viaContextAliases) return true

        // Fuzzy solo para palabras cortas y cuando la estructura externa ya
        // confirmó que lo siguiente parece una vía. Evita correcciones globales.
        return token.length in 2..5 &&
            editDistance(token, "via") <= 2
    }

    private fun viaAliasLength(
        tokens: List<String>,
        index: Int
    ): Int {
        val token = tokens.getOrNull(index) ?: return 0

        // Primero evaluar alias exactos de dos palabras. "vi" se parece a
        // "via" por distancia de edición, pero en "vi a" debe consumirse el
        // par completo antes de aplicar la coincidencia difusa individual.
        val pair = tokens.getOrNull(index + 1)
            ?.let { next -> "$token $next" }

        if (pair in viaContextPairAliases) return 2

        return if (resemblesVia(token)) 1 else 0
    }

    private fun resemblesPlaca(token: String): Boolean {
        if (token in placaContextAliases) return true
        return token.length in 4..7 &&
            editDistance(token, "placa") <= 1
    }

    /**
     * Repara errores del SpeechRecognizer usando la gramática de AVIX:
     * ACCIÓN -> VÍA -> PLACA.
     *
     * Ejemplo:
     * "fuga habia 101 plata alfa bravo..." ->
     * "fuga via 101 placa alfa bravo..."
     */
    fun normalizeOperationalContext(text: String): String {
        val basic = basicNormalize(text)
        if (basic.isBlank()) return basic

        val tokens = basic.split(" ").toMutableList()

        // 1) Acción: corrección muy restringida. "juga" solo se interpreta como
        // FUGA si inmediatamente después aparece un candidato a VÍA seguido de número.
        if (tokens.isNotEmpty() && tokens[0] in fugaContextAliases) {
            val possibleViaIndex = 1
            val aliasLength = viaAliasLength(
                tokens,
                possibleViaIndex
            )
            val possibleNumberIndex =
                possibleViaIndex + aliasLength

            if (
                tokens[0] != "fuga" &&
                aliasLength > 0 &&
                tokens.getOrNull(possibleNumberIndex)
                    ?.let { isNumberContextToken(it) } == true
            ) {
                tokens[0] = "fuga"
            }
        }

        // Ubicar una acción ya reconocida. Solo después de ella buscamos VÍA.
        val joinedBeforeVia = tokens.joinToString(" ")
        val actionIndex = when {
            aliasFuga.any { joinedBeforeVia.contains(it) } ||
                aliasDerivado.any { joinedBeforeVia.contains(it) } -> {
                tokens.indexOfFirst { token ->
                    token in aliasFuga || token in aliasDerivado || token == "fuga"
                }.coerceAtLeast(0)
            }
            else -> -1
        }

        if (actionIndex >= 0) {
            val plateBoundary = tokens.indexOfFirst { it in placaContextAliases }
                .let { if (it >= 0) it else tokens.size }

            for (i in (actionIndex + 1) until plateBoundary) {
                val aliasLength = viaAliasLength(tokens, i)
                if (aliasLength == 0) continue

                val numberToken = tokens.getOrNull(
                    i + aliasLength
                )

                if (
                    numberToken != null &&
                    isNumberContextToken(numberToken)
                ) {
                    tokens[i] = "via"

                    if (aliasLength == 2) {
                        tokens.removeAt(i + 1)
                    }

                    break
                }
            }
        }

        val viaIndex = tokens.indexOfFirst { it == "via" || it == "carril" || it == "pista" }
        if (viaIndex >= 0) {
            for (i in (viaIndex + 1) until tokens.size) {
                val token = tokens[i]

                if (
                    resemblesPlaca(token) &&
                    isPlateContextToken(tokens, i + 1)
                ) {
                    tokens[i] = "placa"
                    break
                }
            }
        }

        // 4) Inferencia estricta de VÍA: si Android omite su etiqueta
        // pero reconoce un número inmediatamente antes de "placa",
        // insertar la etiqueta sin tocar ni la acción ni el bloque de placa.
        // Ej.: "fuga 101 105 placa ABC123" -> "fuga 101 via 105 placa ABC123".
        // No se infiere a partir de números sin "placa", ni si ya hay vía
        // (o un marcador equivalente) antes de la placa.
        val plateIndex = tokens.indexOf("placa")
        if (plateIndex > 0) {
            val hasExplicitVia = tokens.take(plateIndex).any {
                it == "via" || it == "carril" ||
                    it == "pista" || it == "numero"
            }
            val numberImmediatelyBeforePlate = tokens[plateIndex - 1]
            if (
                !hasExplicitVia &&
                numberImmediatelyBeforePlate.all { it.isDigit() } &&
                numberImmediatelyBeforePlate.toIntOrNull()?.let { it > 0 } == true
            ) {
                tokens.add(plateIndex - 1, "via")
            }
        }

        return tokens.joinToString(" ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    fun normalize(text: String): String {
        return normalizeOperationalContext(text)
    }

    private fun actionDetected(clean: String): Boolean {
        return aliasFuga.any { clean.contains(it) } ||
            aliasDerivado.any { clean.contains(it) } ||
            aliasLiberado.any { clean.contains(it) }
    }

    private fun viaMarkerPosition(clean: String): Pair<Int, Int> {
        var start = -1
        var len = 0
        for (kw in viaKeywords) {
            val idx = clean.indexOf(kw)
            if (idx >= 0 && (start == -1 || idx < start)) {
                start = idx
                len = kw.length
            }
        }
        return start to len
    }

    private fun plateMarkerPosition(clean: String): Pair<Int, Int> {
        val marker = "placa "
        val idx = clean.indexOf(marker)
        return idx to if (idx >= 0) marker.length else 0
    }

    fun platePositionValidity(placa: String): List<Boolean> {
        val normalized = placa
            .uppercase(Locale.ROOT)
            .replace("-", "")
            .replace(" ", "")

        return (0..5).map { index ->
            val ch = normalized.getOrNull(index) ?: return@map false
            when (index) {
                0 -> ch.isLetter()
                1, 2 -> ch.isLetterOrDigit()
                else -> ch.isDigit()
            }
        }
    }

    fun isValidPeruPlate(placa: String): Boolean {
        val normalized = placa
            .uppercase(Locale.ROOT)
            .replace("-", "")
            .replace(" ", "")

        return normalized.length == 6 &&
            normalized.matches(Regex("[A-Z][A-Z0-9]{2}\\d{3}"))
    }

    fun analyzeCommand(
        rawText: String,
        allowedVias: Set<Int> = emptySet()
    ): AviCommandAnalysis {
        val clean = normalize(rawText)
        val parsed = parse(rawText, allowedVias)
        val hasAction = actionDetected(clean)
        val hasViaMarker = viaMarkerPosition(clean).first >= 0
        val hasPlateMarker = plateMarkerPosition(clean).first >= 0
        val viaIsAllowed = parsed.via != null &&
            parsed.via > 0 &&
            (allowedVias.isEmpty() || parsed.via in allowedVias)
        val plateValid = isValidPeruPlate(parsed.placa)

        val stage = when {
            !hasAction -> AviCommandStage.ACCION
            !hasViaMarker || !viaIsAllowed -> AviCommandStage.VIA
            !hasPlateMarker || !plateValid -> AviCommandStage.PLACA
            else -> AviCommandStage.COMPLETO
        }

        val prompt = when (stage) {
            AviCommandStage.ACCION -> "Diga la acción: FUGA, DERIVADO o LIBERADO."
            AviCommandStage.VIA -> if (
                parsed.via != null &&
                allowedVias.isNotEmpty() &&
                parsed.via !in allowedVias
            ) {
                "La vía ${parsed.via} no corresponde a esta plaza. Repita solo la vía."
            } else {
                "No se reconoció la vía. Repita solo la vía."
            }
            AviCommandStage.PLACA -> "No se reconoció una placa válida. Repita solo la placa."
            AviCommandStage.COMPLETO -> "Comando completo."
        }

        return AviCommandAnalysis(
            stage = stage,
            parsed = parsed,
            actionDetected = hasAction,
            viaMarkerDetected = hasViaMarker,
            plateMarkerDetected = hasPlateMarker,
            platePositions = platePositionValidity(parsed.placa),
            prompt = prompt
        )
    }

    fun scoreCandidate(
        rawText: String,
        allowedVias: Set<Int> = emptySet()
    ): Int {
        val clean = normalize(rawText)
        if (clean.isBlank()) return Int.MIN_VALUE

        val analysis = analyzeCommand(rawText, allowedVias)
        val parsed = analysis.parsed
        var score = 0

        if (analysis.actionDetected) score += 20
        if (analysis.viaMarkerDetected) score += 25
        if (analysis.plateMarkerDetected) score += 35

        val actionPositions = (aliasFuga + aliasDerivado + aliasLiberado)
            .map { clean.indexOf(it) }
            .filter { it >= 0 }
        val actionPos = actionPositions.minOrNull() ?: -1
        val viaPos = viaKeywords
            .map { clean.indexOf(it) }
            .filter { it >= 0 }
            .minOrNull() ?: -1
        val placaPos = clean.indexOf("placa ")

        if (actionPos >= 0 && viaPos > actionPos && placaPos > viaPos) {
            score += 30
        }

        if (parsed.via != null && parsed.via > 0) {
            score += 25
            if (allowedVias.isEmpty() || parsed.via in allowedVias) {
                score += 20
            } else {
                score -= 60
            }
        }

        // La placa se evalúa posición por posición, no solo como una cadena final.
        score += analysis.platePositions.count { it } * 8
        if (isValidPeruPlate(parsed.placa)) score += 50

        if (analysis.stage == AviCommandStage.COMPLETO && parsed.valido) score += 100
        score -= parsed.errores.size * 30
        score -= parsed.advertencias.size * 5

        val qTokens = setOf(
            "negativo", "negativa",
            "primero", "primer", "primera",
            "segundo", "segunda",
            "tercero", "tercer", "tercera",
            "cuarto", "cuarta", "quinto", "quinta",
            "sexto", "sexta", "septimo", "septima",
            "octavo", "octava", "noveno", "novena"
        )
        score += clean.split(" ").count { it in qTokens } * 3

        return score
    }

    private fun <T> addVote(
        votes: MutableMap<T, Float>,
        value: T,
        weight: Float
    ) {
        votes[value] = (votes[value] ?: 0f) + weight
    }

    private fun <T> chooseVote(
        votes: Map<T, Float>
    ): Pair<T?, Float> {
        if (votes.isEmpty()) return null to 0f

        val total = votes.values.sum().coerceAtLeast(0.0001f)
        val winner = votes.maxByOrNull { it.value } ?: return null to 0f
        return winner.key to (winner.value / total)
    }

    /**
     * Fusiona hasta cinco hipótesis del SpeechRecognizer en lugar de obligar
     * a que una sola frase gane completa.
     *
     * - Acción: voto ponderado FUGA / DERIVADO / LIBERADO.
     * - Vía: voto ponderado y filtrado por el catálogo real de AVIX.
     * - Placa: voto independiente por cada una de las seis posiciones.
     *
     * Si la fusión no produce algo al menos tan coherente como la mejor
     * hipótesis individual, se conserva selectBestHypothesis como fallback.
     */
    fun fuseHypotheses(
        candidates: List<String>,
        confidenceScores: FloatArray? = null,
        allowedVias: Set<Int> = emptySet()
    ): HypothesisFusionResult {
        val usable = candidates
            .filter { it.isNotBlank() }
            .take(5)

        if (usable.isEmpty()) {
            val empty = parse("", allowedVias)
            return HypothesisFusionResult(
                text = "",
                parsed = empty,
                fused = false,
                sourceCount = 0,
                actionConfidence = 0f,
                viaConfidence = 0f,
                platePositionConfidence = List(6) { 0f }
            )
        }

        val bestText = selectBestHypothesis(
            candidates = usable,
            confidenceScores = confidenceScores,
            allowedVias = allowedVias
        )
        val bestParsed = parse(bestText, allowedVias)
        val bestAnalysis = analyzeCommand(bestText, allowedVias)

        val actionVotes = mutableMapOf<String, Float>()
        val viaVotes = mutableMapOf<Int, Float>()
        val plateVotes = List(6) { mutableMapOf<Char, Float>() }

        usable.forEachIndexed { index, candidate ->
            val parsed = parse(candidate, allowedVias)
            val analysis = analyzeCommand(candidate, allowedVias)

            val confidence = confidenceScores
                ?.getOrNull(index)
                ?.takeIf { it >= 0f }
                ?.coerceIn(0f, 1f)
                ?: 0.55f

            var baseWeight =
                1f +
                    (confidence * 1.6f) +
                    ((usable.size - index) * 0.05f)

            if (parsed.valido) baseWeight += 0.35f

            if (
                analysis.actionDetected &&
                parsed.accion in setOf("FUGA", "DERIVADO", "LIBERADO")
            ) {
                addVote(
                    actionVotes,
                    parsed.accion,
                    baseWeight + 0.25f
                )
            }

            val via = parsed.via
            if (
                analysis.viaMarkerDetected &&
                via != null &&
                via > 0 &&
                (allowedVias.isEmpty() || via in allowedVias)
            ) {
                addVote(
                    viaVotes,
                    via,
                    baseWeight + if (allowedVias.isNotEmpty()) 0.55f else 0.25f
                )
            }

            val plate = parsed.placa
                .uppercase(Locale.ROOT)
                .replace("-", "")
                .replace(" ", "")

            // Las placas parciales desplazan posiciones y contaminan el voto
            // de caracteres (p. ej. I1L10 frente a I1L110).
            if (analysis.plateMarkerDetected && isValidPeruPlate(plate)) {
                val fullPlateBonus =
                    if (isValidPeruPlate(plate)) 0.45f else 0f

                for (position in 0..5) {
                    val ch = plate.getOrNull(position) ?: continue
                    val validForPosition = when (position) {
                        0 -> ch.isLetter()
                        1, 2 -> ch.isLetterOrDigit()
                        else -> ch.isDigit()
                    }

                    if (validForPosition) {
                        addVote(
                            plateVotes[position],
                            ch,
                            baseWeight + fullPlateBonus
                        )
                    }
                }
            }
        }

        val (actionWinner, actionConfidence) = chooseVote(actionVotes)
        val (viaWinner, viaConfidence) = chooseVote(viaVotes)

        val plateWinners = mutableListOf<Char?>()
        val plateConfidences = mutableListOf<Float>()

        for (position in 0..5) {
            val (winner, confidence) = chooseVote(plateVotes[position])
            plateWinners += winner
            plateConfidences += confidence
        }

        val votedPlate = if (plateWinners.all { it != null }) {
            plateWinners.joinToString("") { it.toString() }
        } else {
            ""
        }

        val fallbackAction =
            if (bestAnalysis.actionDetected) bestParsed.accion else null
        val fallbackVia =
            if (bestAnalysis.viaMarkerDetected) bestParsed.via else null
        val fallbackPlate =
            if (bestAnalysis.plateMarkerDetected) bestParsed.placa else ""

        val finalAction = actionWinner ?: fallbackAction
        val finalVia = viaWinner ?: fallbackVia
        val validIndividualPlates = usable.mapNotNull { candidate ->
            parse(candidate, allowedVias).placa
                .takeIf(::isValidPeruPlate)
        }.toSet()

        // Si la votación crea una placa no pronunciada en ninguna hipótesis,
        // exigir consenso alto en todas sus posiciones. De lo contrario,
        // conservar la mejor placa completa detectada.
        val votedPlateHasSupport = validIndividualPlates.contains(votedPlate) ||
            (validIndividualPlates.isEmpty() ||
                plateConfidences.all { it >= 0.72f })
        val finalPlate = when {
            isValidPeruPlate(votedPlate) && votedPlateHasSupport -> votedPlate
            isValidPeruPlate(fallbackPlate) -> fallbackPlate
            isValidPeruPlate(votedPlate) -> votedPlate
            fallbackPlate.isNotBlank() -> fallbackPlate
            else -> votedPlate
        }

        val pieces = mutableListOf<String>()
        if (finalAction != null) {
            pieces += finalAction.lowercase(Locale.ROOT)
        }
        if (finalVia != null) {
            pieces += "vía $finalVia"
        }
        if (finalPlate.isNotBlank()) {
            pieces += "placa $finalPlate"
        }

        val fusedText = pieces.joinToString(" ").trim()
        val fusedParsed = parse(fusedText, allowedVias)

        fun recognizedFieldCount(
            text: String,
            parsed: ParsedCommand
        ): Int {
            val analysis = analyzeCommand(text, allowedVias)
            var count = 0
            if (analysis.actionDetected) count++
            if (analysis.viaMarkerDetected && parsed.via != null) count++
            if (analysis.plateMarkerDetected && parsed.placa.isNotBlank()) count++
            return count
        }

        val fusedFieldCount = recognizedFieldCount(fusedText, fusedParsed)
        val bestFieldCount = recognizedFieldCount(bestText, bestParsed)

        val shouldUseFusion =
            usable.size > 1 &&
                fusedText.isNotBlank() &&
                (
                    fusedParsed.valido ||
                        (!bestParsed.valido && fusedFieldCount >= bestFieldCount) ||
                        fusedFieldCount > bestFieldCount
                )

        val selectedText = if (shouldUseFusion) fusedText else bestText
        val selectedParsed = if (shouldUseFusion) fusedParsed else bestParsed

        return HypothesisFusionResult(
            text = selectedText,
            parsed = selectedParsed.copy(
                textoOriginal = usable.joinToString(" || ")
            ),
            fused = shouldUseFusion,
            sourceCount = usable.size,
            actionConfidence = actionConfidence,
            viaConfidence = viaConfidence,
            // La confianza debe describir los caracteres efectivamente
            // mostrados, no una placa alternativa descartada.
            platePositionConfidence = (0..5).map { index ->
                val chosen = selectedParsed.placa.getOrNull(index)
                if (chosen == null || !isValidPeruPlate(selectedParsed.placa)) {
                    0f
                } else {
                    val votes = plateVotes[index]
                    val total = votes.values.sum()
                    if (total > 0f) {
                        (votes[chosen] ?: 0f) / total
                    } else {
                        0f
                    }
                }
            }
        )
    }

    fun selectBestHypothesis(
        candidates: List<String>,
        confidenceScores: FloatArray? = null,
        allowedVias: Set<Int> = emptySet()
    ): String {
        if (candidates.isEmpty()) return ""

        return candidates
            .mapIndexed { index, candidate ->
                val confidence = confidenceScores
                    ?.getOrNull(index)
                    ?.takeIf { it >= 0f }
                    ?: 0f

                val combinedScore =
                    scoreCandidate(candidate, allowedVias) + (confidence * 20f)

                candidate to combinedScore
            }
            .maxByOrNull { it.second }
            ?.first
            .orEmpty()
    }

    fun parse(
        rawText: String,
        allowedVias: Set<Int> = emptySet()
    ): ParsedCommand {
        val clean = normalize(rawText)

        if (clean.isBlank()) {
            return ParsedCommand(
                placa = "",
                via = null,
                accion = "FUGA",
                textoOriginal = rawText,
                valido = false,
                errores = listOf("No se recibió ningún texto para interpretar.")
            )
        }

        val errores = mutableListOf<String>()
        val advertencias = mutableListOf<String>()

        val hasDerivado = aliasDerivado.any { clean.contains(it) }
        val hasFuga = aliasFuga.any { clean.contains(it) }
        val hasLiberado = aliasLiberado.any { clean.contains(it) }

        val detectedActions = listOfNotNull(
            "FUGA".takeIf { hasFuga },
            "DERIVADO".takeIf { hasDerivado },
            "LIBERADO".takeIf { hasLiberado }
        )

        val accion = detectedActions.singleOrNull()
            ?: detectedActions.firstOrNull()
            ?: "FUGA"

        when {
            detectedActions.size > 1 ->
                errores.add("Se detectaron varias acciones a la vez. Repita solo FUGA, DERIVADO o LIBERADO.")
            detectedActions.isEmpty() ->
                errores.add("No se detectó una acción. Diga FUGA, DERIVADO o LIBERADO.")
        }

        var viaWords = ""
        var placaWords = ""

        val (viaStart, viaKeyLen) = viaMarkerPosition(clean)
        val (placaStart, placaKeyLen) = plateMarkerPosition(clean)

        if (placaStart >= 0 && viaStart >= 0) {
            if (placaStart < viaStart) {
                placaWords = clean.substring(placaStart + placaKeyLen, viaStart).trim()
                viaWords = clean.substring(viaStart + viaKeyLen).trim()
                advertencias.add("Se recomienda dictar en el orden ACCIÓN, VÍA, PLACA.")
            } else {
                viaWords = clean.substring(viaStart + viaKeyLen, placaStart).trim()
                placaWords = clean.substring(placaStart + placaKeyLen).trim()
            }
        } else if (placaStart >= 0) {
            placaWords = clean.substring(placaStart + placaKeyLen).trim()
        } else if (viaStart >= 0) {
            viaWords = clean.substring(viaStart + viaKeyLen).trim()
        } else {
            viaWords = clean
        }

        for (alias in aliasFuga + aliasDerivado + aliasLiberado) {
            placaWords = placaWords.replace(Regex("\\b${Regex.escape(alias)}\\b"), " ").trim()
            viaWords = viaWords.replace(Regex("\\b${Regex.escape(alias)}\\b"), " ").trim()
        }

        val placaParsed = parsePlaca(placaWords)
        when {
            placaStart < 0 ->
                errores.add("No se detectó la palabra PLACA.")
            placaParsed.isBlank() ->
                errores.add("No se detectó la placa del vehículo.")
            !isValidPeruPlate(placaParsed) ->
                errores.add(
                    "La placa detectada ($placaParsed) no cumple el formato esperado: " +
                        "1 letra, 2 caracteres alfanuméricos y 3 números."
                )
        }

        // Un número sin etiqueta solo puede clasificarse como vía si
        // normalizeOperationalContext confirmó "NÚMERO placa".
        // Fuera de ese patrón se deja sin clasificar, para revisión.
        val viaParsed = if (viaStart >= 0) {
            resolveShortVia(
                parseVia(viaWords, clean),
                viaWords,
                allowedVias
            )
        } else {
            null
        }
        when {
            viaStart < 0 ->
                errores.add("No se detectó la palabra VÍA.")
            viaParsed == null || viaParsed <= 0 ->
                errores.add("No se detectó un número de vía válido.")
            allowedVias.isNotEmpty() && viaParsed !in allowedVias ->
                errores.add(
                    "La vía $viaParsed no está habilitada para esta plaza. " +
                        "Permitidas: ${allowedVias.sorted().joinToString(", ")}."
                )
        }

        return ParsedCommand(
            placa = placaParsed,
            via = viaParsed,
            accion = accion,
            textoOriginal = rawText,
            valido = errores.isEmpty(),
            errores = errores.distinct(),
            advertencias = advertencias.distinct()
        )
    }

    /**
     * Cambia un solo carácter cuando se indica explícitamente su posición.
     * Ejemplo: "corrige último dígito negativo". No sustituye la placa
     * completa ni adivina caracteres a partir de semejanza visual.
     */
    private fun correctSinglePlateCharacter(
        previous: ParsedCommand,
        cleanCorrection: String
    ): String? {
        if (!isValidPeruPlate(previous.placa)) return null

        val command = Regex(
            """^(?:(?:corrige|corregir|cambia|cambiar)\s+)?""" +
                """(?:el\s+)?""" +
                """(primer|primero|segundo|tercero|cuarto|quinto|sexto|ultimo)""" +
                """\s+(?:caracter|digito|numero|simbolo)\s+""" +
                """(?:(?:a|por|es)\s+)?(.+)$"""
        ).matchEntire(cleanCorrection) ?: return null

        val index = when (command.groupValues[1]) {
            "primer", "primero" -> 0
            "segundo" -> 1
            "tercero" -> 2
            "cuarto" -> 3
            "quinto" -> 4
            "sexto", "ultimo" -> 5
            else -> return null
        }

        // parsePlaca exige un prefijo completo, así que no sirve para
        // instrucciones de un único dígito como "negativo".
        val spokenSymbol = command.groupValues[2].trim()
        val symbol = when {
            spokenSymbol in singleDigits ->
                singleDigits.getValue(spokenSymbol).digitToChar()
            spokenSymbol in phoneticAlphabet ->
                phoneticAlphabet.getValue(spokenSymbol).single()
            spokenSymbol.length == 1 &&
                spokenSymbol[0].isLetterOrDigit() ->
                spokenSymbol.uppercase(Locale.ROOT)[0]
            else -> null
        } ?: return null

        val validSymbol = when (index) {
            0 -> symbol in 'A'..'Z'
            1, 2 -> symbol.isLetterOrDigit()
            else -> symbol.isDigit()
        }
        if (!validSymbol) return null

        return previous.placa.replaceRange(index, index + 1, symbol.toString())
    }

    /**
     * Fusiona una corrección corta con el resultado anterior.
     * Permite decir únicamente "placa ...", "vía ..." o "derivado/fuga"
     * sin repetir todo el comando.
     */
    fun mergeCorrection(
        previous: ParsedCommand,
        rawCorrection: String,
        allowedVias: Set<Int> = emptySet()
    ): ParsedCommand {
        val clean = normalize(rawCorrection)

        correctSinglePlateCharacter(previous, clean)?.let { correctedPlate ->
            val merged = mergeCorrection(
                previous = previous,
                rawCorrection = "placa $correctedPlate",
                allowedVias = allowedVias
            )
            return merged.copy(
                textoOriginal = listOf(previous.textoOriginal, rawCorrection)
                    .filter { it.isNotBlank() }
                    .joinToString(" | corrección: ")
            )
        }

        if (clean.startsWith("corrige ") || clean.startsWith("cambia ")) {
            val refersToCharacter = listOf("digito", "numero", "caracter", "simbolo")
                .any { " $it " in " $clean " }
            if (refersToCharacter) {
                return previous.copy(
                    valido = false,
                    errores = listOf(
                        "No pude corregir ese carácter. Indica la posición y un único símbolo válido."
                    )
                )
            }
        }

        val fresh = parse(rawCorrection, allowedVias)

        val hasAction = actionDetected(clean)
        val hasVia = viaMarkerPosition(clean).first >= 0
        val hasPlate = plateMarkerPosition(clean).first >= 0

        val mergedAction = if (hasAction) fresh.accion else previous.accion
        val mergedVia = if (hasVia) fresh.via else previous.via
        val mergedPlate = if (hasPlate) fresh.placa else previous.placa

        val errores = mutableListOf<String>()

        val previousActionValid = previous.errores.none {
            it.contains("acción", ignoreCase = true) ||
                it.contains("accion", ignoreCase = true)
        }
        val previousViaValid = previous.errores.none {
            it.contains("vía", ignoreCase = true) ||
                it.contains("via", ignoreCase = true)
        }
        val previousPlateValid = previous.errores.none {
            it.contains("placa", ignoreCase = true)
        }

        if (
            mergedAction !in setOf("FUGA", "DERIVADO", "LIBERADO") ||
            (!hasAction && !previousActionValid)
        ) {
            errores.add("No se detectó una acción válida.")
        }

        if (
            mergedVia == null ||
            mergedVia <= 0 ||
            (!hasVia && !previousViaValid)
        ) {
            errores.add("No se detectó una vía válida.")
        } else if (allowedVias.isNotEmpty() && mergedVia !in allowedVias) {
            errores.add(
                "La vía $mergedVia no está habilitada para esta plaza. " +
                    "Permitidas: ${allowedVias.sorted().joinToString(", ")}."
            )
        }

        if (
            !isValidPeruPlate(mergedPlate) ||
            (!hasPlate && !previousPlateValid)
        ) {
            errores.add("La placa no cumple el formato esperado.")
        }

        val original = listOf(previous.textoOriginal, rawCorrection)
            .filter { it.isNotBlank() }
            .joinToString(" | corrección: ")

        return ParsedCommand(
            placa = mergedPlate,
            via = mergedVia,
            accion = mergedAction,
            textoOriginal = original,
            valido = errores.isEmpty(),
            errores = errores,
            advertencias = emptyList()
        )
    }

    /**
     * Permite dictar el sufijo de dos dígitos de vías de la serie 100:
     * "vía 51" / "vía cincuenta y uno" / "vía quinto primero" -> 151.
     *
     * Solo se amplía cuando la vía 1xx existe en el catálogo habilitado.
     * Una vía corta real tiene prioridad (51 gana frente a 151).
     * Un dígito suelto no se convierte: "vía 1" puede ser ambiguo.
     *
     * Para 101..109 se admite "01", "02", etc., incluidos los dos
     * dígitos de código Q: "negativo primero", "negativo segundo".
     */
    /**
     * Normaliza una vía numérica ya reconocida o editada en pantalla.
     * La burbuja y la pantalla principal comparten el mismo catálogo
     * de vías; nunca se amplía a una vía deshabilitada.
     */
    fun normalizeViaNumber(
        rawVia: String,
        allowedVias: Set<Int>
    ): Int? {
        val raw = rawVia.trim()
        if (raw.isEmpty() || raw.any { !it.isDigit() }) return null
        return resolveShortVia(raw.toIntOrNull(), raw, allowedVias)
    }

    private fun resolveShortVia(
        spokenVia: Int?,
        rawVia: String,
        allowedVias: Set<Int>
    ): Int? {
        val number = spokenVia ?: return null
        if (number in allowedVias || allowedVias.isEmpty()) return number

        val words = rawVia.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        val leadingZeroWritten = words.firstOrNull()
            ?.matches(Regex("0[1-9]")) == true
        val leadingZeroQ = words.size >= 2 &&
            words[0] in setOf("negativo", "negativa", "cero") &&
            (singleDigits[words[1]]?.let { it in 1..9 } == true)

        val isTwoDigitForm = number in 10..99 ||
            (number in 1..9 && (leadingZeroWritten || leadingZeroQ))
        if (!isTwoDigitForm) return number

        val expanded = 100 + number
        return expanded.takeIf { it in allowedVias } ?: number
    }

    private fun parseVia(
        viaSnippet: String,
        fullCleanText: String
    ): Int? {
        val target = if (viaSnippet.isNotBlank()) {
            viaSnippet
        } else {
            val direct = Regex(
                "(?:via|carril|numero)\\s+(\\d+)",
                RegexOption.IGNORE_CASE
            ).find(fullCleanText)

            if (direct != null) {
                return direct.groupValues[1].toIntOrNull()
            }
            ""
        }

        val numberTokens = mutableListOf<String>()
        var numberStarted = false

        for (token in target.split(" ").filter { it.isNotBlank() }) {
            val isJoiner = token == "y" || token == "numero" || token == "de"
            val isDirectDigits = token.all { it.isDigit() }
            val isNumberWord =
                singleDigits.containsKey(token) ||
                    spanishNumbers.containsKey(token)

            when {
                isDirectDigits || isNumberWord -> {
                    numberStarted = true
                    numberTokens.add(token)
                }
                isJoiner && numberStarted -> numberTokens.add(token)
                isJoiner && !numberStarted -> continue
                numberStarted -> break
                else -> continue
            }
        }

        val tokens = numberTokens.filter {
            it != "y" && it != "numero" && it != "de"
        }

        if (tokens.isEmpty()) return null

        val areAllDigitLike = tokens.all { token ->
            token.all { it.isDigit() } || singleDigits.containsKey(token)
        }

        if (areAllDigitLike) {
            if (tokens.size == 1 && tokens[0].all { it.isDigit() }) {
                return tokens[0].toIntOrNull()
            }

            val digitString = tokens.joinToString("") { token ->
                when {
                    token.all { it.isDigit() } -> token
                    else -> singleDigits[token]?.toString().orEmpty()
                }
            }

            return digitString.toIntOrNull()
        }

        val compuesto = convertWordsToNumber(numberTokens.joinToString(" "))
        return if (compuesto > 0) compuesto else null
    }

    private fun convertWordsToNumber(phrase: String): Int {
        val tokens = phrase
            .split(" ")
            .filter { it.isNotBlank() && it != "y" }

        var current = 0
        var total = 0

        for (token in tokens) {
            val value = spanishNumbers[token] ?: continue
            when {
                value == 1000 -> {
                    total += if (current == 0) 1000 else current * 1000
                    current = 0
                }
                value == 100 -> {
                    current = if (current == 0) 100 else current + 100
                }
                value >= 100 -> current += value
                else -> current += value
            }
        }

        total += current
        return total
    }

    private fun parsePlaca(placaSnippet: String): String {
        val target = placaSnippet.trim()
        if (target.isBlank()) return ""

        // Solo aceptar directamente una placa si TODO el fragmento posterior a
        // "placa" es exactamente la matrícula. Si existe texto adicional,
        // reconstruirla completa para no ignorar una corrección final.
        val compactCandidate = target
            .uppercase(Locale.ROOT)
            .replace("-", "")
            .replace(" ", "")

        if (compactCandidate.matches(Regex("[A-Z][A-Z0-9]{2}\\d{3}"))) {
            return compactCandidate
        }

        val symbols = mutableListOf<Char>()

        val tokens = target
            .split(" ")
            .filter {
                it.isNotBlank() &&
                    it != "y" &&
                    it != "guion" &&
                    it != "menos"
            }

        var index = 0
        while (index < tokens.size) {
            val token = tokens[index]

            // Alias fonéticos de dos palabras:
            // "ve grande", "doble ve", "i griega", "x ray", etc.
            if (index + 1 < tokens.size) {
                val pair = "$token ${tokens[index + 1]}"
                val mappedPair = phoneticAlphabet[pair]
                if (mappedPair != null) {
                    symbols += mappedPair.first()
                    index += 2
                    continue
                }
            }

            // Bloques numéricos que el ASR puede devolver juntos.
            if (token.all { it.isDigit() }) {
                token.forEach { symbols += it }
                index++
                continue
            }

            val mappedDigit = singleDigits[token]
            if (mappedDigit != null) {
                symbols += mappedDigit.digitToChar()
                index++
                continue
            }

            val mappedLetter = phoneticAlphabet[token]
            if (mappedLetter != null) {
                symbols += mappedLetter.first()
                index++
                continue
            }

            // Inferencia fonética aproximada únicamente mientras todavía
            // estamos formando las primeras tres posiciones alfanuméricas.
            if (symbols.size <= 2) {
                val inferredLetter = inferPhoneticLetter(token)
                if (inferredLetter != null) {
                    symbols += inferredLetter.first()
                    index++
                    continue
                }
            }

            if (token.length == 1 && token[0].isLetter()) {
                symbols += token.uppercase(Locale.ROOT).first()
                index++
                continue
            }

            // Android también puede entregar una matrícula parcial o completa
            // unida en un único token, por ejemplo I1L111.
            if (
                token.length in 2..8 &&
                token.all { it.isLetterOrDigit() } &&
                token.any { it.isDigit() }
            ) {
                token.uppercase(Locale.ROOT).forEach { symbols += it }
            }

            index++
        }

        if (symbols.isEmpty()) return ""

        // Si el dictado produjo exactamente seis símbolos, conservarlos tal
        // como fueron interpretados. La validación posterior decide qué
        // posición es inválida; no debemos "arreglar" silenciosamente una
        // placa como 1AB234 convirtiéndola en AB234.
        if (symbols.size == 6) {
            return symbols.joinToString("")
        }

        // Construir las tres primeras posiciones respetando la estructura:
        // 1 = letra; 2 y 3 = alfanuméricas.
        val prefix = mutableListOf<Char>()
        var symbolIndex = 0

        while (symbolIndex < symbols.size && prefix.size < 3) {
            val symbol = symbols[symbolIndex]
            val validForPosition = when (prefix.size) {
                0 -> symbol.isLetter()
                else -> symbol.isLetterOrDigit()
            }

            if (validForPosition) {
                prefix += symbol
            }
            symbolIndex++
        }

        if (prefix.isEmpty()) return ""

        // Después de las tres primeras posiciones solo interesan números.
        // Si el ASR insertó un dígito duplicado (ej. I1L111 NEGATIVO),
        // conservar los ÚLTIMOS tres números pronunciados:
        // I1L + [1,1,1,0] -> I1L110.
        val numericTail = symbols
            .drop(symbolIndex)
            .filter { it.isDigit() }

        val finalDigits = when {
            numericTail.size >= 3 -> numericTail.takeLast(3)
            else -> numericTail
        }

        return (prefix + finalDigits)
            .joinToString("")
            .take(6)
    }
}
