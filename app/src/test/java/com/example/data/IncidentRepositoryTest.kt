package com.example.data

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import com.example.api.SigoApiService
import com.example.core.security.SessionState
import com.example.core.security.SessionStore
import com.example.data.db.IncidentDao
import com.example.data.db.IncidentEntity
import com.example.model.LoginRequest
import com.example.model.LoginResponse
import com.example.model.ParsedCommand
import com.example.model.RegistroSigoRequest
import com.example.model.RegistroSigoResponse
import com.example.model.RegistroSigoUpdateRequest
import com.example.model.SigoStatusResponse
import com.example.model.UsuarioDto
import com.example.model.ViaDto
import com.example.ui.registration.RegistrationViewModel
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import retrofit2.Response

@RunWith(RobolectricTestRunner::class)
@Config(
    sdk = [34],
    application = android.app.Application::class
)
class IncidentRepositoryTest {

    private lateinit var context: Context
    private lateinit var dao: FakeIncidentDao
    private lateinit var session: FakeSessionStore
    private lateinit var api: StubSigoApiService
    private lateinit var repository: IncidentRepository
    private lateinit var enqueueCount: AtomicInteger

    @Before
    fun setUp() {
        context =
            ApplicationProvider
                .getApplicationContext()

        context.getSharedPreferences(
            "avi_sigo_prefs",
            Context.MODE_PRIVATE
        ).edit().clear().commit()

        dao = FakeIncidentDao()
        session = FakeSessionStore(
            user = user(
                id = 1L,
                code = 2396,
                plaza = 3L
            )
        )
        api = StubSigoApiService()
        enqueueCount = AtomicInteger(0)

        repository =
            IncidentRepository(
                context = context,
                dao = dao,
                sessionManager = session,
                apiFactory = { _, _ -> api },
                enqueueSync = {
                    enqueueCount.incrementAndGet()
                },
                startBackground = false
            )

        // Inyectar lista de vías del backend antes de crear registros locales.
        runBlocking { repository.cargarViasPermitidas() }
    }

    @Test
    fun `empty server via list cannot authorize any road`() = runBlocking {
        api.vias = emptyList()
        repository.cargarViasPermitidas()

        assertFalse(repository.isViaPermitida(101))
        assertFalse(repository.isViaPermitida(999))

        val result = repository.registrarIncidencia(
            placa = "ABC123",
            via = 101,
            accion = "FUGA",
            fechaHoraEvento = "2026-10-12T18:00:00-05:00",
            textoReconocido = "prueba"
        )
        assertTrue(result.isFailure)
    }

    @Test
    fun `editing lists only enabled vias of current plaza in server order`() =
        runBlocking {
            api.viaResults = listOf(
                ViaDto(plazaId = 3L, numero = 105, nombre = "Central", orden = 2),
                ViaDto(plazaId = 3L, numero = 101, nombre = "Express", orden = 1),
                ViaDto(plazaId = 3L, numero = 107, activa = false),
                ViaDto(plazaId = 99L, numero = 108),
                ViaDto(plazaId = 3L, numero = 0),
                ViaDto(plazaId = 3L, numero = 101, orden = 3)
            )

            val result = repository.consultarViasHabilitadasParaEdicion()
            assertTrue(result.isSuccess)
            assertEquals(listOf(101, 105), result.getOrThrow().map { it.numero })
            assertEquals("Express", result.getOrThrow().first().nombre)
            assertTrue(repository.isViaPermitida(105))
            assertFalse(repository.isViaPermitida(107))
            assertFalse(repository.isViaPermitida(108))
        }

    @Test
    fun `editing fails closed when server unavailable even with cached vias`() =
        runBlocking {
            assertTrue(repository.isViaPermitida(101))
            api.viaCode = 503

            val result = repository.consultarViasHabilitadasParaEdicion()

            assertTrue(result.isFailure)
        }

    @Test
    fun `editing treats empty enabled server list as no available vias`() =
        runBlocking {
            api.viaResults = emptyList()

            val result = repository.consultarViasHabilitadasParaEdicion()

            assertTrue(result.isSuccess)
            assertTrue(result.getOrThrow().isEmpty())
            assertFalse(repository.isViaPermitida(101))
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `shared submit gate ignores a double tap while saving`() =
        runTest {
            val testDispatcher =
                StandardTestDispatcher(
                    testScheduler
                )

            Dispatchers.setMain(
                testDispatcher
            )

            val gatedRepository =
                IncidentRepository(
                    context = context,
                    dao = dao,
                    sessionManager = session,
                    apiFactory = { _, _ -> api },
                    enqueueSync = {
                        enqueueCount.incrementAndGet()
                    },
                    ioDispatcher = testDispatcher,
                    startBackground = false
                )
            gatedRepository.cargarViasPermitidas()

            try {
                val draft =
                    RegistrationViewModel(
                        SavedStateHandle()
                    )

                draft.newDraft(
                    gatedRepository.currentOwner()
                )

                val command =
                    ParsedCommand(
                        placa = "ABC123",
                        via = 101,
                        accion = "FUGA",
                        textoOriginal = "dictado",
                        valido = true
                    )

                var callbacks = 0

                draft.submit(
                    repository = gatedRepository,
                    command = command,
                    expectedOwner =
                        gatedRepository.currentOwner()
                ) {
                    callbacks++
                }

                // La segunda pulsación ocurre antes de que termine la primera.
                draft.submit(
                    repository = gatedRepository,
                    command = command,
                    expectedOwner =
                        gatedRepository.currentOwner()
                ) {
                    callbacks++
                }

                advanceUntilIdle()

                assertEquals(1, callbacks)
                assertEquals(1, dao.size())
                assertTrue(draft.submitted.value)
                assertFalse(draft.saving.value)
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test
    fun `register saves locally without waiting for HTTP`() =
        runBlocking {
            val result =
                repository.registrarIncidencia(
                    placa = "ABC123",
                    via = 101,
                    accion = "FUGA",
                    fechaHoraEvento =
                        "2026-10-09T01:00:00-05:00",
                    textoReconocido =
                        "fuga via 101 placa abc 123",
                    operationId = "op-1"
                )

            assertTrue(result.isSuccess)
            assertEquals(
                "op-1",
                result.getOrThrow().id
            )
            assertEquals(
                1,
                dao.size()
            )
            assertEquals(
                1,
                enqueueCount.get()
            )
            assertEquals(
                0,
                api.postCalls.get()
            )
        }

    @Test
    fun `same operation is idempotent and cannot overwrite payload`() =
        runBlocking {
            val first =
                repository.registrarIncidencia(
                    placa = "ABC123",
                    via = 101,
                    accion = "FUGA",
                    fechaHoraEvento =
                        "2026-10-09T01:00:00-05:00",
                    textoReconocido = "original",
                    operationId = "same-id"
                )

            val repeated =
                repository.registrarIncidencia(
                    placa = "ABC123",
                    via = 101,
                    accion = "FUGA",
                    fechaHoraEvento =
                        "2026-10-09T01:00:00-05:00",
                    textoReconocido = "original",
                    operationId = "same-id"
                )

            val divergent =
                repository.registrarIncidencia(
                    placa = "ABD123",
                    via = 101,
                    accion = "FUGA",
                    fechaHoraEvento =
                        "2026-10-09T01:00:00-05:00",
                    textoReconocido = "original",
                    operationId = "same-id"
                )

            assertTrue(first.isSuccess)
            assertTrue(repeated.isSuccess)
            assertTrue(divergent.isFailure)
            assertEquals(1, dao.size())
        }

    @Test
    fun `changing operator hides and never sends another owners rows`() =
        runBlocking {
            repository.registrarIncidencia(
                placa = "ABC123",
                via = 101,
                accion = "FUGA",
                fechaHoraEvento =
                    "2026-10-09T01:00:00-05:00",
                textoReconocido = "owner one",
                operationId = "owner-1"
            )

            session.setUser(
                user(
                    id = 2L,
                    code = 2450,
                    plaza = 3L
                )
            )

            assertTrue(
                repository.incidentsFlow
                    .first()
                    .isEmpty()
            )

            repository.registrarIncidencia(
                placa = "XYZ987",
                via = 101,
                accion = "DERIVADO",
                fechaHoraEvento =
                    "2026-10-09T01:05:00-05:00",
                textoReconocido = "owner two",
                operationId = "owner-2"
            )

            val visibleToTwo =
                repository.incidentsFlow.first()

            assertEquals(
                listOf("owner-2"),
                visibleToTwo.map { it.id }
            )

            api.postCode = 201
            repository
                .sincronizarPendientesDesdeWorker()

            assertEquals(
                listOf("owner-2"),
                api.receivedIds
            )

            session.setUser(
                user(
                    id = 1L,
                    code = 2396,
                    plaza = 3L
                )
            )

            assertEquals(
                listOf("owner-1"),
                repository.incidentsFlow
                    .first()
                    .map { it.id }
            )
        }

    @Test
    fun `HTTP 400 becomes review and is not retried`() =
        runBlocking {
            repository.registrarIncidencia(
                placa = "ABC123",
                via = 101,
                accion = "FUGA",
                fechaHoraEvento =
                    "2026-10-09T01:00:00-05:00",
                textoReconocido = "test",
                operationId = "bad-request"
            )

            api.postCode = 400

            val first =
                repository
                    .sincronizarPendientesDesdeWorker()

            assertEquals(
                SyncOutcome.COMPLETE,
                first
            )
            assertEquals(
                "REQUIERE_REVISION",
                dao.getById(
                    "bad-request"
                )?.estadoSincronizacion
            )

            repository
                .sincronizarPendientesDesdeWorker()

            assertEquals(
                1,
                api.postCalls.get()
            )
        }

    @Test
    fun `HTTP 503 stays pending and requests retry with same UUID`() =
        runBlocking {
            repository.registrarIncidencia(
                placa = "ABC123",
                via = 101,
                accion = "FUGA",
                fechaHoraEvento =
                    "2026-10-09T01:00:00-05:00",
                textoReconocido = "test",
                operationId = "temporary"
            )

            api.postCode = 503

            val first =
                repository
                    .sincronizarPendientesDesdeWorker()

            assertEquals(
                SyncOutcome.RETRY,
                first
            )
            assertEquals(
                "PENDIENTE",
                dao.getById(
                    "temporary"
                )?.estadoSincronizacion
            )

            api.postCode = 201

            val second =
                repository
                    .sincronizarPendientesDesdeWorker()

            assertEquals(
                SyncOutcome.COMPLETE,
                second
            )
            assertEquals(
                listOf(
                    "temporary",
                    "temporary"
                ),
                api.receivedIds
            )
        }

    @Test
    fun `HTTP 401 expires session and pauses queue`() =
        runBlocking {
            repository.registrarIncidencia(
                placa = "ABC123",
                via = 101,
                accion = "FUGA",
                fechaHoraEvento =
                    "2026-10-09T01:00:00-05:00",
                textoReconocido = "test",
                operationId = "expired"
            )

            api.postCode = 401

            val outcome =
                repository
                    .sincronizarPendientesDesdeWorker()

            assertEquals(
                SyncOutcome.AUTH_REQUIRED,
                outcome
            )
            assertFalse(
                session.sessionState
                    .value
                    .isLoggedIn
            )
            assertTrue(
                session.sessionState
                    .value
                    .requiresLogin
            )
        }

    private fun user(
        id: Long,
        code: Int,
        plaza: Long
    ) = UsuarioDto(
        id = id,
        codigo = code,
        nombre = "Operador $id",
        rol = "OPERADOR",
        plazaId = plaza,
        plaza = "P$plaza"
    )
}

private class FakeIncidentDao :
    IncidentDao {

    private val rows =
        linkedMapOf<String, IncidentEntity>()

    private val changes =
        MutableStateFlow<List<IncidentEntity>>(
            emptyList()
        )

    override fun getAllFlow(
        operatorId: String,
        plazaId: Long,
        server: String
    ): Flow<List<IncidentEntity>> {
        return changes.map { list ->
            list.filter {
                it.operatorId ==
                    operatorId &&
                    it.ownerPlazaId ==
                    plazaId &&
                    it.serverOrigin ==
                    server
            }
                .sortedByDescending {
                    it.timestamp
                }
        }
    }

    override suspend fun getPendientes(
        operatorId: String,
        plazaId: Long,
        server: String
    ): List<IncidentEntity> {
        return rows.values
            .filter {
                it.operatorId ==
                    operatorId &&
                    it.ownerPlazaId ==
                    plazaId &&
                    it.serverOrigin ==
                    server &&
                    it.estadoSincronizacion in
                    setOf(
                        "PENDIENTE",
                        "SINCRONIZANDO"
                    )
            }
            .sortedBy {
                it.timestamp
            }
    }

    override fun getUnassignedCountFlow():
        Flow<Int> {
        return changes.map { list ->
            list.count {
                it.operatorId == null ||
                    it.ownerPlazaId == null ||
                    it.serverOrigin == null
            }
        }
    }

    override suspend fun getById(
        id: String
    ): IncidentEntity? =
        rows[id]

    override suspend fun insert(
        entity: IncidentEntity
    ): Long {
        if (
            rows.containsKey(
                entity.id
            )
        ) {
            return -1L
        }

        rows[entity.id] = entity
        publish()
        return rows.size.toLong()
    }

    override suspend fun update(
        entity: IncidentEntity
    ) {
        rows[entity.id] = entity
        publish()
    }

    override suspend fun delete(
        id: String
    ) {
        rows.remove(id)
        publish()
    }

    fun size(): Int =
        rows.size

    private fun publish() {
        changes.value =
            rows.values.toList()
    }
}

private class FakeSessionStore(
    user: UsuarioDto
) : SessionStore {

    private val state =
        MutableStateFlow(
            SessionState(
                isLoggedIn = true,
                token = "token",
                usuario = user
            )
        )

    override val sessionState =
        state.asStateFlow()

    override fun saveSession(
        response: LoginResponse
    ) {
        state.value =
            SessionState(
                isLoggedIn = true,
                token = response.token,
                usuario = response.usuario
            )
    }

    override fun getToken():
        String? =
        state.value.token

    override fun clearSession() {
        state.value =
            SessionState()
    }

    override fun expireSession() {
        state.value =
            state.value.copy(
                isLoggedIn = false,
                token = null,
                requiresLogin = true
            )
    }

    fun setUser(
        user: UsuarioDto
    ) {
        state.value =
            SessionState(
                isLoggedIn = true,
                token = "token-${user.id}",
                usuario = user
            )
    }
}

private class StubSigoApiService :
    SigoApiService {

    var postCode = 201
    var viaCode = 200
    var viaResults: List<ViaDto>? = null
    var vias =
        listOf(101)
    val postCalls =
        AtomicInteger(0)
    val receivedIds =
        mutableListOf<String>()

    override suspend fun login(
        request: LoginRequest
    ): Response<LoginResponse> {
        return Response.success(
            LoginResponse(
                token = "token",
                expiresIn = 3600,
                usuario = UsuarioDto(
                    id = 1,
                    codigo = request.codigo,
                    plazaId = 3,
                    plaza = "P3"
                )
            )
        )
    }

    override suspend fun getStatus():
        Response<SigoStatusResponse> =
        Response.success(
            SigoStatusResponse()
        )

    override suspend fun listarVias(
        plazaId: Long
    ): Response<List<ViaDto>> {
        if (viaCode !in 200..299) {
            return Response.error(viaCode, "{}".toResponseBody(null))
        }
        return Response.success(
            viaResults ?: vias.map { numero ->
                ViaDto(plazaId = plazaId, numero = numero)
            }
        )
    }

    override suspend fun registrarEvento(
        request: RegistroSigoRequest
    ): Response<RegistroSigoResponse> {
        postCalls.incrementAndGet()
        receivedIds += request.id

        return if (
            postCode in 200..299
        ) {
            Response.success(
                postCode,
                RegistroSigoResponse(
                    id = request.id,
                    fechaHoraRecepcion =
                        "2026-10-09T01:00:01-05:00"
                )
            )
        } else {
            Response.error(
                postCode,
                "{}"
                    .toResponseBody(
                        null
                    )
            )
        }
    }

    override suspend fun actualizarEvento(
        id: String,
        request: RegistroSigoUpdateRequest
    ): Response<Unit> =
        Response.success(Unit)

    override suspend fun consultarRegistros(
        desde: String?,
        hasta: String?,
        plazaId: String?,
        via: Int?,
        accion: String?
    ): Response<List<RegistroSigoResponse>> =
        Response.success(emptyList())
}
