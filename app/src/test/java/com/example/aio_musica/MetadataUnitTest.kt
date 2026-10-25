package com.example.aio_musica

import com.example.aio_musica.library.Song
import android.net.Uri
import com.example.aio_musica.library.Metadata
import org.junit.Test
import org.junit.Assert.*
import org.junit.Before

//Tests for Metadata Class

 class MetadataUnitTest {

     private lateinit var metadata: Metadata
     private lateinit var song: Song

     @Before
     fun setUp() {
         val resource = javaClass.classLoader!!
             .getResource("MetaDataTester.mp3")!!

         val file = java.io.File(resource.toURI())
         val uri = Uri.parse(file.toURI().toString())
         metadata = Metadata(uri)
         song = metadata.generateSong()
     }

     //test 1: see if the actual title metadata of the song matches the title metadata gotten
     @Test
     fun readMetadata_CorrectTitle(){
         assertEquals("Background Music", metadata.getTitle())
     }

     //test 2: see if the actual artist metadata of the song matches the artist metadata gotten
     @Test
     fun readMetadata_CorrectArtist(){
         assertEquals("JonasBlakewood", metadata.getArtist())
     }

     //test 3: see if the actual album metadata of the song matches the album metadata gotten
     @Test
     fun readMetadata_CorrectAlbum(){
         assertEquals("n/a", metadata.getAlbum())
     }

     //test 4: see if the generated song matches the title given
     @Test
     fun generateSong_CorrectTitle(){
         assertEquals("Background Music", song.title)
     }

     //test 5: see if the generated song matches the artist given
     @Test
     fun generateSong_CorrectArtist(){
         assertEquals("JonasBlakewood", song.artist)
     }

     //test 6: see if the generated song matches the album given
     @Test
     fun generateSong_CorrectAlbum(){
         assertEquals("n/a", song.album)
     }
 }