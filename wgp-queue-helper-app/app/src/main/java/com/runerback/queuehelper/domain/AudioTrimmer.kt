package com.runerback.queuehelper.domain

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

object AudioTrimmer {

    suspend fun trimToWav(
        context: Context,
        sourceUri: Uri,
        destFile: File,
        startSeconds: Float,
        endSeconds: Float
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            require(startSeconds < endSeconds) { "startSeconds must be less than endSeconds" }

            val mime = context.contentResolver.getType(sourceUri) ?: ""
            if (mime.contains("wav")) {
                trimWav(context, sourceUri, destFile, startSeconds, endSeconds)
            } else {
                trimWithMediaCodec(context, sourceUri, destFile, startSeconds, endSeconds)
            }
        }
    }

    private fun trimWav(
        context: Context,
        sourceUri: Uri,
        destFile: File,
        startSeconds: Float,
        endSeconds: Float
    ) {
        context.contentResolver.openInputStream(sourceUri)?.use { input ->
            val header = WavHeader.read(input) ?: throw IllegalStateException("Invalid WAV file")
            val totalSeconds = header.dataSize.toFloat() / header.byteRate

            val startByte = (startSeconds.coerceIn(0f, totalSeconds) * header.byteRate).toLong()
            val endByte = (endSeconds.coerceIn(startSeconds, totalSeconds) * header.byteRate).toLong()
            val trimmedSize = (endByte - startByte).coerceAtLeast(0).toInt()

            if (trimmedSize == 0) {
                throw IllegalStateException("Trimmed audio is empty")
            }

            val trimmedData = ByteArray(trimmedSize)
            input.skip(startByte)
            input.read(trimmedData)

            writeWavFile(
                destFile,
                trimmedData,
                header.sampleRate,
                header.channelCount,
                header.bitsPerSample
            )
        } ?: throw IllegalStateException("Could not open audio input stream")
    }

    private fun trimWithMediaCodec(
        context: Context,
        sourceUri: Uri,
        destFile: File,
        startSeconds: Float,
        endSeconds: Float
    ) {
        val extractor = MediaExtractor()
        extractor.setDataSource(context, sourceUri, null)

        val trackIndex = findAudioTrackIndex(extractor)
            ?: throw IllegalStateException("No audio track found")

        extractor.selectTrack(trackIndex)
        val format = extractor.getTrackFormat(trackIndex)
        val mime = format.getString(MediaFormat.KEY_MIME) ?: "audio/raw"
        val sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        val channelCount = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)

        val startUs = (startSeconds * 1_000_000).toLong()
        val endUs = (endSeconds * 1_000_000).toLong()
        extractor.seekTo(startUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)

        val decoder = MediaCodec.createDecoderByType(mime)
        decoder.configure(format, null, null, 0)
        decoder.start()

        val trimmedPcm = decodeTrimmedPcm(extractor, decoder, sampleRate, channelCount, startUs, endUs)

        decoder.release()
        extractor.release()

        if (trimmedPcm.isEmpty()) {
            throw IllegalStateException("Trimmed audio is empty")
        }

        writeWavFile(destFile, trimmedPcm, sampleRate, channelCount, bitsPerSample = 16)
    }

    private fun findAudioTrackIndex(extractor: MediaExtractor): Int? {
        for (i in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(i)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
            if (mime.startsWith("audio/")) return i
        }
        return null
    }

    private fun decodeTrimmedPcm(
        extractor: MediaExtractor,
        decoder: MediaCodec,
        sampleRate: Int,
        channelCount: Int,
        startUs: Long,
        endUs: Long
    ): ByteArray {
        val outputBuffers = mutableListOf<ByteArray>()
        var sawInputEOS = false
        var sawOutputEOS = false
        val timeoutUs = 10_000L
        val bytesPerFrame = 2 * channelCount

        while (!sawOutputEOS) {
            if (!sawInputEOS) {
                val inputBufferId = decoder.dequeueInputBuffer(timeoutUs)
                if (inputBufferId >= 0) {
                    val inputBuffer = decoder.getInputBuffer(inputBufferId) ?: continue
                    val sampleSize = extractor.readSampleData(inputBuffer, 0)
                    if (sampleSize < 0) {
                        decoder.queueInputBuffer(inputBufferId, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        sawInputEOS = true
                    } else {
                        decoder.queueInputBuffer(inputBufferId, 0, sampleSize, extractor.sampleTime, 0)
                        extractor.advance()
                    }
                }
            }

            val bufferInfo = MediaCodec.BufferInfo()
            val outputBufferId = decoder.dequeueOutputBuffer(bufferInfo, timeoutUs)
            if (outputBufferId >= 0) {
                val outputBuffer = decoder.getOutputBuffer(outputBufferId) ?: continue
                val chunk = extractChunkInRange(
                    outputBuffer,
                    bufferInfo,
                    sampleRate,
                    channelCount,
                    startUs,
                    endUs
                )
                if (chunk.isNotEmpty()) {
                    outputBuffers.add(chunk)
                }
                decoder.releaseOutputBuffer(outputBufferId, false)
                if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                    sawOutputEOS = true
                }
            } else if (outputBufferId == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                // Decoder output format is now available, but we already have sampleRate/channelCount.
            }
        }

        return outputBuffers.fold(ByteArray(0)) { acc, chunk -> acc + chunk }
    }

    private fun extractChunkInRange(
        buffer: ByteBuffer,
        bufferInfo: MediaCodec.BufferInfo,
        sampleRate: Int,
        channelCount: Int,
        startUs: Long,
        endUs: Long
    ): ByteArray {
        val bytesPerFrame = 2 * channelCount
        val frameCount = bufferInfo.size / bytesPerFrame
        if (frameCount <= 0) return ByteArray(0)

        val bufferStartUs = bufferInfo.presentationTimeUs
        val bufferDurationUs = frameCount * 1_000_000L / sampleRate
        val bufferEndUs = bufferStartUs + bufferDurationUs

        if (bufferEndUs <= startUs) return ByteArray(0)
        if (bufferStartUs >= endUs) return ByteArray(0)

        val keepStartUs = (startUs - bufferStartUs).coerceAtLeast(0)
        val keepEndUs = (endUs - bufferStartUs).coerceAtMost(bufferDurationUs)

        val startFrame = (keepStartUs * sampleRate / 1_000_000L).toInt()
        val endFrame = (keepEndUs * sampleRate / 1_000_000L).toInt()
        val keepFrames = (endFrame - startFrame).coerceAtLeast(0)
        if (keepFrames <= 0) return ByteArray(0)

        val startByte = bufferInfo.offset + startFrame * bytesPerFrame
        val endByte = startByte + keepFrames * bytesPerFrame

        buffer.position(startByte)
        val chunk = ByteArray(endByte - startByte)
        buffer.get(chunk)
        return chunk
    }

    private fun writeWavFile(
        file: File,
        pcmData: ByteArray,
        sampleRate: Int,
        channelCount: Int,
        bitsPerSample: Int
    ) {
        val byteRate = sampleRate * channelCount * bitsPerSample / 8
        val blockAlign = channelCount * bitsPerSample / 8
        val dataChunkSize = pcmData.size
        val overallSize = 36 + dataChunkSize

        FileOutputStream(file).use { out ->
            val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
            header.put("RIFF".toByteArray(Charsets.US_ASCII))
            header.putInt(overallSize)
            header.put("WAVE".toByteArray(Charsets.US_ASCII))
            header.put("fmt ".toByteArray(Charsets.US_ASCII))
            header.putInt(16) // Subchunk1Size
            header.putShort(1) // AudioFormat PCM
            header.putShort(channelCount.toShort())
            header.putInt(sampleRate)
            header.putInt(byteRate)
            header.putShort(blockAlign.toShort())
            header.putShort(bitsPerSample.toShort())
            header.put("data".toByteArray(Charsets.US_ASCII))
            header.putInt(dataChunkSize)

            out.write(header.array())
            out.write(pcmData)
        }
    }

    private data class WavHeader(
        val sampleRate: Int,
        val channelCount: Int,
        val bitsPerSample: Int,
        val byteRate: Int,
        val dataSize: Long,
        val dataOffset: Long
    ) {
        companion object {
            fun read(input: java.io.InputStream): WavHeader? {
                val reader = LittleEndianReader(input)
                if (reader.readFourCc() != "RIFF") return null
                reader.readInt() // overall size
                if (reader.readFourCc() != "WAVE") return null

                var fmtRead = false
                var sampleRate = 0
                var channelCount = 0
                var bitsPerSample = 0
                var byteRate = 0
                var dataSize = 0L
                var dataOffset = 0L

                while (true) {
                    val chunkId = reader.readFourCc() ?: break
                    val chunkSize = reader.readInt().toLong()
                    val chunkStart = reader.bytesRead

                    when (chunkId) {
                        "fmt " -> {
                            if (chunkSize < 16) return null
                            reader.readShort() // audio format
                            channelCount = reader.readShort().toInt()
                            sampleRate = reader.readInt()
                            byteRate = reader.readInt()
                            reader.readShort() // block align
                            bitsPerSample = reader.readShort().toInt()
                            fmtRead = true
                        }
                        "data" -> {
                            dataSize = chunkSize
                            dataOffset = chunkStart
                        }
                        else -> {
                            reader.skip(chunkSize)
                        }
                    }

                    if (fmtRead && dataOffset > 0) {
                        return WavHeader(sampleRate, channelCount, bitsPerSample, byteRate, dataSize, dataOffset)
                    }

                    val bytesConsumed = reader.bytesRead - chunkStart
                    val padding = chunkSize - bytesConsumed
                    if (padding > 0) reader.skip(padding)
                }
                return null
            }
        }
    }

    private class LittleEndianReader(private val input: java.io.InputStream) {
        var bytesRead = 0L
            private set

        fun readFourCc(): String? {
            val bytes = ByteArray(4)
            if (input.read(bytes) != 4) return null
            bytesRead += 4
            return String(bytes, Charsets.US_ASCII)
        }

        fun readInt(): Int {
            val bytes = ByteArray(4)
            input.read(bytes)
            bytesRead += 4
            return ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).int
        }

        fun readShort(): Short {
            val bytes = ByteArray(2)
            input.read(bytes)
            bytesRead += 2
            return ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).short
        }

        fun skip(amount: Long) {
            val skipped = input.skip(amount)
            bytesRead += skipped
        }
    }
}
