package com.example.voice

/**
 * VAD adaptativo para la captura reforzada de AVIX.
 *
 * Trabaja con niveles dBFS por frame:
 * - calibra el piso de ruido durante ~200 ms;
 * - exige varios frames consecutivos sobre el umbral para iniciar voz;
 * - usa histéresis para no cortar palabras entre ruido intermitente;
 * - termina tras ~700 ms de silencio o por límites de seguridad.
 *
 * No modifica el audio. Solo decide cuándo empieza y termina el segmento
 * que NoiseReducedAudioSource envía al SpeechRecognizer.
 */
class AdaptiveVoiceActivityDetector(
    private val calibrationFrames: Int = 10,
    private val speechStartFrames: Int = 3,
    private val endSilenceFrames: Int = 35,
    private val minSpeechFrames: Int = 25,
    private val maxSpeechFrames: Int = 325,
    private val maxWaitingFrames: Int = 225,
    private val startMarginDb: Float = 5f,
    private val endMarginDb: Float = 2.5f
) {

    enum class Phase {
        CALIBRATING,
        WAITING_FOR_SPEECH,
        SPEECH,
        FINISHED
    }

    data class State(
        val phase: Phase,
        val levelDb: Float,
        val noiseFloorDb: Float,
        val startThresholdDb: Float,
        val endThresholdDb: Float,
        val speechStarted: Boolean,
        val speechEnded: Boolean,
        val timedOut: Boolean
    )

    private var phase = Phase.CALIBRATING
    private var calibrationCount = 0
    private var calibrationSumDb = 0f

    private var noiseFloorDb = DEFAULT_NOISE_FLOOR_DB
    private var consecutiveVoiceFrames = 0
    private var consecutiveSilenceFrames = 0
    private var waitingFrames = 0
    private var speechFrames = 0

    fun process(rawLevelDb: Float): State {
        val levelDb = rawLevelDb.coerceIn(MIN_DB, MAX_DB)
        var speechStartedNow = false
        var speechEndedNow = false
        var timedOutNow = false

        when (phase) {
            Phase.CALIBRATING -> {
                calibrationSumDb += levelDb
                calibrationCount++

                if (calibrationCount >= calibrationFrames) {
                    noiseFloorDb =
                        (calibrationSumDb / calibrationCount)
                            .coerceIn(MIN_NOISE_FLOOR_DB, MAX_NOISE_FLOOR_DB)
                    phase = Phase.WAITING_FOR_SPEECH
                }
            }

            Phase.WAITING_FOR_SPEECH -> {
                waitingFrames++

                val startThreshold = startThresholdDb()
                if (levelDb > startThreshold) {
                    consecutiveVoiceFrames++
                } else {
                    consecutiveVoiceFrames = 0

                    // Mientras aún no hay voz, adaptar lentamente el piso de ruido
                    // para soportar cambios de tráfico, motores o ventilación.
                    noiseFloorDb = (
                        noiseFloorDb * 0.97f +
                            levelDb * 0.03f
                        ).coerceIn(
                        MIN_NOISE_FLOOR_DB,
                        MAX_NOISE_FLOOR_DB
                    )
                }

                if (consecutiveVoiceFrames >= speechStartFrames) {
                    phase = Phase.SPEECH
                    speechFrames = 0
                    consecutiveSilenceFrames = 0
                    speechStartedNow = true
                } else if (waitingFrames >= maxWaitingFrames) {
                    phase = Phase.FINISHED
                    timedOutNow = true
                }
            }

            Phase.SPEECH -> {
                speechFrames++

                if (levelDb < endThresholdDb()) {
                    consecutiveSilenceFrames++
                } else {
                    consecutiveSilenceFrames = 0
                }

                val silenceEnded =
                    speechFrames >= minSpeechFrames &&
                        consecutiveSilenceFrames >= endSilenceFrames

                val maxDurationReached =
                    speechFrames >= maxSpeechFrames

                if (silenceEnded || maxDurationReached) {
                    phase = Phase.FINISHED
                    speechEndedNow = true
                    timedOutNow = maxDurationReached
                }
            }

            Phase.FINISHED -> Unit
        }

        return State(
            phase = phase,
            levelDb = levelDb,
            noiseFloorDb = noiseFloorDb,
            startThresholdDb = startThresholdDb(),
            endThresholdDb = endThresholdDb(),
            speechStarted = speechStartedNow,
            speechEnded = speechEndedNow,
            timedOut = timedOutNow
        )
    }

    private fun startThresholdDb(): Float {
        return (noiseFloorDb + startMarginDb)
            .coerceAtMost(MAX_START_THRESHOLD_DB)
    }

    private fun endThresholdDb(): Float {
        return (noiseFloorDb + endMarginDb)
            .coerceAtMost(MAX_END_THRESHOLD_DB)
    }

    companion object {
        private const val MIN_DB = -90f
        private const val MAX_DB = 0f
        private const val DEFAULT_NOISE_FLOOR_DB = -45f
        private const val MIN_NOISE_FLOOR_DB = -75f
        private const val MAX_NOISE_FLOOR_DB = -2.5f

        // Evitar pedir niveles imposibles cuando el ambiente ya es muy ruidoso.
        private const val MAX_START_THRESHOLD_DB = -1.5f
        private const val MAX_END_THRESHOLD_DB = -3f
    }
}
