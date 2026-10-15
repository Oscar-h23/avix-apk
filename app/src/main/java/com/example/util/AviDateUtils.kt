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

    fun nowLimaIso(): String {
        return synchronized(isoFormatter) {
            isoFormatter.format(Date())
        }
    }

    fun timestampToLimaIso(timestamp: Long): String {
        return synchronized(isoFormatter) {
            isoFormatter.format(Date(timestamp))
        }
    }

    fun nowLimaDisplay(): String {
        return synchronized(displayFormatter) {
            displayFormatter.format(Date())
        }
    }

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
