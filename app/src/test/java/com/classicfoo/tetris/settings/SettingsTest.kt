package com.classicfoo.tetris.settings

import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsTest {
    @Test
    fun `music is enabled by default`() {
        assertTrue(GameSettings().musicEnabled)
    }
}
