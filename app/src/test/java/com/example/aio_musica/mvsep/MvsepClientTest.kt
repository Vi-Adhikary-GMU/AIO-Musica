package com.example.aio_musica.mvsep

import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class MvsepClientTest {

    private lateinit var server: MockWebServer

    @Before
    fun setup() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    // createApi tests

    @Test
    fun `createApi creates API with custom base URL`() {
        val api = MvsepClient.createApi(
            baseUrl = server.url("/custom/").toString(),
            client = OkHttpClient()
        )
        assertNotNull(api)
    }

    @Test
    fun `createApi uses supplied OkHttp client`() = kotlinx.coroutines.runBlocking {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """
                    {
                        "success": true,
                        "status": "processing",
                        "data": null
                    }
                    """.trimIndent()
                )
        )
        val customClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request()
                    .newBuilder()
                    .addHeader("X-Test-Header", "custom-client")
                    .build()
                chain.proceed(request)
            }
            .build()
        val api = MvsepClient.createApi(
            baseUrl = server.url("/api/").toString(),
            client = customClient
        )
        val result = api.getSeparationStatus("test_hash")
        val request = server.takeRequest()
        assertEquals("custom-client", request.getHeader("X-Test-Header"))
        assertTrue(result.success)
        assertEquals("processing", result.status)
    }

    @Test
    fun `createApi rejects invalid base URL`() {
        try {
            MvsepClient.createApi(
                baseUrl = "not-a-valid-url",
                client = OkHttpClient()
            )
            fail("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertNotNull(e.message)
        }
    }

    // Shared API test

    @Test
    fun `shared API instance is available`() {
        assertNotNull(MvsepClient.api)
    }

    // createRepository tests

    @Test
    fun `createRepository uses shared API`() {
        val repository = MvsepClient.createRepository(
            apiToken = "test_token"
        )
        assertNotNull(repository)
        assertTrue(repository is MvsepRepository)
    }

    @Test
    fun `createRepository accepts custom API`() {
        val customApi = MvsepClient.createApi(
            baseUrl = server.url("/custom/").toString(),
            client = OkHttpClient()
        )
        val repository = MvsepClient.createRepository(
            apiToken = "test_token",
            api = customApi
        )
        assertNotNull(repository)
        assertTrue(repository is MvsepRepository)
    }
}
