package com.example.domain

import com.example.model.EstadoSincronizacion
import com.example.model.Incident
import java.text.SimpleDateFormat
import java.util.Locale

enum class HistoryStatusFilter(
    val label: String
) {
    TODOS("Todos"),
    FUGAS("Fugas"),
    DERIVADOS("Derivados"),
    LIBERADOS("Liberados"),
    PENDIENTES("Pendientes"),
    RECHAZADOS("Requieren revisión")
}

data class HistoryFilterResult(
    val items: List<Incident>,
    val validationMessage: String? = null
)

data class HistoryPage(
    val items: List<Incident>,
    val currentPage: Int,
    val totalPages: Int,
    val totalItems: Int,
    val pageSize: Int
) {
    val fromItem: Int
        get() =
            if (totalItems == 0) {
                0
            } else {
                ((currentPage - 1) * pageSize) + 1
            }

    val toItem: Int
        get() =
            if (totalItems == 0) {
                0
            } else {
                minOf(
                    currentPage * pageSize,
                    totalItems
                )
            }
}

object HistoryFilters {

    const val HISTORY_PAGE_SIZE = 12

    fun paginate(
        incidents: List<Incident>,
        page: Int,
        pageSize: Int = HISTORY_PAGE_SIZE
    ): HistoryPage {
        require(pageSize > 0) {
            "El tamaño de página debe ser mayor que cero."
        }

        val totalItems =
            incidents.size

        val totalPages =
            maxOf(
                1,
                (totalItems + pageSize - 1) /
                    pageSize
            )

        val safePage =
            page.coerceIn(
                1,
                totalPages
            )

        val startIndex =
            (safePage - 1) * pageSize

        val pageItems =
            if (
                totalItems == 0 ||
                startIndex >= totalItems
            ) {
                emptyList()
            } else {
                incidents.subList(
                    startIndex,
                    minOf(
                        startIndex + pageSize,
                        totalItems
                    )
                )
            }

        return HistoryPage(
            items = pageItems,
            currentPage = safePage,
            totalPages = totalPages,
            totalItems = totalItems,
            pageSize = pageSize
        )
    }

    fun apply(
        incidents: List<Incident>,
        status: HistoryStatusFilter,
        plateQuery: String = "",
        fromDate: String = "",
        toDate: String = ""
    ): HistoryFilterResult {
        val from =
            parseDateOrNull(
                fromDate
            )
        val to =
            parseDateOrNull(
                toDate
            )

        if (
            fromDate.isNotBlank() &&
            from == null
        ) {
            return HistoryFilterResult(
                items = emptyList(),
                validationMessage =
                    "Fecha inicial inválida. Usa AAAA-MM-DD."
            )
        }

        if (
            toDate.isNotBlank() &&
            to == null
        ) {
            return HistoryFilterResult(
                items = emptyList(),
                validationMessage =
                    "Fecha final inválida. Usa AAAA-MM-DD."
            )
        }

        if (
            from != null &&
            to != null &&
            from > to
        ) {
            return HistoryFilterResult(
                items = emptyList(),
                validationMessage =
                    "La fecha inicial no puede ser posterior a la final."
            )
        }

        val plate =
            plateQuery
                .uppercase(Locale.ROOT)
                .replace("-", "")
                .replace(" ", "")
                .trim()

        val filtered =
            incidents.filter { incident ->
                val statusMatches =
                    when (status) {
                        HistoryStatusFilter.TODOS ->
                            true

                        HistoryStatusFilter.FUGAS ->
                            incident.accion.equals(
                                "FUGA",
                                ignoreCase = true
                            )

                        HistoryStatusFilter.DERIVADOS ->
                            incident.accion.equals(
                                "DERIVADO",
                                ignoreCase = true
                            )

                        HistoryStatusFilter.LIBERADOS ->
                            incident.accion.equals(
                                "LIBERADO",
                                ignoreCase = true
                            )

                        HistoryStatusFilter.PENDIENTES ->
                            incident.estadoSincronizacion in
                                setOf(
                                    EstadoSincronizacion.PENDIENTE,
                                    EstadoSincronizacion.SINCRONIZANDO
                                )

                        HistoryStatusFilter.RECHAZADOS ->
                            incident.estadoSincronizacion ==
                                EstadoSincronizacion.REQUIERE_REVISION
                    }

                val plateMatches =
                    plate.isBlank() ||
                        incident.placa
                            .uppercase(Locale.ROOT)
                            .contains(plate)

                val eventDate =
                    incident.fechaHoraEvento
                        .take(10)
                        .takeIf {
                            it.length == 10
                        }
                        ?.let {
                            parseDateOrNull(it)
                        }

                val fromMatches =
                    from == null ||
                        (
                            eventDate != null &&
                                eventDate >= from
                            )

                val toMatches =
                    to == null ||
                        (
                            eventDate != null &&
                                eventDate <= to
                            )

                statusMatches &&
                    plateMatches &&
                    fromMatches &&
                    toMatches
            }

        return HistoryFilterResult(
            items = filtered
        )
    }

    private fun parseDateOrNull(
        text: String
    ): Long? {
        if (
            text.isBlank()
        ) {
            return null
        }

        if (
            !text.matches(
                Regex(
                    "\\d{4}-\\d{2}-\\d{2}"
                )
            )
        ) {
            return null
        }

        return try {
            val formatter =
                SimpleDateFormat(
                    "yyyy-MM-dd",
                    Locale.US
                ).apply {
                    isLenient = false
                }

            formatter
                .parse(text)
                ?.time
        } catch (_: Exception) {
            null
        }
    }
}
