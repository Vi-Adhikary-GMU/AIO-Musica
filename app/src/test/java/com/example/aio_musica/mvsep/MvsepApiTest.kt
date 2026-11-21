package com.example.aio_musica.mvsep

import kotlinx.coroutines.runBlocking
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import retrofit2.HttpException
import java.io.File

class MvsepApiTest {

    private lateinit var server: MockWebServer
    private lateinit var api: MvsepApi
    private lateinit var audioFile: File

    @Before
    fun setup() {
        server = MockWebServer()
        server.start()
        api = MvsepClient.createApi(
            baseUrl = server.url("/api/").toString(),
            client = OkHttpClient()
        )
        audioFile = File.createTempFile(
            "test_audio",
            ".mp3"
        )
        audioFile.writeBytes(byteArrayOf(1, 2, 3, 4, 5))
    }

    @After
    fun tearDown() {
        server.shutdown()
        audioFile.delete()
    }

    // createSeparation tests

    @Test
    fun `create separation sends correct multipart request`() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """
                    {
                        "success": true,
                        "data": {
                            "hash": "test_hash",
                            "link": "https://mvsep.com/result",
                            "message": "Created"
                        }
                    }
                    """.trimIndent()
                )
        )
        val audioPart = MultipartBody.Part.createFormData(
            "audiofile",
            audioFile.name,
            audioFile.readBytes().toRequestBody()
        )
        val result = api.createSeparation(
            token = "test_token".toRequestBody(),
            separationType = "63".toRequestBody(),
            outputFormat = "0".toRequestBody(),
            audiofile = audioPart
        )
        val request = server.takeRequest()
        val requestBody = request.body.readUtf8()
        assertEquals("POST", request.method)
        assertEquals(
            "/api/separation/create",
            request.path
        )
        assertTrue(
            request.getHeader("Content-Type")!!
                .contains("multipart/form-data")
        )
        assertTrue(requestBody.contains("name=\"api_token\""))
        assertTrue(requestBody.contains("test_token"))
        assertTrue(requestBody.contains("name=\"sep_type\""))
        assertTrue(requestBody.contains("63"))
        assertTrue(requestBody.contains("name=\"output_format\""))
        assertTrue(requestBody.contains("0"))
        assertTrue(requestBody.contains("name=\"audiofile\""))
        assertTrue(requestBody.contains(audioFile.name))
        assertTrue(result.success)
        assertEquals("test_hash", result.data?.hash)
        assertEquals("Created", result.data?.message)
    }

    @Test
    fun `create separation deserializes failed response`() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """
                    {
                        "success": false,
                        "data": {
                            "hash": null,
                            "link": null,
                            "message": "Invalid token"
                        }
                    }
                    """.trimIndent()
                )
        )
        val audioPart = MultipartBody.Part.createFormData(
            "audiofile",
            audioFile.name,
            audioFile.readBytes().toRequestBody()
        )
        val result = api.createSeparation(
            "test_token".toRequestBody(),
            "63".toRequestBody(),
            "0".toRequestBody(),
            audioPart
        )
        assertFalse(result.success)
        assertNull(result.data?.hash)
        assertEquals("Invalid token", result.data?.message)
    }

    @Test
    fun `create separation handles HTTP error`() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(500)
                .setBody("Internal Server Error")
        )
        val audioPart = MultipartBody.Part.createFormData(
            "audiofile",
            audioFile.name,
            audioFile.readBytes().toRequestBody()
        )
        try {
            api.createSeparation(
                "test_token".toRequestBody(),
                "63".toRequestBody(),
                "0".toRequestBody(),
                audioPart
            )
            fail("Expected HttpException")
        } catch (e: HttpException) {
            assertEquals(500, e.code())
        }
    }

    // getSeparationStatus tests

    @Test
    fun `get separation status sends correct GET request`() = runBlocking {

        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """
                    {
                        "success": true,
                        "status": "done",
                        "data": {
                            "message": "Completed",
                            "files": [
                                {
                                    "name": "vocals",
                                    "type": "audio",
                                    "url": "https://example.com/vocals.wav"
                                }
                            ]
                        }
                    }
                    """.trimIndent()
                )
        )
        val result = api.getSeparationStatus("test_hash")
        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals(
            "/api/separation/get?hash=test_hash",
            request.path
        )
        assertTrue(result.success)
        assertEquals("done", result.status)
        assertEquals(1, result.data?.files?.size)
        assertEquals(
            "vocals",
            result.data?.files?.get(0)?.name
        )
        assertEquals(
            "audio",
            result.data?.files?.get(0)?.type
        )
    }

    @Test
    fun `get separation status handles processing response`() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
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
        val result = api.getSeparationStatus("processing_hash")
        val request = server.takeRequest()
        assertEquals(
            "/api/separation/get?hash=processing_hash",
            request.path
        )
        assertTrue(result.success)
        assertEquals("processing", result.status)
        assertNull(result.data)
    }

    @Test
    fun `get separation status handles HTTP error`() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(404)
                .setBody("Job not found")
        )
        try {
            api.getSeparationStatus("missing_hash")
            fail("Expected HttpException")
        } catch (e: HttpException) {
            assertEquals(404, e.code())
        }
    }
}
