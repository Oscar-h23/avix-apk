package com.example.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object AviDateUtils {
    private val TIME_ZONE_LIMA = TimeZone.getTimeZone("America/Lima")

    private val isoFormatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US).apply {
        timeZone = TIME_ZONE_LIMA
    }

    private val displayFormatter = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.US).apply {
        timeZone = TIME_ZONE_LIMA
    }

    private val timeOnlyFormatter = SimpleDateFormat("HH:mm:ss", Locale.US).apply {
        timeZone = TIME_ZONE_LIMA
    }

    /**
     * Devuelve la fecha/hora actual en zona horaria America/Lima en formato ISO-8601 con desplazamiento
     * Ej: "2026-10-06T13:56:14-05:00"
     */
    fun nowLimaIso(): String {
        return synchronized(isoFormatter) {
            isoFormatter.format(Date())
        }
    }

    /**
     * Formatea un timestamp Unix (milisegundos) en formato ISO-8601 para America/Lima
     */
    fun timestampToLimaIso(timestamp: Long): String {
        return synchronized(isoFormatter) {
            isoFormatter.format(Date(timestamp))
        }
    }

    /**
     * Devuelve la fecha/hora actual legible para el operador en America/Lima
     * Ej: "06/10/2026 13:56:14"
     */
    fun nowLimaDisplay(): String {
        return synchronized(displayFormatter) {
            displayFormatter.format(Date())
        }
    }

    /**
     * Convierte una cadena ISO-8601 a formato legible "dd/MM/yyyy HH:mm:ss"
     */
    fun formatIsoToDisplay(isoString: String): String {
        return try {
            val date = synchronized(isoFormatter) {
                isoFormatter.parse(isoString)
            }
            if (date != null) {
                synchronized(displayFormatter) {
                    displayFormatter.format(date)
                }
            } else {
                isoString
            }
        } catch (_: Exception) {
            isoString
        }
    }

    /**
     * Extrae solo la hora "HH:mm:ss"
     */
    fun formatIsoToTimeOnly(isoString: String): String {
        return try {
            val date = synchronized(isoFormatter) {
                isoFormatter.parse(isoString)
            }
            if (date != null) {
                synchronized(timeOnlyFormatter) {
                    timeOnlyFormatter.format(date)
                }
            } else {
                isoString
            }
        } catch (_: Exception) {
            isoString
        }
    }
}
