package ai.parayoo.demo

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import kotlin.concurrent.thread

class WavRecorder(private val cacheDirectory: File) {
    @Volatile private var recording = false
    private var recorder: AudioRecord? = null
    private var recordingThread: Thread? = null
    private var destination: File? = null

    fun isRecording(): Boolean = recording

    fun start(): Boolean {
        val minBuffer = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBuffer <= 0) return false

        val audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            minBuffer * 2
        )
        if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
            audioRecord.release()
            return false
        }

        destination = File(cacheDirectory, "parayoo-${System.currentTimeMillis()}.wav")
        recorder = audioRecord
        recording = true
        audioRecord.startRecording()
        recordingThread = thread(name = "ParayooAudio") {
            writeRecording(audioRecord, destination!!, minBuffer)
        }
        return true
    }

    fun stop(): File? {
        if (!recording) return null
        recording = false
        recorder?.runCatching { stop() }
        recordingThread?.join(1_000)
        recorder?.release()
        recorder = null
        return destination?.takeIf { it.length() > WAV_HEADER_SIZE }
    }

    fun release() {
        stop()
    }

    private fun writeRecording(audioRecord: AudioRecord, file: File, bufferSize: Int) {
        var dataBytes = 0
        runCatching {
            BufferedOutputStream(FileOutputStream(file)).use { output ->
                output.write(ByteArray(WAV_HEADER_SIZE))
                val buffer = ByteArray(bufferSize)
                while (recording) {
                    val bytesRead = audioRecord.read(buffer, 0, buffer.size)
                    if (bytesRead > 0) {
                        output.write(buffer, 0, bytesRead)
                        dataBytes += bytesRead
                    }
                }
            }
            RandomAccessFile(file, "rw").use {
                it.seek(0)
                it.write(wavHeader(dataBytes))
            }
        }
    }

    private fun wavHeader(dataBytes: Int): ByteArray {
        val totalSize = dataBytes + WAV_HEADER_SIZE - 8
        return ByteArray(WAV_HEADER_SIZE).apply {
            putAscii(0, "RIFF")
            putIntLE(4, totalSize)
            putAscii(8, "WAVE")
            putAscii(12, "fmt ")
            putIntLE(16, 16)
            putShortLE(20, 1)
            putShortLE(22, 1)
            putIntLE(24, SAMPLE_RATE)
            putIntLE(28, SAMPLE_RATE * 2)
            putShortLE(32, 2)
            putShortLE(34, 16)
            putAscii(36, "data")
            putIntLE(40, dataBytes)
        }
    }

    private fun ByteArray.putAscii(offset: Int, text: String) {
        text.forEachIndexed { index, char -> this[offset + index] = char.code.toByte() }
    }

    private fun ByteArray.putIntLE(offset: Int, value: Int) {
        for (index in 0..3) this[offset + index] = (value shr (8 * index)).toByte()
    }

    private fun ByteArray.putShortLE(offset: Int, value: Int) {
        this[offset] = value.toByte()
        this[offset + 1] = (value shr 8).toByte()
    }

    private companion object {
        const val SAMPLE_RATE = 16_000
        const val WAV_HEADER_SIZE = 44
    }
}
