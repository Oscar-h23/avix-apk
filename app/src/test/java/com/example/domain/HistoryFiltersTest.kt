package com.example.domain

import com.example.model.EstadoSincronizacion
import com.example.model.Incident
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoryFiltersTest {

    private val incidents =
        listOf(
            Incident(
                id = "1",
                placa = "X1Z505",
                via = 101,
                accion = "FUGA",
                fechaHoraEvento =
                    "2026-10-08T20:00:00-05:00",
                estadoSincronizacion =
                    EstadoSincronizacion.SINCRONIZADO
            ),
            Incident(
                id = "2",
                placa = "Q0O090",
                via = 102,
                accion = "DERIVADO",
                fechaHoraEvento =
                    "2026-10-09T08:00:00-05:00",
                estadoSincronizacion =
                    EstadoSincronizacion.PENDIENTE
            ),
            Incident(
                id = "3",
                placa = "ABC123",
                via = 103,
                accion = "FUGA",
                fechaHoraEvento =
                    "2026-10-10T09:00:00-05:00",
                estadoSincronizacion =
                    EstadoSincronizacion.REQUIERE_REVISION
            )
        )

    @Test
    fun `search plate and date range`() {
        val result =
            HistoryFilters.apply(
                incidents = incidents,
                status =
                    HistoryStatusFilter.TODOS,
                plateQuery = "q0o",
                fromDate = "2026-10-09",
                toDate = "2026-10-09"
            )

        assertEquals(
            listOf("2"),
            result.items.map { it.id }
        )
        assertEquals(
            null,
            result.validationMessage
        )
    }

    @Test
    fun `rejected filter is separate from pending`() {
        val pending =
            HistoryFilters.apply(
                incidents,
                HistoryStatusFilter.PENDIENTES
            )
        val rejected =
            HistoryFilters.apply(
                incidents,
                HistoryStatusFilter.RECHAZADOS
            )

        assertEquals(
            listOf("2"),
            pending.items.map { it.id }
        )
        assertEquals(
            listOf("3"),
            rejected.items.map { it.id }
        )
    }

    @Test
    fun `history pagination returns twelve items per page`() {
        val manyIncidents =
            (1..25).map { index ->
                Incident(
                    id = index.toString(),
                    placa = "ABC123",
                    via = 101,
                    accion = "FUGA",
                    fechaHoraEvento =
                        "2026-10-09T08:00:00-05:00"
                )
            }

        val first =
            HistoryFilters.paginate(
                incidents = manyIncidents,
                page = 1
            )
        val second =
            HistoryFilters.paginate(
                incidents = manyIncidents,
                page = 2
            )
        val third =
            HistoryFilters.paginate(
                incidents = manyIncidents,
                page = 3
            )

        assertEquals(12, first.items.size)
        assertEquals(12, second.items.size)
        assertEquals(1, third.items.size)
        assertEquals(3, third.totalPages)
        assertEquals(25, third.totalItems)
        assertEquals(25, third.fromItem)
        assertEquals(25, third.toItem)
    }

    @Test
    fun `history pagination clamps page to valid range`() {
        val fewIncidents =
            (1..5).map { index ->
                Incident(
                    id = index.toString(),
                    placa = "ABC123",
                    via = 101,
                    accion = "FUGA",
                    fechaHoraEvento =
                        "2026-10-09T08:00:00-05:00"
                )
            }

        val page =
            HistoryFilters.paginate(
                incidents = fewIncidents,
                page = 99
            )

        assertEquals(1, page.currentPage)
        assertEquals(1, page.totalPages)
        assertEquals(5, page.items.size)
    }

    @Test
    fun `invalid date is reported instead of silently filtering`() {
        val result =
            HistoryFilters.apply(
                incidents = incidents,
                status =
                    HistoryStatusFilter.TODOS,
                fromDate = "2026-99-99"
            )

        assertTrue(result.items.isEmpty())
        assertNotNull(
            result.validationMessage
        )
    }
}
