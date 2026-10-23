package com.example.aio_musica.file_upload

import android.net.Uri
import io.ktor.client.HttpClient
import io.ktor.client.plugins.onUpload
import io.ktor.client.request.forms.formData
import io.ktor.client.request.forms.submitFormWithBinaryData
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow

class FileRepository (
    private val httpClient: HttpClient,
    private val fileReader: FileReader,
) {

    /**
     * Uploads a file to the server.
     * @param contentUri The URI of the file to upload.
     * @return A flow of [ProgressInfo] objects representing the upload progress.
     */
    fun uploadFile(contentUri: Uri) : Flow<ProgressUpdate> = channelFlow {
        val info = fileReader.uriToFileInfo(contentUri)

        httpClient.submitFormWithBinaryData(
            url = "https://dlptest.com/https-post/", // TODO: This uses a test website, we need to replace this with our own actual backend
            formData = formData {
                append("description", "Test") //TODO: This is the specification for the test website. Once we implement our own backend the specifications will probably change.
                append("the_file", info.bytes, Headers.build {
                    append(HttpHeaders.ContentType, info.mimeType)
                    append(HttpHeaders.ContentDisposition, "filename=${info.name}")
                })
            }
        ) {
            onUpload { bytesSentTotal, totalBytes ->
                if (totalBytes != null) {
                    if(totalBytes > 0) {
                        send(ProgressUpdate(bytesSentTotal, totalBytes))
                    }
                }
            }
        }
    }
}

/**
 * Contains information about the current upload progress.
 * @param bytesSent The number of bytes sent.
 * @param totalBytes The total number of bytes to send.
 */
data class ProgressUpdate(
    val bytesSent: Long,
    val totalBytes: Long
)