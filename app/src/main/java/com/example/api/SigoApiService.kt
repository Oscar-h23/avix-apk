package com.example.api

import com.example.model.LoginRequest
import com.example.model.LoginResponse
import com.example.model.RegistroSigoRequest
import com.example.model.RegistroSigoResponse
import com.example.model.RegistroSigoUpdateRequest
import com.example.model.SigoStatusResponse
import com.example.model.ViaDto
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

interface SigoApiService {

    /**
     * Autenticación de trabajador mediante código.
     * POST /api/avi/auth/login
     */
    @Headers("Content-Type: application/json")
    @POST("api/avi/auth/login")
    suspend fun login(
        @Body request: LoginRequest
    ): Response<LoginResponse>

    /**
     * Verificación de conectividad y estado del backend SIGO.
     * GET /api/avi/status
     */
    @GET("api/avi/status")
    suspend fun getStatus(): Response<SigoStatusResponse>

    /**
     * Vías activas y visibles para AVIX en una plaza.
     * GET /api/avi/vias?plazaId={id}
     */
    @GET("api/avi/vias")
    suspend fun listarVias(
        @Query("plazaId") plazaId: Long
    ): Response<List<ViaDto>>

    /**
     * Envío y confirmación de evento vehicular.
     * POST /api/avi/registros
     */
    @Headers("Content-Type: application/json")
    @POST("api/avi/registros")
    suspend fun registrarEvento(
        @Body request: RegistroSigoRequest
    ): Response<RegistroSigoResponse>

    /**
     * Edición de un registro ya sincronizado.
     * PUT /api/avi/registros/{id}
     */
    @Headers("Content-Type: application/json")
    @PUT("api/avi/registros/{id}")
    suspend fun actualizarEvento(
        @Path("id") id: String,
        @Body request: RegistroSigoUpdateRequest
    ): Response<Unit>

    /**
     * Consulta de historial de eventos en el servidor SIGO.
     * GET /api/avi/registros
     */
    @GET("api/avi/registros")
    suspend fun consultarRegistros(
        @Query("desde") desde: String? = null,
        @Query("hasta") hasta: String? = null,
        @Query("plazaId") plazaId: String? = null,
        @Query("via") via: Int? = null,
        @Query("accion") accion: String? = null
    ): Response<List<RegistroSigoResponse>>

    companion object {
        const val PRODUCTION_BASE_URL = "https://sigo-back-production-70c2.up.railway.app/"
        const val DEFAULT_BASE_URL = PRODUCTION_BASE_URL
        const val LOCAL_MAC_BASE_URL = "http://172.20.10.8:8080/"
        const val EMULATOR_BASE_URL = "http://10.0.2.2:8080/"

        val defaultMoshi: Moshi = Moshi.Builder()
            .addLast(KotlinJsonAdapterFactory())
            .build()

        fun create(
            baseUrl: String,
            tokenProvider: () -> String? = { null }
        ): SigoApiService {
            val safeUrl = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"

            val authInterceptor = Interceptor { chain ->
                val originalRequest = chain.request()
                val token = tokenProvider()
                val newRequestBuilder = originalRequest.newBuilder()
                    .addHeader("Accept", "application/json")

                if (!token.isNullOrBlank()) {
                    newRequestBuilder.addHeader("Authorization", "Bearer $token")
                }
                chain.proceed(newRequestBuilder.build())
            }

            val loggingInterceptor = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            }

            val okHttpClient = OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .writeTimeout(20, TimeUnit.SECONDS)
                .addInterceptor(authInterceptor)
                .addInterceptor(loggingInterceptor)
                .build()

            return Retrofit.Builder()
                .baseUrl(safeUrl)
                .client(okHttpClient)
                .addConverterFactory(MoshiConverterFactory.create(defaultMoshi))
                .build()
                .create(SigoApiService::class.java)
        }
    }
}
