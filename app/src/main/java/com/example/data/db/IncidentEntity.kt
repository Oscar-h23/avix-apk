package com.example.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.model.EstadoSincronizacion
import com.example.model.Incident

@Entity(
    tableName = "incidencias",
    indices = [
        Index(
            value = [
                "operatorId",
                "ownerPlazaId",
                "serverOrigin",
                "estadoSincronizacion",
                "timestamp"
            ],
            name = "index_incident_owner_sync"
        )
    ]
)
data class IncidentEntity(
    @PrimaryKey val id: String,
    val placa: String,
    val via: Int?,
    val accion: String,
    val plazaId: String,
    val fechaHoraEvento: String,
    val textoReconocido: String,
    val estadoSincronizacion: String,
    val intentosSincronizacion: Int,
    val ultimoError: String?,
    val fechaHoraRecepcion: String?,
    val timestamp: Long,
    val operatorId: String? = null,
    val ownerPlazaId: Long? = null,
    val serverOrigin: String? = null
) {
    fun toDomain(): Incident {
        return Incident(
            id = id,
            placa = placa,
            via = via,
            accion = accion,
            plazaId = plazaId,
            fechaHoraEvento = fechaHoraEvento,
            textoReconocido = textoReconocido,
            estadoSincronizacion = try {
                EstadoSincronizacion.valueOf(
                    estadoSincronizacion
                )
            } catch (_: Exception) {
                EstadoSincronizacion.PENDIENTE
            },
            intentosSincronizacion = intentosSincronizacion,
            ultimoError = ultimoError,
            fechaHoraRecepcion = fechaHoraRecepcion,
            timestamp = timestamp,
            operatorId = operatorId,
            ownerPlazaId = ownerPlazaId,
            serverOrigin = serverOrigin
        )
    }

    companion object {
        fun fromDomain(
            incident: Incident
        ): IncidentEntity {
            return IncidentEntity(
                id = incident.id,
                placa = incident.placa,
                via = incident.via,
                accion = incident.accion,
                plazaId = incident.plazaId,
                fechaHoraEvento = incident.fechaHoraEvento,
                textoReconocido = incident.textoReconocido,
                estadoSincronizacion =
                    incident.estadoSincronizacion.name,
                intentosSincronizacion =
                    incident.intentosSincronizacion,
                ultimoError = incident.ultimoError,
                fechaHoraRecepcion =
                    incident.fechaHoraRecepcion,
                timestamp = incident.timestamp,
                operatorId = incident.operatorId,
                ownerPlazaId = incident.ownerPlazaId,
                serverOrigin = incident.serverOrigin
            )
        }
    }
}
