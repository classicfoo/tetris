package com.classicfoo.tetris.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsTest {
    @Test
    fun `legacy high scores migrate from one-based levels`() {
        assertEquals(0, migrateScoreLevel(1, LEGACY_LEVEL_BASE))
        assertEquals(1, migrateScoreLevel(2, LEGACY_LEVEL_BASE))
        assertEquals(0, migrateScoreLevel(0, LEGACY_LEVEL_BASE))
    }

    @Test
    fun `music is enabled by default`() {
        assertTrue(GameSettings().musicEnabled)
    }
}
