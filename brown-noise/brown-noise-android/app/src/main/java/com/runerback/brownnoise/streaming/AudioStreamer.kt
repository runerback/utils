package com.runerback.brownnoise.streaming

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import java.io.DataInputStream
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder

class AudioStreamer(private val onStateChange: (StreamState) -> Unit) {

    companion object {
        const val DEFAULT_SAMPLE_RATE = 44100
        const val DEFAULT_CHANNELS = 2
        const val DEFAULT_BITS = 16
    }

    @Volatile
    private var running = false

    private var thread: Thread? = null
    private var socket: Socket? = null
    private var audioTrack: AudioTrack? = null

    @Volatile
    private var pendingVolume = 1.0f

    fun start(host: String, port: Int, volume: Float = 1.0f) {
        if (running) return
        pendingVolume = volume
        running = true
        onStateChange(StreamState.Connecting)
        thread = Thread({ streamLoop(host, port) }, "AudioStreamThread").apply { start() }
    }

    fun stop() {
        running = false
        try {
            socket?.close()
        } catch (_: Exception) {
        }
        thread?.interrupt()
        thread?.join(1000)
        releaseAudioTrack()
        onStateChange(StreamState.Idle)
    }

    fun setVolume(volume: Float) {
        pendingVolume = volume.coerceIn(0f, 1f)
        audioTrack?.setVolume(pendingVolume)
    }

    fun flush() {
        audioTrack?.flush()
    }

    private fun releaseAudioTrack() {
        try {
            audioTrack?.stop()
            audioTrack?.release()
        } catch (_: Exception) {
        }
        audioTrack = null
    }

    private fun streamLoop(host: String, port: Int) {
        try {
            Socket(host, port).use { sock ->
                socket = sock
                DataInputStream(sock.getInputStream()).use { input ->
                    val header = ByteArray(10)
                    input.readFully(header)
                    val headerBuf = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
                    val sampleRate = headerBuf.getInt()
                    val channels = headerBuf.getInt()
                    val bits = headerBuf.getShort().toInt() and 0xFFFF

                    if (channels != DEFAULT_CHANNELS || bits != DEFAULT_BITS) {
                        throw UnsupportedOperationException(
                            "Unsupported stream format: $sampleRate/$channels/$bits"
                        )
                    }

                    createAudioTrack(sampleRate, channels)
                    onStateChange(StreamState.Streaming)

                    val lengthBuffer = ByteArray(4)
                    while (running && !Thread.currentThread().isInterrupted) {
                        input.readFully(lengthBuffer)
                        val length = ByteBuffer.wrap(lengthBuffer)
                            .order(ByteOrder.LITTLE_ENDIAN)
                            .getInt()
                        if (length <= 0 || length > 1024 * 1024) {
                            throw IllegalStateException("Bad packet length: $length")
                        }
                        val data = ByteArray(length)
                        input.readFully(data)
                        audioTrack?.write(data, 0, data.size)
                    }
                }
            }
        } catch (e: Exception) {
            if (running) {
                onStateChange(StreamState.Error(e.message ?: "Stream error"))
            }
        } finally {
            releaseAudioTrack()
            socket = null
        }
    }

    private fun createAudioTrack(sampleRate: Int, channels: Int) {
        val channelMask = if (channels >= 2) AudioFormat.CHANNEL_OUT_STEREO else AudioFormat.CHANNEL_OUT_MONO
        val bufferSize = AudioTrack.getMinBufferSize(
            sampleRate,
            channelMask,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(4096)

        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(sampleRate)
                    .setChannelMask(channelMask)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .build()
            )
            .setBufferSizeInBytes(bufferSize * 2)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        track.setVolume(pendingVolume)
        track.play()
        audioTrack = track
    }
}

sealed class StreamState {
    object Idle : StreamState()
    object Connecting : StreamState()
    object Streaming : StreamState()
    data class Error(val message: String) : StreamState()
}
