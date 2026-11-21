package com.example.aio_musica.mvsep

import com.google.gson.Gson
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class MvsepModelsTest {

    private lateinit var gson: Gson

    @Before
    fun setup() {
        gson = Gson()
    }

    // MvsepCreateResponse tests

    @Test
    fun `deserialize successful creation response`() {
        val json = """
            {
                "success": true,
                "data": {
                    "hash": "abc123",
                    "link": "https://mvsep.com/result",
                    "message": "Job created"
                }
            }
        """.trimIndent()
        val result = gson.fromJson(
            json,
            MvsepCreateResponse::class.java
        )
        assertTrue(result.success)
        assertNotNull(result.data)
        assertEquals("abc123", result.data?.hash)
        assertEquals("https://mvsep.com/result", result.data?.link)
        assertEquals("Job created", result.data?.message)
    }

    @Test
    fun `deserialize failed creation response`() {
        val json = """
            {
                "success": false,
                "data": {
                    "hash": null,
                    "link": null,
                    "message": "Invalid token"
                }
            }
        """.trimIndent()
        val result = gson.fromJson(
            json,
            MvsepCreateResponse::class.java
        )
        assertFalse(result.success)
        assertNull(result.data?.hash)
        assertNull(result.data?.link)
        assertEquals("Invalid token", result.data?.message)
    }

    @Test
    fun `deserialize creation response without data`() {
        val json = """
            {
                "success": false,
                "data": null
            }
        """.trimIndent()
        val result = gson.fromJson(
            json,
            MvsepCreateResponse::class.java
        )
        assertFalse(result.success)
        assertNull(result.data)
    }

    @Test
    fun `deserialize creation response with missing data field`() {
        val json = """
            {
                "success": false
            }
        """.trimIndent()
        val result = gson.fromJson(
            json,
            MvsepCreateResponse::class.java
        )
        assertFalse(result.success)
        assertNull(result.data)
    }

    // MvsepJobData tests

    @Test
    fun `deserialize job data with all fields`() {
        val json = """
            {
                "hash": "job123",
                "link": "https://mvsep.com/result",
                "message": "Created"
            }
        """.trimIndent()
        val result = gson.fromJson(
            json,
            MvsepJobData::class.java
        )
        assertEquals("job123", result.hash)
        assertEquals("https://mvsep.com/result", result.link)
        assertEquals("Created", result.message)
    }

    @Test
    fun `deserialize job data with null fields`() {
        val json = """
            {
                "hash": null,
                "link": null,
                "message": null
            }
        """.trimIndent()
        val result = gson.fromJson(
            json,
            MvsepJobData::class.java
        )
        assertNull(result.hash)
        assertNull(result.link)
        assertNull(result.message)
    }

    // MvsepStatusResponse tests

    @Test
    fun `deserialize successful status response`() {
        val json = """
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
                        },
                        {
                            "name": "bass",
                            "type": "audio",
                            "url": "https://example.com/bass.wav"
                        }
                    ]
                }
            }
        """.trimIndent()
        val result = gson.fromJson(
            json,
            MvsepStatusResponse::class.java
        )
        assertTrue(result.success)
        assertEquals("done", result.status)
        assertNotNull(result.data)
        assertEquals("Completed", result.data?.message)
        val files = result.data?.files
        assertEquals(2, files?.size)
        assertEquals("vocals", files?.get(0)?.name)
        assertEquals("audio", files?.get(0)?.type)
        assertEquals(
            "https://example.com/vocals.wav",
            files?.get(0)?.url
        )
        assertEquals("bass", files?.get(1)?.name)
        assertEquals("audio", files?.get(1)?.type)
        assertEquals(
            "https://example.com/bass.wav",
            files?.get(1)?.url
        )
    }

    @Test
    fun `deserialize status response without result data`() {
        val json = """
            {
                "success": true,
                "status": "processing",
                "data": null
            }
        """.trimIndent()
        val result = gson.fromJson(
            json,
            MvsepStatusResponse::class.java
        )
        assertTrue(result.success)
        assertEquals("processing", result.status)
        assertNull(result.data)
    }

    @Test
    fun `deserialize status response with missing data field`() {
        val json = """
            {
                "success": true,
                "status": "waiting"
            }
        """.trimIndent()
        val result = gson.fromJson(
            json,
            MvsepStatusResponse::class.java
        )
        assertTrue(result.success)
        assertEquals("waiting", result.status)
        assertNull(result.data)
    }

    // MvsepResultData tests

    @Test
    fun `deserialize empty stem list`() {
        val json = """
            {
                "message": "Completed",
                "files": []
            }
        """.trimIndent()
        val result = gson.fromJson(
            json,
            MvsepResultData::class.java
        )
        assertEquals("Completed", result.message)
        assertNotNull(result.files)
        assertTrue(result.files!!.isEmpty())
    }

    @Test
    fun `deserialize result data with null files`() {
        val json = """
            {
                "message": "Processing",
                "files": null
            }
        """.trimIndent()
        val result = gson.fromJson(
            json,
            MvsepResultData::class.java
        )
        assertEquals("Processing", result.message)
        assertNull(result.files)
    }

    @Test
    fun `deserialize result data with missing files`() {
        val json = """
            {
                "message": "Processing"
            }
        """.trimIndent()

        val result = gson.fromJson(
            json,
            MvsepResultData::class.java
        )
        assertEquals("Processing", result.message)
        assertNull(result.files)
    }

    // MvsepStem tests

    @Test
    fun `deserialize stem with all fields`() {
        val json = """
            {
                "name": "vocals",
                "type": "audio",
                "url": "https://example.com/vocals.wav"
            }
        """.trimIndent()
        val result = gson.fromJson(
            json,
            MvsepStem::class.java
        )
        assertEquals("vocals", result.name)
        assertEquals("audio", result.type)
        assertEquals(
            "https://example.com/vocals.wav",
            result.url
        )
    }

    @Test
    fun `deserialize stem with null fields`() {
        val json = """
            {
                "name": null,
                "type": null,
                "url": null
            }
        """.trimIndent()
        val result = gson.fromJson(
            json,
            MvsepStem::class.java
        )
        assertNull(result.name)
        assertNull(result.type)
        assertNull(result.url)
    }

    @Test
    fun `deserialize stem with missing fields`() {
        val json = """
            {}
        """.trimIndent()

        val result = gson.fromJson(
            json,
            MvsepStem::class.java
        )
        assertNull(result.name)
        assertNull(result.type)
        assertNull(result.url)
    }

    @Test
    fun `serialize stem correctly`() {
        val stem = MvsepStem(
            name = "vocals",
            type = "audio",
            url = "https://example.com/vocals.wav"
        )
        val json = gson.toJson(stem)
        assertTrue(json.contains("\"name\":\"vocals\""))
        assertTrue(json.contains("\"type\":\"audio\""))
        assertTrue(
            json.contains(
                "\"url\":\"https://example.com/vocals.wav\""
            )
        )
    }

    @Test
    fun `serialize stem with null fields`() {
        val stem = MvsepStem(
            name = null,
            type = null,
            url = null
        )
        val json = gson.toJson(stem)
        // Gson omits null fields by default.
        assertEquals("{}", json)
    }
}
