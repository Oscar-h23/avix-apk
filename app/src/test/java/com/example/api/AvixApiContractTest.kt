package com.example.api

import com.example.model.LoginRequest
import com.example.model.RegistroAvixRequest
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test

class AvixApiContractTest {
    @Test fun defaultLocalApiUsesCurrentMacAddress() {
        assertEquals("http://172.18.20.32:8080/", AvixApiService.LOCAL_MAC_BASE_URL)
    }

    @Test fun mobileLoginUsesNewRouteAndStringCode() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(MockResponse().setResponseCode(200).setHeader("Content-Type", "application/json")
                .setBody("""{"token":"test-token","tipo":"Bearer","expiraEn":"2026-10-16T05:00:00Z","usuario":{"id":"64f579a3-2937-440a-a315-bc9c2a9baf31","codigo":"AG001","nombres":"Agente","rol":"AGENTE"}}"""))
            val response = AvixApiService.create(server.url("/").toString()).login(LoginRequest("AG001"))
            assertTrue(response.isSuccessful)
            assertEquals("AG001", response.body()?.usuario?.codigoTexto)
            assertEquals("64f579a3-2937-440a-a315-bc9c2a9baf31", response.body()?.usuario?.uuid)
            val request = server.takeRequest()
            assertEquals("POST", request.method)
            assertEquals("/api/v1/auth/movil", request.path)
            assertTrue(request.body.readUtf8().contains("\"codigo\":\"AG001\""))
        } finally { server.shutdown() }
    }

    @Test fun assignmentsAreReadFromAuthenticatedUserNotGeneralCatalog() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(MockResponse().setHeader("Content-Type", "application/json")
                .setBody("""[{"id":2,"codigo":"P2","activa":true}]"""))
            val service = AvixApiService.create(server.url("/").toString()) { "test-token" }
            val assigned = service.listarPlazasAsignadas()
            assertTrue(assigned.isSuccessful)
            assertEquals("P2", assigned.body()?.single()?.codigo)
            val request = server.takeRequest()
            assertEquals("/api/v1/catalogos/plazas/asignadas", request.path)
            assertEquals("Bearer test-token", request.getHeader("Authorization"))
        } finally { server.shutdown() }
    }

    @Test fun catalogsUseAuthenticatedAvixRoutes() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(MockResponse().setHeader("Content-Type", "application/json")
                .setBody("""[{"id":3,"codigo":"P3","activa":true}]"""))
            server.enqueue(MockResponse().setHeader("Content-Type", "application/json")
                .setBody("""[{"id":22,"plaza_id":3,"numero":151,"activa":true}]"""))
            val service = AvixApiService.create(server.url("/").toString()) { "test-token" }
            assertEquals("P3", service.listarPlazas().body()?.first()?.codigo)
            assertEquals(151, service.listarVias("P3").body()?.first()?.numero)
            val first = server.takeRequest()
            val second = server.takeRequest()
            assertEquals("/api/v1/catalogos/plazas", first.path)
            assertEquals("/api/v1/catalogos/vias?plaza=P3", second.path)
            assertEquals("Bearer test-token", second.getHeader("Authorization"))
        } finally { server.shutdown() }
    }

    @Test fun registrationSendsAvixApiFieldsAndStableIdempotencyKey() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(MockResponse().setResponseCode(201).setHeader("Content-Type", "application/json")
                .setBody("""{"id":"b2274473-6357-45ec-a48c-73a61056776d","placa":"X1Z505","creado_en":"2026-10-15T06:00:00Z"}"""))
            val service = AvixApiService.create(server.url("/").toString()) { "test-token" }
            val response = service.registrarEvento(RegistroAvixRequest(
                id = "b2274473-6357-45ec-a48c-73a61056776d", placa = "X1Z505",
                via = 151, accion = "FUGA", fechaHoraEvento = "2026-10-14T22:10:00-05:00",
                textoReconocido = "fuga via 151", plaza = "P3", origen = "VOZ"))
            assertTrue(response.isSuccessful)
            assertEquals("b2274473-6357-45ec-a48c-73a61056776d", response.body()?.id)
            val request = server.takeRequest()
            assertEquals("POST", request.method)
            assertEquals("/api/v1/registros", request.path)
            assertEquals("Bearer test-token", request.getHeader("Authorization"))
            val body = request.body.readUtf8()
            for (field in listOf("idempotenciaId", "plaza", "via", "accion", "placa", "ocurridoEn", "origen")) {
                assertTrue("Falta: $field", body.contains("\"$field\""))
            }
            assertFalse(body.contains("\"fechaHoraEvento\""))
        } finally { server.shutdown() }
    }
}
