package com.classicfoo.tetris.audio

/** Three newly composed NES-era-inspired loops; no commercial MIDI or recordings are bundled. */
object OriginalSongs {
    private const val SECTION_COUNT = 8
    private const val SECTION_BEATS = 32.0

    val songs: List<MidiSong> by lazy {
        listOf(
            song(
                name = "Stackline",
                bpm = 150,
                lead = listOf(
                    intArrayOf(76, 79, 81, 79, 84, 83, 81, 79, 76, 79, 81, 84, 83, 81, 79, 76),
                    intArrayOf(79, 81, 84, 86, 84, 81, 79, 76, 79, 81, 83, 86, 84, 83, 81, 79),
                    intArrayOf(81, 84, 86, 88, 86, 84, 81, 79, 81, 84, 86, 89, 88, 86, 84, 81),
                    intArrayOf(84, 83, 81, 79, 81, 83, 84, 86, 88, 86, 84, 81, 79, 81, 83, 84),
                    intArrayOf(88, 86, 84, 81, 84, 86, 88, 91, 89, 88, 86, 84, 81, 84, 86, 88),
                    intArrayOf(86, 89, 91, 93, 91, 89, 86, 84, 86, 89, 91, 94, 93, 91, 89, 86),
                    intArrayOf(84, 86, 88, 91, 88, 86, 84, 81, 84, 86, 89, 91, 89, 86, 84, 81),
                    intArrayOf(79, 81, 84, 86, 84, 81, 79, 76, 79, 81, 83, 86, 84, 81, 79, 76),
                ),
                bass = listOf(
                    intArrayOf(40, 40, 47, 47, 45, 45, 36, 36),
                    intArrayOf(40, 40, 43, 43, 48, 48, 36, 36),
                    intArrayOf(41, 41, 48, 48, 43, 43, 38, 38),
                    intArrayOf(43, 43, 47, 47, 40, 40, 36, 36),
                    intArrayOf(45, 45, 52, 52, 48, 48, 41, 41),
                    intArrayOf(45, 45, 48, 48, 53, 53, 41, 41),
                    intArrayOf(43, 43, 50, 50, 45, 45, 38, 38),
                    intArrayOf(40, 40, 47, 47, 43, 43, 36, 36),
                ),
                counter = listOf(
                    intArrayOf(60, 64, 67, 64, 62, 65, 69, 65, 60, 64, 67, 71, 69, 65, 62, 60),
                    intArrayOf(64, 67, 71, 67, 65, 69, 72, 69, 64, 67, 71, 74, 72, 69, 65, 64),
                    intArrayOf(65, 69, 72, 69, 67, 71, 74, 71, 65, 69, 72, 76, 74, 71, 67, 65),
                    intArrayOf(67, 71, 74, 71, 69, 72, 76, 72, 67, 71, 74, 77, 76, 72, 69, 67),
                    intArrayOf(69, 72, 76, 72, 71, 74, 77, 74, 69, 72, 76, 79, 77, 74, 71, 69),
                    intArrayOf(71, 74, 77, 74, 72, 76, 79, 76, 71, 74, 77, 81, 79, 76, 72, 71),
                    intArrayOf(67, 71, 74, 71, 69, 72, 76, 72, 67, 71, 74, 77, 76, 72, 69, 67),
                    intArrayOf(64, 67, 71, 67, 65, 69, 72, 69, 64, 67, 71, 74, 72, 69, 65, 64),
                ),
                noiseSeed = 11,
            ),
            song(
                name = "Copper Circuit",
                bpm = 156,
                lead = listOf(
                    intArrayOf(72, 75, 79, 75, 77, 80, 84, 80, 79, 82, 86, 82, 80, 77, 75, 72),
                    intArrayOf(75, 79, 82, 79, 80, 84, 87, 84, 82, 86, 89, 86, 84, 80, 79, 75),
                    intArrayOf(79, 82, 86, 82, 84, 87, 91, 87, 86, 89, 92, 89, 87, 84, 82, 79),
                    intArrayOf(82, 86, 89, 86, 87, 91, 94, 91, 89, 92, 96, 92, 91, 87, 86, 82),
                    intArrayOf(84, 87, 91, 87, 89, 92, 96, 92, 91, 94, 98, 94, 92, 89, 87, 84),
                    intArrayOf(87, 91, 94, 91, 92, 96, 99, 96, 94, 98, 101, 98, 96, 92, 91, 87),
                    intArrayOf(84, 87, 91, 87, 89, 92, 96, 92, 91, 94, 98, 94, 92, 89, 87, 84),
                    intArrayOf(79, 82, 86, 82, 84, 87, 91, 87, 86, 89, 92, 89, 87, 84, 82, 79),
                ),
                bass = listOf(
                    intArrayOf(36, 36, 43, 43, 41, 41, 34, 34),
                    intArrayOf(36, 36, 40, 40, 45, 45, 33, 33),
                    intArrayOf(38, 38, 45, 45, 41, 41, 34, 34),
                    intArrayOf(40, 40, 47, 47, 43, 43, 36, 36),
                    intArrayOf(41, 41, 48, 48, 45, 45, 38, 38),
                    intArrayOf(43, 43, 50, 50, 47, 47, 40, 40),
                    intArrayOf(41, 41, 48, 48, 45, 45, 38, 38),
                    intArrayOf(38, 38, 45, 45, 41, 41, 34, 34),
                ),
                counter = listOf(
                    intArrayOf(60, 63, 67, 63, 65, 68, 72, 68, 67, 70, 74, 70, 68, 65, 63, 60),
                    intArrayOf(63, 67, 70, 67, 68, 72, 75, 72, 70, 74, 77, 74, 72, 68, 67, 63),
                    intArrayOf(67, 70, 74, 70, 72, 75, 79, 75, 74, 77, 80, 77, 75, 72, 70, 67),
                    intArrayOf(70, 74, 77, 74, 75, 79, 82, 79, 77, 80, 84, 80, 79, 75, 74, 70),
                    intArrayOf(72, 75, 79, 75, 77, 80, 84, 80, 79, 82, 86, 82, 80, 77, 75, 72),
                    intArrayOf(75, 79, 82, 79, 80, 84, 87, 84, 82, 86, 89, 86, 84, 80, 79, 75),
                    intArrayOf(72, 75, 79, 75, 77, 80, 84, 80, 79, 82, 86, 82, 80, 77, 75, 72),
                    intArrayOf(67, 70, 74, 70, 72, 75, 79, 75, 74, 77, 80, 77, 75, 72, 70, 67),
                ),
                noiseSeed = 37,
            ),
            song(
                name = "Lockstep",
                bpm = 162,
                lead = listOf(
                    intArrayOf(67, 71, 74, 71, 76, 74, 71, 69, 67, 71, 74, 79, 77, 74, 71, 67),
                    intArrayOf(69, 72, 76, 72, 77, 76, 72, 71, 69, 72, 76, 81, 79, 76, 72, 69),
                    intArrayOf(71, 74, 79, 74, 79, 77, 74, 72, 71, 74, 79, 83, 81, 79, 74, 71),
                    intArrayOf(72, 76, 79, 76, 81, 79, 76, 74, 72, 76, 79, 84, 82, 79, 76, 72),
                    intArrayOf(74, 79, 83, 79, 84, 83, 79, 77, 74, 79, 83, 88, 86, 83, 79, 74),
                    intArrayOf(76, 81, 84, 81, 86, 84, 81, 79, 76, 81, 84, 89, 87, 84, 81, 76),
                    intArrayOf(74, 79, 83, 79, 84, 83, 79, 77, 74, 79, 83, 88, 86, 83, 79, 74),
                    intArrayOf(71, 74, 79, 74, 79, 77, 74, 72, 71, 74, 79, 83, 81, 79, 74, 71),
                ),
                bass = listOf(
                    intArrayOf(43, 43, 50, 50, 47, 47, 40, 40),
                    intArrayOf(45, 45, 52, 52, 48, 48, 41, 41),
                    intArrayOf(47, 47, 54, 54, 50, 50, 43, 43),
                    intArrayOf(48, 48, 55, 55, 52, 52, 45, 45),
                    intArrayOf(50, 50, 57, 57, 53, 53, 47, 47),
                    intArrayOf(52, 52, 59, 59, 55, 55, 48, 48),
                    intArrayOf(50, 50, 57, 57, 53, 53, 47, 47),
                    intArrayOf(47, 47, 54, 54, 50, 50, 43, 43),
                ),
                counter = listOf(
                    intArrayOf(55, 59, 62, 59, 64, 62, 59, 57, 55, 59, 62, 67, 65, 62, 59, 55),
                    intArrayOf(57, 60, 64, 60, 65, 64, 60, 59, 57, 60, 64, 69, 67, 64, 60, 57),
                    intArrayOf(59, 62, 67, 62, 67, 65, 62, 60, 59, 62, 67, 71, 69, 67, 62, 59),
                    intArrayOf(60, 64, 67, 64, 69, 67, 64, 62, 60, 64, 67, 72, 70, 67, 64, 60),
                    intArrayOf(62, 67, 71, 67, 72, 71, 67, 65, 62, 67, 71, 76, 74, 71, 67, 62),
                    intArrayOf(64, 69, 72, 69, 74, 72, 69, 67, 64, 69, 72, 77, 75, 72, 69, 64),
                    intArrayOf(62, 67, 71, 67, 72, 71, 67, 65, 62, 67, 71, 76, 74, 71, 67, 62),
                    intArrayOf(59, 62, 67, 62, 67, 65, 62, 60, 59, 62, 67, 71, 69, 67, 62, 59),
                ),
                noiseSeed = 71,
            ),
        )
    }

    private fun song(
        name: String,
        bpm: Int,
        lead: List<IntArray>,
        bass: List<IntArray>,
        counter: List<IntArray>,
        noiseSeed: Int,
    ): MidiSong {
        val notes = compose(lead, bass, counter, noiseSeed)
        return MidiSong(
            name = name,
            bytes = StandardMidiBuilder.build(
                bpm = bpm,
                notes = notes,
                endTick = TETRIS_LOOP_END_TICK,
                loopStartTick = 0,
                loopEndTick = TETRIS_LOOP_END_TICK,
            ),
            loopStartTick = 0L,
            loopEndTick = TETRIS_LOOP_END_TICK.toLong(),
        )
    }

    private fun compose(
        lead: List<IntArray>,
        bass: List<IntArray>,
        counter: List<IntArray>,
        noiseSeed: Int,
    ): List<SongNote> = buildList {
        repeat(SECTION_COUNT) { section ->
            val sectionStart = section * SECTION_BEATS
            val leadPattern = lead[section]
            val sparseBreakdown = section == 4
            val buildSection = section == 5 || section == 6
            repeat(4) { phrase ->
                leadPattern.forEachIndexed { step, pitch ->
                    val keepBreakdownNote = phrase == 0 && step % 2 == 0 || phrase == 3 && step >= 12
                    if (!sparseBreakdown || keepBreakdownNote) {
                        val duration = when {
                            section == SECTION_COUNT - 1 && phrase == 3 && step >= 14 -> 0.28
                            sparseBreakdown -> 0.30
                            buildSection -> 0.42
                            else -> 0.38
                        }
                        add(SongNote(sectionStart + phrase * 8.0 + step * 0.5, duration, pitch, 108, 0, 80))
                    }
                }
            }

            bass[section].forEachIndexed { step, pitch ->
                if (!sparseBreakdown || step % 2 == 0) {
                    add(SongNote(sectionStart + step * 4.0, if (sparseBreakdown) 1.8 else 3.35, pitch, 82, 1, 33))
                }
            }

            if (!sparseBreakdown) {
                repeat(4) { phrase ->
                    counter[section].forEachIndexed { step, pitch ->
                        add(SongNote(sectionStart + phrase * 8.0 + 0.25 + step * 0.5, 0.22, pitch, 58, 2, 81))
                    }
                }
            }

            repeat(64) { step ->
                if (sparseBreakdown && step % 4 != 0) return@repeat
                val beat = sectionStart + step * 0.5
                val accent = (step + section + noiseSeed) % 8
                val pitch = when {
                    accent == 0 -> 36
                    accent == 4 -> 38
                    step % 2 == 0 -> 42
                    else -> 46
                }
                val velocity = when {
                    accent == 0 -> 106
                    accent == 4 -> 92
                    else -> (54 + ((step + noiseSeed) % 3) * 8) + if (buildSection) 10 else 0
                }
                add(SongNote(beat, if (pitch == 42 || pitch == 46) 0.12 else 0.18, pitch, velocity, 9, 0))
            }
        }
    }
}
