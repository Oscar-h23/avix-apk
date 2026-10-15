package com.example.domain

import com.example.model.UsuarioDto

/**
 * Identidad propietaria de una incidencia local.
 *
 * El operador y la plaza siempre provienen de la sesión autenticada devuelta
 * por SIGO. El servidor forma parte de la identidad para impedir que una cola
 * creada contra un entorno termine enviándose a otro.
 */
data class IncidentOwner(
    val operatorId: String,
    val plazaId: Long,
    val server: String
) {
    companion object {
        fun from(
            user: UsuarioDto?,
            server: String
        ): IncidentOwner? {
            val plaza = user?.plazaId
                ?.takeIf { it > 0L }
                ?: return null

            val operator = user.id
                ?.takeIf { it > 0L }
                ?.let { "id:$it" }
                ?: user.codigo
                    ?.takeIf { it > 0 }
                    ?.let { "codigo:$it" }
                ?: return null

            val normalizedServer =
                server.trim().trimEnd('/') + "/"

            return IncidentOwner(
                operatorId = operator,
                plazaId = plaza,
                server = normalizedServer
            )
        }
    }
}
