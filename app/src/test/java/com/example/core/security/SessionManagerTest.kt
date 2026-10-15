package com.example.core.security

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.model.LoginResponse
import com.example.model.UsuarioDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(
    sdk = [34],
    application = android.app.Application::class
)
class SessionManagerTest {

    private val context: Context
        get() =
            ApplicationProvider
                .getApplicationContext()

    @Test
    fun `legacy plaintext session is deleted on startup`() {
        val fallback =
            context.getSharedPreferences(
                "avi_fallback_session",
                Context.MODE_PRIVATE
            )

        fallback.edit()
            .putString(
                "jwt_token",
                "plaintext-token"
            )
            .putString(
                "usuario_nombre",
                "Legacy"
            )
            .commit()

        SessionManager(context)

        assertTrue(
            fallback.all.isEmpty()
        )
    }

    @Test
    fun `expiring a session removes token and requires login`() {
        val manager =
            SessionManager(context)

        manager.clearSession()

        manager.saveSession(
            LoginResponse(
                token = "jwt-test",
                expiresIn = 3600,
                usuario = UsuarioDto(
                    id = 10,
                    codigo = 2396,
                    nombre = "Operador",
                    rol = "OPERADOR",
                    plazaId = 3,
                    plaza = "P3"
                )
            )
        )

        assertTrue(
            manager.sessionState.value.isLoggedIn
        )
        assertEquals(
            "jwt-test",
            manager.getToken()
        )

        manager.expireSession()

        assertFalse(
            manager.sessionState.value.isLoggedIn
        )
        assertTrue(
            manager.sessionState.value.requiresLogin
        )
        assertNull(
            manager.getToken()
        )

        manager.clearSession()
    }

    @Test
    fun `session without server expiry is not restored after process recreation`() {
        val manager =
            SessionManager(context)

        manager.clearSession()

        manager.saveSession(
            LoginResponse(
                token = "memory-only",
                expiresIn = null,
                usuario = UsuarioDto(
                    id = 11,
                    codigo = 2450,
                    nombre = "Temporal",
                    rol = "OPERADOR",
                    plazaId = 3,
                    plaza = "P3"
                )
            )
        )

        assertEquals(
            "memory-only",
            manager.getToken()
        )

        val recreated =
            SessionManager(context)

        assertFalse(
            recreated.sessionState.value.isLoggedIn
        )
        assertNull(
            recreated.getToken()
        )

        manager.clearSession()
        recreated.clearSession()
    }
}
