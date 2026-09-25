package com.classicfoo.tetris.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsTest {
    @Test
    fun `legacy themes migrate to classic`() {
        assertEquals(ThemeOption.CLASSIC, ThemeOption.fromStoredName("NEON"))
        assertEquals(ThemeOption.CLASSIC, ThemeOption.fromStoredName("MONOCHROME"))
        assertEquals(ThemeOption.CLASSIC, ThemeOption.fromStoredName("unknown"))
        assertEquals(ThemeOption.CLASSIC, ThemeOption.fromStoredName(null))
    }

    @Test
    fun `new themes remain distinct and cycle`() {
        assertEquals(ThemeOption.TENGEN_BEVEL, ThemeOption.CLASSIC.next())
        assertEquals(ThemeOption.GAME_BOY, ThemeOption.TENGEN_BEVEL.next())
        assertEquals(ThemeOption.CLASSIC, ThemeOption.GAME_BOY.next())
    }

    @Test
    fun `music is enabled by default`() {
        assertTrue(GameSettings().musicEnabled)
    }
}
