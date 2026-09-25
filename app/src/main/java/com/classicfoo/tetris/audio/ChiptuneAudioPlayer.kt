package com.classicfoo.tetris.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Handler
import android.os.Looper
import com.classicfoo.tetris.engine.GameEvent
import com.classicfoo.tetris.settings.GameSettings

interface AudioPlayer {
    fun startMusic()
    fun pauseMusic()
    fun resumeMusic()
    fun stopMusic()
    fun restartMusic()
    fun updateSettings(settings: GameSettings)
    fun playEffect(event: GameEvent)
    fun release()
}

/** Android output layer for the dependency-free MIDI parser and chiptune renderer. */
class ChiptuneAudioPlayer : AudioPlayer {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val lock = Any()
    private val effectTracks = mutableSetOf<AudioTrack>()
    private var settings = GameSettings()
    private var musicTrack: AudioTrack? = null
    private var preparing = false
    private var wantsMusic = false
    private var released = false
    private var songIndex = 0
    private var renderThread: Thread? = null

    override fun startMusic() {
        wantsMusic = true
        if (!settings.musicEnabled || released) return
        musicTrack?.let { track ->
            runCatching { track.play() }
            return
        }
        prepareMusic()
    }

    override fun pauseMusic() {
        wantsMusic = false
        runCatching { musicTrack?.pause() }
    }

    override fun resumeMusic() {
        wantsMusic = true
        if (!settings.musicEnabled || released) return
        if (musicTrack == null) prepareMusic() else runCatching { musicTrack?.play() }
    }

    override fun stopMusic() {
        wantsMusic = false
        runCatching {
            musicTrack?.pause()
            musicTrack?.flush()
        }
    }

    override fun restartMusic() {
        stopMusic()
        songIndex = (songIndex + 1) % OriginalSongs.songs.size
        musicTrack?.release()
        musicTrack = null
        renderThread?.interrupt()
        renderThread = null
        startMusic()
    }

    override fun updateSettings(settings: GameSettings) {
        this.settings = settings
        if (!settings.musicEnabled) {
            pauseMusic()
        } else if (wantsMusic) {
            resumeMusic()
        }
    }

    override fun playEffect(event: GameEvent) {
        if (released || !settings.soundEnabled) return
        val samples = ChiptuneSynth.renderEffect(event)
        if (samples.isEmpty()) return
        val track = createStaticTrack(samples) ?: return
        synchronized(lock) { effectTracks += track }
        runCatching { track.play() }.onFailure { releaseEffect(track) }
        val durationMs = samples.size * 1_000L / ChiptuneSynth.SAMPLE_RATE + 120L
        mainHandler.postDelayed({ releaseEffect(track) }, durationMs)
    }

    override fun release() {
        released = true
        wantsMusic = false
        mainHandler.removeCallbacksAndMessages(null)
        synchronized(lock) {
            effectTracks.forEach { track -> runCatching { track.release() } }
            effectTracks.clear()
        }
        renderThread?.interrupt()
        renderThread = null
        musicTrack?.release()
        musicTrack = null
    }

    private fun prepareMusic() {
        if (preparing || released) return
        preparing = true
        val song = OriginalSongs.songs[songIndex]
        renderThread = Thread {
            val samples = runCatching { ChiptuneSynth.render(song) }.getOrNull()
            val canceled = Thread.currentThread().isInterrupted
            mainHandler.post {
                preparing = false
                if (released || canceled || samples == null) return@post
                musicTrack?.release()
                musicTrack = createStaticTrack(samples)
                if (wantsMusic && settings.musicEnabled) runCatching { musicTrack?.play() }
            }
        }.apply {
            name = "tetris-midi-renderer"
            isDaemon = true
            start()
        }
    }

    private fun createStaticTrack(samples: ShortArray): AudioTrack? {
        var track: AudioTrack? = null
        return try {
            val attributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()
            val format = AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(ChiptuneSynth.SAMPLE_RATE)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build()
            track = AudioTrack.Builder()
                .setAudioAttributes(attributes)
                .setAudioFormat(format)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .setBufferSizeInBytes(samples.size * 2)
                .build()
            val written = track.write(samples, 0, samples.size)
            check(written == samples.size) { "AudioTrack wrote $written of ${samples.size} samples" }
            track.setLoopPoints(0, samples.size, -1)
            track
        } catch (_: RuntimeException) {
            track?.release()
            null
        }
    }

    private fun releaseEffect(track: AudioTrack) {
        synchronized(lock) {
            if (effectTracks.remove(track)) {
                runCatching { track.stop() }
                runCatching { track.release() }
            }
        }
    }
}
