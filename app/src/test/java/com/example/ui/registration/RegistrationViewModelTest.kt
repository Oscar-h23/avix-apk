package com.example.ui.registration

import androidx.lifecycle.SavedStateHandle
import com.example.domain.IncidentOwner
import com.example.model.ParsedCommand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class RegistrationViewModelTest {

    @Test
    fun `draft keeps operation id time fields and owner`() {
        val handle = SavedStateHandle()
        val first =
            RegistrationViewModel(handle)

        val owner =
            IncidentOwner(
                operatorId = "id:10",
                plazaId = 3L,
                server =
                    "https://example.test/"
            )

        first.newDraft(owner)

        val id =
            first.operationId
        val time =
            first.eventTime

        first.updateDraft(
            ParsedCommand(
                placa = "X1Z505",
                via = 101,
                accion = "FUGA",
                textoOriginal = "dictado",
                valido = true
            )
        )

        // Simula recreación con el mismo estado guardado.
        val restored =
            RegistrationViewModel(handle)

        assertEquals(
            id,
            restored.operationId
        )
        assertEquals(
            time,
            restored.eventTime
        )
        assertEquals(
            "X1Z505",
            restored
                .restoreCommand()
                .placa
        )
        assertEquals(
            owner,
            restored.expectedOwner()
        )
        assertFalse(
            restored.submitted.value
        )
    }
}
