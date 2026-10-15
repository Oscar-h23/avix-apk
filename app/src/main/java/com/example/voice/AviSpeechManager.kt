package com.example.voice

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.core.content.ContextCompat
import com.example.model.DiagnosticStage
import com.example.model.ParsedCommand
import com.example.model.VoiceState
import com.example.parser.AviParser
import com.example.parser.AviCommandStage
import com.example.parser.HypothesisFusionResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Gestor unificado de voz para AVIX.
 * Implementa patrón Singleton para evitar conflictos de micrófono entre
 * MainActivity y FloatingBubbleService.
 */
data class VoiceMetricsSnapshot(
    val attempts: Int,
    val validFirstPass: Int,
    val invalidResults: Int,
    val alternativeSelected: Int,
    val missingAction: Int,
    val missingVia: Int,
    val missingPlate: Int,
    val manualCorrections: Int,
    val plateCorrections: Int,
    val viaCorrections: Int,
    val actionCorrections: Int,
    val averageRecognitionMs: Long,
    val timedRecognitionCount: Int
)

class AviSpeechManager private constructor(private val appContext: Context) {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(Dispatchers.Main)
    private var simulationJob: Job? = null
    private var speechStartTimestampMs: Long? = null

    private var allowedVias: Set<Int> = emptySet()
    private val metricsPrefs = appContext.getSharedPreferences(
        "avix_voice_metrics",
        Context.MODE_PRIVATE
    )

    private var speechRecognizer: SpeechRecognizer? = null
    private var usingOnDeviceRecognizer = false
    // El modelo offline no siempre acepta es-PE. Probar otras variantes
    // del español disponibles en el teléfono, nunca un servidor remoto.
    private val offlineSpanishLanguages =
        listOf("es-ES", "es-MX", "es-US", "es-PE", "es")
    private var offlineLanguageAttempt = 0
    private var onlineLanguageAttempt = 0
    private val onlineSpanishLanguages = listOf("es-PE", "es-ES", "es-MX", "es-US", "es")
    private val noiseReducedAudioSource = NoiseReducedAudioSource()
    private var enhancedAudioActive = false
    private var currentAttemptUsesEnhancedAudio = false
    private var automaticEnhancedRetryAttempted = false
    private var automaticRetryInProgress = false
    private var primaryRawText = ""
    private var primaryInterpretedText = ""
    private var primaryScore = Int.MIN_VALUE
    private var primaryPlateConfidence: List<Float> = emptyList()
    private var captureTimeoutRunnable: Runnable? = null

    var isRecognizerAvailable = false
        private set

    private val _voiceState = MutableStateFlow(VoiceState())
    val voiceState: StateFlow<VoiceState> = _voiceState.asStateFlow()

    private val resultListeners = mutableSetOf<(String) -> Unit>()

    init {
        mainHandler.post {
            initRecognizer()
        }
    }

    fun addResultListener(listener: (String) -> Unit) {
        resultListeners.add(listener)
    }

    fun removeResultListener(listener: (String) -> Unit) {
        resultListeners.remove(listener)
    }

    fun setAllowedVias(vias: Set<Int>) {
        allowedVias = vias.filter { it > 0 }.toSet()
    }

    fun getVoiceMetrics(): VoiceMetricsSnapshot {
        return VoiceMetricsSnapshot(
            attempts = metricsPrefs.getInt(KEY_ATTEMPTS, 0),
            validFirstPass = metricsPrefs.getInt(KEY_VALID_FIRST_PASS, 0),
            invalidResults = metricsPrefs.getInt(KEY_INVALID_RESULTS, 0),
            alternativeSelected = metricsPrefs.getInt(KEY_ALTERNATIVE_SELECTED, 0),
            missingAction = metricsPrefs.getInt(KEY_MISSING_ACTION, 0),
            missingVia = metricsPrefs.getInt(KEY_MISSING_VIA, 0),
            missingPlate = metricsPrefs.getInt(KEY_MISSING_PLATE, 0),
            manualCorrections = metricsPrefs.getInt(KEY_MANUAL_CORRECTIONS, 0),
            plateCorrections = metricsPrefs.getInt(KEY_PLATE_CORRECTIONS, 0),
            viaCorrections = metricsPrefs.getInt(KEY_VIA_CORRECTIONS, 0),
            actionCorrections = metricsPrefs.getInt(KEY_ACTION_CORRECTIONS, 0),
            averageRecognitionMs = metricsPrefs.getLong(KEY_LATENCY_TOTAL_MS, 0L)
                .let { total ->
                    val count = metricsPrefs.getInt(KEY_LATENCY_COUNT, 0)
                    if (count > 0) total / count else 0L
                },
            timedRecognitionCount = metricsPrefs.getInt(KEY_LATENCY_COUNT, 0)
        )
    }

    fun recordManualCorrection(
        original: ParsedCommand,
        finalPlate: String,
        finalVia: Int?,
        finalAction: String
    ) {
        val normalizedPlate = finalPlate.uppercase().replace("-", "").replace(" ", "")
        val plateChanged = original.placa != normalizedPlate
        val viaChanged = original.via != finalVia
        val actionChanged = !original.accion.equals(finalAction, ignoreCase = true)

        if (!plateChanged && !viaChanged && !actionChanged) return

        incrementMetric(KEY_MANUAL_CORRECTIONS)
        if (plateChanged) incrementMetric(KEY_PLATE_CORRECTIONS)
        if (viaChanged) incrementMetric(KEY_VIA_CORRECTIONS)
        if (actionChanged) incrementMetric(KEY_ACTION_CORRECTIONS)
    }

    private fun incrementMetric(key: String) {
        metricsPrefs.edit()
            .putInt(key, metricsPrefs.getInt(key, 0) + 1)
            .apply()
    }

    private fun recordRecognition(
        matches: List<String>,
        selected: String
    ) {
        speechStartTimestampMs?.let { start ->
            val elapsed = (SystemClock.elapsedRealtime() - start)
                .coerceIn(0L, 60_000L)
            val count = metricsPrefs.getInt(KEY_LATENCY_COUNT, 0)
            val total = metricsPrefs.getLong(KEY_LATENCY_TOTAL_MS, 0L)
            metricsPrefs.edit()
                .putInt(KEY_LATENCY_COUNT, count + 1)
                .putLong(KEY_LATENCY_TOTAL_MS, total + elapsed)
                .apply()
        }
        speechStartTimestampMs = null
        incrementMetric(KEY_ATTEMPTS)

        val selectedIndex = matches.indexOf(selected)
        if (selectedIndex > 0) {
            incrementMetric(KEY_ALTERNATIVE_SELECTED)
        }

        val analysis = AviParser.analyzeCommand(selected, allowedVias)
        if (analysis.parsed.valido) {
            incrementMetric(KEY_VALID_FIRST_PASS)
        } else {
            incrementMetric(KEY_INVALID_RESULTS)
            when (analysis.stage) {
                AviCommandStage.ACCION -> incrementMetric(KEY_MISSING_ACTION)
                AviCommandStage.VIA -> incrementMetric(KEY_MISSING_VIA)
                AviCommandStage.PLACA -> incrementMetric(KEY_MISSING_PLATE)
                AviCommandStage.COMPLETO -> Unit
            }
        }
    }

    private fun canUseEnhancedAudio(): Boolean {
        // Desactivado: AVIX usa directamente el audio capturado por SpeechRecognizer.
        // El flujo AudioRecord + VAD + EXTRA_AUDIO_SOURCE empeoraba el reconocimiento
        // de letras y números cortos de las placas.
        return false
    }

    private fun shouldRetryWithEnhancedAudio(
        fusion: HypothesisFusionResult,
        confidenceScores: FloatArray?
    ): Boolean {
        if (
            automaticEnhancedRetryAttempted ||
            !canUseEnhancedAudio() ||
            usingOnDeviceRecognizer ||
            !hasValidatedInternet()
        ) {
            return false
        }

        val analysis = AviParser.analyzeCommand(
            fusion.text,
            allowedVias
        )

        if (!analysis.parsed.valido) {
            return true
        }

        val knownScores = confidenceScores
            ?.filter { it >= 0f }
            .orEmpty()

        val lowRecognizerConfidence =
            knownScores.isNotEmpty() &&
                (knownScores.maxOrNull() ?: 1f) < 0.52f

        val hasMultipleSources = fusion.sourceCount > 1

        val lowActionConsensus =
            hasMultipleSources &&
                fusion.actionConfidence > 0f &&
                fusion.actionConfidence < 0.55f

        val lowViaConsensus =
            hasMultipleSources &&
                fusion.viaConfidence > 0f &&
                fusion.viaConfidence < 0.55f

        val plateConfidences =
            fusion.platePositionConfidence.filter { it > 0f }

        val lowPlateConsensus =
            hasMultipleSources &&
                plateConfidences.size == 6 &&
                plateConfidences.any { it < 0.57f }

        return lowRecognizerConfidence ||
            lowActionConsensus ||
            lowViaConsensus ||
            lowPlateConsensus
    }

    private fun rememberPrimaryAttempt(
        rawText: String,
        interpretedText: String,
        plateConfidence: List<Float> = emptyList()
    ) {
        primaryRawText = rawText
        primaryInterpretedText = interpretedText
        primaryPlateConfidence = plateConfidence
        primaryScore = if (interpretedText.isBlank()) {
            Int.MIN_VALUE
        } else {
            AviParser.scoreCandidate(
                interpretedText,
                allowedVias
            )
        }
    }

    private fun clearPrimaryAttempt() {
        primaryRawText = ""
        primaryInterpretedText = ""
        primaryScore = Int.MIN_VALUE
        primaryPlateConfidence = emptyList()
        automaticRetryInProgress = false
    }

    private fun publishFinalRecognition(
        rawText: String,
        interpretedText: String,
        successDescription: String? = null,
        plateConfidence: List<Float> = emptyList()
    ) {
        if (interpretedText.isBlank()) return

        val analysis = AviParser.analyzeCommand(
            interpretedText,
            allowedVias
        )

        _voiceState.value = _voiceState.value.copy(
            isListening = false,
            stage = DiagnosticStage.STAGE_4,
            stageDescription = if (
                analysis.parsed.valido &&
                successDescription != null
            ) {
                successDescription
            } else if (analysis.parsed.valido) {
                "4/4 Comando completo y validado."
            } else {
                analysis.prompt
            },
            recognizedText = rawText.ifBlank {
                interpretedText
            },
            interpretedText = interpretedText,
            platePositionConfidence = plateConfidence,
            retryCount = 0,
            errorMessage = if (analysis.parsed.valido) {
                null
            } else {
                analysis.prompt
            }
        )

        notifyListeners(interpretedText)
        clearPrimaryAttempt()
    }

    private fun handleVadState(
        state: AdaptiveVoiceActivityDetector.State
    ) {
        mainHandler.post {
            if (!currentAttemptUsesEnhancedAudio) {
                return@post
            }

            val level = (state.levelDb + 90f)
                .coerceIn(0f, 90f)

            _voiceState.value = _voiceState.value.copy(
                rmsLevel = level,
                stage = when (state.phase) {
                    AdaptiveVoiceActivityDetector.Phase.CALIBRATING ->
                        DiagnosticStage.STAGE_1

                    AdaptiveVoiceActivityDetector.Phase.WAITING_FOR_SPEECH ->
                        DiagnosticStage.STAGE_2

                    AdaptiveVoiceActivityDetector.Phase.SPEECH ->
                        DiagnosticStage.STAGE_3

                    AdaptiveVoiceActivityDetector.Phase.FINISHED ->
                        _voiceState.value.stage
                },
                stageDescription = when (state.phase) {
                    AdaptiveVoiceActivityDetector.Phase.CALIBRATING ->
                        "1/4 Calibrando ruido ambiente..."

                    AdaptiveVoiceActivityDetector.Phase.WAITING_FOR_SPEECH ->
                        "2/4 Ruido calibrado. Habla ahora y repite el comando completo."

                    AdaptiveVoiceActivityDetector.Phase.SPEECH ->
                        "3/4 Voz detectada y aislada del ruido."

                    AdaptiveVoiceActivityDetector.Phase.FINISHED ->
                        if (state.timedOut) {
                            "Procesando el mejor segmento de voz disponible..."
                        } else {
                            "Procesando captura reforzada..."
                        }
                }
            )
        }
    }

    private fun hasValidatedInternet(): Boolean {
        val connectivityManager =
            appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return false

        val activeNetwork = connectivityManager.activeNetwork ?: return false
        val capabilities =
            connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false

        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    private fun canUseOnDeviceRecognition(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            SpeechRecognizer.isOnDeviceRecognitionAvailable(appContext)
    }

    private fun initRecognizer(
        preferOnDevice: Boolean = !hasValidatedInternet()
    ) {
        try {
            val useOnDevice =
                preferOnDevice && canUseOnDeviceRecognition()

            // Offline: nunca usar el motor estándar de red como sustituto.
            isRecognizerAvailable =
                if (preferOnDevice) {
                    useOnDevice
                } else {
                    SpeechRecognizer.isRecognitionAvailable(appContext)
                }

            if (isRecognizerAvailable) {
                speechRecognizer?.destroy()
                speechRecognizer =
                    if (useOnDevice) {
                        SpeechRecognizer.createOnDeviceSpeechRecognizer(appContext)
                    } else {
                        SpeechRecognizer.createSpeechRecognizer(appContext)
                    }

                usingOnDeviceRecognizer = useOnDevice
                setupRecognitionListener()
            } else {
                usingOnDeviceRecognizer = false
                _voiceState.value = _voiceState.value.copy(
                    errorMessage =
                        if (!hasValidatedInternet()) {
                            "Reconocimiento offline no disponible en este dispositivo. " +
                                "Descarga el paquete de voz español (Perú) en los ajustes de reconocimiento."
                        } else {
                            "Servicio de reconocimiento de voz no disponible."
                        }
                )
            }
        } catch (e: Exception) {
            isRecognizerAvailable = false
            usingOnDeviceRecognizer = false
            _voiceState.value = _voiceState.value.copy(
                errorMessage = "Error al iniciar reconocedor: ${e.localizedMessage}"
            )
        }
    }

    private fun setupRecognitionListener() {
        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                _voiceState.value = _voiceState.value.copy(
                    isListening = true,
                    stage = DiagnosticStage.STAGE_1,
                    stageDescription = "1/4 Diga: acción, vía y placa.",
                    errorMessage = null
                )
            }

            override fun onBeginningOfSpeech() {
                _voiceState.value = _voiceState.value.copy(
                    stage = DiagnosticStage.STAGE_3,
                    stageDescription = "3/4 Voz detectada en el micrófono."
                )
            }

            override fun onRmsChanged(rmsdB: Float) {
                val safeRms = (rmsdB + 2f).coerceAtLeast(0f)
                val currentStage = _voiceState.value.stage
                val newStage = if (safeRms > 0.5f && (currentStage == DiagnosticStage.STAGE_1 || currentStage == DiagnosticStage.IDLE)) {
                    DiagnosticStage.STAGE_2
                } else currentStage

                _voiceState.value = _voiceState.value.copy(
                    rmsLevel = safeRms,
                    stage = newStage,
                    stageDescription = if (newStage == DiagnosticStage.STAGE_2) "2/4 Señal de audio detectada (RMS: ${String.format("%.1f", safeRms)} dB)" else _voiceState.value.stageDescription
                )
            }

            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {
                if (enhancedAudioActive) {
                    noiseReducedAudioSource.finishInput()
                }

                _voiceState.value = _voiceState.value.copy(
                    isListening = false,
                    stageDescription = "Procesando audio recibido..."
                )
            }

            override fun onError(error: Int) {
                handleSpeechError(error)
            }

            override fun onResults(results: Bundle?) {
                val wasAutomaticRetry = automaticRetryInProgress
                val wasEnhancedAttempt =
                    currentAttemptUsesEnhancedAudio

                cleanupEnhancedAudio()
                currentAttemptUsesEnhancedAudio = false

                val matches = results
                    ?.getStringArrayList(
                        SpeechRecognizer.RESULTS_RECOGNITION
                    )
                    ?.filter { it.isNotBlank() }
                    .orEmpty()

                val confidenceScores = results
                    ?.getFloatArray(
                        SpeechRecognizer.CONFIDENCE_SCORES
                    )

                val rawText =
                    matches.firstOrNull()?.trim().orEmpty()

                val fusion = AviParser.fuseHypotheses(
                    candidates = matches,
                    confidenceScores = confidenceScores,
                    allowedVias = allowedVias
                )

                val interpretedText = fusion.text

                if (interpretedText.isBlank()) {
                    handleSpeechError(
                        SpeechRecognizer.ERROR_NO_MATCH
                    )
                    return
                }

                recordRecognition(
                    matches,
                    interpretedText
                )

                if (
                    !wasAutomaticRetry &&
                    shouldRetryWithEnhancedAudio(
                        fusion,
                        confidenceScores
                    )
                ) {
                    rememberPrimaryAttempt(
                        rawText = rawText,
                        interpretedText = interpretedText,
                        plateConfidence = fusion.platePositionConfidence
                    )

                    automaticEnhancedRetryAttempted = true
                    automaticRetryInProgress = true

                    _voiceState.value =
                        _voiceState.value.copy(
                            isListening = false,
                            stage = DiagnosticStage.STAGE_2,
                            stageDescription =
                                "Resultado dudoso. AVIX activará reducción de ruido; repite el comando completo.",
                            recognizedText = rawText.ifBlank {
                                interpretedText
                            },
                            interpretedText = interpretedText,
                            platePositionConfidence = fusion.platePositionConfidence,
                            retryCount = 1,
                            errorMessage = null
                        )

                    mainHandler.postDelayed({
                        startListeningInternal(
                            preferEnhancedAudio = true,
                            preserveOriginalText = true
                        )
                    }, 250)

                    return
                }

                if (wasAutomaticRetry) {
                    val retryScore =
                        AviParser.scoreCandidate(
                            interpretedText,
                            allowedVias
                        )

                    val useRetry =
                        primaryInterpretedText.isBlank() ||
                            retryScore >= primaryScore

                    val finalText = if (useRetry) {
                        interpretedText
                    } else {
                        primaryInterpretedText
                    }

                    val finalRaw =
                        primaryRawText.ifBlank {
                            rawText.ifBlank {
                                finalText
                            }
                        }

                    publishFinalRecognition(
                        rawText = finalRaw,
                        interpretedText = finalText,
                        plateConfidence = if (useRetry) {
                            fusion.platePositionConfidence
                        } else {
                            primaryPlateConfidence
                        },
                        successDescription = if (
                            useRetry &&
                            wasEnhancedAttempt
                        ) {
                            "4/4 Captura reforzada completada y validada."
                        } else if (useRetry) {
                            "4/4 Segundo intento completado y validado."
                        } else {
                            "4/4 Se conservó el primer resultado por mayor calidad."
                        }
                    )

                    return
                }

                val analysis = AviParser.analyzeCommand(
                    interpretedText,
                    allowedVias
                )

                publishFinalRecognition(
                    rawText = rawText,
                    interpretedText = interpretedText,
                    plateConfidence = fusion.platePositionConfidence,
                    successDescription = when {
                        analysis.parsed.valido &&
                            fusion.fused ->
                            "4/4 Comando validado fusionando ${fusion.sourceCount} alternativas."

                        analysis.parsed.valido ->
                            "4/4 Comando completo y validado."

                        else -> null
                    }
                )
            }

            override fun onPartialResults(
                partialResults: Bundle?
            ) {
                val matches = partialResults
                    ?.getStringArrayList(
                        SpeechRecognizer.RESULTS_RECOGNITION
                    )
                    ?.filter { it.isNotBlank() }
                    .orEmpty()

                val rawPartialText =
                    matches.firstOrNull()?.trim().orEmpty()

                val partialText =
                    AviParser.selectBestHypothesis(
                        candidates = matches,
                        allowedVias = allowedVias
                    )

                if (partialText.isNotBlank()) {
                    _voiceState.value =
                        _voiceState.value.copy(
                            recognizedText =
                                if (
                                    automaticRetryInProgress &&
                                    primaryRawText.isNotBlank()
                                ) {
                                    primaryRawText
                                } else {
                                    rawPartialText.ifBlank {
                                        partialText
                                    }
                                },
                            interpretedText = partialText,
                            stage = DiagnosticStage.STAGE_3,
                            stageDescription =
                                if (automaticRetryInProgress) {
                                    "3/4 Analizando captura reforzada..."
                                } else {
                                    "3/4 Interpretando comando..."
                                }
                        )
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
    }

    private fun notifyListeners(text: String) {
        resultListeners.forEach { listener ->
            try {
                listener(text)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun speechErrorName(errorCode: Int): String {
        return when (errorCode) {
            SpeechRecognizer.ERROR_AUDIO -> "AUDIO"
            SpeechRecognizer.ERROR_CLIENT -> "CLIENT"
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "PERMISSIONS"
            SpeechRecognizer.ERROR_NETWORK -> "NETWORK"
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "NETWORK_TIMEOUT"
            SpeechRecognizer.ERROR_NO_MATCH -> "NO_MATCH"
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "RECOGNIZER_BUSY"
            SpeechRecognizer.ERROR_SERVER -> "SERVER"
            SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED -> "LANGUAGE_NOT_SUPPORTED"
            SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> "LANGUAGE_UNAVAILABLE"
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "SPEECH_TIMEOUT"
            else -> "CODE_$errorCode"
        }
    }

    private fun handleSpeechError(errorCode: Int) {
        val wasEnhancedAttempt =
            currentAttemptUsesEnhancedAudio
        val wasAutomaticRetry =
            automaticRetryInProgress

        cleanupEnhancedAudio()
        currentAttemptUsesEnhancedAudio = false

        // CODE_12: el reconocedor no admite el idioma solicitado.
        // CODE_13: está admitido, pero el modelo todavía no está instalado.
        val isOfflineLanguageError =
            errorCode == SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED ||
                errorCode == SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE

        if (!hasValidatedInternet() && isOfflineLanguageError) {
            if (offlineLanguageAttempt < offlineSpanishLanguages.lastIndex) {
                offlineLanguageAttempt++
                val nextLanguage = offlineSpanishLanguages[offlineLanguageAttempt]
                _voiceState.value = _voiceState.value.copy(
                    isListening = false,
                    stageDescription = "Probando español offline ($nextLanguage)...",
                    errorMessage = null
                )
                mainHandler.postDelayed({
                    initRecognizer(preferOnDevice = canUseOnDeviceRecognition())
                    startListeningInternal(preferEnhancedAudio = false)
                }, 250L)
                return
            }
        }

        // También manejar CODE_12 cuando los datos móviles siguen activos
        // aunque el usuario haya desactivado el Wi-Fi.
        if (hasValidatedInternet() && isOfflineLanguageError &&
            onlineLanguageAttempt < onlineSpanishLanguages.lastIndex
        ) {
            onlineLanguageAttempt++
            val nextLanguage = onlineSpanishLanguages[onlineLanguageAttempt]
            _voiceState.value = _voiceState.value.copy(
                isListening = false,
                stageDescription = "Probando reconocimiento en $nextLanguage...",
                errorMessage = null
            )
            mainHandler.postDelayed({
                initRecognizer(preferOnDevice = false)
                startListeningInternal(preferEnhancedAudio = false)
            }, 250L)
            return
        }

        if (
            wasAutomaticRetry &&
            primaryInterpretedText.isNotBlank()
        ) {
            publishFinalRecognition(
                rawText = primaryRawText,
                interpretedText = primaryInterpretedText,
                plateConfidence = primaryPlateConfidence,
                successDescription =
                    if (wasEnhancedAttempt) {
                        "4/4 El modo reforzado no mejoró el resultado; se conservó el primer intento."
                    } else {
                        "4/4 Se conservó el primer resultado disponible."
                    }
            )
            return
        }

        if (
            wasAutomaticRetry &&
            primaryInterpretedText.isBlank() &&
            (
                errorCode == SpeechRecognizer.ERROR_NO_MATCH ||
                    errorCode == SpeechRecognizer.ERROR_SPEECH_TIMEOUT
                )
        ) {
            automaticRetryInProgress = false

            _voiceState.value = _voiceState.value.copy(
                isListening = false,
                stageDescription =
                    "La captura reforzada no produjo texto. Volviendo al reconocimiento normal...",
                retryCount = 1,
                errorMessage = null
            )

            mainHandler.postDelayed({
                startListeningInternal(
                    preferEnhancedAudio = false
                )
            }, 250)

            return
        }

        val eligibleForEnhancedRetry =
            errorCode == SpeechRecognizer.ERROR_NO_MATCH ||
                errorCode ==
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT

        if (
            eligibleForEnhancedRetry &&
            !automaticEnhancedRetryAttempted &&
            canUseEnhancedAudio() &&
            !usingOnDeviceRecognizer &&
            hasValidatedInternet()
        ) {
            automaticEnhancedRetryAttempted = true
            automaticRetryInProgress = true

            rememberPrimaryAttempt(
                rawText = _voiceState.value.recognizedText,
                interpretedText =
                    _voiceState.value.interpretedText,
                plateConfidence =
                    _voiceState.value.platePositionConfidence
            )

            _voiceState.value =
                _voiceState.value.copy(
                    isListening = false,
                    stage = DiagnosticStage.STAGE_1,
                    stageDescription =
                        "No hubo un resultado fiable. AVIX calibrará el ruido; repite el comando completo.",
                    retryCount = 1,
                    errorMessage = null
                )

            mainHandler.postDelayed({
                startListeningInternal(
                    preferEnhancedAudio = true,
                    preserveOriginalText = true
                )
            }, 250)

            return
        }

        if (
            errorCode !=
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY
        ) {
            incrementMetric(KEY_ATTEMPTS)
            incrementMetric(KEY_INVALID_RESULTS)
        }

        val (message, canRetryStandard) =
            when (errorCode) {
                SpeechRecognizer.ERROR_NO_MATCH ->
                    "No se reconoció ninguna frase de voz." to true

                SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
                    "Tiempo de espera agotado sin detección de voz." to true

                SpeechRecognizer.ERROR_AUDIO ->
                    "Error de captura de audio del micrófono." to false

                SpeechRecognizer.ERROR_CLIENT ->
                    "El servicio de reconocimiento no respondió." to false

                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
                    "Permiso RECORD_AUDIO no concedido." to false

                SpeechRecognizer.ERROR_NETWORK,
                SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
                    "Error de red en el servicio de voz." to false

                SpeechRecognizer.ERROR_RECOGNIZER_BUSY ->
                    "El reconocedor de voz estaba ocupado. Reiniciando..." to true

                SpeechRecognizer.ERROR_SERVER ->
                    "Error en el servidor de reconocimiento." to false

                SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED,
                SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE ->
                    if (!hasValidatedInternet()) {
                        "No hay un modelo de español offline compatible instalado. " +
                            "Conéctate a Wi-Fi una vez, descarga Español para " +
                            "reconocimiento sin conexión en los ajustes de voz del teléfono y vuelve a intentarlo." to false
                    } else {
                        "El servicio de voz no admite el idioma seleccionado." to false
                    }

                else ->
                    "Error en el reconocimiento de voz (código $errorCode)." to false
            }

        val currentRetries =
            _voiceState.value.retryCount

        if (
            canRetryStandard &&
            currentRetries < 1
        ) {
            _voiceState.value =
                _voiceState.value.copy(
                    isListening = false,
                    stageDescription =
                        "Reintentando reconocimiento...",
                    retryCount =
                        currentRetries + 1,
                    errorMessage = message
                )

            mainHandler.postDelayed({
                startListeningInternal(
                    preferEnhancedAudio = false
                )
            }, 300)
        } else {
            speechStartTimestampMs = null
            clearPrimaryAttempt()

            val errorName =
                speechErrorName(errorCode)

            _voiceState.value =
                _voiceState.value.copy(
                    isListening = false,
                    stageDescription =
                        "Reconocimiento detenido ($errorName). Pulsa el micrófono para intentar nuevamente.",
                    errorMessage =
                        "$message [$errorName]"
                )
        }
    }

    private fun cleanupEnhancedAudio() {
        captureTimeoutRunnable?.let { mainHandler.removeCallbacks(it) }
        captureTimeoutRunnable = null
        noiseReducedAudioSource.release()
        enhancedAudioActive = false
    }

    fun startListening() {
        // AVIX usa siempre el reconocimiento estándar del sistema para preservar
        // las letras y números de la placa sin procesamiento intermedio.
        automaticEnhancedRetryAttempted = false
        automaticRetryInProgress = false
        currentAttemptUsesEnhancedAudio = false
        offlineLanguageAttempt = 0
        onlineLanguageAttempt = 0
        clearPrimaryAttempt()

        _voiceState.value = _voiceState.value.copy(
            retryCount = 0,
            errorMessage = null,
            recognizedText = "",
            interpretedText = "",
            platePositionConfidence = emptyList()
        )

        startListeningInternal(
            preferEnhancedAudio = false
        )
    }

    private fun startListeningInternal(
        preferEnhancedAudio: Boolean,
        preserveOriginalText: Boolean = false
    ) {
        simulationJob?.cancel()

        if (
            ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            _voiceState.value = _voiceState.value.copy(
                isListening = false,
                errorMessage = "Permiso de micrófono (RECORD_AUDIO) no otorgado."
            )
            return
        }

        mainHandler.post {
            try {
                cleanupEnhancedAudio()

                // Cancelar únicamente si había una captura realmente activa.
                // Evita que un callback tardío de cancel() interfiera con el nuevo intento.
                if (_voiceState.value.isListening) {
                    try {
                        speechRecognizer?.cancel()
                    } catch (_: Exception) {
                    }
                }

                val networkAvailable = hasValidatedInternet()
                val onDeviceAvailable = canUseOnDeviceRecognition()
                val shouldUseOnDevice =
                    !networkAvailable && onDeviceAvailable

                if (!networkAvailable && !onDeviceAvailable &&
                    !SpeechRecognizer.isRecognitionAvailable(appContext)
                ) {
                    speechRecognizer?.destroy()
                    speechRecognizer = null
                    isRecognizerAvailable = false
                    usingOnDeviceRecognizer = false
                    _voiceState.value = _voiceState.value.copy(
                        isListening = false,
                        stageDescription = "Reconocimiento de voz offline no disponible.",
                        errorMessage =
                            "Este teléfono no tiene un motor de voz local activo. " +
                                "Conéctate a Wi-Fi y configura Reconocimiento de voz " +
                                "sin conexión en los ajustes de entrada de voz."
                    )
                    return@post
                }

                if (
                    speechRecognizer == null ||
                    usingOnDeviceRecognizer != shouldUseOnDevice
                ) {
                    initRecognizer(
                        preferOnDevice = shouldUseOnDevice
                    )
                }

                if (speechRecognizer == null) {
                    _voiceState.value = _voiceState.value.copy(
                        isListening = false,
                        errorMessage =
                            if (!networkAvailable) {
                                "No hay un motor de voz offline disponible en el dispositivo."
                            } else {
                                "No se pudo iniciar el servicio de reconocimiento de voz."
                            }
                    )
                    return@post
                }

                // Reconocimiento directo: sin AudioRecord propio, sin VAD intermedio
                // y sin inyección de audio mediante EXTRA_AUDIO_SOURCE.
                val enhancedSession: NoiseReducedAudioSource.Session? = null

                enhancedAudioActive = false
                currentAttemptUsesEnhancedAudio = false

                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(
                        RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                        RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                    )
                    val language = if (!networkAvailable) {
                        offlineSpanishLanguages[offlineLanguageAttempt]
                    } else {
                        onlineSpanishLanguages[onlineLanguageAttempt]
                    }
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, language)
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
                    putExtra(
                        RecognizerIntent.EXTRA_PREFER_OFFLINE,
                        !networkAvailable || usingOnDeviceRecognizer
                    )

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        putStringArrayListExtra(
                            RecognizerIntent.EXTRA_BIASING_STRINGS,
                            ArrayList(
                                AviParser.recognitionBiasingPhrases(
                                    allowedVias = allowedVias
                                )
                            )
                        )

                        enhancedSession?.let { session ->
                            putExtra(
                                RecognizerIntent.EXTRA_AUDIO_SOURCE,
                                session.readDescriptor
                            )
                            putExtra(
                                RecognizerIntent.EXTRA_AUDIO_SOURCE_CHANNEL_COUNT,
                                session.channelCount
                            )
                            putExtra(
                                RecognizerIntent.EXTRA_AUDIO_SOURCE_ENCODING,
                                AudioFormat.ENCODING_PCM_16BIT
                            )
                            putExtra(
                                RecognizerIntent.EXTRA_AUDIO_SOURCE_SAMPLING_RATE,
                                session.sampleRate
                            )
                        }
                    }

                    putExtra(
                        RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS,
                        1200L
                    )
                    putExtra(
                        RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS,
                        700L
                    )
                    putExtra(
                        RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS,
                        1100L
                    )

                    putExtra(
                        RecognizerIntent.EXTRA_CALLING_PACKAGE,
                        appContext.packageName
                    )
                }

                val audioModeText = "Micrófono listo."

                val previousRaw =
                    _voiceState.value.recognizedText
                val previousInterpreted =
                    _voiceState.value.interpretedText

                _voiceState.value = _voiceState.value.copy(
                    isListening = true,
                    stage = DiagnosticStage.STAGE_1,
                    stageDescription =
                        "1/4 $audioModeText Diga: acción, vía y placa.",
                    recognizedText =
                        if (preserveOriginalText) {
                            previousRaw
                        } else {
                            ""
                        },
                    interpretedText =
                        if (preserveOriginalText) {
                            previousInterpreted
                        } else {
                            ""
                        },
                    rmsLevel = 0f,
                    errorMessage = null
                )

                speechStartTimestampMs = SystemClock.elapsedRealtime()
                speechRecognizer?.startListening(intent)

                // Límite de seguridad. Cerrar el pipe evita sesiones colgadas en
                // implementaciones que esperan EOF del EXTRA_AUDIO_SOURCE.
                if (enhancedSession != null) {
                    val timeout = Runnable {
                        if (enhancedAudioActive) {
                            noiseReducedAudioSource.finishInput()
                        }
                    }
                    captureTimeoutRunnable = timeout
                    mainHandler.postDelayed(timeout, ENHANCED_AUDIO_MAX_DURATION_MS)
                }
            } catch (e: Exception) {
                cleanupEnhancedAudio()
                _voiceState.value = _voiceState.value.copy(
                    isListening = false,
                    errorMessage = "Error al iniciar escucha: ${e.localizedMessage}"
                )
            }
        }
    }

    fun stopListening() {
        simulationJob?.cancel()
        speechStartTimestampMs = null
        mainHandler.post {
            if (enhancedAudioActive) {
                noiseReducedAudioSource.finishInput()
            }

            try {
                speechRecognizer?.stopListening()
            } catch (_: Exception) {
            }

            _voiceState.value = _voiceState.value.copy(isListening = false)
        }
    }

    fun simulateVoiceInput(phrase: String) {
        simulationJob?.cancel()
        simulationJob = scope.launch {
            _voiceState.value = VoiceState(
                isListening = true,
                stage = DiagnosticStage.STAGE_1,
                stageDescription = "1/4 Micrófono abierto (Canal activo)",
                errorMessage = null,
                retryCount = 0
            )
            delay(300)

            _voiceState.value = _voiceState.value.copy(
                stage = DiagnosticStage.STAGE_2,
                rmsLevel = 6.2f,
                stageDescription = "2/4 Audio detectado (Señal recibida)"
            )
            delay(350)

            _voiceState.value = _voiceState.value.copy(
                stage = DiagnosticStage.STAGE_3,
                rmsLevel = 8.5f,
                stageDescription = "3/4 Voz detectada: \"$phrase\""
            )
            delay(400)

            _voiceState.value = _voiceState.value.copy(
                isListening = false,
                stage = DiagnosticStage.STAGE_4,
                rmsLevel = 0f,
                stageDescription = "4/4 Texto reconocido con éxito",
                recognizedText = phrase,
                interpretedText = phrase
            )
            notifyListeners(phrase)
        }
    }

    fun destroy() {
        simulationJob?.cancel()
        speechStartTimestampMs = null
        mainHandler.post {
            cleanupEnhancedAudio()
            try {
                speechRecognizer?.destroy()
            } catch (_: Exception) {
            }
            speechRecognizer = null
        }
    }

    companion object {
        private const val KEY_ATTEMPTS = "attempts"
        private const val KEY_VALID_FIRST_PASS = "valid_first_pass"
        private const val KEY_INVALID_RESULTS = "invalid_results"
        private const val KEY_ALTERNATIVE_SELECTED = "alternative_selected"
        private const val KEY_MISSING_ACTION = "missing_action"
        private const val KEY_MISSING_VIA = "missing_via"
        private const val KEY_MISSING_PLATE = "missing_plate"
        private const val KEY_MANUAL_CORRECTIONS = "manual_corrections"
        private const val KEY_PLATE_CORRECTIONS = "plate_corrections"
        private const val KEY_VIA_CORRECTIONS = "via_corrections"
        private const val KEY_ACTION_CORRECTIONS = "action_corrections"
        private const val KEY_LATENCY_TOTAL_MS = "recognition_latency_total_ms"
        private const val KEY_LATENCY_COUNT = "recognition_latency_count"
        private const val ENHANCED_AUDIO_MAX_DURATION_MS = 9_500L

        @Volatile
        private var instance: AviSpeechManager? = null

        fun getInstance(context: Context): AviSpeechManager {
            return instance ?: synchronized(this) {
                instance ?: AviSpeechManager(context.applicationContext).also { instance = it }
            }
        }
    }
}
