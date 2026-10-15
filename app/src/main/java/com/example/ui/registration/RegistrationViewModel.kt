package com.example.ui.registration

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.IncidentRepository
import com.example.domain.IncidentOwner
import com.example.model.Incident
import com.example.model.ParsedCommand
import com.example.util.AviDateUtils
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Borrador persistente de una operación de registro.
 *
 * El mismo UUID y fecha se conservan durante rotaciones/recreaciones. submit()
 * ignora una segunda pulsación mientras la primera escritura local sigue activa.
 */
class RegistrationViewModel(
    private val state: SavedStateHandle
) : ViewModel() {

    private val _saving =
        MutableStateFlow(false)
    val saving: StateFlow<Boolean> =
        _saving.asStateFlow()

    private val _submitted =
        MutableStateFlow(
            state[KEY_SUBMITTED] ?: false
        )
    val submitted: StateFlow<Boolean> =
        _submitted.asStateFlow()

    val operationId: String
        get() = state.get<String>(
            KEY_OPERATION_ID
        ) ?: newOperationId().also {
            state[KEY_OPERATION_ID] = it
        }

    val eventTime: String
        get() = state.get<String>(
            KEY_EVENT_TIME
        ) ?: AviDateUtils.nowLimaIso().also {
            state[KEY_EVENT_TIME] = it
        }

    fun newDraft(
        owner: IncidentOwner? = null
    ) {
        state[KEY_OPERATION_ID] =
            newOperationId()
        state[KEY_EVENT_TIME] =
            AviDateUtils.nowLimaIso()

        state[KEY_PLATE] = ""
        state.remove<Int>(KEY_VIA)
        state[KEY_ACTION] = "FUGA"
        state[KEY_ORIGINAL_TEXT] = ""
        state[KEY_SUBMITTED] = false

        _submitted.value = false
        _saving.value = false

        bindOwner(owner)
    }

    fun bindOwner(
        owner: IncidentOwner?
    ) {
        if (owner == null) {
            state.remove<String>(KEY_OWNER_OPERATOR)
            state.remove<Long>(KEY_OWNER_PLAZA)
            state.remove<String>(KEY_OWNER_SERVER)
            return
        }

        state[KEY_OWNER_OPERATOR] =
            owner.operatorId
        state[KEY_OWNER_PLAZA] =
            owner.plazaId
        state[KEY_OWNER_SERVER] =
            owner.server
    }

    fun expectedOwner():
        IncidentOwner? {
        val operator =
            state.get<String>(
                KEY_OWNER_OPERATOR
            ) ?: return null

        val plaza =
            state.get<Long>(
                KEY_OWNER_PLAZA
            ) ?: return null

        val server =
            state.get<String>(
                KEY_OWNER_SERVER
            ) ?: return null

        return IncidentOwner(
            operatorId = operator,
            plazaId = plaza,
            server = server
        )
    }

    fun updateDraft(
        command: ParsedCommand
    ) {
        state[KEY_PLATE] =
            command.placa

        state[KEY_VIA] =
            command.via

        state[KEY_ACTION] =
            command.accion

        state[KEY_ORIGINAL_TEXT] =
            command.textoOriginal
    }

    fun restoreCommand():
        ParsedCommand {
        return ParsedCommand(
            placa =
                state[KEY_PLATE] ?: "",
            via =
                state[KEY_VIA],
            accion =
                state[KEY_ACTION] ?: "FUGA",
            textoOriginal =
                state[KEY_ORIGINAL_TEXT] ?: "",
            valido = false
        )
    }

    fun setEventTime(
        value: String
    ) {
        state[KEY_EVENT_TIME] = value
    }

    fun submit(
        repository: IncidentRepository,
        command: ParsedCommand,
        expectedOwner: IncidentOwner? =
            expectedOwner()
                ?: repository.currentOwner(),
        onResult: (Result<Incident>) -> Unit
    ) {
        if (
            _saving.value ||
            _submitted.value
        ) {
            return
        }

        val id = operationId
        val time = eventTime

        _saving.value = true
        updateDraft(command)

        viewModelScope.launch {
            val result =
                repository.registrarIncidencia(
                    placa = command.placa,
                    via = command.via,
                    accion = command.accion,
                    fechaHoraEvento = time,
                    textoReconocido =
                        command.textoOriginal,
                    operationId = id,
                    expectedOwner =
                        expectedOwner
                )

            _saving.value = false

            // Si el usuario creó otro borrador mientras terminaba esta
            // operación, la respuesta vieja no puede cambiar la nueva.
            if (
                operationId != id
            ) {
                return@launch
            }

            if (
                result.isSuccess
            ) {
                state[KEY_SUBMITTED] = true
                _submitted.value = true
            }

            onResult(result)
        }
    }

    private fun newOperationId():
        String =
        UUID.randomUUID().toString()

    companion object {
        private const val KEY_OPERATION_ID =
            "registration_operation_id"
        private const val KEY_EVENT_TIME =
            "registration_event_time"
        private const val KEY_PLATE =
            "registration_plate"
        private const val KEY_VIA =
            "registration_via"
        private const val KEY_ACTION =
            "registration_action"
        private const val KEY_ORIGINAL_TEXT =
            "registration_original_text"
        private const val KEY_SUBMITTED =
            "registration_submitted"

        private const val KEY_OWNER_OPERATOR =
            "registration_owner_operator"
        private const val KEY_OWNER_PLAZA =
            "registration_owner_plaza"
        private const val KEY_OWNER_SERVER =
            "registration_owner_server"
    }
}
