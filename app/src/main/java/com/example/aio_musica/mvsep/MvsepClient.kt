package com.example.aio_musica.mvsep

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Provides a centralized configuration for communicating with the MVSEP API.
 * Initializes Retrofit and OkHttp, configures network timeouts,
 * and exposes an API instance for audio separation operations.
 */
object MvsepClient {

    /**
     * Base URL for the MVSEP API.
     * */
    private const val BASE_URL = "https://mvsep.com/api/"

    /**
     * Enables basic HTTP logging messages.
     *
     * Can be set to true during app testing to see logging messages,
     * but keep it disabled during actual app usage
     */
    private const val ENABLE_HTTP_LOGGING = false

    /**
     * Creates an MVSEP API instance using the supplied Retrofit configuration.
     *
     * @param baseUrl Base URL of the API. Must end with a slash.
     * @param client HTTP client used for network requests.
     */
    internal fun createApi(
        baseUrl: String = BASE_URL,
        client: OkHttpClient = httpClient
    ): MvsepApi {
        val retrofit = Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(
                GsonConverterFactory.create()
            )
            .build()
        return retrofit.create(MvsepApi::class.java)
    }

    /**
     * Configures HTTP request logging.
     *
     * BASIC logs request URLs and response status information.
     * BODY logging is intentionally avoided.
     */
    private val loggingInterceptor =
        HttpLoggingInterceptor().apply {
            level = if (ENABLE_HTTP_LOGGING) {
                HttpLoggingInterceptor.Level.BASIC
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }

    /**
     * Configures the OkHttp client used for MVSEP requests.
     * Longer write timeouts accommodate potentially large audio uploads.
     */
    private val httpClient: OkHttpClient =
        OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(120, TimeUnit.SECONDS)
            .build()

    /**
     * Shared MVSEP API interface.
     * Used for submitting separation requests and checking job status.
     */
    val api: MvsepApi = createApi()

    /**
     * Creates an MVSEP repository using the shared API instance.
     *
     * @param apiToken Authentication token required by MVSEP.
     * @param api API implementation to use. Defaults to the shared
     * production API instance.
     *
     * @return Configured [MvsepRepository].
     */
    fun createRepository(
        apiToken: String,
        api: MvsepApi = this.api
    ): MvsepRepository {
        return MvsepRepository(api = api, apiToken = apiToken)
    }
}