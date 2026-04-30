package com.runerback.screenrecorder.recorder

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.projection.MediaProjection
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.os.Handler
import android.os.Looper
import android.view.Surface
import android.view.WindowManager
import com.runerback.screenrecorder.data.RecorderSettings
import com.runerback.screenrecorder.data.RecordingStore
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread
import kotlin.math.roundToInt

class ScreenRecordingSession(
    private val context: Context,
    private val mediaProjection: MediaProjection,
    private val settings: RecorderSettings,
    private val onStarted: (outputUri: android.net.Uri, audioActive: Boolean) -> Unit,
    private val onFinished: (outputUri: android.net.Uri, audioActive: Boolean) -> Unit,
    private val onError: (String) -> Unit,
) {
    private lateinit var pendingRecording: RecordingStore.PendingRecording
    private lateinit var videoEncoder: MediaCodec
    private var audioEncoder: MediaCodec? = null
    private var audioRecord: AudioRecord? = null
    private lateinit var muxer: MediaMuxer
    private lateinit var inputSurface: Surface
    private lateinit var projectionCallback: MediaProjection.Callback

    private var muxerStarted = false
    private var videoEncoderStarted = false
    private var audioEncoderStarted = false
    private var audioRecordStarted = false
    private var videoTrackIndex = -1
    private var audioTrackIndex = -1
    private var audioEnabled = false
    private var hasWrittenVideoSample = false
    private var hasWrittenAudioSample = false
    private var failureMessage: String? = null
    private var display: VirtualDisplay? = null
    private var startTimeNanos = 0L

    private val stopRequested = AtomicBoolean(false)
    private val stopInProgress = AtomicBoolean(false)
    private val muxerLock = Object()

    private var videoDrainThread: Thread? = null
    private var audioDrainThread: Thread? = null
    private var audioInputThread: Thread? = null

    @Throws(IOException::class, IllegalStateException::class, SecurityException::class)
    fun start() {
        pendingRecording = RecordingStore.createPendingRecording(context)
        configureVideoEncoder()
        audioEnabled = configureAudioPipeline()
        muxer = MediaMuxer(
            pendingRecording.fileDescriptor.fileDescriptor,
            MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4,
        )

        projectionCallback = object : MediaProjection.Callback() {
            override fun onStop() {
                requestStop("Screen capture permission ended.")
            }
        }
        mediaProjection.registerCallback(projectionCallback, Handler(Looper.getMainLooper()))

        videoEncoder.start()
        videoEncoderStarted = true
        if (audioEnabled) {
            audioEncoder?.start()
            audioEncoderStarted = true
        }

        val displaySpec = displaySpec()
        display = mediaProjection.createVirtualDisplay(
            pendingRecording.displayName,
            displaySpec.width,
            displaySpec.height,
            displaySpec.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            inputSurface,
            null,
            null,
        )
        startTimeNanos = System.nanoTime()
        startWorkers()
        onStarted(pendingRecording.uri, audioEnabled)
    }

    fun stop() {
        requestStop()
    }

    private fun configureVideoEncoder() {
        val displaySpec = displaySpec()
        val bitrate = ((displaySpec.width.toLong() * displaySpec.height.toLong() * 4L *
            settings.frameRatePreset.framesPerSecond) / 30L)
            .coerceAtLeast(2_000_000L)
            .coerceAtMost(Int.MAX_VALUE.toLong())
            .toInt()
        val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, displaySpec.width, displaySpec.height).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, bitrate)
            setInteger(MediaFormat.KEY_FRAME_RATE, settings.frameRatePreset.framesPerSecond)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        }

        videoEncoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        videoEncoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        inputSurface = videoEncoder.createInputSurface()
    }

    private fun configureAudioPipeline(): Boolean {
        if (!settings.captureSystemAudio) {
            return false
        }
        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            return false
        }
        val sampleRate = 44_100
        val channelMask = AudioFormat.CHANNEL_IN_STEREO
        val minBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelMask, AudioFormat.ENCODING_PCM_16BIT)
        if (minBufferSize <= 0) {
            return false
        }

        val playbackConfig = AudioPlaybackCaptureConfiguration.Builder(mediaProjection)
            .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
            .addMatchingUsage(AudioAttributes.USAGE_GAME)
            .build()

        val audioFormat = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(sampleRate)
            .setChannelMask(channelMask)
            .build()

        val record = try {
            AudioRecord.Builder()
                .setAudioFormat(audioFormat)
                .setBufferSizeInBytes(minBufferSize * 4)
                .setAudioPlaybackCaptureConfig(playbackConfig)
                .build()
        } catch (_: IllegalArgumentException) {
            return false
        } catch (_: UnsupportedOperationException) {
            return false
        }

        if (record.state != AudioRecord.STATE_INITIALIZED) {
            record.release()
            return false
        }

        val encoderFormat = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, sampleRate, 2).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, 128_000)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, minBufferSize * 4)
        }

        val encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
        encoder.configure(encoderFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)

        audioRecord = record
        audioEncoder = encoder
        return true
    }

    private fun startWorkers() {
        videoDrainThread = thread(name = "video-drain") {
            drainVideoEncoder()
        }

        if (audioEnabled) {
            audioInputThread = thread(name = "audio-input") {
                runAudioInputLoop()
            }
            audioDrainThread = thread(name = "audio-drain") {
                drainAudioEncoder()
            }
        }
    }

    private fun drainVideoEncoder() {
        try {
            val bufferInfo = MediaCodec.BufferInfo()
            while (true) {
                val outputIndex = videoEncoder.dequeueOutputBuffer(bufferInfo, CODEC_TIMEOUT_US)
                when {
                    outputIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                        if (stopRequested.get()) {
                            continue
                        }
                    }

                    outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> synchronized(muxerLock) {
                        videoTrackIndex = muxer.addTrack(videoEncoder.outputFormat)
                        maybeStartMuxerLocked()
                    }

                    outputIndex >= 0 -> {
                        val outputBuffer = videoEncoder.getOutputBuffer(outputIndex)
                            ?: throw IllegalStateException("Video output buffer was unavailable.")
                        if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
                            bufferInfo.size = 0
                        }

                        if (bufferInfo.size > 0 && awaitMuxerStart()) {
                            outputBuffer.position(bufferInfo.offset)
                            outputBuffer.limit(bufferInfo.offset + bufferInfo.size)
                            synchronized(muxerLock) {
                                muxer.writeSampleData(videoTrackIndex, outputBuffer, bufferInfo)
                            }
                            hasWrittenVideoSample = true
                        }

                        val endOfStream = bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                        videoEncoder.releaseOutputBuffer(outputIndex, false)
                        if (endOfStream) {
                            return
                        }
                    }
                }
            }
        } catch (exception: IllegalStateException) {
            requestStop(exception.message ?: "The video encoder stopped unexpectedly.")
        }
    }

    private fun runAudioInputLoop() {
        try {
            val encoder = audioEncoder ?: return
            val recorder = audioRecord ?: return

            recorder.startRecording()
            audioRecordStarted = true

            while (!stopRequested.get()) {
                val inputIndex = encoder.dequeueInputBuffer(CODEC_TIMEOUT_US)
                if (inputIndex < 0) {
                    continue
                }

                val inputBuffer = encoder.getInputBuffer(inputIndex)
                    ?: throw IllegalStateException("Audio input buffer was unavailable.")
                inputBuffer.clear()
                val readCount = recorder.read(inputBuffer, inputBuffer.remaining(), AudioRecord.READ_BLOCKING)
                val presentationTimeUs = presentationTimeUs()

                when {
                    readCount > 0 -> {
                        encoder.queueInputBuffer(inputIndex, 0, readCount, presentationTimeUs, 0)
                    }

                    readCount == 0 -> {
                        encoder.queueInputBuffer(inputIndex, 0, 0, presentationTimeUs, 0)
                    }

                    readCount == AudioRecord.ERROR_DEAD_OBJECT -> {
                        requestStop("Playback audio capture ended unexpectedly.")
                        return
                    }

                    else -> {
                        requestStop("Playback audio capture failed with code $readCount.")
                        return
                    }
                }
            }

            queueAudioEndOfStream(encoder)
        } catch (exception: IllegalStateException) {
            requestStop(exception.message ?: "Playback audio capture stopped unexpectedly.")
        }
    }

    private fun drainAudioEncoder() {
        try {
            val encoder = audioEncoder ?: return
            val bufferInfo = MediaCodec.BufferInfo()
            while (true) {
                val outputIndex = encoder.dequeueOutputBuffer(bufferInfo, CODEC_TIMEOUT_US)
                when {
                    outputIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                        if (stopRequested.get()) {
                            continue
                        }
                    }

                    outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> synchronized(muxerLock) {
                        audioTrackIndex = muxer.addTrack(encoder.outputFormat)
                        maybeStartMuxerLocked()
                    }

                    outputIndex >= 0 -> {
                        val outputBuffer = encoder.getOutputBuffer(outputIndex)
                            ?: throw IllegalStateException("Audio output buffer was unavailable.")
                        if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
                            bufferInfo.size = 0
                        }

                        if (bufferInfo.size > 0 && awaitMuxerStart()) {
                            outputBuffer.position(bufferInfo.offset)
                            outputBuffer.limit(bufferInfo.offset + bufferInfo.size)
                            synchronized(muxerLock) {
                                muxer.writeSampleData(audioTrackIndex, outputBuffer, bufferInfo)
                            }
                            hasWrittenAudioSample = true
                        }

                        val endOfStream = bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                        encoder.releaseOutputBuffer(outputIndex, false)
                        if (endOfStream) {
                            return
                        }
                    }
                }
            }
        } catch (exception: IllegalStateException) {
            requestStop(exception.message ?: "The audio encoder stopped unexpectedly.")
        }
    }

    private fun queueAudioEndOfStream(encoder: MediaCodec) {
        while (true) {
            val inputIndex = encoder.dequeueInputBuffer(CODEC_TIMEOUT_US)
            if (inputIndex >= 0) {
                encoder.queueInputBuffer(
                    inputIndex,
                    0,
                    0,
                    presentationTimeUs(),
                    MediaCodec.BUFFER_FLAG_END_OF_STREAM,
                )
                return
            }
        }
    }

    private fun requestStop(message: String? = null) {
        if (message != null && failureMessage == null) {
            failureMessage = message
        }

        if (stopInProgress.compareAndSet(false, true)) {
            stopRequested.set(true)
            thread(name = "recording-stopper") {
                stopInternal()
            }
        }
    }

    private fun stopInternal() {
        if (audioRecordStarted) {
            try {
                audioRecord?.stop()
            } catch (_: IllegalStateException) {
                if (failureMessage == null) {
                    failureMessage = "Audio capture stopped before the recorder could finish cleanly."
                }
            }
            audioRecordStarted = false
        }

        if (videoEncoderStarted) {
            try {
                videoEncoder.signalEndOfInputStream()
            } catch (_: IllegalStateException) {
                if (failureMessage == null) {
                    failureMessage = "Video capture stopped before the recorder could finish cleanly."
                }
            }
        }

        joinWorker(audioInputThread)
        joinWorker(audioDrainThread)
        joinWorker(videoDrainThread)

        releaseResources()

        val success = failureMessage == null && hasWrittenVideoSample
        if (success) {
            RecordingStore.finalizeRecording(context, pendingRecording.uri)
            onFinished(pendingRecording.uri, audioEnabled && hasWrittenAudioSample)
        } else {
            RecordingStore.deleteRecording(context, pendingRecording.uri)
            onError(failureMessage ?: "Recording finished before media data could be written.")
        }
    }

    private fun releaseResources() {
        display?.release()
        display = null

        if (audioEncoderStarted) {
            try {
                audioEncoder?.stop()
            } catch (_: IllegalStateException) {
                if (failureMessage == null) {
                    failureMessage = "Audio encoding did not stop cleanly."
                }
            }
            audioEncoderStarted = false
        }
        audioEncoder?.release()
        audioEncoder = null

        if (videoEncoderStarted) {
            try {
                videoEncoder.stop()
            } catch (_: IllegalStateException) {
                if (failureMessage == null) {
                    failureMessage = "Video encoding did not stop cleanly."
                }
            }
            videoEncoderStarted = false
        }
        videoEncoder.release()

        if (inputSurface.isValid) {
            inputSurface.release()
        }

        audioRecord?.release()
        audioRecord = null

        synchronized(muxerLock) {
            if (muxerStarted) {
                try {
                    muxer.stop()
                } catch (_: IllegalStateException) {
                    if (failureMessage == null) {
                        failureMessage = "The recording container could not be finalized."
                    }
                }
                muxerStarted = false
            }
        }
        muxer.release()

        mediaProjection.unregisterCallback(projectionCallback)
        mediaProjection.stop()
        pendingRecording.fileDescriptor.close()
    }

    private fun maybeStartMuxerLocked() {
        if (!muxerStarted && videoTrackIndex != -1 && (!audioEnabled || audioTrackIndex != -1)) {
            muxer.start()
            muxerStarted = true
            muxerLock.notifyAll()
        }
    }

    private fun awaitMuxerStart(): Boolean {
        synchronized(muxerLock) {
            while (!muxerStarted && failureMessage == null) {
                muxerLock.wait(25L)
                if (stopRequested.get() && !muxerStarted) {
                    break
                }
            }
            return muxerStarted
        }
    }

    private fun presentationTimeUs(): Long {
        return (System.nanoTime() - startTimeNanos) / 1_000L
    }

    private fun joinWorker(worker: Thread?) {
        if (worker != null && worker !== Thread.currentThread()) {
            worker.join()
        }
    }

    private fun displaySpec(): DisplaySpec {
        val bounds = context.getSystemService(WindowManager::class.java).currentWindowMetrics.bounds
        val nativeWidth = even(bounds.width()).coerceAtLeast(2)
        val nativeHeight = even(bounds.height()).coerceAtLeast(2)
        val densityDpi = context.resources.configuration.densityDpi
        val targetLongEdge = settings.resolutionPreset.targetLongEdge
            ?: return DisplaySpec(width = nativeWidth, height = nativeHeight, densityDpi = densityDpi)

        val isLandscape = nativeWidth >= nativeHeight
        val nativeLongEdge = if (isLandscape) nativeWidth else nativeHeight
        val nativeShortEdge = if (isLandscape) nativeHeight else nativeWidth
        val scale = minOf(1f, targetLongEdge.toFloat() / nativeLongEdge.toFloat())
        val scaledLongEdge = even((nativeLongEdge * scale).roundToInt()).coerceAtLeast(2)
        val scaledShortEdge = even((nativeShortEdge * scale).roundToInt()).coerceAtLeast(2)

        return if (isLandscape) {
            DisplaySpec(width = scaledLongEdge, height = scaledShortEdge, densityDpi = densityDpi)
        } else {
            DisplaySpec(width = scaledShortEdge, height = scaledLongEdge, densityDpi = densityDpi)
        }
    }

    private fun even(value: Int): Int {
        return if (value % 2 == 0) value else value - 1
    }

    private data class DisplaySpec(
        val width: Int,
        val height: Int,
        val densityDpi: Int,
    )

    companion object {
        private const val CODEC_TIMEOUT_US = 10_000L
    }
}
