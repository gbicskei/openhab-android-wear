package org.openhab.habdroid.wear.phone.ui.tiledesign.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TileEditorModelsTest {

    // --- StateDisplay enum ---

    @Test
    fun `StateDisplay fromApi returns VALUE for null`() {
        assertEquals(StateDisplay.VALUE, StateDisplay.fromApi(null))
    }

    @Test
    fun `StateDisplay fromApi returns VALUE for empty string`() {
        assertEquals(StateDisplay.VALUE, StateDisplay.fromApi(""))
    }

    @Test
    fun `StateDisplay fromApi returns VALUE for value`() {
        assertEquals(StateDisplay.VALUE, StateDisplay.fromApi("value"))
    }

    @Test
    fun `StateDisplay fromApi returns COLOR for color`() {
        assertEquals(StateDisplay.COLOR, StateDisplay.fromApi("color"))
    }

    @Test
    fun `StateDisplay fromApi returns COLOR case insensitive`() {
        assertEquals(StateDisplay.COLOR, StateDisplay.fromApi("Color"))
        assertEquals(StateDisplay.COLOR, StateDisplay.fromApi("COLOR"))
    }

    @Test
    fun `StateDisplay fromApi returns NONE for none`() {
        assertEquals(StateDisplay.NONE, StateDisplay.fromApi("none"))
    }

    @Test
    fun `StateDisplay fromApi returns NONE case insensitive`() {
        assertEquals(StateDisplay.NONE, StateDisplay.fromApi("None"))
        assertEquals(StateDisplay.NONE, StateDisplay.fromApi("NONE"))
    }

    @Test
    fun `StateDisplay fromApi returns VALUE for unknown`() {
        assertEquals(StateDisplay.VALUE, StateDisplay.fromApi("icon"))
        assertEquals(StateDisplay.VALUE, StateDisplay.fromApi("text"))
    }

    @Test
    fun `StateDisplay apiValue roundtrips`() {
        assertEquals("value", StateDisplay.VALUE.apiValue)
        assertEquals("color", StateDisplay.COLOR.apiValue)
        assertEquals("none", StateDisplay.NONE.apiValue)
    }

    // --- SlotAction ---

    @Test
    fun `SlotAction Toggle is data object`() {
        val a: SlotAction = SlotAction.Toggle
        val b: SlotAction = SlotAction.Toggle
        assertEquals(a, b)
    }

    @Test
    fun `SlotAction Navigate holds targetPage`() {
        val nav = SlotAction.Navigate("security")
        assertEquals("security", nav.targetPage)
    }

    // --- TileSlotState toDto roundtrip ---

    @Test
    fun `toSlotDto preserves all fields`() {
        val slot = TileSlotState(
            position = 3,
            item = "BDR_Light",
            icon = "light",
            label = "Bedroom",
            stateDisplay = StateDisplay.COLOR,
            action = SlotAction.Command,
            actionCommand = "ON",
            actionItem = "Gate_Trigger",
            stateItem = "Gate_Sensor",
            invertState = true,
            actionConfirmation = true,
            aggregateState = false
        )

        val dto = slot.toSlotDto()
        val config = dto.config

        assertEquals("wear:tile-slot", dto.component)
        assertEquals(3.0, config.position, 0.01)
        assertEquals("BDR_Light", config.item)
        assertEquals("light", config.icon)
        assertEquals("Bedroom", config.label)
        assertEquals("color", config.stateDisplay)
        assertEquals("command", config.action)
        assertEquals("ON", config.actionCommand)
        assertEquals("Gate_Trigger", config.actionItem)
        assertEquals("Gate_Sensor", config.stateItem)
        assertTrue(config.invertState)
        assertTrue(config.actionConfirmation)
        assertFalse(config.aggregateState)
    }

    @Test
    fun `toSlotDto Toggle action writes toggle`() {
        val slot = TileSlotState(position = 1, action = SlotAction.Toggle)
        assertEquals("toggle", slot.toSlotDto().config.action)
    }

    @Test
    fun `toSlotDto Navigate action writes page prefix`() {
        val slot = TileSlotState(position = 1, action = SlotAction.Navigate("security"))
        assertEquals("page:security", slot.toSlotDto().config.action)
    }

    // --- TileSlotState fromDto roundtrip ---

    @Test
    fun `fromDto parses toggle action`() {
        val dto = SlotDto(config = SlotConfig(position = 1.0, action = "toggle"))
        val state = TileSlotState.fromDto(dto)
        assertEquals(SlotAction.Toggle, state.action)
    }

    @Test
    fun `fromDto parses command action`() {
        val dto = SlotDto(config = SlotConfig(position = 1.0, action = "command", actionCommand = "ON"))
        val state = TileSlotState.fromDto(dto)
        assertEquals(SlotAction.Command, state.action)
        assertEquals("ON", state.actionCommand)
    }

    @Test
    fun `fromDto parses page navigation action`() {
        val dto = SlotDto(config = SlotConfig(position = 1.0, action = "page:security"))
        val state = TileSlotState.fromDto(dto)
        assertTrue(state.action is SlotAction.Navigate)
        assertEquals("security", (state.action as SlotAction.Navigate).targetPage)
    }

    @Test
    fun `fromDto null action defaults to Auto`() {
        val dto = SlotDto(config = SlotConfig(position = 1.0, action = null))
        val state = TileSlotState.fromDto(dto)
        assertEquals(SlotAction.Auto, state.action)
    }

    @Test
    fun `fromDto parses stateDisplay`() {
        val dto = SlotDto(config = SlotConfig(position = 1.0, stateDisplay = "none"))
        val state = TileSlotState.fromDto(dto)
        assertEquals(StateDisplay.NONE, state.stateDisplay)
    }

    @Test
    fun `fromDto parses position from float`() {
        val dto = SlotDto(config = SlotConfig(position = 5.0))
        val state = TileSlotState.fromDto(dto)
        assertEquals(5, state.position)
    }

    // --- TileSlotState isEmpty ---

    @Test
    fun `isEmpty true when no item and not navigation`() {
        val slot = TileSlotState(position = 1, item = null, action = SlotAction.Toggle)
        assertTrue(slot.isEmpty)
    }

    @Test
    fun `isEmpty false when item is set`() {
        val slot = TileSlotState(position = 1, item = "BDR_Light", action = SlotAction.Toggle)
        assertFalse(slot.isEmpty)
    }

    @Test
    fun `isEmpty false when action is Navigate even without item`() {
        val slot = TileSlotState(position = 1, item = null, action = SlotAction.Navigate("security"))
        assertFalse(slot.isEmpty)
    }

    // --- TilePageState toDto ---

    @Test
    fun `TilePageState toDto creates correct structure`() {
        val page = TilePageState(
            uid = "security",
            label = "Security",
            layout = 4,
            configVersion = 7,
            slots = listOf(
                TileSlotState(position = 1, item = "Gate"),
                TileSlotState(position = 2, item = null) // empty, should be filtered
            )
        )

        val dto = page.toDto()

        assertEquals("security", dto.uid)
        assertEquals("wear:tile-page", dto.component)
        assertEquals("Security", dto.config.label)
        assertEquals(4.0, dto.config.layout, 0.01)
        assertEquals(7.0, dto.config.configVersion, 0.01)
        assertEquals(1, dto.slots.default.size) // empty slot filtered out
    }

    // --- TilePageState fromDto ---

    @Test
    fun `TilePageState fromDto parses correctly`() {
        val dto = WearTilePageDto(
            uid = "main",
            component = "wear:tile-page",
            config = PageConfig(label = "Main", layout = 6.0, configVersion = 3.0),
            slots = Slots(
                default = listOf(
                    SlotDto(config = SlotConfig(position = 1.0, item = "Light1")),
                    SlotDto(config = SlotConfig(position = 2.0, item = "Light2"))
                )
            )
        )

        val page = TilePageState.fromDto(dto)

        assertEquals("main", page.uid)
        assertEquals("Main", page.label)
        assertEquals(6, page.layout)
        assertEquals(3, page.configVersion)
        assertEquals(2, page.slots.size)
        assertEquals("Light1", page.slots[0].item)
        assertEquals("Light2", page.slots[1].item)
    }

    // --- SlotConfig positionInt ---

    @Test
    fun `SlotConfig positionInt clamps to range`() {
        assertEquals(1, SlotConfig(position = 0.0).positionInt)
        assertEquals(1, SlotConfig(position = -5.0).positionInt)
        assertEquals(7, SlotConfig(position = 10.0).positionInt)
        assertEquals(3, SlotConfig(position = 3.0).positionInt)
    }

    // --- PageConfig layoutInt ---

    @Test
    fun `PageConfig layoutInt clamps to range`() {
        assertEquals(1, PageConfig(layout = 0.0).layoutInt)
        assertEquals(1, PageConfig(layout = -1.0).layoutInt)
        assertEquals(7, PageConfig(layout = 99.0).layoutInt)
        assertEquals(4, PageConfig(layout = 4.0).layoutInt)
    }

    // --- DoubleTap fields in fromDto ---

    @Test
    fun `fromDto parses doubleTapItem`() {
        val dto = SlotDto(config = SlotConfig(
            position = 1.0,
            item = "AC_Power",
            doubleTapItem = "AC_Setpoint"
        ))
        val state = TileSlotState.fromDto(dto)
        assertEquals("AC_Setpoint", state.doubleTapItem)
    }

    @Test
    fun `fromDto parses doubleTapAction toggle`() {
        val dto = SlotDto(config = SlotConfig(
            position = 1.0,
            doubleTapItem = "X",
            doubleTapAction = "toggle"
        ))
        val state = TileSlotState.fromDto(dto)
        assertEquals(SlotAction.Toggle, state.doubleTapAction)
    }

    @Test
    fun `fromDto parses doubleTapAction command`() {
        val dto = SlotDto(config = SlotConfig(
            position = 1.0,
            doubleTapItem = "X",
            doubleTapAction = "command"
        ))
        val state = TileSlotState.fromDto(dto)
        assertEquals(SlotAction.Command, state.doubleTapAction)
    }

    @Test
    fun `fromDto null doubleTapAction maps to null`() {
        val dto = SlotDto(config = SlotConfig(
            position = 1.0,
            doubleTapItem = "X",
            doubleTapAction = null
        ))
        val state = TileSlotState.fromDto(dto)
        assertNull(state.doubleTapAction)
    }

    @Test
    fun `fromDto parses doubleTapCommand`() {
        val dto = SlotDto(config = SlotConfig(
            position = 1.0,
            doubleTapItem = "X",
            doubleTapCommand = "BOOST"
        ))
        val state = TileSlotState.fromDto(dto)
        assertEquals("BOOST", state.doubleTapCommand)
    }

    @Test
    fun `fromDto parses doubleTapConfirmation`() {
        val dto = SlotDto(config = SlotConfig(
            position = 1.0,
            doubleTapItem = "X",
            doubleTapConfirmation = true
        ))
        val state = TileSlotState.fromDto(dto)
        assertTrue(state.doubleTapConfirmation)
    }

    @Test
    fun `fromDto parses doubleTapStateDisplay value`() {
        val dto = SlotDto(config = SlotConfig(
            position = 1.0,
            doubleTapItem = "X",
            doubleTapStateDisplay = "value"
        ))
        val state = TileSlotState.fromDto(dto)
        assertEquals(StateDisplay.VALUE, state.doubleTapStateDisplay)
    }

    @Test
    fun `fromDto parses doubleTapStateDisplay none`() {
        val dto = SlotDto(config = SlotConfig(
            position = 1.0,
            doubleTapItem = "X",
            doubleTapStateDisplay = "none"
        ))
        val state = TileSlotState.fromDto(dto)
        assertEquals(StateDisplay.NONE, state.doubleTapStateDisplay)
    }

    // --- toDto round-trip with doubleTap ---

    @Test
    fun `TilePageState toDto includes slots with doubleTap config`() {
        val page = TilePageState(
            uid = "climate",
            label = "Climate",
            layout = 2,
            slots = listOf(
                TileSlotState(
                    position = 1,
                    item = "AC_Power",
                    doubleTapItem = "AC_Setpoint",
                    doubleTapAction = SlotAction.Toggle,
                    doubleTapCommand = null,
                    doubleTapConfirmation = true,
                    doubleTapStateDisplay = StateDisplay.VALUE
                )
            )
        )
        val dto = page.toDto()
        val slotConfig = dto.slots.default[0].config
        assertEquals("AC_Setpoint", slotConfig.doubleTapItem)
        assertEquals("toggle", slotConfig.doubleTapAction)
        assertTrue(slotConfig.doubleTapConfirmation)
        assertEquals("value", slotConfig.doubleTapStateDisplay)
    }
}
