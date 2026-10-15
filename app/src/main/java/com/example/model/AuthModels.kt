package com.example.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class LoginRequest(val codigo: String)

@JsonClass(generateAdapter = true)
data class LoginResponse(
    val token: String,
    val tipo: String = "Bearer",
    val expiresIn: Long? = null,
    val usuario: UsuarioDto? = null,
    val expiraEn: String? = null
)

@JsonClass(generateAdapter = true)
data class UsuarioDto(
    @Json(name = "legacy_id") val id: Long? = null,
    @Json(name = "legacy_codigo") val codigo: Int? = null,
    @Json(name = "nombres") val nombre: String? = null,
    val rol: String? = null,
    val plazaId: Long? = null,
    val plaza: String? = null,
    @Json(name = "id") val uuid: String? = null,
    @Json(name = "codigo") val codigoTexto: String? = null
)

@JsonClass(generateAdapter = true)
data class PlazaDto(val id: Long, val codigo: String, val activa: Boolean = true)

@JsonClass(generateAdapter = true)
data class ViaDto(
    val id: Long? = null,
    @Json(name = "plaza_id") val plazaId: Long? = null,
    val numero: Int,
    val nombre: String? = null,
    val activa: Boolean = true,
    val orden: Int? = null
)
