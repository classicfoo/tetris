package com.classicfoo.tetris.audio

import com.classicfoo.tetris.engine.GameEvent
import java.io.ByteArrayOutputStream
import java.util.ArrayDeque
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

data class MidiSong(
    val name: String,
    val bytes: ByteArray,
)

data class MidiNote(
    val channel: Int,
    val pitch: Int,
    val velocity: Int,
    val startTick: Long,
    val endTick: Long,
    val program: Int,
)

data class MidiTempo(
    val tick: Long,
    val microsecondsPerQuarter: Int,
)

data class ParsedMidiSong(
    val division: Int,
    val notes: List<MidiNote>,
    val tempos: List<MidiTempo>,
    val endTick: Long,
)

/** Small Standard MIDI 1.0 parser for the bundled original songs. */
object MidiParser {
    fun parse(bytes: ByteArray): ParsedMidiSong {
        val reader = ByteReader(bytes)
        require(reader.readAscii(4) == "MThd") { "Invalid MIDI header" }
        val headerLength = reader.readInt32()
        require(headerLength >= 6) { "Invalid MIDI header length" }
        val format = reader.readUInt16()
        require(format in 0..1) { "Only MIDI formats 0 and 1 are supported" }
        val trackCount = reader.readUInt16()
        val division = reader.readUInt16()
        require(division > 0 && division and 0x8000 == 0) { "SMPTE MIDI timing is unsupported" }
        reader.skip(headerLength - 6)

        val notes = mutableListOf<MidiNote>()
        val tempos = mutableListOf<MidiTempo>()
        val programs = IntArray(16)
        var endTick = 0L

        repeat(trackCount) {
            require(reader.readAscii(4) == "MTrk") { "Invalid MIDI track" }
            val trackLength = reader.readInt32()
            require(trackLength >= 0 && reader.position + trackLength <= bytes.size) { "Invalid MIDI track length" }
            val trackEnd = reader.position + trackLength
            var tick = 0L
            var runningStatus = 0
            val active = mutableMapOf<Int, ArrayDeque<OpenNote>>()

            while (reader.position < trackEnd) {
                tick += reader.readVariableLength()
                var status = reader.readUInt8()
                var firstData: Int? = null
                if (status < 0x80) {
                    firstData = status
                    status = runningStatus
                } else if (status < 0xF0) {
                    runningStatus = status
                }

                when {
                    status == 0xFF -> {
                        val metaType = reader.readUInt8()
                        val length = reader.readVariableLength().toInt()
                        if (metaType == 0x51 && length == 3) {
                            tempos += MidiTempo(tick, reader.readUInt24())
                        } else {
                            reader.skip(length)
                        }
                        if (metaType == 0x2F) {
                            reader.skip(length)
                            break
                        }
                    }
                    status == 0xF0 || status == 0xF7 -> reader.skip(reader.readVariableLength().toInt())
                    status >= 0xF0 -> when (status) {
                        0xF1, 0xF3 -> reader.skip(1)
                        0xF2 -> reader.skip(2)
                        0xF4, 0xF5, 0xF6, 0xF8, 0xF9, 0xFA, 0xFB, 0xFC, 0xFD, 0xFE -> Unit
                        else -> throw IllegalArgumentException("Unsupported MIDI system status: $status")
                    }
                    else -> {
                        val command = status and 0xF0
                        val channel = status and 0x0F
                        val data1 = firstData ?: reader.readUInt8()
                        when (command) {
                            0x80, 0x90 -> {
                                val velocity = reader.readUInt8()
                                val key = channel * 128 + data1
                                if (command == 0x90 && velocity > 0) {
                                    active.getOrPut(key) { ArrayDeque() }
                                        .addLast(OpenNote(tick, data1, velocity, programs[channel], channel))
                                } else {
                                    active[key]?.pollLast()?.let { open ->
                                        notes += MidiNote(
                                            channel = channel,
                                            pitch = open.pitch,
                                            velocity = open.velocity,
                                            startTick = open.startTick,
                                            endTick = max(tick, open.startTick + 1),
                                            program = open.program,
                                        )
                                    }
                                }
                            }
                            0xB0, 0xE0, 0xA0 -> reader.skip(1)
                            0xC0 -> programs[channel] = data1
                            0xD0 -> Unit
                            else -> throw IllegalArgumentException("Unsupported MIDI command: $command")
                        }
                    }
                }
                endTick = max(endTick, tick)
            }

            active.values.forEach { queue ->
                queue.forEach { open ->
                    notes += MidiNote(
                        channel = open.channel,
                        pitch = open.pitch,
                        velocity = open.velocity,
                        startTick = open.startTick,
                        endTick = max(tick, open.startTick + 1),
                        program = open.program,
                    )
                }
            }
            reader.position = trackEnd
            endTick = max(endTick, tick)
        }

        return ParsedMidiSong(
            division = division,
            notes = notes.sortedBy { it.startTick },
            tempos = tempos.sortedBy { it.tick },
            endTick = endTick,
        )
    }

    private data class OpenNote(
        val startTick: Long,
        val pitch: Int,
        val velocity: Int,
        val program: Int,
        val channel: Int = 0,
    )

    private class ByteReader(private val bytes: ByteArray) {
        var position: Int = 0

        fun readUInt8(): Int {
            require(position < bytes.size) { "Unexpected end of MIDI data" }
            return bytes[position++].toInt() and 0xFF
        }

        fun readUInt16(): Int = (readUInt8() shl 8) or readUInt8()

        fun readUInt24(): Int = (readUInt8() shl 16) or (readUInt8() shl 8) or readUInt8()

        fun readInt32(): Int {
            val value = (readUInt8() shl 24) or (readUInt8() shl 16) or (readUInt8() shl 8) or readUInt8()
            require(value >= 0) { "MIDI chunk is too large" }
            return value
        }

        fun readAscii(length: Int): String = buildString {
            repeat(length) { append(readUInt8().toChar()) }
        }

        fun readVariableLength(): Long {
            var value = 0L
            repeat(4) {
                val byte = readUInt8()
                value = (value shl 7) or (byte and 0x7F).toLong()
                if (byte and 0x80 == 0) return value
            }
            throw IllegalArgumentException("Invalid MIDI variable length value")
        }

        fun skip(length: Int) {
            require(length >= 0 && position + length <= bytes.size) { "Invalid MIDI length" }
            position += length
        }
    }

    const val DEFAULT_TEMPO_US = 500_000
}

data class TimedMidiNote(
    val channel: Int,
    val pitch: Int,
    val velocity: Int,
    val startMicros: Long,
    val endMicros: Long,
    val program: Int,
)

class MidiSequencer(song: ParsedMidiSong) {
    val notes: List<TimedMidiNote> = song.notes.map { note ->
        TimedMidiNote(
            channel = note.channel,
            pitch = note.pitch,
            velocity = note.velocity,
            startMicros = tickToMicros(note.startTick, song),
            endMicros = tickToMicros(note.endTick, song),
            program = note.program,
        )
    }
    val durationMicros: Long = max(
        tickToMicros(song.endTick, song),
        notes.maxOfOrNull { it.endMicros } ?: 0L,
    )

    private fun tickToMicros(tick: Long, song: ParsedMidiSong): Long {
        var previousTick = 0L
        var tempo = MidiParser.DEFAULT_TEMPO_US
        var micros = 0.0
        song.tempos.forEach { change ->
            if (change.tick > tick) return@forEach
            if (change.tick > previousTick) {
                micros += (change.tick - previousTick) * tempo.toDouble() / song.division
                previousTick = change.tick
            }
            tempo = change.microsecondsPerQuarter
        }
        if (tick > previousTick) {
            micros += (tick - previousTick) * tempo.toDouble() / song.division
        }
        return micros.toLong()
    }
}

object ChiptuneSynth {
    const val SAMPLE_RATE = 22_050

    fun render(song: MidiSong, maxSeconds: Int = 32): ShortArray {
        val sequence = MidiSequencer(MidiParser.parse(song.bytes))
        val duration = min(sequence.durationMicros, maxSeconds * 1_000_000L) + 120_000L
        val samples = (duration * SAMPLE_RATE / 1_000_000L).toInt().coerceAtLeast(1)
        val mix = FloatArray(samples)

        sequence.notes.forEach { note ->
            val start = (note.startMicros * SAMPLE_RATE / 1_000_000L).toInt().coerceIn(0, samples)
            if (start >= samples) return@forEach
            val end = (note.endMicros * SAMPLE_RATE / 1_000_000L).toInt().coerceIn(start + 1, samples)
            val frequency = 440.0 * 2.0.pow((note.pitch - 69) / 12.0)
            val amplitude = note.velocity / 127f * if (note.channel == 9) 0.16f else 0.19f
            var noise = (note.pitch * 7919 + start * 104729) or 1
            for (sample in start until end) {
                val elapsed = sample - start
                val remaining = end - sample
                val envelope = min(1f, elapsed / (SAMPLE_RATE * 0.008f)) *
                    min(1f, remaining / (SAMPLE_RATE * 0.045f))
                val phase = ((elapsed * frequency / SAMPLE_RATE) % 1.0).toFloat()
                val wave = if (note.channel == 9) {
                    noise = noise * 1_664_525 + 1_013_904_223
                    ((noise ushr 16) and 0x7FFF) / 16_384f - 1f
                } else if (note.channel % 3 == 1 || note.program in 80..84) {
                    1f - 4f * abs(phase - 0.5f)
                } else if (note.channel % 3 == 2) {
                    if (phase < 0.25f) 1f else -1f
                } else {
                    if (phase < 0.5f) 1f else -1f
                }
                mix[sample] += wave * amplitude * envelope
            }
        }
        return toPcm(mix)
    }

    fun renderEffect(event: GameEvent): ShortArray {
        val pattern = when (event) {
            GameEvent.MOVE -> listOf(EffectNote(520, 42, 0.12f))
            GameEvent.ROTATE -> listOf(EffectNote(680, 62, 0.14f))
            GameEvent.SOFT_DROP -> listOf(EffectNote(420, 35, 0.10f))
            GameEvent.HARD_DROP -> listOf(EffectNote(180, 80, 0.20f), EffectNote(110, 90, 0.16f, 45))
            GameEvent.HOLD -> listOf(EffectNote(760, 70, 0.16f), EffectNote(980, 80, 0.14f, 45))
            GameEvent.LAND -> listOf(EffectNote(240, 55, 0.12f))
            GameEvent.LINE_CLEAR -> listOf(EffectNote(620, 90, 0.15f), EffectNote(880, 110, 0.16f, 60))
            GameEvent.T_SPIN -> listOf(EffectNote(440, 70, 0.14f), EffectNote(740, 95, 0.17f, 55))
            GameEvent.PERFECT_CLEAR -> listOf(EffectNote(660, 70, 0.15f), EffectNote(880, 80, 0.16f, 55), EffectNote(1_100, 120, 0.18f, 115))
            GameEvent.GAME_OVER -> listOf(EffectNote(440, 150, 0.18f), EffectNote(330, 180, 0.18f, 150), EffectNote(220, 220, 0.18f, 180))
            GameEvent.PAUSE -> listOf(EffectNote(300, 70, 0.12f))
            GameEvent.RESUME -> listOf(EffectNote(520, 70, 0.12f))
            GameEvent.NONE -> emptyList()
        }
        if (pattern.isEmpty()) return ShortArray(0)
        val lengthMs = pattern.maxOf { it.startMs + it.durationMs } + 40
        val mix = FloatArray(lengthMs * SAMPLE_RATE / 1_000)
        pattern.forEach { note ->
            val start = note.startMs * SAMPLE_RATE / 1_000
            val end = ((note.startMs + note.durationMs) * SAMPLE_RATE / 1_000).coerceAtMost(mix.size)
            for (sample in start until end) {
                val elapsed = sample - start
                val remaining = end - sample
                val envelope = min(1f, elapsed / (SAMPLE_RATE * 0.004f)) *
                    min(1f, remaining / (SAMPLE_RATE * 0.025f))
                val phase = (elapsed * note.frequency / SAMPLE_RATE) % 1f
                val wave = if (note.frequency < 150) {
                    1f - 4f * abs(phase - 0.5f)
                } else if (phase < 0.5f) {
                    1f
                } else {
                    -1f
                }
                mix[sample] += wave * note.amplitude * envelope
            }
        }
        return toPcm(mix)
    }

    private fun toPcm(mix: FloatArray): ShortArray {
        val peak = mix.maxOfOrNull { abs(it) }?.coerceAtLeast(0.001f) ?: 1f
        val gain = min(0.86f / peak, 1.0f)
        return ShortArray(mix.size) { index ->
            (mix[index] * gain * Short.MAX_VALUE).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
    }

    private data class EffectNote(
        val frequency: Int,
        val durationMs: Int,
        val amplitude: Float,
        val startMs: Int = 0,
    )

    private fun Double.pow(power: Double): Double = kotlin.math.exp(power * kotlin.math.ln(this))
}

data class SongNote(
    val startBeat: Double,
    val durationBeats: Double,
    val pitch: Int,
    val velocity: Int = 96,
    val channel: Int = 0,
    val program: Int = 80,
)

object StandardMidiBuilder {
    private const val TICKS_PER_BEAT = 96

    fun build(bpm: Int, notes: List<SongNote>): ByteArray {
        val events = mutableListOf<BuildEvent>()
        events += BuildEvent(0, 0, byteArrayOf(0xFF.toByte(), 0x51, 0x03, (60_000_000 / bpm shr 16).toByte(), (60_000_000 / bpm shr 8).toByte(), (60_000_000 / bpm).toByte()))
        notes.map { it.channel to it.program }.distinct().forEach { (channel, program) ->
            events += BuildEvent(0, 1, byteArrayOf((0xC0 or channel).toByte(), program.toByte()))
        }
        notes.forEach { note ->
            val start = (note.startBeat * TICKS_PER_BEAT).toInt().coerceAtLeast(0)
            val end = ((note.startBeat + note.durationBeats) * TICKS_PER_BEAT).toInt().coerceAtLeast(start + 1)
            events += BuildEvent(start, 2, byteArrayOf((0x90 or note.channel).toByte(), note.pitch.toByte(), note.velocity.coerceIn(1, 127).toByte()))
            events += BuildEvent(end, 0, byteArrayOf((0x80 or note.channel).toByte(), note.pitch.toByte(), 0))
        }
        val track = ByteArrayOutputStream()
        var previousTick = 0
        events.sortedWith(compareBy<BuildEvent> { it.tick }.thenBy { it.order }).forEach { event ->
            writeVariableLength(track, event.tick - previousTick)
            track.write(event.bytes)
            previousTick = event.tick
        }
        writeVariableLength(track, 0)
        track.write(byteArrayOf(0xFF.toByte(), 0x2F, 0x00))

        val output = ByteArrayOutputStream()
        output.write("MThd".encodeToByteArray())
        writeInt32(output, 6)
        writeUInt16(output, 0)
        writeUInt16(output, 1)
        writeUInt16(output, TICKS_PER_BEAT)
        output.write("MTrk".encodeToByteArray())
        writeInt32(output, track.size())
        output.write(track.toByteArray())
        return output.toByteArray()
    }

    private fun writeVariableLength(output: ByteArrayOutputStream, value: Int) {
        var buffer = value and 0x7F
        var remaining = value ushr 7
        while (remaining > 0) {
            buffer = buffer shl 8
            buffer = buffer or ((remaining and 0x7F) or 0x80)
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

    private data class BuildEvent(val tick: Int, val order: Int, val bytes: ByteArray)
}
