package com.classicfoo.tetris.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class LevelLockedPaletteTest {
    @Test
    fun `palette repeats every ten levels, including negative modulo`() {
        assertEquals(LevelLockedPalette.ramp(0), LevelLockedPalette.ramp(10))
        assertEquals(LevelLockedPalette.ramp(1), LevelLockedPalette.ramp(21))
        assertEquals(LevelLockedPalette.ramp(9), LevelLockedPalette.ramp(-1))
    }

    @Test
    fun `representative levels map to the requested base colors`() {
        assertEquals(0xFF9EA3A8.toInt(), LevelLockedPalette.ramp(0).base) // light grey
        assertEquals(0xFFC54646.toInt(), LevelLockedPalette.ramp(1).base) // red
        assertEquals(0xFF8D7832.toInt(), LevelLockedPalette.ramp(2).base) // olive/brown
        assertEquals(0xFF23AFC4.toInt(), LevelLockedPalette.ramp(7).base) // cyan/light blue
        assertEquals(0xFF4EAB57.toInt(), LevelLockedPalette.ramp(9).base) // green
    }

    @Test
    fun `ramps preserve distinct bevel tones and full opacity`() {
        (0 until 10).forEach { level ->
            val ramp = LevelLockedPalette.ramp(level)

            assertEquals(0xFF, ramp.base ushr 24)
            assertEquals(0xFF, ramp.highlight ushr 24)
            assertEquals(0xFF, ramp.shadow ushr 24)
            assertEquals(true, ramp.highlight != ramp.base)
            assertEquals(true, ramp.shadow != ramp.base)
        }
    }
}
