package com.example.aio_musica

import android.media.MediaMetadataRetriever
import android.net.Uri
import com.example.aio_musica.library.Metadata
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [Metadata].
 *
 * MediaMetadataRetriever and Uri are Android framework classes that don't work on the host JVM,
 * so the retriever is mocked (via constructor mocking) and the Uri is a MockK mock.
 */
class MetadataUnitTest {

    private lateinit var uri: Uri

    @Before
    fun setUp() {
        uri = mockk { every { path } returns "/data/songs/MetaDataTester.mp3" }
        stubRetriever(title = "Background Music", artist = "JonasBlakewood", album = "n/a")
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    /** Makes every new MediaMetadataRetriever report the given tags (null = tag missing). */
    private fun stubRetriever(title: String?, artist: String?, album: String?) {
        mockkConstructor(MediaMetadataRetriever::class)
        every { anyConstructed<MediaMetadataRetriever>().setDataSource(any<String>()) } just Runs
        every {
            anyConstructed<MediaMetadataRetriever>()
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
        } returns title
        every {
            anyConstructed<MediaMetadataRetriever>()
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
        } returns artist
        every {
            anyConstructed<MediaMetadataRetriever>()
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
        } returns album
        every { anyConstructed<MediaMetadataRetriever>().release() } just Runs
    }

    // ----- reading tags -----

    @Test
    fun readMetadata_CorrectTitle() {
        assertEquals("Background Music", Metadata(uri).getTitle())
    }

    @Test
    fun readMetadata_CorrectArtist() {
        assertEquals("JonasBlakewood", Metadata(uri).getArtist())
    }

    @Test
    fun readMetadata_CorrectAlbum() {
        assertEquals("n/a", Metadata(uri).getAlbum())
    }

    @Test
    fun readMetadata_readsFromTheUriPath() {
        Metadata(uri)

        verify(exactly = 1) {
            anyConstructed<MediaMetadataRetriever>().setDataSource("/data/songs/MetaDataTester.mp3")
        }
    }

    @Test
    fun readMetadata_releasesTheRetriever() {
        Metadata(uri)

        verify(exactly = 1) { anyConstructed<MediaMetadataRetriever>().release() }
    }

    @Test
    fun readMetadata_propertiesMatchTheGetters() {
        val metadata = Metadata(uri)

        assertEquals(metadata.getTitle(), metadata.title)
        assertEquals(metadata.getArtist(), metadata.artist)
        assertEquals(metadata.getAlbum(), metadata.album)
    }

    // ----- title fallbacks -----

    @Test
    fun readMetadata_missingTitle_usesTheFallbackTitle() {
        stubRetriever(title = null, artist = "Artist", album = "Album")

        assertEquals("My Fallback", Metadata(uri, fallbackTitle = "My Fallback").getTitle())
    }

    @Test
    fun readMetadata_blankTitle_usesTheFallbackTitle() {
        stubRetriever(title = "   ", artist = "Artist", album = "Album")

        assertEquals("My Fallback", Metadata(uri, fallbackTitle = "My Fallback").getTitle())
    }

    @Test
    fun readMetadata_emptyTitle_usesTheFallbackTitle() {
        stubRetriever(title = "", artist = "Artist", album = "Album")

        assertEquals("My Fallback", Metadata(uri, fallbackTitle = "My Fallback").getTitle())
    }

    @Test
    fun readMetadata_missingTitleWithDefaultFallback_isUnknownTitle() {
        stubRetriever(title = null, artist = "Artist", album = "Album")

        assertEquals("Unknown Title", Metadata(uri).getTitle())
    }

    // ----- artist / album fallbacks -----

    @Test
    fun readMetadata_missingArtist_isUnknownArtist() {
        stubRetriever(title = "Title", artist = null, album = "Album")

        assertEquals("Unknown Artist", Metadata(uri).getArtist())
    }

    @Test
    fun readMetadata_missingAlbum_isUnknownAlbum() {
        stubRetriever(title = "Title", artist = "Artist", album = null)

        assertEquals("Unknown Album", Metadata(uri).getAlbum())
    }

    @Test
    fun readMetadata_allTagsMissing_usesEveryDefault() {
        stubRetriever(title = null, artist = null, album = null)

        val metadata = Metadata(uri)

        assertEquals("Unknown Title", metadata.getTitle())
        assertEquals("Unknown Artist", metadata.getArtist())
        assertEquals("Unknown Album", metadata.getAlbum())
    }

    // ----- failures while reading -----

    @Test
    fun readMetadata_setDataSourceFails_keepsDefaultsAndStillReleases() {
        every {
            anyConstructed<MediaMetadataRetriever>().setDataSource(any<String>())
        } throws RuntimeException("corrupt file")

        val metadata = Metadata(uri, fallbackTitle = "My Fallback")

        assertEquals("My Fallback", metadata.getTitle())
        assertEquals("Unknown Artist", metadata.getArtist())
        assertEquals("Unknown Album", metadata.getAlbum())
        verify(exactly = 1) { anyConstructed<MediaMetadataRetriever>().release() }
    }

    @Test
    fun readMetadata_extractFailsAfterTitle_keepsTheTitleAndDefaultsForTheRest() {
        every {
            anyConstructed<MediaMetadataRetriever>()
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
        } throws IllegalStateException("retriever broke")

        val metadata = Metadata(uri)

        assertEquals("Background Music", metadata.getTitle())
        assertEquals("Unknown Artist", metadata.getArtist())
        assertEquals("Unknown Album", metadata.getAlbum())
        verify(exactly = 1) { anyConstructed<MediaMetadataRetriever>().release() }
    }

    @Test
    fun readMetadata_uriWithoutAPath_keepsDefaultsAndStillReleases() {
        val pathless: Uri = mockk { every { path } returns null }

        val metadata = Metadata(pathless, fallbackTitle = "My Fallback")

        assertEquals("My Fallback", metadata.getTitle())
        assertEquals("Unknown Artist", metadata.getArtist())
        assertEquals("Unknown Album", metadata.getAlbum())
        verify(exactly = 1) { anyConstructed<MediaMetadataRetriever>().release() }
    }

    // ----- mutable properties -----

    @Test
    fun properties_canBeChangedAfterConstruction() {
        val metadata = Metadata(uri)

        metadata.title = "New Title"
        metadata.artist = "New Artist"
        metadata.album = "New Album"

        assertEquals("New Title", metadata.getTitle())
        assertEquals("New Artist", metadata.getArtist())
        assertEquals("New Album", metadata.getAlbum())
    }

    // ----- mime type -----

    @Test
    fun mimeType_defaultsToAudioMpeg() {
        assertEquals("audio/mpeg", Metadata(uri).mimeType)
    }

    @Test
    fun mimeType_usesTheProvidedValue() {
        assertEquals("audio/wav", Metadata(uri, mimeType = "audio/wav").mimeType)
    }

    // ----- generateSong -----

    @Test
    fun generateSong_CorrectTitle() {
        assertEquals("Background Music", Metadata(uri).generateSong().title)
    }

    @Test
    fun generateSong_CorrectArtist() {
        assertEquals("JonasBlakewood", Metadata(uri).generateSong().artist)
    }

    @Test
    fun generateSong_CorrectAlbum() {
        assertEquals("n/a", Metadata(uri).generateSong().album)
    }

    @Test
    fun generateSong_carriesTheUriAndMimeType() {
        val song = Metadata(uri, mimeType = "audio/wav").generateSong()

        assertEquals(uri, song.uri)
        assertEquals("audio/wav", song.mimeType)
    }

    @Test
    fun generateSong_usesFallbacksWhenTagsAreMissing() {
        stubRetriever(title = null, artist = null, album = null)

        val song = Metadata(uri, fallbackTitle = "My Fallback").generateSong()

        assertEquals("My Fallback", song.title)
        assertEquals("Unknown Artist", song.artist)
        assertEquals("Unknown Album", song.album)
    }

    @Test
    fun generateSong_reflectsChangesMadeToTheProperties() {
        val metadata = Metadata(uri)
        metadata.title = "Edited"

        assertEquals("Edited", metadata.generateSong().title)
    }

    @Test
    fun generateSong_idIsAUuid() {
        val id = Metadata(uri).generateSong().id

        assertTrue(id.matches(Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")))
    }

    @Test
    fun generateSong_sameMetadataAlwaysGivesTheSameId() {
        val metadata = Metadata(uri)

        assertEquals(metadata.generateSong().id, metadata.generateSong().id)
    }

    @Test
    fun generateSong_differentMetadataObjectsGetDifferentIds() {
        val first = Metadata(uri).generateSong()
        val second = Metadata(uri).generateSong()

        assertNotEquals(first.id, second.id)
    }
}