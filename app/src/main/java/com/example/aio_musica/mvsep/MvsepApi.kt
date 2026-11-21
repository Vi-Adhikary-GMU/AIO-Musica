package com.example.aio_musica.mvsep

import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.http.*

/**
 * Retrofit API interface for communicating with the MVSEP audio separation service.
 * Provides endpoints for submitting audio separation jobs and retrieving
 * their processing status and generated audio stems.
 *
 * All API operations are suspend functions and should be called from
 * a coroutine or another suspend function.
 */
interface MvsepApi {
    /**
     * Submits an audio file to MVSEP for audio source separation.
     *
     * @param token API authentication token sent as a multipart form field.
     * @param separationType Separation model or type requested from MVSEP.
     * @param outputFormat Desired output audio format.
     * @param audiofile Audio file attached as a multipart file part.
     *
     * @return [MvsepCreateResponse] containing the submission result
     * and job information when available.
     *
     * @throws retrofit2.HttpException If the server returns an unsuccessful
     * HTTP response.
     * @throws java.io.IOException If a network communication error occurs.
     */
    @Multipart
    @POST("separation/create")
    suspend fun createSeparation(
        @Part("api_token") token: RequestBody,
        @Part("sep_type") separationType: RequestBody,
        @Part("output_format") outputFormat: RequestBody,
        @Part audiofile: MultipartBody.Part
    ): MvsepCreateResponse

    /**
     * Retrieves the current processing status of an MVSEP separation job.
     *
     * @param hash Unique identifier returned when the separation job
     * was initially submitted.
     *
     * @return [MvsepStatusResponse] containing the job status and
     * generated audio stems when processing is complete.
     *
     * @throws retrofit2.HttpException If the server returns an unsuccessful
     * HTTP response.
     * @throws java.io.IOException If a network communication error occurs.
     */
    @GET("separation/get")
    suspend fun getSeparationStatus(
        @Query("hash") hash: String
    ): MvsepStatusResponse
}