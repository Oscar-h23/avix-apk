package com.example.domain

import com.example.model.UsuarioDto
import org.junit.Assert.*
import org.junit.Test

class AvixIdentityTest {
    @Test fun usesBackendUuidAndAssignedPlazaAsLocalOwner() {
        val user = UsuarioDto(uuid = "64f579a3-2937-440a-a315-bc9c2a9baf31",
            codigoTexto = "AG001", nombre = "Agente", rol = "AGENTE", plazaId = 3, plaza = "P3")
        val owner = IncidentOwner.from(user, "https://api.avix.example/")
        assertEquals("uuid:64f579a3-2937-440a-a315-bc9c2a9baf31", owner?.operatorId)
        assertEquals(3L, owner?.plazaId)
        assertEquals("https://api.avix.example/", owner?.server)
    }

    @Test fun refusesMissingPlaza() {
        assertNull(IncidentOwner.from(UsuarioDto(uuid = "uuid", codigoTexto = "AG001"),
            "https://api.avix.example/"))
    }
}
