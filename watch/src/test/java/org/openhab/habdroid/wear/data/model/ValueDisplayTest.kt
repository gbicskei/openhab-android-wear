package org.openhab.habdroid.wear.data.model

import org.junit.Assert.assertEquals
import org.junit.Test

class ValueDisplayTest {

    @Test
    fun `fromString returns COLOR for color`() {
        assertEquals(ValueDisplay.COLOR, ValueDisplay.fromString("color"))
    }

    @Test
    fun `fromString returns COLOR for COLOR uppercase`() {
        assertEquals(ValueDisplay.COLOR, ValueDisplay.fromString("COLOR"))
    }

    @Test
    fun `fromString returns COLOR for Color mixed case`() {
        assertEquals(ValueDisplay.COLOR, ValueDisplay.fromString("Color"))
    }

    @Test
    fun `fromString returns VALUE for value`() {
        assertEquals(ValueDisplay.VALUE, ValueDisplay.fromString("value"))
    }

    @Test
    fun `fromString returns VALUE for VALUE uppercase`() {
        assertEquals(ValueDisplay.VALUE, ValueDisplay.fromString("VALUE"))
    }

    @Test
    fun `fromString returns VALUE for null`() {
        assertEquals(ValueDisplay.VALUE, ValueDisplay.fromString(null))
    }

    @Test
    fun `fromString returns VALUE for empty string`() {
        assertEquals(ValueDisplay.VALUE, ValueDisplay.fromString(""))
    }

    @Test
    fun `fromString returns VALUE for unknown string`() {
        assertEquals(ValueDisplay.VALUE, ValueDisplay.fromString("text"))
        assertEquals(ValueDisplay.VALUE, ValueDisplay.fromString("icon"))
    }
}
