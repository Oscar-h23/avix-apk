package com.example.api

import com.example.BuildConfig
import com.example.model.*
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.*
import java.util.concurrent.TimeUnit

interface AvixApiService {
    @POST("api/v1/auth/movil")
    suspend fun login(@Body request: LoginRequest): Response<LoginResponse>

    @GET("api/v1/status")
    suspend fun getStatus(): Response<AvixStatusResponse>

    @GET("api/v1/catalogos/plazas")
    suspend fun listarPlazas(): Response<List<PlazaDto>>

    @GET("api/v1/catalogos/vias")
    suspend fun listarVias(@Query("plaza") plaza: String): Response<List<ViaDto>>

    @POST("api/v1/registros")
    suspend fun registrarEvento(@Body request: RegistroAvixRequest): Response<RegistroAvixResponse>

    @PATCH("api/v1/registros/{id}")
    suspend fun actualizarEvento(@Path("id") id: String, @Body request: RegistroAvixUpdateRequest): Response<Unit>

    @GET("api/v1/registros/consulta")
    suspend fun consultarRegistros(
        @Query("desde") desde: String,
        @Query("hasta") hasta: String,
        @Query("plaza") plaza: String? = null,
        @Query("via") via: Int? = null,
        @Query("accion") accion: String? = null
    ): Response<List<RegistroAvixResponse>>

    companion object {
        val PRODUCTION_BASE_URL: String = BuildConfig.AVIX_API_BASE_URL
            .ifBlank { if (BuildConfig.DEBUG) "http://172.18.20.32:8080/" else "https://avix-api.invalid/" }
            .trimEnd('/') + "/"
        val DEFAULT_BASE_URL: String = PRODUCTION_BASE_URL
        const val LOCAL_MAC_BASE_URL = "http://172.18.20.32:8080/"
        const val EMULATOR_BASE_URL = "http://10.0.2.2:8080/"

        val defaultMoshi: Moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()

        fun create(baseUrl: String, tokenProvider: () -> String? = { null }): AvixApiService {
            val authentication = Interceptor { chain ->
                val builder = chain.request().newBuilder().header("Accept", "application/json")
                tokenProvider()?.takeIf { it.isNotBlank() }?.let { builder.header("Authorization", "Bearer $it") }
                chain.proceed(builder.build())
            }
            val client = OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .writeTimeout(20, TimeUnit.SECONDS)
                .addInterceptor(authentication)
                .build()
            return Retrofit.Builder().baseUrl(baseUrl.trimEnd('/') + "/")
                .client(client)
                .addConverterFactory(MoshiConverterFactory.create(defaultMoshi))
                .build().create(AvixApiService::class.java)
        }
    }
}
