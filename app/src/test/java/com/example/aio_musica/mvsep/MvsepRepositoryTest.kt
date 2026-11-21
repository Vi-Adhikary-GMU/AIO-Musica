package com.example.aio_musica.mvsep

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import okhttp3.MultipartBody
import okhttp3.RequestBody
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File
import java.io.IOException

class MvsepRepositoryTest {

    private lateinit var api: FakeMvsepApi
    private lateinit var repository: MvsepRepository
    private lateinit var audioFile: File

    @Before
    fun setup() {
        api = FakeMvsepApi()
        repository = MvsepRepository(
            api = api,
            apiToken = "test_token"
        )
        audioFile = File.createTempFile(
            "test_audio",
            ".mp3"
        )
    }

    @After
    fun tearDown() {
        audioFile.delete()
    }

    private fun successfulCreation() =
        MvsepCreateResponse(
            success = true,
            data = MvsepJobData(
                hash = "test_hash",
                link = null,
                message = "Created"
            )
        )

    private fun status(
        value: String,
        success: Boolean = true,
        files: List<MvsepStem>? = null,
        message: String? = null
    ) = MvsepStatusResponse(
        success = success,
        status = value,
        data = MvsepResultData(
            message = message,
            files = files
        )
    )

    private fun validStem(
        name: String = "vocals",
        type: String? = "vocals",
        url: String? = "https://example.com/vocals.wav"
    ) = MvsepStem(
        name = name,
        type = type,
        url = url
    )

    @Test
    fun `separate song completes successfully`() = runTest {
        api.statusResponses.add(
            status(
                "done",
                files = listOf(validStem())
            )
        )
        val result = repository.separateSong(audioFile)
        assertEquals(1, result.size)
        assertEquals("vocals", result[0].name)
        assertEquals(1, api.statusCalls)
        assertEquals("test_hash", api.requestedHash)
    }

    @Test
    fun `separate song handles waiting status`() = runTest {
        api.statusResponses.add(status("waiting"))
        api.statusResponses.add(
            status("done", files = listOf(validStem()))
        )
        val result = repository.separateSong(audioFile)
        assertEquals(1, result.size)
        assertEquals(2, api.statusCalls)
    }

    @Test
    fun `separate song handles processing status`() = runTest {
        api.statusResponses.add(status("processing"))
        api.statusResponses.add(
            status("done", files = listOf(validStem()))
        )
        val result = repository.separateSong(audioFile)
        assertEquals(1, result.size)
        assertEquals(2, api.statusCalls)
    }

    @Test
    fun `separate song handles distributing status`() = runTest {
        api.statusResponses.add(status("distributing"))
        api.statusResponses.add(
            status("done", files = listOf(validStem()))
        )
        val result = repository.separateSong(audioFile)
        assertEquals(1, result.size)
    }

    @Test
    fun `separate song handles merging status`() = runTest {
        api.statusResponses.add(status("merging"))
        api.statusResponses.add(
            status("done", files = listOf(validStem()))
        )
        val result = repository.separateSong(audioFile)
        assertEquals(1, result.size)
    }

    @Test
    fun `separate song handles uppercase status`() = runTest {
        api.statusResponses.add(status("DONE", files = listOf(validStem())))
        val result = repository.separateSong(audioFile)
        assertEquals(1, result.size)
    }

    @Test
    fun `separate song handles failed creation`() = runTest {
        api.creationResponse = MvsepCreateResponse(
            false,
            MvsepJobData(null, null, "Upload rejected")
        )
        try {
            repository.separateSong(audioFile)
            fail("Expected IOException")
        } catch (e: IOException) {
            assertEquals("Upload rejected", e.message)
        }
    }

    @Test
    fun `separate song handles failed creation without message`() = runTest {
        api.creationResponse = MvsepCreateResponse(
            false,
            null
        )
        try {
            repository.separateSong(audioFile)
            fail("Expected IOException")
        } catch (e: IOException) {
            assertEquals("MVSEP upload failed.", e.message)
        }
    }

    @Test
    fun `separate song handles missing creation hash`() = runTest {
        api.creationResponse = MvsepCreateResponse(
            true,
            MvsepJobData(null, null, null)
        )
        try {
            repository.separateSong(audioFile)
            fail("Expected IOException")
        } catch (e: IOException) {
            assertTrue(e.message!!.contains("hash"))
        }
    }

    @Test
    fun `separate song handles blank creation hash`() = runTest {
        api.creationResponse = MvsepCreateResponse(
            true,
            MvsepJobData("   ", null, null)
        )
        try {
            repository.separateSong(audioFile)
            fail("Expected IOException")
        } catch (e: IOException) {
            assertTrue(e.message!!.contains("hash"))
        }
    }

    @Test
    fun `separate song handles failed processing`() = runTest {
        api.statusResponses.add(
            status("failed", message = "Processing failed")
        )
        try {
            repository.separateSong(audioFile)
            fail("Expected IOException")
        } catch (e: IOException) {
            assertEquals("Processing failed", e.message)
        }
    }

    @Test
    fun `separate song handles failed processing without message`() = runTest {
        api.statusResponses.add(status("failed"))
        try {
            repository.separateSong(audioFile)
            fail("Expected IOException")
        } catch (e: IOException) {
            assertEquals(
                "MVSEP audio separation failed.",
                e.message
            )
        }
    }

    @Test
    fun `separate song handles missing job`() = runTest {
        api.statusResponses.add(status("not_found"))
        try {
            repository.separateSong(audioFile)
            fail("Expected IOException")
        } catch (e: IOException) {
            assertTrue(e.message!!.contains("not found"))
        }
    }

    @Test
    fun `separate song handles unsuccessful status response`() = runTest {
        api.statusResponses.add(
            status(
                "processing",
                success = false,
                message = "Status request rejected"
            )
        )

        try {
            repository.separateSong(audioFile)
            fail("Expected IOException")
        } catch (e: IOException) {
            assertEquals("Status request rejected", e.message)
        }
    }

    @Test
    fun `separate song handles unsuccessful status without message`() = runTest {
        api.statusResponses.add(
            status("processing", success = false)
        )
        try {
            repository.separateSong(audioFile)
            fail("Expected IOException")
        } catch (e: IOException) {
            assertEquals(
                "Could not retrieve separation status.",
                e.message
            )
        }
    }

    @Test
    fun `separate song handles unknown status`() = runTest {
        api.statusResponses.add(status("unknown_status"))
        try {
            repository.separateSong(audioFile)
            fail("Expected IOException")
        } catch (e: IOException) {
            assertTrue(e.message!!.contains("Unknown MVSEP status"))
        }
    }

    @Test
    fun `separate song handles empty output list`() = runTest {
        api.statusResponses.add(status("done", files = emptyList()))
        try {
            repository.separateSong(audioFile)
            fail("Expected IOException")
        } catch (e: IOException) {
            assertTrue(e.message!!.contains("no valid output files"))
        }
    }

    @Test
    fun `separate song handles null output list`() = runTest {
        api.statusResponses.add(status("done"))
        try {
            repository.separateSong(audioFile)
            fail("Expected IOException")
        } catch (e: IOException) {
            assertTrue(e.message!!.contains("no valid output files"))
        }
    }

    @Test
    fun `separate song ignores stems with missing URLs`() = runTest {
        api.statusResponses.add(
            status(
                "done",
                files = listOf(
                    validStem("vocals", "vocals", null),
                    validStem("bass", "bass", "https://example.com/bass.wav")
                )
            )
        )
        val result = repository.separateSong(audioFile)
        assertEquals(1, result.size)
        assertEquals("bass", result[0].name)
    }

    @Test
    fun `separate song ignores blank URLs`() = runTest {
        api.statusResponses.add(
            status(
                "done",
                files = listOf(
                    validStem("vocals", "vocals", ""),
                    validStem("bass", "bass", "https://example.com/bass.wav")
                )
            )
        )
        val result = repository.separateSong(audioFile)
        assertEquals(1, result.size)
        assertEquals("bass", result[0].name)
    }

    @Test
    fun `separate song handles all invalid output URLs`() = runTest {
        api.statusResponses.add(
            status(
                "done",
                files = listOf(
                    validStem("vocals", "vocals", null),
                    validStem("bass", "bass", "")
                )
            )
        )
        try {
            repository.separateSong(audioFile)
            fail("Expected IOException")
        } catch (e: IOException) {
            assertTrue(e.message!!.contains("no valid output files"))
        }
    }

    @Test
    fun `separate song rejects missing audio file`() = runTest {
        val missingFile = File(
            audioFile.parentFile,
            "missing_audio_file.mp3"
        )
        try {
            repository.separateSong(missingFile)
            fail("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("does not exist"))
        }
    }

    @Test
    fun `separate song rejects directory instead of audio file`() = runTest {
        val directory = File.createTempFile("test_directory", ".tmp")
        directory.delete()
        directory.mkdir()
        try {
            repository.separateSong(directory)
            fail("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("does not exist"))
        } finally {
            directory.delete()
        }
    }

    @Test
    fun `separate song rejects blank API token`() = runTest {
        val invalidRepository = MvsepRepository(
            api,
            "   "
        )
        try {
            invalidRepository.separateSong(audioFile)
            fail("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("token"))
        }
    }

    @Test
    fun `separate song preserves IOException`() = runTest {
        api.creationException = IOException("Network failure")
        try {
            repository.separateSong(audioFile)
            fail("Expected IOException")
        } catch (e: IOException) {
            assertEquals("Network failure", e.message)
        }
    }

    @Test
    fun `separate song wraps unexpected creation exception`() = runTest {
        api.creationException = IllegalStateException("Unexpected failure")
        try {
            repository.separateSong(audioFile)
            fail("Expected IOException")
        } catch (e: IOException) {
            assertEquals(
                "An error occurred while communicating with MVSEP",
                e.message
            )
            assertTrue(e.cause is IllegalStateException)
        }
    }

    @Test
    fun `separate song wraps unexpected status exception`() = runTest {
        api.statusException = IllegalStateException("Unexpected status error")
        try {
            repository.separateSong(audioFile)
            fail("Expected IOException")
        } catch (e: IOException) {
            assertTrue(e.cause is IllegalStateException)
        }
    }

    @Test
    fun `separate song preserves coroutine cancellation`() = runTest {
        api.blockStatusRequest = true
        val job = async {
            repository.separateSong(audioFile)
        }
        runCurrent()
        job.cancelAndJoin()
        assertTrue(job.isCancelled)
    }

    @Test
    fun `separate song times out after maximum polling attempts`() = runTest {
        api.alwaysReturnProcessing = true
        try {
            repository.separateSong(audioFile)
            fail("Expected IOException")
        } catch (e: IOException) {
            assertTrue(e.message!!.contains("timed out"))
            assertEquals(180, api.statusCalls)
        }
    }

    private class FakeMvsepApi : MvsepApi {
        var creationResponse = MvsepCreateResponse(
            true,
            MvsepJobData("test_hash", null, null)
        )
        var creationException: Exception? = null
        var statusException: Exception? = null
        val statusResponses = mutableListOf<MvsepStatusResponse>()
        var statusCalls = 0
        var requestedHash: String? = null
        var alwaysReturnProcessing = false
        var blockStatusRequest = false
        override suspend fun createSeparation(
            token: RequestBody,
            separationType: RequestBody,
            outputFormat: RequestBody,
            audiofile: MultipartBody.Part
        ): MvsepCreateResponse {
            creationException?.let {
                throw it
            }
            return creationResponse
        }
        override suspend fun getSeparationStatus(
            hash: String
        ): MvsepStatusResponse {
            statusCalls++
            requestedHash = hash
            if (blockStatusRequest) {
                awaitCancellation()
            }
            statusException?.let {
                throw it
            }
            if (alwaysReturnProcessing) {
                return MvsepStatusResponse(
                    success = true,
                    status = "processing",
                    data = null
                )
            }
            if (statusResponses.isEmpty()) {
                throw IOException("No fake status response configured")
            }
            return statusResponses.removeAt(0)
        }
    }
}