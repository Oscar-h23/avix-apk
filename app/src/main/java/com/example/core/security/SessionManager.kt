package com.example.core.security

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.example.model.LoginResponse
import com.example.model.UsuarioDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class SessionState(
    val isLoggedIn: Boolean = false,
    val token: String? = null,
    val usuario: UsuarioDto? = null,
    val expiresAt: Long? = null,
    val requiresLogin: Boolean = false
)

interface SessionStore {
    val sessionState: StateFlow<SessionState>

    fun saveSession(response: LoginResponse)
    fun getToken(): String?
    fun getUsuario(): UsuarioDto? = sessionState.value.usuario
    fun isLoggedIn(): Boolean = getToken() != null
    fun clearSession()
    fun expireSession()
}

class SessionManager(context: Context) : SessionStore {

    /**
     * Nunca degradar a SharedPreferences en texto plano.
     *
     * Si Android Keystore no está disponible, la sesión seguirá funcionando
     * únicamente en memoria hasta que el proceso termine.
     */
    private val prefs: SharedPreferences? = try {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        EncryptedSharedPreferences.create(
            context,
            PREFS_FILE_SECURE,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Exception) {
        Log.w(
            TAG,
            "Almacenamiento seguro no disponible; sesión solo en memoria: ${e.message}"
        )
        null
    }

    init {
        // Versiones antiguas podían usar un fallback sin cifrar. No se reutiliza.
        context.getSharedPreferences(
            PREFS_FILE_FALLBACK,
            Context.MODE_PRIVATE
        ).edit().clear().commit()
    }

    private val _sessionState = MutableStateFlow(load())
    override val sessionState: StateFlow<SessionState> =
        _sessionState.asStateFlow()

    private fun load(): SessionState {
        val securePrefs = prefs ?: return SessionState()

        return try {
            val token = securePrefs.getString(KEY_TOKEN, null)
            val expiresAt = securePrefs.getLong(KEY_EXPIRES_AT, 0L)

            if (
                token.isNullOrBlank() ||
                expiresAt <= System.currentTimeMillis()
            ) {
                securePrefs.edit().clear().commit()
                SessionState()
            } else {
                SessionState(
                    isLoggedIn = true,
                    token = token,
                    usuario = UsuarioDto(
                        id = securePrefs
                            .getLong(KEY_USER_ID, 0L)
                            .takeIf { it > 0L },
                        codigo = securePrefs
                            .getInt(KEY_USER_CODIGO, 0)
                            .takeIf { it > 0 },
                        nombre = securePrefs
                            .getString(KEY_USER_NOMBRE, null),
                        rol = securePrefs
                            .getString(KEY_USER_ROL, null),
                        plazaId = securePrefs
                            .getLong(KEY_USER_PLAZA_ID, 0L)
                            .takeIf { it > 0L },
                        plaza = securePrefs
                            .getString(KEY_USER_PLAZA, null)
                    ),
                    expiresAt = expiresAt,
                    requiresLogin = false
                )
            }
        } catch (_: Exception) {
            SessionState()
        }
    }

    @Synchronized
    override fun saveSession(response: LoginResponse) {
        require(response.token.isNotBlank()) {
            "SIGO no devolvió una sesión válida."
        }

        val expiresAt = response.expiresIn
            ?.takeIf { it > 0L && it <= MAX_SERVER_SESSION_SECONDS }
            ?.let {
                System.currentTimeMillis() + (it * 1000L)
            }

        val user = response.usuario

        // Eliminar por completo la identidad anterior antes de guardar la nueva.
        prefs?.edit()?.clear()?.apply {
            // Si el backend no informa vencimiento, no persistir el token.
            if (expiresAt != null) {
                putString(KEY_TOKEN, response.token)
                putLong(KEY_EXPIRES_AT, expiresAt)

                user?.id?.let {
                    putLong(KEY_USER_ID, it)
                }
                user?.codigo?.let {
                    putInt(KEY_USER_CODIGO, it)
                }
                putString(KEY_USER_NOMBRE, user?.nombre)
                putString(KEY_USER_ROL, user?.rol)
                user?.plazaId?.let {
                    putLong(KEY_USER_PLAZA_ID, it)
                }
                putString(KEY_USER_PLAZA, user?.plaza)
            }
        }?.commit()

        _sessionState.value = SessionState(
            isLoggedIn = true,
            token = response.token,
            usuario = user,
            expiresAt = expiresAt,
            requiresLogin = false
        )
    }

    @Synchronized
    override fun getToken(): String? {
        val current = _sessionState.value

        if (
            current.expiresAt?.let {
                it <= System.currentTimeMillis()
            } == true
        ) {
            expireSession()
        }

        return _sessionState.value.token
    }

    @Synchronized
    override fun clearSession() {
        prefs?.edit()?.clear()?.commit()
        _sessionState.value = SessionState()
    }

    @Synchronized
    override fun expireSession() {
        prefs?.edit()?.clear()?.commit()

        _sessionState.value = _sessionState.value.copy(
            isLoggedIn = false,
            token = null,
            expiresAt = null,
            requiresLogin = true
        )
    }

    companion object {
        private const val TAG = "SessionManager"
        private const val PREFS_FILE_SECURE =
            "avi_keystore_secure_session"
        private const val PREFS_FILE_FALLBACK =
            "avi_fallback_session"

        private const val KEY_TOKEN = "jwt_token"
        private const val KEY_EXPIRES_AT = "expires_at"
        private const val KEY_USER_ID = "usuario_id"
        private const val KEY_USER_CODIGO = "usuario_codigo"
        private const val KEY_USER_NOMBRE = "usuario_nombre"
        private const val KEY_USER_ROL = "usuario_rol"
        private const val KEY_USER_PLAZA_ID = "usuario_plaza_id"
        private const val KEY_USER_PLAZA = "usuario_plaza"

        private const val MAX_SERVER_SESSION_SECONDS =
            31_536_000L
    }
}
