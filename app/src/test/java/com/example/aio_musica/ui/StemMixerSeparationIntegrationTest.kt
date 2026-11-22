package com.example.aio_musica.ui

import android.content.ContentResolver
import android.net.Uri
import com.example.aio_musica.mvsep.MvsepClient
import com.example.aio_musica.mvsep.MvsepDownloader
import com.example.aio_musica.mvsep.MvsepRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.InetAddress
import java.util.concurrent.atomic.AtomicInteger

@OptIn(ExperimentalCoroutinesApi::class)
class StemMixerSeparationIntegrationTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var testDispatcher: TestDispatcher
    private lateinit var server: MockWebServer
    private lateinit var viewModel: StemMixerViewModel
    private lateinit var songFile: File
    private lateinit var outDir: File
    private val baseUrl: String get() = "https://localhost:${server.port}/"
    private val statusCalls = AtomicInteger(0)
    private var createResponse: () -> MockResponse = { json(CREATE_OK) }
    private var statusResponse: () -> MockResponse = { json(doneJson(ALL_STEMS)) }
    private var missingFile: String? = null

    @Before
    fun setUp() {
        testDispatcher = StandardTestDispatcher()
        Dispatchers.setMain(testDispatcher)
        val loopback = InetAddress.getByName("127.0.0.1")
        val held = HeldCertificate.Builder().addSubjectAlternativeName("localhost").build()
        val serverCerts = HandshakeCertificates.Builder().heldCertificate(held).build()
        val clientCerts = HandshakeCertificates.Builder()
            .addTrustedCertificate(held.certificate)
            .build()
        val tlsClient = OkHttpClient.Builder()
            .sslSocketFactory(clientCerts.sslSocketFactory(), clientCerts.trustManager)
            .dns(object : Dns {
                override fun lookup(hostname: String): List<InetAddress> = listOf(loopback)
            })
            .build()

        server = MockWebServer()
        server.useHttps(serverCerts.sslSocketFactory(), false)
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                return when {
                    path.startsWith("/separation/create") -> createResponse()
                    path.startsWith("/separation/get") -> {
                        statusCalls.incrementAndGet()
                        statusResponse()
                    }
                    path.startsWith("/files/") -> fileResponse(path)
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        server.start(loopback, 0)

        val api = MvsepClient.createApi(
            baseUrl = baseUrl,
            client = tlsClient
        )
        val repository = MvsepRepository(api, "test-token")

        viewModel = StemMixerViewModel(MvsepDownloader(tlsClient), testDispatcher)
        viewModel.configure(repository)

        songFile = tmp.newFile("song.mp3").also { it.writeText("original song") }
        outDir = File(tmp.root, "stems")
    }

    @After
    fun tearDown() {
        server.shutdown()
        Dispatchers.resetMain()
    }

    private fun json(body: String) =
        MockResponse().setResponseCode(200).setHeader("Content-Type", "application/json").setBody(body)

    private fun fileResponse(path: String): MockResponse {
        val name = path.removePrefix("/files/").substringBeforeLast(".")
        return if (name == missingFile) {
            MockResponse().setResponseCode(404)
        } else {
            MockResponse().setResponseCode(200).setBody("bytes-$name")
        }
    }

    private fun doneJson(names: List<String>): String {
        val base = "${baseUrl}files/"
        val files = names.joinToString(",") {
            """{"name":null,"type":"$it","url":"$base$it.mp3"}"""
        }
        return """{"success":true,"status":"done","data":{"message":null,"files":[$files]}}"""
    }

    private fun TestScope.separateAndWait() {
        viewModel.separateSong(songFile, outDir)
        runCurrent()
        val deadline = System.currentTimeMillis() + 20_000
        while (viewModel.isProcessing.value) {
            advanceUntilIdle()
            check(System.currentTimeMillis() < deadline) { "Separation did not finish in time" }
            Thread.sleep(20)
        }
    }

    @Test
    fun fullSeparation_producesSortedNamedStemsOnDisk() = runTest(testDispatcher) {
        separateAndWait()

        val stems = viewModel.stems.value
        assertEquals(
            listOf("Vocals", "Instrum", "Guitar", "Piano", "Bass", "Drums", "Other"),
            stems.map { it.name }
        )
        assertTrue(stems.first { it.name == "Instrum" }.muted)
        assertTrue(stems.filter { it.name != "Instrum" }.none { it.muted })
        for (stem in stems) {
            assertEquals("bytes-${stem.name}", stem.audioFile!!.readText())
        }
        assertEquals("Separation complete!", viewModel.status.value)
        assertNull(viewModel.error.value)
        assertFalse(viewModel.isProcessing.value)
    }

    @Test
    fun separation_sendsTheSongAndSettingsToMvsep() = runTest(testDispatcher) {
        separateAndWait()

        val create = server.takeRequest()
        assertEquals("POST", create.method)
        assertTrue(create.path!!.startsWith("/separation/create"))
        val body = create.body.readUtf8()
        assertTrue(body.contains("test-token"))
        assertTrue(body.contains("sep_type"))
        assertTrue(body.contains("63"))        // BS Roformer SW
        assertTrue(body.contains("original song"))

        val status = server.takeRequest()
        assertTrue(status.path!!.contains("hash=job123"))
    }

    @Test
    fun jobStillProcessing_isPolledAgainUntilDone() = runTest(testDispatcher) {
        statusResponse = {
            if (statusCalls.get() < 2) {
                json("""{"success":true,"status":"processing","data":null}""")
            } else {
                json(doneJson(listOf("Vocals", "Other")))
            }
        }
        separateAndWait()
        assertEquals(2, statusCalls.get())
        assertEquals(listOf("Vocals", "Other"), viewModel.stems.value.map { it.name })
    }

    @Test
    fun mvsepRejectsTheUpload_showsItsMessage() = runTest(testDispatcher) {
        createResponse = {
            json("""{"success":false,"data":{"hash":null,"link":null,"message":"Quota exceeded"}}""")
        }

        separateAndWait()

        assertEquals("Quota exceeded", viewModel.error.value)
        assertEquals("Separation failed", viewModel.status.value)
        assertTrue(viewModel.stems.value.isEmpty())
    }

    @Test
    fun mvsepJobFails_showsItsMessage() = runTest(testDispatcher) {
        statusResponse = {
            json("""{"success":true,"status":"failed","data":{"message":"Bad audio","files":null}}""")
        }

        separateAndWait()

        assertEquals("Bad audio", viewModel.error.value)
        assertTrue(viewModel.stems.value.isEmpty())
    }

    @Test
    fun wrongToken_http401_showsGenericCommunicationError() = runTest(testDispatcher) {
        createResponse = { MockResponse().setResponseCode(401) }

        separateAndWait()

        assertEquals(
            "An error occurred while communicating with MVSEP",
            viewModel.error.value
        )
        assertEquals("Separation failed", viewModel.status.value)
    }

    @Test
    fun oneStemFailsToDownload_noStemsAndNoPartialFilesRemain() = runTest(testDispatcher) {
        statusResponse = { json(doneJson(listOf("Vocals", "Bass", "Other"))) }
        missingFile = "Other"

        separateAndWait()

        assertEquals("Download failed: HTTP 404", viewModel.error.value)
        assertTrue(viewModel.stems.value.isEmpty())
        // The stems that did download are cleaned up again.
        assertTrue(outDir.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun separatedStem_canBeExportedToAChosenLocation() = runTest(testDispatcher) {
        separateAndWait()
        val vocals = viewModel.stems.value.first { it.name == "Vocals" }
        val destination = mockk<Uri>()
        val out = ByteArrayOutputStream()
        val resolver = mockk<ContentResolver>()
        every { resolver.openOutputStream(destination) } returns out

        viewModel.exportStem(vocals.id, destination, resolver)
        advanceUntilIdle()

        assertEquals("bytes-Vocals", out.toString())
        assertEquals("Saved Vocals.mp3", viewModel.status.value)
    }

    @Test
    fun afterSeparation_instrumentalAndBackingStemsTradeMuteStates() = runTest(testDispatcher) {
        separateAndWait()
        val instrum = viewModel.stems.value.first { it.name == "Instrum" }

        viewModel.toggleMute(instrum.id) // turn Instrumental on

        val after = viewModel.stems.value.associateBy { it.name }
        assertFalse(after.getValue("Instrum").muted)
        assertTrue(after.getValue("Drums").muted)
        assertFalse(after.getValue("Vocals").muted)
    }

    private companion object {
        val ALL_STEMS = listOf("Other", "Drums", "Bass", "Piano", "Guitar", "Instrum", "Vocals")

        const val CREATE_OK =
            """{"success":true,"data":{"hash":"job123","link":"https://mvsep.test/job123","message":null}}"""
    }
}