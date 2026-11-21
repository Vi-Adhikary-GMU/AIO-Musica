package com.example.aio_musica.mvsep

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import java.net.InetAddress
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File
import java.io.IOException
import java.nio.file.Files

class MvsepDownloaderTest {

    private lateinit var server: MockWebServer
    private lateinit var downloader: MvsepDownloader
    private lateinit var tempDirectory: File

    @Before
    fun setup() {
        server = MockWebServer()
        val certificate = HeldCertificate.Builder()
            .commonName("localhost")
            .addSubjectAlternativeName("localhost")
            .addSubjectAlternativeName("127.0.0.1")
            .build()
        val serverCertificates = HandshakeCertificates.Builder()
            .heldCertificate(certificate)
            .build()
        val clientCertificates = HandshakeCertificates.Builder()
            .addTrustedCertificate(certificate.certificate)
            .build()
        server.useHttps(
            serverCertificates.sslSocketFactory(),
            false
        )
        server.start(InetAddress.getByName("127.0.0.1"), 0)
        val client = OkHttpClient.Builder()
            .sslSocketFactory(
                clientCertificates.sslSocketFactory(),
                clientCertificates.trustManager
            )
            .build()
        downloader = MvsepDownloader(client)
        tempDirectory = Files.createTempDirectory(
            "mvsep_test"
        ).toFile()
    }

    @After
    fun tearDown() {
        server.shutdown()
        tempDirectory.deleteRecursively()
    }

    private fun stem(
        name: String?,
        path: String?,
        type: String? = null
    ): MvsepStem {
        return MvsepStem(
            name = name,
            type = type,
            url = path?.let {
                server.url(it)
                    .newBuilder()
                    .scheme("https")
                    .host("127.0.0.1")
                    .build()
                    .toString()
            }
        )
    }

    @Test
    fun `download single stem successfully`() = runBlocking {
        val audioBytes = byteArrayOf(1, 2, 3, 4, 5)
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(okio.Buffer().write(audioBytes))
        )
        val result = downloader.downloadStems(
            listOf(stem("vocals", "/vocals.wav")),
            tempDirectory
        )
        assertEquals(1, result.size)
        assertEquals("vocals", result[0].name)
        assertTrue(result[0].file.exists())
        assertArrayEquals(audioBytes, result[0].file.readBytes())
    }

    @Test
    fun `download multiple stems successfully`() = runBlocking {
        server.enqueue(MockResponse().setBody("vocals"))
        server.enqueue(MockResponse().setBody("bass"))
        val result = downloader.downloadStems(
            listOf(
                stem("vocals", "/vocals.wav"),
                stem("bass", "/bass.wav")
            ),
            tempDirectory
        )
        assertEquals(2, result.size)
        assertTrue(result.all { it.file.exists() })
    }

    @Test
    fun `download uses fallback name when name and type are null`() =
        runBlocking {
            server.enqueue(MockResponse().setBody("audio"))
            val result = downloader.downloadStems(
                listOf(stem(null, "/audio.wav")),
                tempDirectory
            )
            assertEquals("stem_1", result[0].name)
        }

    @Test
    fun `download uses type when name is null`() = runBlocking {
        server.enqueue(MockResponse().setBody("audio"))
        val result = downloader.downloadStems(
            listOf(
                stem(
                    name = null,
                    path = "/audio.wav",
                    type = "vocals"
                )
            ),
            tempDirectory
        )
        assertEquals("vocals", result[0].name)
    }

    @Test
    fun `download uses fallback when name and type are blank`() =
        runBlocking {
            server.enqueue(MockResponse().setBody("audio"))
            val result = downloader.downloadStems(
                listOf(
                    stem(
                        name = " ",
                        path = "/audio.wav",
                        type = " "
                    )
                ),
                tempDirectory
            )
            assertEquals("stem_1", result[0].name)
        }

    @Test
    fun `download sanitizes stem filename`() = runBlocking {
        server.enqueue(MockResponse().setBody("audio"))
        val result = downloader.downloadStems(
            listOf(stem("vocals/test", "/audio.wav")),
            tempDirectory
        )
        assertEquals("vocals_test", result[0].name)
    }

    @Test
    fun `download sanitizes name with only invalid characters`() =
        runBlocking {
            server.enqueue(MockResponse().setBody("audio"))
            val result = downloader.downloadStems(
                listOf(stem("///", "/audio.wav")),
                tempDirectory
            )
            assertEquals("stem_1", result[0].name)
        }

    @Test
    fun `download preserves existing filename`() = runBlocking {
        val existingFile = File(tempDirectory, "vocals.wav")
        existingFile.writeText("original")
        server.enqueue(MockResponse().setBody("new audio"))
        val result = downloader.downloadStems(
            listOf(stem("vocals", "/vocals.wav")),
            tempDirectory
        )
        assertEquals("vocals_2.wav", result[0].file.name)
        assertEquals("original", existingFile.readText())
    }

    @Test
    fun `download falls back to wav extension`() = runBlocking {
        server.enqueue(MockResponse().setBody("audio"))
        val result = downloader.downloadStems(
            listOf(stem("vocals", "/vocals.unknown")),
            tempDirectory
        )
        assertEquals("vocals.wav", result[0].file.name)
    }

    @Test
    fun `download supports recognized extensions`() = runBlocking {
        val extensions = listOf("mp3", "wav", "flac", "m4a", "ogg")
        extensions.forEach { extension ->
            server.enqueue(MockResponse().setBody("audio"))
        }
        val result = downloader.downloadStems(
            extensions.map {
                stem(it, "/audio.$it")
            },
            tempDirectory
        )
        assertEquals(5, result.size)
        extensions.forEachIndexed { index, extension ->
            assertEquals(extension, result[index].file.extension)
        }
    }

    @Test
    fun `download rejects empty stem list`() {
        try {
            runBlocking {
                downloader.downloadStems(emptyList(), tempDirectory)
            }
            fail("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertEquals("No audio stems were provided", e.message)
        }
    }

    @Test
    fun `download rejects missing URL`() {
        try {
            runBlocking {
                downloader.downloadStems(
                    listOf(MvsepStem("vocals", null, null)),
                    tempDirectory
                )
            }
            fail("Expected IOException")
        } catch (e: IOException) {
            assertTrue(e.message!!.contains("Missing download URL"))
        }
    }

    @Test
    fun `download rejects malformed URL`() {
        try {
            runBlocking {
                downloader.downloadStems(
                    listOf(
                        MvsepStem(
                            "vocals",
                            null,
                            "https://[invalid"
                        )
                    ),
                    tempDirectory
                )
            }
            fail("Expected IOException")
        } catch (e: IOException) {
            assertTrue(e.message!!.contains("Invalid download URL"))
        }
    }

    @Test
    fun `download rejects insecure URL`() {
        try {
            runBlocking {
                downloader.downloadStems(
                    listOf(
                        MvsepStem(
                            "vocals",
                            null,
                            "http://example.com/vocals.wav"
                        )
                    ),
                    tempDirectory
                )
            }
            fail("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("Invalid or insecure"))
        }
    }

    @Test
    fun `download rejects URL without host`() {
        try {
            runBlocking {
                downloader.downloadStems(
                    listOf(
                        MvsepStem(
                            "vocals",
                            null,
                            "https:///vocals.wav"
                        )
                    ),
                    tempDirectory
                )
            }
            fail("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("Invalid or insecure"))
        }
    }

    @Test
    fun `download handles HTTP error`() {
        server.enqueue(MockResponse().setResponseCode(500))
        try {
            runBlocking {
                downloader.downloadStems(
                    listOf(stem("vocals", "/vocals.wav")),
                    tempDirectory
                )
            }
            fail("Expected IOException")
        } catch (e: IOException) {
            assertTrue(e.message!!.contains("HTTP 500"))
        }
    }

    @Test
    fun `download cleans earlier files when later download fails`() {
        server.enqueue(MockResponse().setBody("vocals"))
        server.enqueue(MockResponse().setResponseCode(500))
        try {
            runBlocking {
                downloader.downloadStems(
                    listOf(
                        stem("vocals", "/vocals.wav"),
                        stem("bass", "/bass.wav")
                    ),
                    tempDirectory
                )
            }
            fail("Expected IOException")
        } catch (e: IOException) {
            assertTrue(
                tempDirectory.listFiles()
                    ?.none { it.extension == "wav" } == true
            )
        }
    }

    @Test
    fun `download rejects invalid destination`() {
        val invalidDestination = File(
            tempDirectory,
            "not_a_directory"
        )
        invalidDestination.writeText("file")
        try {
            runBlocking {
                downloader.downloadStems(
                    listOf(stem("vocals", "/vocals.wav")),
                    invalidDestination
                )
            }
            fail("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("Destination path"))
        }
    }

    @Test
    fun `download creates missing destination directory`() =
        runBlocking {
            val newDirectory = File(tempDirectory, "new_directory")
            server.enqueue(MockResponse().setBody("audio"))
            val result = downloader.downloadStems(
                listOf(stem("vocals", "/vocals.wav")),
                newDirectory
            )
            assertTrue(newDirectory.isDirectory)
            assertTrue(result[0].file.exists())
        }

    @Test
    fun `download rejects destination that cannot be created`() {
        val parentFile = File(tempDirectory, "parent")
        parentFile.writeText("not a directory")
        val invalidDirectory = File(parentFile, "child")
        try {
            runBlocking {
                downloader.downloadStems(
                    listOf(stem("vocals", "/vocals.wav")),
                    invalidDirectory
                )
            }
            fail("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(
                e.message!!.contains("Could not create destination")
            )
        }
    }

    @Test
    fun `download handles cancelled coroutine`() {
        try {
            runBlocking {
                kotlinx.coroutines.currentCoroutineContext().cancel()
                downloader.downloadStems(
                    listOf(stem("vocals", "/vocals.wav")),
                    tempDirectory
                )
            }
            fail("Expected CancellationException")
        } catch (e: CancellationException) {
            assertTrue(true)
        }
    }
}
