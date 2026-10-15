package com.example.model

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class LoginRequest(
    val codigo: Int
)

@JsonClass(generateAdapter = true)
data class LoginResponse(
    val token: String,
    val tipo: String = "Bearer",
    val expiresIn: Long? = null,
    val usuario: UsuarioDto? = null
)

@JsonClass(generateAdapter = true)
data class UsuarioDto(
    val id: Long? = null,
    val codigo: Int? = null,
    val nombre: String? = null,
    val rol: String? = null,
    val plazaId: Long? = null,
    val plaza: String? = null
)


@JsonClass(generateAdapter = true)
data class ViaDto(
    val id: Long? = null,
    val plazaId: Long? = null,
    val numero: Int,
    val nombre: String? = null,
    val activa: Boolean = true,
    val orden: Int? = null
)
