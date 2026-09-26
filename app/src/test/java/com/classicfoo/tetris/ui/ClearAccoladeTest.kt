package com.classicfoo.tetris.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class ClearAccoladeTest {
    @Test
    fun `standard line counts receive distinct accolades`() {
        assertEquals(ClearAccolade.SINGLE, ClearAccolade.forLines(1))
        assertEquals(ClearAccolade.DOUBLE, ClearAccolade.forLines(2))
        assertEquals(ClearAccolade.TRIPLE, ClearAccolade.forLines(3))
        assertEquals(ClearAccolade.TETRIS, ClearAccolade.forLines(4))
    }

    @Test
    fun `accolade labels are concise and celebratory`() {
        assertEquals("SINGLE!", ClearAccolade.forLines(1).label)
        assertEquals("DOUBLE!", ClearAccolade.forLines(2).label)
        assertEquals("TRIPLE!", ClearAccolade.forLines(3).label)
        assertEquals("TETRIS!", ClearAccolade.forLines(4).label)
    }

    @Test
    fun `unexpected line counts use a safe clear fallback`() {
        assertEquals(ClearAccolade.CLEAR, ClearAccolade.forLines(0))
        assertEquals(ClearAccolade.CLEAR, ClearAccolade.forLines(-1))
        assertEquals(ClearAccolade.CLEAR, ClearAccolade.forLines(5))
        assertEquals("CLEAR!", ClearAccolade.forLines(99).label)
    }
}
