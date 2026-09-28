package com.classicfoo.tetris.ui

/**
 * Shared bevel colors for locked pieces. Every piece locked during a level
 * should use the same ramp; the ten-color sequence repeats as levels advance.
 */
object LevelLockedPalette {
    private val ramps = listOf(
        // 0: light grey
        OpaqueColorRamp(0xFF9EA3A8.toInt(), 0xFFE1E5E8.toInt(), 0xFF4A5056.toInt()),
        // 1: red
        OpaqueColorRamp(0xFFC54646.toInt(), 0xFFF0786F.toInt(), 0xFF681F2D.toInt()),
        // 2: olive/brown
        OpaqueColorRamp(0xFF8D7832.toInt(), 0xFFC8B15B.toInt(), 0xFF4E411D.toInt()),
        // 3: blue
        OpaqueColorRamp(0xFF3E64B7.toInt(), 0xFF7FA1E1.toInt(), 0xFF1D356F.toInt()),
        // 4: orange
        OpaqueColorRamp(0xFFD96B32.toInt(), 0xFFF5A05B.toInt(), 0xFF73311F.toInt()),
        // 5: magenta
        OpaqueColorRamp(0xFFA043A8.toInt(), 0xFFD979CF.toInt(), 0xFF55225E.toInt()),
        // 6: lime green
        OpaqueColorRamp(0xFF78AA3D.toInt(), 0xFFB8E56A.toInt(), 0xFF3C6420.toInt()),
        // 7: cyan/light blue
        OpaqueColorRamp(0xFF23AFC4.toInt(), 0xFF75DFE1.toInt(), 0xFF0F506B.toInt()),
        // 8: purple
        OpaqueColorRamp(0xFF7A45B5.toInt(), 0xFFBE82E7.toInt(), 0xFF412061.toInt()),
        // 9: green
        OpaqueColorRamp(0xFF4EAB57.toInt(), 0xFF89D875.toInt(), 0xFF245931.toInt()),
    )

    /** Returns the locked-piece ramp for [level], repeating every ten levels. */
    fun ramp(level: Int): OpaqueColorRamp = ramps[Math.floorMod(level, ramps.size)]
}
