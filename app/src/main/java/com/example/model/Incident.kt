package com.example.model

import com.example.util.AviDateUtils
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import java.util.UUID

enum class EstadoSincronizacion(val label: String) {
    PENDIENTE("Guardado en dispositivo"),
    SINCRONIZANDO("Enviando"),
    SINCRONIZADO("Confirmado por SIGO"),
    REQUIERE_REVISION("Requiere revisión")
}

data class Incident(
    val id: String = UUID.randomUUID().toString(),
    val placa: String,
    val via: Int?,
    val accion: String = "FUGA", // FUGA o DERIVADO
    val plazaId: String = "PLAZA-01",
    val fechaHoraEvento: String = AviDateUtils.nowLimaIso(),
    val textoReconocido: String = "",
    val estadoSincronizacion: EstadoSincronizacion = EstadoSincronizacion.PENDIENTE,
    val intentosSincronizacion: Int = 0,
    val ultimoError: String? = null,
    val fechaHoraRecepcion: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val operatorId: String? = null,
    val ownerPlazaId: Long? = null,
    val serverOrigin: String? = null
)

data class ParsedCommand(
    val placa: String = "",
    val via: Int? = null,
    val accion: String = "FUGA",
    val textoOriginal: String = "",
    val valido: Boolean = false,
    val errores: List<String> = emptyList(),
    val advertencias: List<String> = emptyList()
)

// DTOs para la integración con SIGO Spring Boot API
@JsonClass(generateAdapter = true)
data class RegistroSigoRequest(
    val id: String,
    val placa: String,
    val via: Int?,
    val accion: String,
    val fechaHoraEvento: String,
    val textoReconocido: String
)

@JsonClass(generateAdapter = true)
data class RegistroSigoUpdateRequest(
    val placa: String,
    val via: Int,
    val accion: String,
    val fechaHoraEvento: String,
    val textoReconocido: String
)

@JsonClass(generateAdapter = true)
data class RegistroSigoResponse(
    val id: String,
    val placa: String? = null,
    val via: Int? = null,
    val accion: String? = null,
    val plazaId: String? = null,
    val fechaHoraEvento: String? = null,
    val fechaHoraRecepcion: String? = null,
    val estadoSincronizacion: String = "SINCRONIZADO",
    val mensaje: String? = null
)

@JsonClass(generateAdapter = true)
data class SigoStatusResponse(
    val status: String = "UP",
    val modulo: String = "SIGO AVI",
    val baseDatos: String = "sigo test",
    val timestamp: String? = null
)

enum class DiagnosticStage(val step: Int, val label: String) {
    IDLE(0, "En espera"),
    STAGE_1(1, "1/4 Micrófono abierto"),
    STAGE_2(2, "2/4 Audio detectado"),
    STAGE_3(3, "3/4 Voz detectada"),
    STAGE_4(4, "4/4 Texto reconocido")
}

data class VoiceState(
    val isListening: Boolean = false,
    val stage: DiagnosticStage = DiagnosticStage.IDLE,
    val stageDescription: String = "Presione DICTAR REGISTRO o use Registro Manual",
    // Primera hipótesis cruda devuelta por Android. No se corrige ni reconstruye.
    val recognizedText: String = "",
    // Resultado interno después de ranking, contexto y fusión de alternativas.
    val interpretedText: String = "",
    val rmsLevel: Float = 0f,
    val errorMessage: String? = null,
    val platePositionConfidence: List<Float> = emptyList(),
    val retryCount: Int = 0
)
