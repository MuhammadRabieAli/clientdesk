package com.example.ai

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

/**
 * Lightweight 16kHz 16-bit mono PCM audio recorder that encodes directly to in-memory WAV
 * (`audio/wav`) for Gemini multimodal audio analysis while emitting live RMS voice levels
 * for waveform visualization. Requires zero file storage permissions.
 */
class VoiceAudioRecorder {

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _amplitude = MutableStateFlow(0f)
    val amplitude: StateFlow<Float> = _amplitude.asStateFlow()

    private val _waveformLevels = MutableStateFlow(List(WAVEFORM_BARS) { 0.08f })
    val waveformLevels: StateFlow<List<Float>> = _waveformLevels.asStateFlow()

    private val _elapsedMillis = MutableStateFlow(0L)
    val elapsedMillis: StateFlow<Long> = _elapsedMillis.asStateFlow()

    private var audioRecord: AudioRecord? = null
    private var captureJob: Job? = null
    private val pcmLock = Any()
    private var pcmBuffer = ByteArrayOutputStream()
    private var startedAtMillis: Long = 0L

    @SuppressLint("MissingPermission")
    fun start(
        scope: CoroutineScope,
        onMaxDurationReached: (ByteArray) -> Unit,
        onError: (String) -> Unit
    ): Boolean {
        if (_isRecording.value) return true

        val minBufSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBufSize <= 0) {
            onError("Microphone hardware is unavailable on this device.")
            return false
        }

        val bufferSizeBytes = maxOf(minBufSize * 2, SAMPLE_RATE / 5 * 2)
        val recorder = try {
            AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSizeBytes
            ).takeIf { it.state == AudioRecord.STATE_INITIALIZED }
                ?: AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufferSizeBytes
                )
        } catch (e: SecurityException) {
            Log.e(TAG, "RECORD_AUDIO permission missing", e)
            onError("Microphone permission is required to record voice commands.")
            return false
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize AudioRecord", e)
            onError("Could not initialize the microphone.")
            return false
        }

        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            recorder.release()
            onError("Could not initialize the microphone.")
            return false
        }

        synchronized(pcmLock) {
            pcmBuffer = ByteArrayOutputStream(SAMPLE_RATE * 2 * 6)
        }

        try {
            recorder.startRecording()
        } catch (e: Exception) {
            Log.e(TAG, "AudioRecord.startRecording failed", e)
            recorder.release()
            onError("Could not start recording audio.")
            return false
        }

        audioRecord = recorder
        startedAtMillis = System.currentTimeMillis()
        _isRecording.value = true
        _amplitude.value = 0.08f
        _elapsedMillis.value = 0L
        _waveformLevels.value = List(WAVEFORM_BARS) { 0.08f }

        captureJob = scope.launch(Dispatchers.IO) {
            val shortChunk = ShortArray(CHUNK_SAMPLES)
            val byteChunk = ByteArray(CHUNK_SAMPLES * 2)
            var hitMaxDuration = false

            try {
                while (isActive && _isRecording.value) {
                    val readCount = recorder.read(shortChunk, 0, shortChunk.size)
                    if (readCount > 0) {
                        var sumSquares = 0.0
                        for (i in 0 until readCount) {
                            val sample = shortChunk[i]
                            sumSquares += sample.toDouble() * sample.toDouble()
                            byteChunk[i * 2] = (sample.toInt() and 0xFF).toByte()
                            byteChunk[i * 2 + 1] = ((sample.toInt() shr 8) and 0xFF).toByte()
                        }
                        synchronized(pcmLock) {
                            pcmBuffer.write(byteChunk, 0, readCount * 2)
                        }

                        val rms = sqrt(sumSquares / readCount).toFloat()
                        val normalized = (rms / 6500f).coerceIn(0.06f, 1f)
                        _amplitude.value = normalized
                        val currentLevels = _waveformLevels.value
                        _waveformLevels.value = currentLevels.drop(1) + normalized

                        val elapsed = System.currentTimeMillis() - startedAtMillis
                        _elapsedMillis.value = elapsed
                        if (elapsed >= MAX_DURATION_MILLIS) {
                            hitMaxDuration = true
                            break
                        }
                    } else if (readCount < 0) {
                        Log.w(TAG, "AudioRecord read error: $readCount")
                        break
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error while capturing audio", e)
            } finally {
                if (hitMaxDuration) {
                    val wav = finishInternal()
                    if (wav != null) {
                        withContext(Dispatchers.Main) {
                            onMaxDurationReached(wav)
                        }
                    }
                }
            }
        }

        return true
    }

    suspend fun stopAndEncodeWav(): ByteArray? = withContext(Dispatchers.IO) {
        _isRecording.value = false
        captureJob?.join()
        captureJob = null
        finishInternal()
    }

    fun cancel() {
        _isRecording.value = false
        captureJob?.cancel()
        captureJob = null
        releaseRecorder()
        synchronized(pcmLock) {
            pcmBuffer.reset()
        }
        _amplitude.value = 0f
        _elapsedMillis.value = 0L
        _waveformLevels.value = List(WAVEFORM_BARS) { 0.08f }
    }

    private fun finishInternal(): ByteArray? {
        _isRecording.value = false
        val duration = System.currentTimeMillis() - startedAtMillis
        releaseRecorder()
        val rawPcm = synchronized(pcmLock) {
            val bytes = pcmBuffer.toByteArray()
            pcmBuffer.reset()
            bytes
        }
        _amplitude.value = 0f
        _elapsedMillis.value = 0L
        _waveformLevels.value = List(WAVEFORM_BARS) { 0.08f }

        if (duration < MIN_DURATION_MILLIS || rawPcm.size < SAMPLE_RATE / 4) {
            return null
        }
        return encodePcmAsWav(rawPcm)
    }

    private fun releaseRecorder() {
        val recorder = audioRecord ?: return
        audioRecord = null
        try {
            if (recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                recorder.stop()
            }
        } catch (_: Exception) {
        }
        try {
            recorder.release()
        } catch (_: Exception) {
        }
    }

    private fun encodePcmAsWav(pcmData: ByteArray): ByteArray {
        val totalDataLen = pcmData.size + 36
        val byteRate = SAMPLE_RATE * CHANNELS * BITS_PER_SAMPLE / 8
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray(Charsets.US_ASCII))
            putInt(totalDataLen)
            put("WAVE".toByteArray(Charsets.US_ASCII))
            put("fmt ".toByteArray(Charsets.US_ASCII))
            putInt(16) // PCM chunk size
            putShort(1) // AudioFormat = PCM
            putShort(CHANNELS.toShort())
            putInt(SAMPLE_RATE)
            putInt(byteRate)
            putShort((CHANNELS * BITS_PER_SAMPLE / 8).toShort()) // BlockAlign
            putShort(BITS_PER_SAMPLE.toShort())
            put("data".toByteArray(Charsets.US_ASCII))
            putInt(pcmData.size)
        }.array()

        return ByteArray(header.size + pcmData.size).also { out ->
            System.arraycopy(header, 0, out, 0, header.size)
            System.arraycopy(pcmData, 0, out, header.size, pcmData.size)
        }
    }

    companion object {
        private const val TAG = "VoiceAudioRecorder"
        const val AUDIO_MIME_TYPE = "audio/wav"
        private const val SAMPLE_RATE = 16_000
        private const val CHANNELS = 1
        private const val BITS_PER_SAMPLE = 16
        private const val CHUNK_SAMPLES = 800 // 50ms chunks for responsive 20fps waveform updates
        private const val MIN_DURATION_MILLIS = 350L
        private const val MAX_DURATION_MILLIS = 45_000L
        const val WAVEFORM_BARS = 22
    }
}
