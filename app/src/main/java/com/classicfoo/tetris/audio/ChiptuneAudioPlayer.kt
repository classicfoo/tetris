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
    private val effectLock = Any()
    private val musicLock = Object()
    private val effectTracks = mutableSetOf<AudioTrack>()
    private var settings = GameSettings()
    private var musicTrack: AudioTrack? = null
    private var musicCursor: PcmCursor? = null
    private var musicWriterThread: Thread? = null
    private var preparing = false
    @Volatile private var wantsMusic = false
    @Volatile private var released = false
    @Volatile private var musicGeneration = 0L
    private var songIndex = 0
    private var renderThread: Thread? = null

    override fun startMusic() {
        synchronized(musicLock) {
            wantsMusic = true
            musicCursor?.resume()
            musicLock.notifyAll()
        }
        if (!settings.musicEnabled || released) return
        if (musicTrack == null || musicCursor == null) {
            prepareMusic()
        } else {
            ensureMusicWriter()
            runCatching { musicTrack?.play() }
        }
    }

    override fun pauseMusic() {
        synchronized(musicLock) {
            wantsMusic = false
            musicCursor?.pause()
            musicLock.notifyAll()
        }
        runCatching { musicTrack?.pause() }
    }

    override fun resumeMusic() {
        synchronized(musicLock) {
            wantsMusic = true
            musicCursor?.resume()
            musicLock.notifyAll()
        }
        if (!settings.musicEnabled || released) return
        if (musicTrack == null || musicCursor == null) {
            prepareMusic()
        } else {
            ensureMusicWriter()
            runCatching { musicTrack?.play() }
        }
    }

    override fun stopMusic() {
        synchronized(musicLock) {
            wantsMusic = false
            musicCursor?.pause()
            musicCursor?.reset()
            musicLock.notifyAll()
        }
        runCatching {
            musicTrack?.pause()
            musicTrack?.flush()
        }
    }

    override fun restartMusic() {
        stopMusic()
        songIndex = (songIndex + 1) % OriginalSongs.songs.size
        musicGeneration++
        renderThread?.interrupt()
        renderThread = null
        releaseMusicOutput()
        preparing = false
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
        synchronized(effectLock) { effectTracks += track }
        runCatching { track.play() }.onFailure { releaseEffect(track) }
        val durationMs = samples.size * 1_000L / ChiptuneSynth.SAMPLE_RATE + 120L
        mainHandler.postDelayed({ releaseEffect(track) }, durationMs)
    }

    override fun release() {
        released = true
        wantsMusic = false
        musicGeneration++
        mainHandler.removeCallbacksAndMessages(null)
        synchronized(effectLock) {
            effectTracks.forEach { track -> runCatching { track.release() } }
            effectTracks.clear()
        }
        renderThread?.interrupt()
        renderThread = null
        releaseMusicOutput()
        preparing = false
    }

    private fun prepareMusic() {
        if (preparing || released) return
        preparing = true
        val token = ++musicGeneration
        val song = OriginalSongs.songs[songIndex]
        renderThread = Thread {
            val rendered = runCatching { ChiptuneSynth.renderSong(song) }.getOrNull()
            val canceled = Thread.currentThread().isInterrupted
            mainHandler.post {
                if (token != musicGeneration || released || canceled || rendered == null) {
                    if (token == musicGeneration) preparing = false
                    return@post
                }
                preparing = false
                renderThread = null
                releaseMusicOutput()
                installMusicOutput(rendered, token)
            }
        }.apply {
            name = "tetris-midi-renderer"
            isDaemon = true
            start()
        }
    }

    private fun installMusicOutput(rendered: RenderedPcm, token: Long) {
        if (released || token != musicGeneration) return
        val track = createStreamTrack() ?: return
        val cursor = PcmCursor(rendered)
        musicTrack = track
        musicCursor = cursor
        if (!wantsMusic || !settings.musicEnabled) {
            cursor.pause()
        }
        startMusicWriter(cursor, track, token)
        if (wantsMusic && settings.musicEnabled) runCatching { track.play() }
    }

    private fun ensureMusicWriter() {
        val cursor = musicCursor ?: return
        val track = musicTrack ?: return
        if (musicWriterThread?.isAlive != true) {
            startMusicWriter(cursor, track, musicGeneration)
        }
    }

    private fun startMusicWriter(cursor: PcmCursor, track: AudioTrack, token: Long) {
        if (musicWriterThread?.isAlive == true) return
        val writer = Thread {
            val buffer = ShortArray(STREAM_CHUNK_FRAMES)
            try {
                while (musicOutputIsCurrent(cursor, track, token)) {
                    synchronized(musicLock) {
                        while (musicOutputIsCurrent(cursor, track, token) && (!wantsMusic || cursor.isPaused())) {
                            musicLock.wait()
                        }
                    }
                    if (!musicOutputIsCurrent(cursor, track, token)) break
                    val count = cursor.read(buffer)
                    if (count <= 0) continue
                    val written = track.write(buffer, 0, count, AudioTrack.WRITE_BLOCKING)
                    if (written < 0) break
                }
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            } catch (_: RuntimeException) {
                // Device teardown and AudioTrack errors are handled by releaseMusicOutput().
            } finally {
                synchronized(musicLock) {
                    if (musicWriterThread === Thread.currentThread()) musicWriterThread = null
                    musicLock.notifyAll()
                }
            }
        }.apply {
            name = "tetris-midi-stream"
            isDaemon = true
        }
        musicWriterThread = writer
        writer.start()
    }

    private fun musicOutputIsCurrent(cursor: PcmCursor, track: AudioTrack, token: Long): Boolean =
        !released && token == musicGeneration && musicCursor === cursor && musicTrack === track

    private fun releaseMusicOutput() {
        val writer = musicWriterThread
        musicWriterThread = null
        val cursor = musicCursor
        musicCursor = null
        val track = musicTrack
        musicTrack = null
        cursor?.release()
        synchronized(musicLock) { musicLock.notifyAll() }
        writer?.interrupt()
        track?.let {
            runCatching { it.pause() }
            runCatching { it.flush() }
            runCatching { it.release() }
        }
        if (writer != null && writer !== Thread.currentThread()) {
            runCatching { writer.join(250L) }
        }
    }

    private fun createStreamTrack(): AudioTrack? {
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
            val minimum = AudioTrack.getMinBufferSize(
                ChiptuneSynth.SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
            ).coerceAtLeast(0)
            track = AudioTrack.Builder()
                .setAudioAttributes(attributes)
                .setAudioFormat(format)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .setBufferSizeInBytes(maxOf(minimum, STREAM_CHUNK_FRAMES * 4))
                .build()
            track
        } catch (_: RuntimeException) {
            track?.release()
            null
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
        synchronized(effectLock) {
            if (effectTracks.remove(track)) {
                runCatching { track.stop() }
                runCatching { track.release() }
            }
        }
    }

    private companion object {
        const val STREAM_CHUNK_FRAMES = 2_048
    }
}
