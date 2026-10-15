package com.example.voice

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.ParcelFileDescriptor
import java.io.FileOutputStream
import java.util.ArrayDeque
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Captura PCM orientada a voz para ambientes ruidosos.
 *
 * Usa VOICE_RECOGNITION como fuente orientada al reconocimiento y evita
 * aplicar NoiseSuppressor manualmente para no degradar fonemas cortos de
 * letras y números. Antes de enviar audio al SpeechRecognizer, calibra el
 * ruido ambiente y usa VAD adaptativo para:
 *
 * - esperar voz real en lugar de transmitir ruido continuamente;
 * - conservar un pequeño pre-roll para no cortar la primera sílaba;
 * - cerrar el pipe al detectar silencio final y producir EOF de forma fiable.
 */
class NoiseReducedAudioSource {

    data class Session(
        val readDescriptor: ParcelFileDescriptor,
        val noiseSuppressorEnabled: Boolean,
        val sampleRate: Int = SAMPLE_RATE,
        val channelCount: Int = 1,
        val encoding: Int = AudioFormat.ENCODING_PCM_16BIT
    )

    private val running = AtomicBoolean(false)
    private var executor: ExecutorService? = null
    private var audioRecord: AudioRecord? = null
    private var readSide: ParcelFileDescriptor? = null
    private var writeSide: ParcelFileDescriptor? = null

    @SuppressLint("MissingPermission")
    @Synchronized
    fun start(
        onVadState: ((AdaptiveVoiceActivityDetector.State) -> Unit)? = null
    ): Session? {
        release()

        val minBuffer = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )

        if (minBuffer <= 0) return null

        val recorderBufferSize = max(minBuffer * 2, 4096)

        val recorder = try {
            AudioRecord.Builder()
                .setAudioSource(MediaRecorder.AudioSource.VOICE_RECOGNITION)
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                        .build()
                )
                .setBufferSizeInBytes(recorderBufferSize)
                .build()
        } catch (_: Throwable) {
            return null
        }

        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            recorder.release()
            return null
        }

        val pipe = try {
            ParcelFileDescriptor.createPipe()
        } catch (_: Throwable) {
            recorder.release()
            return null
        }

        audioRecord = recorder
        readSide = pipe[0]
        writeSide = pipe[1]

        return try {
            recorder.startRecording()

            if (
                recorder.recordingState !=
                AudioRecord.RECORDSTATE_RECORDING
            ) {
                release()
                null
            } else {
                running.set(true)

                val localWrite = pipe[1]
                val localRecorder = recorder

                executor = Executors.newSingleThreadExecutor().also { worker ->
                    worker.execute {
                        captureWithAdaptiveVad(
                            recorder = localRecorder,
                            writeDescriptor = localWrite,
                            onVadState = onVadState
                        )
                    }
                }

                Session(
                    readDescriptor = pipe[0],
                    noiseSuppressorEnabled = false
                )
            }
        } catch (_: Throwable) {
            release()
            null
        }
    }

    private fun captureWithAdaptiveVad(
        recorder: AudioRecord,
        writeDescriptor: ParcelFileDescriptor,
        onVadState: ((AdaptiveVoiceActivityDetector.State) -> Unit)?
    ) {
        val vad = AdaptiveVoiceActivityDetector()
        val frame = ByteArray(FRAME_BYTES)
        val preRoll = ArrayDeque<ByteArray>()
        var previousPhase:
            AdaptiveVoiceActivityDetector.Phase? = null

        try {
            FileOutputStream(writeDescriptor.fileDescriptor).use { output ->
                while (running.get()) {
                    val read = recorder.read(
                        frame,
                        0,
                        frame.size,
                        AudioRecord.READ_BLOCKING
                    )

                    if (read <= 0) {
                        if (read < 0) break
                        continue
                    }

                    val levelDb = calculateDbFs(frame, read)
                    val state = vad.process(levelDb)

                    if (
                        state.phase != previousPhase ||
                        state.speechStarted ||
                        state.speechEnded ||
                        state.timedOut
                    ) {
                        onVadState?.invoke(state)
                    }

                    when (state.phase) {
                        AdaptiveVoiceActivityDetector.Phase.CALIBRATING -> {
                            preRoll.clear()
                        }

                        AdaptiveVoiceActivityDetector.Phase.WAITING_FOR_SPEECH -> {
                            preRoll.addLast(frame.copyOf(read))

                            while (preRoll.size > PRE_ROLL_FRAMES) {
                                preRoll.removeFirst()
                            }
                        }

                        AdaptiveVoiceActivityDetector.Phase.SPEECH -> {
                            if (
                                previousPhase !=
                                AdaptiveVoiceActivityDetector.Phase.SPEECH
                            ) {
                                while (preRoll.isNotEmpty()) {
                                    val buffered = preRoll.removeFirst()
                                    output.write(
                                        buffered,
                                        0,
                                        buffered.size
                                    )
                                }
                            }

                            output.write(frame, 0, read)
                        }

                        AdaptiveVoiceActivityDetector.Phase.FINISHED -> {
                            break
                        }
                    }

                    previousPhase = state.phase
                }

                output.flush()
            }
        } catch (_: Throwable) {
            // El pipe puede cerrarse deliberadamente desde finishInput/release.
        } finally {
            running.set(false)

            try {
                recorder.stop()
            } catch (_: Throwable) {
            }

            // FileOutputStream.use() ya cerró el extremo escritor y señaló EOF.
        }
    }

    private fun calculateDbFs(
        buffer: ByteArray,
        length: Int
    ): Float {
        if (length < 2) return MIN_DB_FS

        var index = 0
        var samples = 0
        var sumSquares = 0.0

        while (index + 1 < length) {
            val low = buffer[index].toInt() and 0xFF
            val high = buffer[index + 1].toInt()
            var sample = (high shl 8) or low

            if (sample > Short.MAX_VALUE) {
                sample -= 65_536
            }

            val normalized = sample.toDouble()
            sumSquares += normalized * normalized
            samples++
            index += 2
        }

        if (samples == 0) return MIN_DB_FS

        val rms = sqrt(sumSquares / samples)
        if (rms <= 1.0) return MIN_DB_FS

        val dbFs =
            20.0 * log10(rms / Short.MAX_VALUE.toDouble())

        return dbFs
            .toFloat()
            .coerceIn(MIN_DB_FS, 0f)
    }

    /**
     * Detiene el micrófono y cierra el extremo escritor del pipe para señalar EOF
     * al recognizer. El descriptor lector se conserva hasta release().
     */
    @Synchronized
    fun finishInput() {
        running.set(false)

        try {
            audioRecord?.stop()
        } catch (_: Throwable) {
        }

        try {
            writeSide?.close()
        } catch (_: Throwable) {
        }

        writeSide = null
    }

    @Synchronized
    fun release() {
        running.set(false)

        try {
            audioRecord?.stop()
        } catch (_: Throwable) {
        }

        try {
            writeSide?.close()
        } catch (_: Throwable) {
        }

        try {
            readSide?.close()
        } catch (_: Throwable) {
        }

        try {
            audioRecord?.release()
        } catch (_: Throwable) {
        }

        executor?.shutdownNow()

        executor = null
        audioRecord = null
        readSide = null
        writeSide = null
    }

    companion object {
        const val SAMPLE_RATE = 16_000

        // 20 ms de PCM mono 16-bit a 16 kHz:
        // 16 000 * 0.020 * 2 = 640 bytes.
        private const val FRAME_BYTES = 640
        private const val PRE_ROLL_FRAMES = 10
        private const val MIN_DB_FS = -90f
    }
}
