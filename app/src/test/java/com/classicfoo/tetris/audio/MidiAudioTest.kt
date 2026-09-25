package com.classicfoo.tetris.audio

import com.classicfoo.tetris.engine.GameEvent
import java.io.ByteArrayOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MidiAudioTest {
    @Test
    fun `parser reads standard MIDI header and extracts a closed note`() {
        val midi = midi(
            track(
                event(0, 0xC0, 7),
                event(0, 0x90, 60, 100),
                event(480, 0x80, 60, 0),
                event(0, 0xFF, 0x2F, 0),
            ),
            division = 480,
        )

        val parsed = MidiParser.parse(midi)

        assertEquals(0, headerFormat(midi))
        assertEquals(1, headerTrackCount(midi))
        assertEquals(480, parsed.division)
        assertEquals(1, parsed.notes.size)
        assertEquals(60, parsed.notes.single().pitch)
        assertEquals(100, parsed.notes.single().velocity)
        assertEquals(0, parsed.notes.single().startTick)
        assertEquals(480, parsed.notes.single().endTick)
        assertEquals(7, parsed.notes.single().program)
        assertEquals(480, parsed.endTick)
    }

    @Test
    fun `parser handles running status and note-on velocity zero as note-off`() {
        val midi = midi(
            track(
                event(0, 0x90, 60, 96),
                // The status byte is intentionally omitted here.
                event(120, 62, 80),
                // A zero-velocity note-on is the MIDI-equivalent of note-off.
                event(120, 60, 0),
                event(0, 62, 0),
                event(0, 0xFF, 0x2F, 0),
            ),
            division = 120,
        )

        val notes = MidiParser.parse(midi).notes.sortedBy { it.pitch }

        assertEquals(2, notes.size)
        assertEquals(60, notes[0].pitch)
        assertEquals(0, notes[0].startTick)
        assertEquals(240, notes[0].endTick)
        assertEquals(62, notes[1].pitch)
        assertEquals(120, notes[1].startTick)
        assertEquals(240, notes[1].endTick)
    }

    @Test
    fun `sequencer applies tempo changes to note timing`() {
        val midi = midi(
            track(
                event(0, 0xFF, 0x51, 3, 0x07, 0xA1, 0x20), // 500,000 us/qn
                event(0, 0x90, 60, 100),
                event(480, 0x80, 60, 0),
                event(0, 0xFF, 0x51, 3, 0x0F, 0x42, 0x40), // 1,000,000 us/qn
                event(480, 0x90, 62, 100),
                event(480, 0x80, 62, 0),
                event(0, 0xFF, 0x2F, 0),
            ),
            division = 480,
        )

        val parsed = MidiParser.parse(midi)
        val sequence = MidiSequencer(parsed)
        val first = sequence.notes.first { it.pitch == 60 }
        val second = sequence.notes.first { it.pitch == 62 }

        assertEquals(0L, first.startMicros)
        assertEquals(500_000L, first.endMicros)
        assertEquals(1_500_000L, second.startMicros)
        assertEquals(2_500_000L, second.endMicros)
        assertEquals(2_500_000L, sequence.durationMicros)
        assertEquals(2, parsed.tempos.size)
    }

    @Test
    fun `generated original songs are valid MIDI with playable notes`() {
        assertEquals(listOf("Arcade A", "Arcade B", "Arcade C"), OriginalSongs.songs.map { it.name })

        OriginalSongs.songs.forEach { song ->
            assertTrue(song.bytes.copyOfRange(0, 4).contentEquals("MThd".encodeToByteArray()))

            val parsed = MidiParser.parse(song.bytes)
            assertEquals(96, parsed.division)
            assertTrue(parsed.notes.isNotEmpty())
            assertTrue(parsed.endTick > 0)
            assertTrue(parsed.notes.all { it.pitch in 0..127 && it.velocity in 1..127 })
            assertTrue(parsed.tempos.any { it.microsecondsPerQuarter > 0 })
        }
    }

    @Test
    fun `synth renders non-empty PCM for generated original songs`() {
        OriginalSongs.songs.forEach { song ->
            val samples = ChiptuneSynth.render(song, maxSeconds = 2)

            assertTrue(samples.isNotEmpty())
            assertTrue(samples.any { it.toInt() != 0 })
            assertTrue(samples.all { it.toInt() in Short.MIN_VALUE..Short.MAX_VALUE })
        }
    }

    @Test
    fun `synth produces output for every gameplay effect`() {
        GameEvent.entries
            .filter { it != GameEvent.NONE }
            .forEach { event ->
                val samples = ChiptuneSynth.renderEffect(event)

                assertTrue("Expected samples for $event", samples.isNotEmpty())
                assertTrue("Expected non-silent output for $event", samples.any { it.toInt() != 0 })
            }

        assertTrue(ChiptuneSynth.renderEffect(GameEvent.NONE).isEmpty())
    }

    @Test(expected = IllegalArgumentException::class)
    fun `parser rejects a non-MIDI header`() {
        MidiParser.parse("not midi".encodeToByteArray())
    }

    private fun track(vararg events: ByteArray): ByteArray {
        val body = ByteArrayOutputStream()
        events.forEach(body::write)
        return body.toByteArray()
    }

    private fun event(delta: Int, vararg bytes: Int): ByteArray {
        val output = ByteArrayOutputStream()
        writeVariableLength(output, delta)
        bytes.forEach { output.write(it and 0xFF) }
        return output.toByteArray()
    }

    private fun midi(track: ByteArray, division: Int): ByteArray = midi(listOf(track), division)

    private fun midi(tracks: List<ByteArray>, division: Int): ByteArray {
        val output = ByteArrayOutputStream()
        output.write("MThd".encodeToByteArray())
        writeInt32(output, 6)
        writeUInt16(output, if (tracks.size == 1) 0 else 1)
        writeUInt16(output, tracks.size)
        writeUInt16(output, division)
        tracks.forEach { track ->
            output.write("MTrk".encodeToByteArray())
            writeInt32(output, track.size)
            output.write(track)
        }
        return output.toByteArray()
    }

    private fun headerFormat(bytes: ByteArray): Int =
        ((bytes[8].toInt() and 0xFF) shl 8) or (bytes[9].toInt() and 0xFF)

    private fun headerTrackCount(bytes: ByteArray): Int =
        ((bytes[10].toInt() and 0xFF) shl 8) or (bytes[11].toInt() and 0xFF)

    private fun writeVariableLength(output: ByteArrayOutputStream, value: Int) {
        require(value >= 0)
        var buffer = value and 0x7F
        var remaining = value ushr 7
        while (remaining > 0) {
            buffer = (buffer shl 8) or ((remaining and 0x7F) or 0x80)
            remaining = remaining ushr 7
        }
        while (true) {
            output.write(buffer and 0xFF)
            if (buffer and 0x80 == 0) return
            buffer = buffer ushr 8
        }
    }

    private fun writeUInt16(output: ByteArrayOutputStream, value: Int) {
        output.write(value ushr 8)
        output.write(value)
    }

    private fun writeInt32(output: ByteArrayOutputStream, value: Int) {
        output.write(value ushr 24)
        output.write(value ushr 16)
        output.write(value ushr 8)
        output.write(value)
    }
}
