package com.runerback.queuehelper.domain

import com.runerback.queuehelper.data.model.MiniMaxH3Ref2VaPrompt
import com.runerback.queuehelper.data.model.SubjectDefault
import com.runerback.queuehelper.data.model.SubjectDefaults
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskMediaPayloadUpdaterTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `mergeImageIds appends deduplicates and caps at six`() {
        val merged = TaskMediaPayloadUpdater.mergeImageIds(
            existingIds = listOf("one", "two", "three", "four"),
            addedIds = listOf("two", "five", "six", "seven")
        )

        assertEquals(
            listOf("one", "two", "three", "four", "five", "six"),
            merged
        )
    }

    @Test
    fun `withImages updates media ids and refs while preserving payload fields`() {
        val payload = buildJsonObject {
            put("id", 7)
            put("custom", "kept")
            put("params", buildJsonObject {
                put("prompt", "original prompt")
                put("resolution", "480x832")
            })
            put("pack_settings", buildJsonObject {
                put("video_length_input", "12")
            })
        }

        val updated = TaskMediaPayloadUpdater.withImages(
            payload = payload,
            mediaIds = listOf("image-a", "image-b"),
            fileNameFor = { "image_$it.png" }
        )

        assertEquals("kept", updated["custom"]?.jsonPrimitive?.contentOrNull)
        assertEquals(
            listOf("image-a", "image-b"),
            updated["pack_settings"]!!.jsonObject["image_media_ids"]!!
                .jsonArray.map { it.jsonPrimitive.content }
        )
        assertEquals(
            listOf("image_image-a.png", "image_image-b.png"),
            updated["params"]!!.jsonObject["image_refs"]!!
                .jsonArray.map { it.jsonPrimitive.content }
        )
        assertEquals("480x832", updated["params"]!!.jsonObject["resolution"]?.jsonPrimitive?.content)
        assertEquals(
            "12",
            updated["pack_settings"]!!.jsonObject["video_length_input"]?.jsonPrimitive?.content
        )
    }

    @Test
    fun `withAudio replaces audio and restores default audio prompt line`() {
        val prompt = MiniMaxH3Ref2VaPrompt(
            subjectDefinitions = """
                <Subject 1> a person
                <Audio 1>: old audio instruction
            """.trimIndent(),
            summary = "summary",
            detailedDescription = "details"
        ).toPromptString()
        val payload = buildJsonObject {
            put("id", 3)
            put("custom", JsonPrimitive("kept"))
            put("params", buildJsonObject {
                put("prompt", prompt)
                put("audio_guide", "audio_old.wav")
            })
            put("subject_defaults", json.encodeToJsonElement(
                SubjectDefaults.serializer(),
                SubjectDefaults(
                    subjects = listOf(SubjectDefault(1, "a person")),
                    audio = "<Audio 1>: default audio instruction"
                )
            ))
            put("pack_settings", buildJsonObject {
                put("audio_media_id", "old-audio")
                put("trim_start", 2f)
                put("trim_end", 9f)
                put("image_media_ids", buildJsonArray {
                    add(JsonPrimitive("image-a"))
                })
            })
        }

        val updated = TaskMediaPayloadUpdater.withAudio(
            payload = payload,
            mediaId = "new-audio",
            trimStart = 0f,
            trimEnd = 5f,
            audioFileName = "audio_new-audio_0_5000.wav",
            videoLength = 120
        )

        val params = updated["params"]!!.jsonObject
        val settings = updated["pack_settings"]!!.jsonObject
        assertEquals("audio_new-audio_0_5000.wav", params["audio_guide"]?.jsonPrimitive?.content)
        assertEquals("120", params["video_length"]?.jsonPrimitive?.content)
        assertTrue(params["prompt"]!!.jsonPrimitive.content.contains("<Audio 1>: default audio instruction"))
        assertTrue(!params["prompt"]!!.jsonPrimitive.content.contains("old audio instruction"))
        assertEquals("new-audio", settings["audio_media_id"]?.jsonPrimitive?.content)
        assertEquals("0.0", settings["trim_start"]?.jsonPrimitive?.content)
        assertEquals("5.0", settings["trim_end"]?.jsonPrimitive?.content)
        assertEquals("120", settings["video_length_input"]?.jsonPrimitive?.content)
        assertEquals(
            listOf("image-a"),
            settings["image_media_ids"]!!.jsonArray.map { it.jsonPrimitive.content }
        )
        assertEquals("kept", updated["custom"]?.jsonPrimitive?.content)
    }
}
