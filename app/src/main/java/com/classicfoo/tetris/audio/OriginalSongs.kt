package com.classicfoo.tetris.audio

/** Original short loops, composed for the app rather than copied from any commercial soundtrack. */
object OriginalSongs {
    val songs: List<MidiSong> by lazy {
        listOf(
            MidiSong("Arcade A", StandardMidiBuilder.build(154, arcadeA())),
            MidiSong("Arcade B", StandardMidiBuilder.build(146, arcadeB())),
            MidiSong("Arcade C", StandardMidiBuilder.build(162, arcadeC())),
        )
    }

    private fun arcadeA(): List<SongNote> = buildList {
        val melody = listOf(72, 75, 79, 82, 79, 75, 74, 77, 81, 84, 81, 77, 76, 79, 83, 86)
        melody.forEachIndexed { index, pitch -> add(SongNote(index * 0.5, 0.38, pitch)) }
        val bass = listOf(48, 48, 43, 43, 45, 45, 41, 41)
        bass.forEachIndexed { index, pitch -> add(SongNote(index * 1.0, 0.82, pitch, 76, 1, 32)) }
        listOf(60, 64, 67, 71).forEachIndexed { index, pitch -> add(SongNote(index * 2.0, 0.32, pitch, 56, 2, 81)) }
    }

    private fun arcadeB(): List<SongNote> = buildList {
        val melody = listOf(76, 74, 72, 69, 71, 74, 77, 81, 79, 76, 74, 71, 72, 76, 79, 83)
        melody.forEachIndexed { index, pitch -> add(SongNote(index * 0.5, 0.34, pitch, 94, 0, 81)) }
        val bass = listOf(45, 45, 50, 50, 43, 43, 48, 48)
        bass.forEachIndexed { index, pitch -> add(SongNote(index * 1.0, 0.78, pitch, 74, 1, 32)) }
        (0 until 8).forEach { index ->
            add(SongNote(index * 1.0 + 0.5, 0.18, 57 + (index % 3) * 4, 48, 2, 80))
        }
    }

    private fun arcadeC(): List<SongNote> = buildList {
        val melody = listOf(67, 71, 74, 79, 77, 74, 71, 69, 72, 76, 79, 84, 81, 77, 74, 72)
        melody.forEachIndexed { index, pitch -> add(SongNote(index * 0.5, 0.4, pitch, 92, 0, 80)) }
        val bass = listOf(43, 43, 47, 47, 40, 40, 45, 45)
        bass.forEachIndexed { index, pitch -> add(SongNote(index * 1.0, 0.86, pitch, 78, 1, 33)) }
        listOf(55, 59, 62, 66).forEachIndexed { index, pitch -> add(SongNote(index * 2.0 + 0.25, 0.22, pitch, 50, 2, 82)) }
    }
}
