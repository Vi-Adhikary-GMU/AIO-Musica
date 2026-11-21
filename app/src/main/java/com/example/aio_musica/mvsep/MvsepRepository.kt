
package com.example.aio_musica.mvsep

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.IOException

/**
 * Repository responsible for communicating with the MVSEP API.
 *
 * Handles audio uploads, separation job creation, processing status
 * polling, and retrieval of generated audio stems.
 *
 * @property api Retrofit API interface used for MVSEP requests.
 * @property apiToken Authentication token required by MVSEP.
 */
class MvsepRepository(
    private val api: MvsepApi,
    private val apiToken: String
) {

    /**
     * Uploads an audio file to MVSEP and waits for the separation
     * process to finish.
     *
     * The method submits an audio separation request, retrieves
     * the generated job hash, and periodically checks the job status.
     *
     * @param file Audio file to upload.
     *
     * @return List of generated audio stems when separation completes.
     *
     * @throws IllegalArgumentException If the audio file is missing
     * or the API token is empty.
     * @throws IOException If the API request fails, the job fails,
     * or no output stems are returned.
     * @throws CancellationException If the calling coroutine is cancelled.
     */
    suspend fun separateSong(file: File): List<MvsepStem> {
        require(file.exists() && file.isFile) {
            "Audio file does not exist."
        }
        require(apiToken.isNotBlank()) {
            "MVSEP API token is missing."
        }
        try {
            // Prepare audio file for multipart upload.
            val audioBody = file.asRequestBody(
                "audio/*".toMediaType()
            )
            val audioPart = MultipartBody.Part.createFormData(
                "audiofile",
                file.name,
                audioBody
            )
            // Prepare API parameters.
            val tokenBody = apiToken.toRequestBody()
            val typeBody = "63".toRequestBody()
            val formatBody = "0".toRequestBody()
            // Submit audio separation request.
            val response = api.createSeparation(
                tokenBody,
                typeBody,
                formatBody,
                audioPart
            )
            if (!response.success) {
                throw IOException(
                    response.data?.message
                        ?: "MVSEP upload failed."
                )
            }
            // Retrieve the job identifier.
            val hash = response.data?.hash
                ?.takeIf { it.isNotBlank() }
                ?: throw IOException(
                    "MVSEP did not return a valid separation hash."
                )
            // Poll until separation completes or times out.
            repeat(MAX_POLL_ATTEMPTS) {
                delay(POLL_INTERVAL_MS)
                val status = api.getSeparationStatus(hash)
                if (!status.success) {
                    throw IOException(
                        status.data?.message
                            ?: "Could not retrieve separation status."
                    )
                }
                when (status.status.lowercase()) {
                    "done" -> {
                        return status.data?.files
                            ?.filter {
                                !it.url.isNullOrBlank()
                            }
                            ?.takeIf {
                                it.isNotEmpty()
                            }
                            ?: throw IOException(
                                "Separation completed, but no valid output files were found."
                            )
                    }
                    "failed" -> {
                        throw IOException(
                            status.data?.message
                                ?: "MVSEP audio separation failed."
                        )
                    }
                    "not_found" -> {
                        throw IOException(
                            "MVSEP separation job was not found."
                        )
                    }
                    "waiting",
                    "processing",
                    "distributing",
                    "merging" -> {
                        // Job is still processing.
                    }
                    else -> {
                        throw IOException(
                            "Unknown MVSEP status: ${status.status}"
                        )
                    }
                }
            }
            throw IOException(
                "Audio separation timed out after 15 minutes."
            )
        } catch (e: CancellationException) {
            // Never swallow coroutine cancellation.
            throw e
        } catch (e: IOException) {
            throw e
        } catch (e: Exception) {
            // Convert unexpected network or API exceptions.
            throw IOException(
                "An error occurred while communicating with MVSEP",
                e
            )
        }
    }
    private companion object {

        /** 
         * Maximum number of status polling attempts. 
         * */
        const val MAX_POLL_ATTEMPTS = 180
        /** 
         * Delay between consecutive status requests. 
         * */
        const val POLL_INTERVAL_MS = 5_000L
    }
}
