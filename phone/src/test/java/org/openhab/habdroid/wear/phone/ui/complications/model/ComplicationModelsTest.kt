package org.openhab.habdroid.wear.phone.ui.complications.model

import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ComplicationModelsTest {

    @Test
    fun `readOnly defaults to false`() {
        assertFalse(ComplicationState(item = "X").readOnly)
    }

    @Test
    fun `toDto omits readOnly when false`() {
        val dto = ComplicationState(item = "X", readOnly = false).toDto()
        assertNull(dto.config["readOnly"])
    }

    @Test
    fun `toDto writes readOnly when true`() {
        val dto = ComplicationState(item = "X", readOnly = true).toDto()
        assertEquals("true", dto.config["readOnly"]?.jsonPrimitive?.content)
    }

    @Test
    fun `fromDto parses readOnly true`() {
        val dto = ComplicationState(item = "X", readOnly = true).toDto()
        assertTrue(ComplicationState.fromDto(dto).readOnly)
    }

    @Test
    fun `fromDto defaults readOnly to false when absent`() {
        val dto = ComplicationState(item = "X").toDto()
        assertFalse(ComplicationState.fromDto(dto).readOnly)
    }

    @Test
    fun `readOnly survives round-trip`() {
        val original = ComplicationState(item = "SolarPower", label = "Solar", readOnly = true)
        val restored = ComplicationState.fromDto(original.toDto())
        assertEquals(original.readOnly, restored.readOnly)
    }

    @Test
    fun `editor toDto preserves readOnly through slot map and json string`() {
        val editor = ComplicationEditorState(
            slots = mapOf(1 to ComplicationState(item = "X", readOnly = true))
        )
        val dto = editor.toDto()
        val slot = dto.slots.default.first()
        // config-level check
        assertEquals("true", slot.config["readOnly"]?.jsonPrimitive?.content)

        // full JSON string round-trip via kotlinx serialization
        val jsonStr = kotlinx.serialization.json.Json.encodeToString(ComplicationListDto.serializer(), dto)
        assertTrue("readOnly missing in serialized json: $jsonStr", jsonStr.contains("\"readOnly\""))

        val decoded = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
            .decodeFromString(ComplicationListDto.serializer(), jsonStr)
        val restored = ComplicationEditorState.fromDto(decoded)
        assertTrue(restored.slots[1]?.readOnly == true)
    }
}
