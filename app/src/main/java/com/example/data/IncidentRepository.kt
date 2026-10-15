package com.example.data

import android.content.Context
import com.example.BuildConfig
import com.example.api.AvixApiService
import com.example.core.security.SessionManager
import com.example.core.security.SessionStore
import com.example.data.db.AviDatabase
import com.example.data.db.IncidentDao
import com.example.data.db.IncidentEntity
import com.example.domain.IncidentOwner
import com.example.model.EstadoSincronizacion
import com.example.model.Incident
import com.example.model.LoginRequest
import com.example.model.LoginResponse
import com.example.model.RegistroAvixRequest
import com.example.model.RegistroAvixResponse
import com.example.model.RegistroAvixUpdateRequest
import com.example.model.UsuarioDto
import com.example.parser.AviParser
import com.example.worker.SyncRegistroWorker
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.util.Locale
import java.util.UUID

enum class ApiConnectionState(
    val label: String
) {
    ONLINE("Conectado a AVIX"),
    OFFLINE("Servidor no disponible"),
    SIN_CONFIGURAR("API sin configurar")
}

enum class SyncOutcome {
    COMPLETE,
    RETRY,
    AUTH_REQUIRED
}

/**
 * Room es la fuente de verdad local. Registrar una incidencia significa
 * persistirla primero; WorkManager se encarga de llevarla a AVIX después.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class IncidentRepository internal constructor(
    context: Context,
    private val dao: IncidentDao =
        AviDatabase.getInstance(context).incidentDao(),
    val sessionManager: SessionStore =
        SessionManager(context),
    private val apiFactory:
        (String, String?) -> AvixApiService =
        { url, token ->
            AvixApiService.create(url) { token }
        },
    private val enqueueSync: () -> Unit = {
        SyncRegistroWorker.enqueueImmediateSync(context)
    },
    private val ioDispatcher: CoroutineDispatcher =
        Dispatchers.IO,
    startBackground: Boolean = true
) {

    private val appContext =
        context.applicationContext

    private val prefs =
        appContext.getSharedPreferences(
            PREFS_FILE,
            Context.MODE_PRIVATE
        )

    private val scope =
        CoroutineScope(
            SupervisorJob() + ioDispatcher
        )

    private val syncMutex = Mutex()

    private val _baseUrl =
        MutableStateFlow(resolveInitialBaseUrl())

    val sessionState =
        sessionManager.sessionState

    private val _connectionState =
        MutableStateFlow(
            ApiConnectionState.OFFLINE
        )
    val connectionState =
        _connectionState.asStateFlow()

    private val _allowedVias =
        MutableStateFlow<Set<Int>>(emptySet())
    val allowedVias =
        _allowedVias.asStateFlow()

    private val _isSyncing =
        MutableStateFlow(false)
    val isSyncing =
        _isSyncing.asStateFlow()

    private val _lastSyncSummary =
        MutableStateFlow("Listo para registrar.")
    val lastSyncSummary =
        _lastSyncSummary.asStateFlow()

    private val _sessionExpiredEvent =
        MutableSharedFlow<String>(
            extraBufferCapacity = 1
        )
    val sessionExpiredEvent =
        _sessionExpiredEvent.asSharedFlow()

    val incidentsFlow: Flow<List<Incident>> =
        combine(
            sessionState,
            _baseUrl
        ) { session, url ->
            if (session.isLoggedIn) {
                IncidentOwner.from(
                    session.usuario,
                    url
                )
            } else {
                null
            }
        }
            .distinctUntilChanged()
            .flatMapLatest { owner ->
                if (owner == null) {
                    flowOf(emptyList())
                } else {
                    dao.getAllFlow(
                        owner.operatorId,
                        owner.plazaId,
                        owner.server
                    ).map { rows ->
                        rows.map { it.toDomain() }
                    }
                }
            }

    val pendientesCountFlow: Flow<Int> =
        incidentsFlow.map { incidents ->
            incidents.count {
                it.estadoSincronizacion in setOf(
                    EstadoSincronizacion.PENDIENTE,
                    EstadoSincronizacion.SINCRONIZANDO
                )
            }
        }

    /**
     * Registros de versiones anteriores que no tienen una identidad verificable.
     * Se conservan, pero no se muestran ni sincronizan como si fueran del
     * siguiente usuario que inicia sesión.
     */
    val unassignedCountFlow: Flow<Int> =
        dao.getUnassignedCountFlow()

    init {
        if (startBackground) {
            verificarConexionAvix()

            scope.launch {
                cargarViasPermitidas()
            }
        }
    }

    private fun resolveInitialBaseUrl(): String {
        val stored =
            prefs.getString(
                KEY_BASE_URL,
                null
            )

        return if (
            stored.isNullOrBlank() ||
            stored.contains("sigo-back-production", ignoreCase = true) ||
            stored.trimEnd('/') in setOf("http://172.20.10.8:8080", "http://10.0.2.2:8080") ||
            stored.contains("/api/avi/", ignoreCase = true)
        ) {
            prefs.edit()
                .putString(
                    KEY_BASE_URL,
                    AvixApiService.PRODUCTION_BASE_URL
                )
                .apply()

            AvixApiService.PRODUCTION_BASE_URL
        } else {
            normalizeServer(stored)
        }
    }

    private fun normalizeServer(
        url: String
    ): String {
        return url.trim().trimEnd('/') + "/"
    }

    fun getBaseUrl(): String =
        _baseUrl.value

    fun getUsuarioActual(): UsuarioDto? =
        sessionManager.getUsuario()

    fun getAllowedVias(): Set<Int> =
        _allowedVias.value

    fun isViaPermitida(
        via: Int
    ): Boolean {
        // Una respuesta vacía no significa autorización para cualquier vía.
        return via > 0 && via in _allowedVias.value
    }

    fun isUsuarioAutenticado(): Boolean =
        sessionManager.isLoggedIn()

    fun currentOwner(): IncidentOwner? =
        IncidentOwner.from(
            sessionManager.getUsuario(),
            getBaseUrl()
        )

    private fun api(
        token: String? =
            sessionManager.getToken()
    ): AvixApiService {
        return apiFactory(
            getBaseUrl(),
            token
        )
    }

    private fun owns(
        row: IncidentEntity,
        owner: IncidentOwner
    ): Boolean {
        return row.operatorId ==
            owner.operatorId &&
            row.ownerPlazaId ==
            owner.plazaId &&
            row.serverOrigin ==
            owner.server
    }

    suspend fun login(codigo: Int): Result<LoginResponse> = login(codigo.toString())

    suspend fun login(
        codigo: String
    ): Result<LoginResponse> =
        withContext(ioDispatcher) {
            if (!codigo.matches(Regex("[A-Za-z0-9_-]{3,30}"))) {
                return@withContext Result.failure(
                    IllegalArgumentException(
                        "Ingrese un código de trabajador válido."
                    )
                )
            }

            syncMutex.withLock {
                try {
                    val requestedServer =
                        getBaseUrl()

                    val response =
                        apiFactory(
                            requestedServer,
                            null
                        ).login(
                            LoginRequest(codigo)
                        )

                    check(
                        requestedServer ==
                            getBaseUrl()
                    ) {
                        "El servidor cambió. Inicie sesión nuevamente."
                    }

                    val body = response.body()
                    if (response.isSuccessful && body != null) {
                        val usuario = requireNotNull(body.usuario) { "AVIX API no devolvió el usuario." }
                        val sesion = if (usuario.plazaId != null && usuario.plaza != null) {
                            body
                        } else {
                            val plazasResponse = apiFactory(requestedServer, body.token).listarPlazas()
                            check(plazasResponse.isSuccessful) {
                                "No se pudieron consultar las plazas asignadas (" + plazasResponse.code() + ")."
                            }
                            val plaza = plazasResponse.body().orEmpty().firstOrNull { it.activa }
                                ?: throw IllegalStateException("No tiene una plaza activa asignada. Consulte al supervisor.")
                            body.copy(usuario = usuario.copy(plazaId = plaza.id, plaza = plaza.codigo))
                        }
                        require(IncidentOwner.from(sesion.usuario, requestedServer) != null) {
                            "El usuario no tiene una plaza válida. Consulte al supervisor."
                        }
                        val persistida = if (sesion.expiraEn != null) {
                            sesion.copy(expiresIn = 11 * 3600L)
                        } else sesion
                        sessionManager.saveSession(persistida)
                        _connectionState.value = ApiConnectionState.ONLINE
                        cargarViasPermitidas()
                        enqueueSync()
                        Result.success(persistida)
                    } else {
                        Result.failure(
                            IllegalStateException(
                                when (
                                    response.code()
                                ) {
                                    400 ->
                                        "Código de trabajador inválido."

                                    401, 403 ->
                                        "Código no autorizado. Consulte al administrador."

                                    else ->
                                        "No se pudo iniciar sesión. Intente nuevamente."
                                }
                            )
                        )
                    }
                } catch (
                    e: CancellationException
                ) {
                    throw e
                } catch (e: Exception) {
                    Result.failure(e)
                }
            }
        }

    fun logout() {
        sessionManager.clearSession()
        _allowedVias.value =
            emptySet()

        _lastSyncSummary.value =
            "Sesión cerrada. Los pendientes permanecen asociados a su operador."
    }

    private fun expire(
        token: String
    ) {
        // Una respuesta tardía de otra sesión no debe cerrar la sesión nueva.
        if (
            sessionManager
                .sessionState
                .value
                .token == token
        ) {
            sessionManager.expireSession()
            _allowedVias.value =
                emptySet()

            _sessionExpiredEvent
                .tryEmit(
                    "Su sesión expiró. Inicie sesión para continuar; sus registros están guardados."
                )
        }
    }

    /**
     * Se conserva para depuración. Producción usa siempre HTTPS.
     * Cambiar servidor invalida la sesión para impedir mezclar colas.
     */
    fun updateConfig(
        newBaseUrl: String
    ): Boolean {
        val url =
            newBaseUrl
                .trim()
                .toHttpUrlOrNull()
                ?: return false

        if (
            url.username.isNotEmpty() ||
            url.password.isNotEmpty() ||
            url.query != null ||
            url.fragment != null
        ) {
            return false
        }

        val localDebugHost =
            url.host in setOf(
                "localhost",
                "127.0.0.1",
                "10.0.2.2",
                "172.18.20.32"
            )

        if (
            !url.isHttps &&
            !(BuildConfig.DEBUG &&
                localDebugHost)
        ) {
            return false
        }

        val normalized =
            normalizeServer(
                url.toString()
            )

        if (
            normalized !=
            getBaseUrl()
        ) {
            logout()

            _baseUrl.value =
                normalized

            prefs.edit()
                .putString(
                    KEY_BASE_URL,
                    normalized
                )
                .apply()
        }

        verificarConexionAvix()
        return true
    }

    fun verificarConexionAvix() {
        scope.launch {
            _connectionState.value =
                try {
                    if (
                        api(null)
                            .getStatus()
                            .isSuccessful
                    ) {
                        ApiConnectionState.ONLINE
                    } else {
                        ApiConnectionState.OFFLINE
                    }
                } catch (
                    e: CancellationException
                ) {
                    throw e
                } catch (_: Exception) {
                    ApiConnectionState.OFFLINE
                }
        }
    }

    suspend fun cargarViasPermitidas(
        plazaId: Long? =
            getUsuarioActual()?.plazaId
    ): Set<Int> {
        val token =
            sessionManager.getToken()
                ?: return emptySet()

        if (
            plazaId == null ||
            plazaId <= 0L
        ) {
            return emptySet()
        }

        val owner =
            currentOwner()

        val cacheKey =
            "vias:${owner?.server}:${owner?.operatorId}:$plazaId"

        val cached =
            prefs.getStringSet(
                cacheKey,
                emptySet()
            )
                .orEmpty()
                .mapNotNull {
                    it.toIntOrNull()
                }
                .toSet()

        return try {
            val response =
                api(token)
                    .listarVias(getUsuarioActual()?.plaza ?: return emptySet())

            val vias =
                if (
                    response.isSuccessful
                ) {
                    response.body()
                        .orEmpty()
                        .filter {
                            it.activa &&
                                (
                                    it.plazaId == null ||
                                        it.plazaId == plazaId
                                    )
                        }
                        .map {
                            it.numero
                        }
                        .filter {
                            it > 0
                        }
                        .toSet()
                } else {
                    cached
                }

            if (
                currentOwner() == owner &&
                sessionManager
                    .sessionState
                    .value
                    .token == token
            ) {
                _allowedVias.value =
                    vias

                if (
                    response.isSuccessful
                ) {
                    prefs.edit()
                        .putStringSet(
                            cacheKey,
                            vias.map {
                                it.toString()
                            }.toSet()
                        )
                        .apply()
                }

                if (
                    response.code() == 401
                ) {
                    expire(token)
                }
            }

            _allowedVias.value
        } catch (
            e: CancellationException
        ) {
            throw e
        } catch (_: Exception) {
            if (
                currentOwner() == owner
            ) {
                _allowedVias.value =
                    cached
            }

            _allowedVias.value
        }
    }

    /**
     * Obtiene en tiempo real las vías visibles para AVIX de la plaza del
     * operador. No utiliza la caché: un fallo de red nunca permite escoger
     * una vía que el supervisor haya deshabilitado.
     */
    suspend fun consultarViasHabilitadasParaEdicion():
        Result<List<com.example.model.ViaDto>> =
        withContext(ioDispatcher) {
            try {
                val owner = requireNotNull(currentOwner()) {
                    "Inicie sesión para consultar las vías de su plaza."
                }
                val token = requireNotNull(sessionManager.getToken()) {
                    "La sesión ha expirado. Inicie sesión nuevamente."
                }

                val response = apiFactory(
                    owner.server,
                    token
                ).listarVias(getUsuarioActual()?.plaza ?: throw IllegalStateException("Plaza no disponible."))

                if (response.code() == 401) {
                    expire(token)
                }

                check(response.isSuccessful) {
                    "No se pudieron consultar las vías habilitadas de AVIX (${response.code()})."
                }

                check(
                    currentOwner() == owner &&
                        sessionManager.getToken() == token
                ) {
                    "La sesión cambió. Vuelva a abrir la edición."
                }

                val habilitadas = requireNotNull(response.body()) {
                    "AVIX no devolvió una lista de vías válida."
                }
                    .filter { via ->
                        via.activa &&
                            via.numero > 0 &&
                            (via.plazaId == null ||
                                via.plazaId == owner.plazaId)
                    }
                    .distinctBy { it.numero }
                    .sortedWith(
                        compareBy<com.example.model.ViaDto> {
                            it.orden ?: Int.MAX_VALUE
                        }.thenBy { it.numero }
                    )

                // Mantener la validación de guardado alineada con AVIX.
                _allowedVias.value = habilitadas.map { it.numero }.toSet()

                Result.success(habilitadas)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    private fun validate(
        placa: String,
        via: Int?,
        accion: String
    ) {
        require(
            AviParser.isValidPeruPlate(
                placa
            )
        ) {
            "Revise los seis caracteres de la placa."
        }

        require(
            via != null &&
                isViaPermitida(via)
        ) {
            "Seleccione una vía válida para esta plaza."
        }

        require(
            accion in setOf(
                "FUGA",
                "DERIVADO",
                "LIBERADO"
            )
        ) {
            "Seleccione FUGA, DERIVADO o LIBERADO."
        }
    }

    /**
     * Confirma apenas Room guardó la incidencia. No espera a la red.
     *
     * operationId identifica un único borrador. Repetir la llamada con el
     * mismo UUID y el mismo contenido es idempotente localmente.
     */
    suspend fun registrarIncidencia(
        placa: String,
        via: Int?,
        accion: String,
        fechaHoraEvento: String,
        textoReconocido: String,
        operationId: String =
            UUID.randomUUID().toString(),
        expectedOwner: IncidentOwner? =
            currentOwner()
    ): Result<Incident> =
        withContext(ioDispatcher) {
            try {
                val owner =
                    currentOwner()

                require(
                    owner != null &&
                        owner ==
                        expectedOwner &&
                        sessionManager
                            .isLoggedIn()
                ) {
                    "La sesión cambió. Inicie sesión con el operador del borrador."
                }

                val plate =
                    placa.uppercase(
                        Locale.ROOT
                    )
                        .replace("-", "")
                        .replace(" ", "")

                val action =
                    accion.uppercase(
                        Locale.ROOT
                    )
                        .trim()

                validate(
                    plate,
                    via,
                    action
                )

                val incident =
                    Incident(
                        id = operationId,
                        placa = plate,
                        via = via,
                        accion = action,
                        plazaId =
                            getUsuarioActual()
                                ?.plaza
                                ?: owner.plazaId
                                    .toString(),
                        fechaHoraEvento =
                            fechaHoraEvento,
                        textoReconocido =
                            textoReconocido,
                        operatorId =
                            owner.operatorId,
                        ownerPlazaId =
                            owner.plazaId,
                        serverOrigin =
                            owner.server
                    )

                val row =
                    IncidentEntity
                        .fromDomain(incident)

                val inserted =
                    dao.insert(row)

                val saved =
                    if (
                        inserted == -1L
                    ) {
                        val existing =
                            requireNotNull(
                                dao.getById(
                                    operationId
                                )
                            )

                        require(
                            owns(
                                existing,
                                owner
                            ) &&
                                existing.placa ==
                                plate &&
                                existing.via ==
                                via &&
                                existing.accion ==
                                action &&
                                existing
                                    .fechaHoraEvento ==
                                fechaHoraEvento &&
                                existing
                                    .textoReconocido ==
                                textoReconocido
                        ) {
                            "Esta operación ya fue guardada con otros datos. Inicie un nuevo registro."
                        }

                        existing.toDomain()
                    } else {
                        incident
                    }

                enqueueSync()

                Result.success(saved)
            } catch (
                e: CancellationException
            ) {
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    private suspend fun send(
        row: IncidentEntity,
        token: String,
        client: AvixApiService
    ): IncidentEntity {
        var updated =
            row.copy(
                estadoSincronizacion =
                    EstadoSincronizacion
                        .SINCRONIZANDO
                        .name,
                intentosSincronizacion =
                    row.intentosSincronizacion + 1
            )

        dao.update(updated)

        try {
            val response =
                client.registrarEvento(
                    RegistroAvixRequest(
                        id = row.id,
                        placa = row.placa,
                        via = row.via,
                        accion = row.accion,
                        fechaHoraEvento =
                            row.fechaHoraEvento,
                        textoReconocido =
                            row.textoReconocido.take(1000),
                        plaza = row.plazaId,
                        origen = if (row.textoReconocido.isBlank()) "MANUAL" else "VOZ"
                    )
                )

            updated =
                when (
                    response.code()
                ) {
                    200, 201 ->
                        updated.copy(
                            estadoSincronizacion =
                                EstadoSincronizacion
                                    .SINCRONIZADO
                                    .name,
                            fechaHoraRecepcion =
                                response.body()
                                    ?.fechaHoraRecepcion,
                            ultimoError = null
                        )

                    401 -> {
                        expire(token)

                        updated.copy(
                            estadoSincronizacion =
                                EstadoSincronizacion
                                    .PENDIENTE
                                    .name,
                            ultimoError =
                                "Inicie sesión para reanudar el envío."
                        )
                    }

                    408, 429,
                    in 500..599 ->
                        updated.copy(
                            estadoSincronizacion =
                                EstadoSincronizacion
                                    .PENDIENTE
                                    .name,
                            ultimoError =
                                "AVIX no está disponible. Reintentaremos automáticamente."
                        )

                    else ->
                        updated.copy(
                            estadoSincronizacion =
                                EstadoSincronizacion
                                    .REQUIERE_REVISION
                                    .name,
                            ultimoError =
                                "AVIX rechazó el registro (${response.code()}). Revise los datos o contacte al administrador."
                        )
                }

            _connectionState.value =
                ApiConnectionState.ONLINE
        } catch (
            e: CancellationException
        ) {
            throw e
        } catch (_: Exception) {
            updated =
                updated.copy(
                    estadoSincronizacion =
                        EstadoSincronizacion
                            .PENDIENTE
                            .name,
                    ultimoError =
                        "Guardado en este dispositivo. Se enviará cuando haya conexión."
                )

            _connectionState.value =
                ApiConnectionState.OFFLINE
        }

        dao.update(updated)
        return updated
    }

    private suspend fun sync():
        Pair<SyncOutcome, Int> =
        withContext(ioDispatcher) {
            syncMutex.withLock {
                val token =
                    sessionManager.getToken()

                val owner =
                    currentOwner()

                if (
                    token == null ||
                    owner == null
                ) {
                    return@withLock (
                        SyncOutcome.AUTH_REQUIRED to 0
                    )
                }

                val client =
                    apiFactory(
                        owner.server,
                        token
                    )

                _isSyncing.value = true

                try {
                    val rows =
                        dao.getPendientes(
                            owner.operatorId,
                            owner.plazaId,
                            owner.server
                        )

                    if (rows.isEmpty()) {
                        _lastSyncSummary.value =
                            "No hay registros pendientes."

                        SyncOutcome.COMPLETE to 0
                    } else {
                        _lastSyncSummary.value =
                            "Enviando ${rows.size} registro(s) guardado(s)..."

                        var confirmed = 0
                        var retry = false
                        var authRequired = false

                        for (row in rows) {
                            if (
                                currentOwner() != owner ||
                                sessionManager.getToken() != token
                            ) {
                                authRequired = true
                                break
                            }

                            val sent =
                                send(
                                    row,
                                    token,
                                    client
                                )

                            when (
                                sent.estadoSincronizacion
                            ) {
                                EstadoSincronizacion
                                    .SINCRONIZADO
                                    .name -> {
                                    confirmed++
                                }

                                EstadoSincronizacion
                                    .PENDIENTE
                                    .name -> {
                                    retry = true
                                }
                            }

                            if (
                                sessionManager.getToken() == null
                            ) {
                                authRequired = true
                                break
                            }
                        }

                        val outcome =
                            when {
                                authRequired ->
                                    SyncOutcome.AUTH_REQUIRED

                                retry ->
                                    SyncOutcome.RETRY

                                else ->
                                    SyncOutcome.COMPLETE
                            }

                        _lastSyncSummary.value =
                            when (outcome) {
                                SyncOutcome.AUTH_REQUIRED ->
                                    "Inicie sesión para reanudar los envíos."

                                SyncOutcome.RETRY ->
                                    "$confirmed confirmados en AVIX. Quedan registros pendientes."

                                SyncOutcome.COMPLETE ->
                                    "$confirmed confirmados en AVIX. Los rechazados requieren revisión."
                            }

                        outcome to confirmed
                    }
                } finally {
                    _isSyncing.value = false
                }
            }
        }

    suspend fun sincronizarPendientes():
        Int {
        return try {
            val (
                outcome,
                count
            ) = sync()

            if (
                outcome ==
                SyncOutcome.AUTH_REQUIRED
            ) {
                _lastSyncSummary.value =
                    "Inicie sesión para reanudar los envíos."
            }

            count
        } catch (
            e: CancellationException
        ) {
            throw e
        } catch (_: Exception) {
            _lastSyncSummary.value =
                "No se pudo completar la sincronización. Sus registros siguen guardados."

            0
        }
    }

    suspend fun sincronizarPendientesDesdeWorker():
        SyncOutcome {
        return sync().first
    }

    suspend fun actualizarIncidencia(
        id: String,
        placa: String,
        via: Int?,
        accion: String
    ): Result<Incident> =
        withContext(ioDispatcher) {
            syncMutex.withLock {
                try {
                    val owner =
                        requireNotNull(
                            currentOwner()
                        ) {
                            "Inicie sesión para editar."
                        }

                    val token =
                        requireNotNull(
                            sessionManager
                                .getToken()
                        ) {
                            "Inicie sesión para editar."
                        }

                    val row =
                        requireNotNull(
                            dao.getById(id)
                        ) {
                            "Registro no encontrado."
                        }

                    require(
                        owns(
                            row,
                            owner
                        )
                    ) {
                        "El registro pertenece a otro operador, plaza o servidor."
                    }

                    val plate =
                        placa.uppercase(
                            Locale.ROOT
                        )
                            .replace("-", "")
                            .replace(" ", "")

                    val action =
                        accion.uppercase(
                            Locale.ROOT
                        )
                            .trim()

                    validate(
                        plate,
                        via,
                        action
                    )

                    require(
                        row.estadoSincronizacion !=
                            EstadoSincronizacion
                                .SINCRONIZANDO
                                .name
                    ) {
                        "El envío fue interrumpido. Sincronice antes de modificar para confirmar su estado."
                    }

                    /**
                     * Si ya hubo un POST y su resultado fue ambiguo por red,
                     * primero debe resolverse ese mismo UUID. No se reutiliza
                     * el identificador con un payload distinto.
                     */
                    require(
                        row.estadoSincronizacion !=
                            EstadoSincronizacion
                                .PENDIENTE
                                .name ||
                            row.intentosSincronizacion ==
                            0
                    ) {
                        "Sincronice este registro antes de editar: AVIX puede haber recibido el intento anterior."
                    }

                    require(row.estadoSincronizacion != EstadoSincronizacion.SINCRONIZADO.name) {
                        "Los registros confirmados solo pueden editarse desde AVIX Web."
                    }

                    var edited =
                        row.copy(
                            placa = plate,
                            via = via,
                            accion = action,
                            ultimoError = null
                        )

                    if (
                        row.estadoSincronizacion ==
                        EstadoSincronizacion
                            .SINCRONIZADO
                            .name
                    ) {
                        val response =
                            apiFactory(
                                owner.server,
                                token
                            ).actualizarEvento(
                                id,
                                RegistroAvixUpdateRequest(
                                    placa = plate,
                                    via = requireNotNull(
                                        via
                                    ),
                                    accion = action,
                                    fechaHoraEvento =
                                        row.fechaHoraEvento,
                                    textoReconocido =
                                        row.textoReconocido
                                )
                            )

                        if (
                            response.code() ==
                            401
                        ) {
                            expire(token)
                        }

                        check(
                            response.isSuccessful
                        ) {
                            "No se pudo confirmar la edición en AVIX (${response.code()})."
                        }
                    } else {
                        edited =
                            edited.copy(
                                estadoSincronizacion =
                                    EstadoSincronizacion
                                        .PENDIENTE
                                        .name
                            )
                    }

                    dao.update(edited)

                    if (
                        edited.estadoSincronizacion ==
                        EstadoSincronizacion
                            .PENDIENTE
                            .name
                    ) {
                        enqueueSync()
                    }

                    Result.success(
                        edited.toDomain()
                    )
                } catch (
                    e: CancellationException
                ) {
                    throw e
                } catch (e: Exception) {
                    Result.failure(e)
                }
            }
        }

    private fun normalizarFechaRango(value: String?, end: Boolean): String {
        val lima = java.util.TimeZone.getTimeZone("America/Lima")
        val format = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US)
        format.timeZone = lima
        val now = System.currentTimeMillis()
        if (value.isNullOrBlank()) {
            val millis = if (end) now else now - 7L * 86_400_000
            return format.format(java.util.Date(millis))
        }
        if (value.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))) {
            val cal = java.util.Calendar.getInstance(lima)
            cal.time = java.text.SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
                timeZone = lima
                isLenient = false
            }.parse(value) ?: throw IllegalArgumentException("Fecha inválida")
            if (end) cal.add(java.util.Calendar.DATE, 1)
            return format.format(cal.time)
        }
        return value
    }

    suspend fun consultarHistorialServidor(
        desde: String? = null,
        hasta: String? = null,
        plazaId: String? = null,
        via: Int? = null,
        accion: String? = null
    ): Result<List<RegistroAvixResponse>> =
        withContext(ioDispatcher) {
            try {
                val token =
                    requireNotNull(
                        sessionManager
                            .getToken()
                    ) {
                        "Inicie sesión."
                    }

                val response =
                    api(token)
                        .consultarRegistros(
                            normalizarFechaRango(desde, false),
                            normalizarFechaRango(hasta, true),
                            plazaId,
                            via,
                            accion
                        )

                if (
                    response.code() ==
                    401
                ) {
                    expire(token)
                }

                check(
                    response.isSuccessful
                ) {
                    "No se pudo consultar AVIX (${response.code()})."
                }

                Result.success(
                    response.body()
                        .orEmpty()
                )
            } catch (
                e: CancellationException
            ) {
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun eliminarIncidencia(
        id: String
    ) {
        withContext(ioDispatcher) {
            syncMutex.withLock {
                val owner =
                    requireNotNull(
                        currentOwner()
                    )

                val row =
                    requireNotNull(
                        dao.getById(id)
                    )

                require(
                    owns(
                        row,
                        owner
                    ) &&
                        sessionManager
                            .isLoggedIn()
                )

                require(
                    row.estadoSincronizacion ==
                        EstadoSincronizacion
                            .SINCRONIZADO
                            .name
                ) {
                    "No se pueden eliminar registros pendientes."
                }

                dao.delete(id)
            }
        }
    }

    fun close() {
        scope.cancel()
    }

    companion object {
        private const val PREFS_FILE =
            "avi_sigo_prefs"

        private const val KEY_BASE_URL =
            "sigo_base_url"

        @Volatile
        private var instance:
            IncidentRepository? = null

        fun getInstance(
            context: Context
        ): IncidentRepository {
            return instance
                ?: synchronized(this) {
                    instance
                        ?: IncidentRepository(
                            context.applicationContext
                        ).also {
                            instance = it
                        }
                }
        }
    }
}
