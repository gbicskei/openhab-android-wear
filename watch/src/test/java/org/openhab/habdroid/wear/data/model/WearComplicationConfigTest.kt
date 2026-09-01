package org.openhab.habdroid.wear.data.model

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Test

class WearComplicationConfigTest {

    private fun parseConfig(json: String): WearComplicationConfig {
        val obj = Json.parseToJsonElement(json).jsonObject
        return WearComplicationConfig.fromJson(obj)
    }

    @Test
    fun `parses item and label`() {
        val config = parseConfig("""{"item": "DailyEnergy", "label": "Energy", "icon": "iconify:mdi:lightning-bolt"}""")
        assertEquals("DailyEnergy", config.item)
        assertEquals("Energy", config.label)
        assertEquals("iconify:mdi:lightning-bolt", config.icon)
    }

    @Test
    fun `parses shortText config`() {
        val config = parseConfig("""{"item": "X", "shortText": {"title": "Temp", "text": "%.1f°C"}}""")
        assertEquals("Temp", config.shortText.title)
        assertEquals("%.1f°C", config.shortText.text)
    }

    @Test
    fun `parses longText config`() {
        val config = parseConfig("""{"item": "X", "longText": {"title": "Daily", "text": "%.1f kWh today"}}""")
        assertEquals("Daily", config.longText.title)
        assertEquals("%.1f kWh today", config.longText.text)
    }

    @Test
    fun `parses rangedValue config with min max`() {
        val config = parseConfig("""{"item": "X", "rangedValue": {"title": "Power", "text": "%.0f", "min": 0, "max": 50}}""")
        assertEquals("Power", config.rangedValue.title)
        assertEquals("%.0f", config.rangedValue.text)
        assertEquals(0.0, config.rangedValue.min!!, 0.01)
        assertEquals(50.0, config.rangedValue.max!!, 0.01)
    }

    @Test
    fun `rangedValue min max default to null when absent`() {
        val config = parseConfig("""{"item": "X", "rangedValue": {"title": "X", "text": "%.0f"}}""")
        assertEquals(null, config.rangedValue.min)
        assertEquals(null, config.rangedValue.max)
    }

    @Test
    fun `parses monochromaticImage config`() {
        val config = parseConfig("""{"item": "X", "monochromaticImage": {"monochromaticImage": "iconify:mdi:lock", "activeIcon": "iconify:mdi:lock-open", "inactiveIcon": "iconify:mdi:lock"}}""")
        assertEquals("iconify:mdi:lock", config.monochromaticImage.monochromaticImage)
        assertEquals("iconify:mdi:lock-open", config.monochromaticImage.activeIcon)
        assertEquals("iconify:mdi:lock", config.monochromaticImage.inactiveIcon)
    }

    @Test
    fun `iconForState returns activeIcon when active`() {
        val config = parseConfig("""{"item": "X", "monochromaticImage": {"monochromaticImage": "default", "activeIcon": "on-icon", "inactiveIcon": "off-icon"}}""")
        assertEquals("on-icon", config.monochromaticImage.iconForState(true))
    }

    @Test
    fun `iconForState returns inactiveIcon when inactive`() {
        val config = parseConfig("""{"item": "X", "monochromaticImage": {"monochromaticImage": "default", "activeIcon": "on-icon", "inactiveIcon": "off-icon"}}""")
        assertEquals("off-icon", config.monochromaticImage.iconForState(false))
    }

    @Test
    fun `iconForState falls back to monochromaticImage when specific icons blank`() {
        val config = parseConfig("""{"item": "X", "monochromaticImage": {"monochromaticImage": "default-icon"}}""")
        assertEquals("default-icon", config.monochromaticImage.iconForState(true))
        assertEquals("default-icon", config.monochromaticImage.iconForState(false))
    }

    @Test
    fun `missing fields default to empty strings`() {
        val config = parseConfig("""{"item": "X"}""")
        assertEquals("", config.label)
        assertEquals("", config.icon)
        assertEquals("", config.shortText.title)
        assertEquals("", config.shortText.text)
        assertEquals("", config.longText.title)
        assertEquals("", config.longText.text)
        assertEquals("", config.rangedValue.title)
        assertEquals("", config.rangedValue.text)
        assertEquals("", config.monochromaticImage.monochromaticImage)
    }

    @Test
    fun `missing item field defaults to empty string`() {
        val config = parseConfig("""{"label": "NoItem"}""")
        assertEquals("", config.item)
    }

    @Test
    fun `readOnly defaults to false when absent`() {
        val config = parseConfig("""{"item": "X"}""")
        assertEquals(false, config.readOnly)
    }

    @Test
    fun `parses readOnly as boolean true`() {
        val config = parseConfig("""{"item": "X", "readOnly": true}""")
        assertEquals(true, config.readOnly)
    }

    @Test
    fun `parses readOnly as string true`() {
        // openHAB UI configs sometimes serialize booleans as strings
        val config = parseConfig("""{"item": "X", "readOnly": "true"}""")
        assertEquals(true, config.readOnly)
    }

    @Test
    fun `parses readOnly as false`() {
        val config = parseConfig("""{"item": "X", "readOnly": false}""")
        assertEquals(false, config.readOnly)
    }
}
