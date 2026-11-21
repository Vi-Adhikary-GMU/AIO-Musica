package com.example.aio_musica.mvsep

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.net.URI
import java.util.Locale
import kotlin.coroutines.coroutineContext

/**
 * Represents an audio stem successfully downloaded from MVSEP.
 *
 * @property name Sanitized name of the audio stem.
 * @property file Local file containing the downloaded audio.
 */
data class DownloadedStem(
    val name: String,
    val file: File
)

/**
 * Handles downloading separated audio stems from MVSEP.
 *
 * Downloads files using streaming to avoid loading large audio files
 * entirely into memory.
 *
 * Temporary files are used during downloads to prevent incomplete
 * audio files from being treated as successfully downloaded files.
 *
 * If a batch download fails, files successfully downloaded during
 * that batch are removed.
 *
 * @property client OkHttp client used to perform HTTP requests.
 */
class MvsepDownloader(
    private val client: OkHttpClient = OkHttpClient()
) {

    /**
     * Downloads all separated audio stems into the specified directory.
     *
     * Each audio file is streamed into a temporary file before being
     * moved to its final destination.
     *
     * Existing files are preserved. If a filename already exists,
     * a numeric suffix is added to avoid overwriting it.
     *
     * If any download fails, previously downloaded files from the
     * current batch are deleted.
     *
     * @param stems List of audio stems returned by MVSEP.
     * @param destinationDir Directory where downloaded audio files
     * will be stored.
     *
     * @return List of successfully downloaded audio stems.
     *
     * @throws IllegalArgumentException If no stems are provided or
     * the destination directory is invalid.
     * @throws IOException If a download or file operation fails.
     * @throws kotlinx.coroutines.CancellationException If the
     * coroutine is cancelled.
     */
    suspend fun downloadStems(
        stems: List<MvsepStem>,
        destinationDir: File
    ): List<DownloadedStem> = withContext(Dispatchers.IO) {
        require(stems.isNotEmpty()) {
            "No audio stems were provided"
        }
        if (!destinationDir.exists()) {
            require(destinationDir.mkdirs()) {
                "Could not create destination directory"
            }
        }
        require(destinationDir.isDirectory) {
            "Destination path is not a directory"
        }
        val downloadedStems = mutableListOf<DownloadedStem>()
        try {
            stems.forEachIndexed { index, stem ->
                coroutineContext.ensureActive()
                val url = stem.url
                    ?: throw IOException(
                        "Missing download URL for stem ${index + 1}"
                    )
                val parsedUrl = try {
                    URI(url)
                } catch (e: Exception) {
                    throw IOException(
                        "Invalid download URL for stem ${index + 1}",
                        e
                    )
                }
                require(
                    parsedUrl.scheme.equals("https", ignoreCase = true) &&
                            !parsedUrl.host.isNullOrBlank()
                ) {
                    "Invalid or insecure audio download URL"
                }
                val request = Request.Builder()
                    .url(url)
                    .get()
                    .build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw IOException(
                            "Download failed: HTTP ${response.code}"
                        )
                    }
                    val body = response.body
                        ?: throw IOException("Empty audio response")
                    val originalName = stem.name
                        ?.takeIf { it.isNotBlank() }
                        ?: "stem_${index + 1}"
                    val extension = getAudioExtension(url)
                    val safeName = originalName
                        .substringBeforeLast(".")
                        .replace(
                            Regex("[^a-zA-Z0-9_-]"),
                            "_"
                        )
                        .ifBlank {
                            "stem_${index + 1}"
                        }
                    val outputFile = getUniqueFile(
                        directory = destinationDir,
                        name = safeName,
                        extension = extension
                    )
                    // Create a unique temporary file in the same directory.
                    val tempFile = File.createTempFile(
                        "mvsep_",
                        ".part",
                        destinationDir
                    )
                    try {
                        body.byteStream().use { input ->
                            tempFile.outputStream().use { output ->
                                val buffer = ByteArray(8192)
                                while (true) {
                                    coroutineContext.ensureActive()
                                    val bytesRead = input.read(buffer)
                                    if (bytesRead == -1) {
                                        break
                                    }
                                    output.write(buffer, 0, bytesRead)
                                }
                                output.flush()
                            }
                        }
                        coroutineContext.ensureActive()
                        // Move the completed temporary file to its final location.
                        if (!tempFile.renameTo(outputFile)) {
                            throw IOException(
                                "Could not save audio file: ${outputFile.name}"
                            )
                        }
                        downloadedStems.add(
                            DownloadedStem(
                                name = safeName,
                                file = outputFile
                            )
                        )
                    } finally {
                        // Remove temporary files whether successful or unsuccessful.
                        if (tempFile.exists()) {
                            tempFile.delete()
                        }
                    }
                }
            }
            downloadedStems
        } catch (e: Exception) {
            // Remove only files downloaded during this batch.
            downloadedStems.forEach { downloadedStem ->
                downloadedStem.file.delete()
            }
            // Preserve the original exception, including cancellation.
            throw e
        }
    }

    /**
     * Generates a filename that does not conflict with existing files.
     * Prevents downloaded audio stems from overwriting existing files.
     *
     * For example:
     * - vocals.wav
     * - vocals_2.wav
     * - vocals_3.wav
     *
     * @param directory Destination directory.
     * @param name Sanitized stem name.
     * @param extension Audio file extension.
     *
     * @return A file with a currently unused filename.
     */
    private fun getUniqueFile(
        directory: File,
        name: String,
        extension: String
    ): File {
        var outputFile = File(directory, "$name.$extension")
        var counter = 2
        while (outputFile.exists()) {
            outputFile = File(directory, "${name}_$counter.$extension")
            counter++
        }
        return outputFile
    }

    /**
     * Determines the audio file extension from its download URL,
     * including MP3, WAV, FLAC, M4A and OGG.
     *
     * WAV is used as the fallback when the URL does not contain
     * a recognized extension.
     *
     * @param url Audio download URL.
     * @return Recognized audio extension without a leading dot.
     */
    private fun getAudioExtension(url: String): String {
        val extension = try {
            URI(url).path
                .orEmpty()
                .substringAfterLast('.', "")
                .lowercase(Locale.ROOT)
        } catch (e: Exception) {
            ""
        }
        return when (extension) {
            "mp3", "wav", "flac", "m4a", "ogg" -> extension
            else -> "wav"
        }
    }
}