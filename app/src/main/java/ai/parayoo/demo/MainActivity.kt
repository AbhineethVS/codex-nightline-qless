package ai.parayoo.demo

import android.Manifest
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import org.json.JSONObject
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import java.util.concurrent.Executors
import kotlin.concurrent.thread

class MainActivity : Activity() {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val networkExecutor = Executors.newSingleThreadExecutor()

    private lateinit var recordButton: Button
    private lateinit var copyButton: Button
    private lateinit var sampleButton: Button
    private lateinit var statusText: TextView
    private lateinit var resultText: TextView

    @Volatile private var isRecording = false
    private var recorder: AudioRecord? = null
    private var recordingThread: Thread? = null
    private var audioFile: File? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        recordButton = findViewById(R.id.recordButton)
        copyButton = findViewById(R.id.copyButton)
        sampleButton = findViewById(R.id.sampleButton)
        statusText = findViewById(R.id.statusText)
        resultText = findViewById(R.id.resultText)

        recordButton.setOnClickListener {
            if (isRecording) stopRecordingAndTranscribe() else requestMicAndStart()
        }
        copyButton.setOnClickListener { copyResult() }
        sampleButton.setOnClickListener {
            showResult("njan ippol varam, kurachu wait cheyyu", "Backup demo result shown.")
        }
    }

    private fun requestMicAndStart() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            startRecording()
        } else {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_MIC_PERMISSION)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_MIC_PERMISSION &&
            grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED
        ) {
            startRecording()
        } else if (requestCode == REQUEST_MIC_PERMISSION) {
            statusText.text = "Microphone permission is required to record."
        }
    }

    private fun startRecording() {
        val minBuffer = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBuffer <= 0) {
            statusText.text = "This device does not support the required microphone format."
            return
        }

        audioFile = File(cacheDir, "parayoo-${System.currentTimeMillis()}.wav")
        val audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            minBuffer * 2
        )
        if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
            statusText.text = "Could not start the microphone."
            audioRecord.release()
            return
        }

        recorder = audioRecord
        isRecording = true
        recordButton.text = "Stop and convert"
        statusText.text = "Listening… speak Malayalam (15-second limit)."
        resultText.text = "Recording…"
        copyButton.isEnabled = false
        audioRecord.startRecording()

        recordingThread = thread(name = "ParayooAudio") {
            writeWavRecording(audioRecord, audioFile!!, minBuffer)
        }
        mainHandler.postDelayed(autoStopRecording, MAX_RECORDING_MS)
    }

    private val autoStopRecording = Runnable {
        if (isRecording) stopRecordingAndTranscribe()
    }

    private fun stopRecordingAndTranscribe() {
        if (!isRecording) return
        isRecording = false
        mainHandler.removeCallbacks(autoStopRecording)
        recorder?.runCatching { stop() }
        recordingThread?.join(1_000)
        recorder?.release()
        recorder = null
        recordButton.text = "Record Malayalam"

        val file = audioFile
        if (file == null || file.length() <= WAV_HEADER_SIZE) {
            statusText.text = "No audio was captured. Try recording again."
            return
        }
        if (BuildConfig.SARVAM_API_KEY.isBlank()) {
            statusText.text = "Add SARVAM_API_KEY to local.properties, then rebuild."
            resultText.text = "Demo backup: njan ippol varam, kurachu wait cheyyu"
            copyButton.isEnabled = true
            return
        }

        statusText.text = "Converting with Sarvam…"
        recordButton.isEnabled = false
        networkExecutor.execute {
            try {
                val transcript = transcribeWithSarvam(file)
                mainHandler.post {
                    showResult(transcript, "Converted with Sarvam transliteration.")
                    recordButton.isEnabled = true
                }
            } catch (error: Exception) {
                mainHandler.post {
                    statusText.text = "Could not convert: ${error.message ?: "network error"}"
                    resultText.text = "Use “Show backup demo result” to continue the presentation."
                    sampleButton.visibility = View.VISIBLE
                    recordButton.isEnabled = true
                }
            }
        }
    }

    private fun writeWavRecording(audioRecord: AudioRecord, destination: File, bufferSize: Int) {
        var dataBytes = 0
        try {
            BufferedOutputStream(FileOutputStream(destination)).use { output ->
                output.write(ByteArray(WAV_HEADER_SIZE))
                val buffer = ByteArray(bufferSize)
                while (isRecording) {
                    val bytesRead = audioRecord.read(buffer, 0, buffer.size)
                    if (bytesRead > 0) {
                        output.write(buffer, 0, bytesRead)
                        dataBytes += bytesRead
                    }
                }
            }
            RandomAccessFile(destination, "rw").use { output ->
                output.seek(0)
                output.write(wavHeader(dataBytes))
            }
        } catch (_: IOException) {
            // The UI handles an empty/invalid output file after recording stops.
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

    private fun transcribeWithSarvam(file: File): String {
        val boundary = "Parayoo${UUID.randomUUID()}"
        val connection = (URL(SARVAM_URL).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 20_000
            readTimeout = 45_000
            setRequestProperty("api-subscription-key", BuildConfig.SARVAM_API_KEY)
            setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
        }

        connection.outputStream.use { output ->
            fun writeField(name: String, value: String) {
                output.write("--$boundary\r\n".toByteArray())
                output.write("Content-Disposition: form-data; name=\"$name\"\r\n\r\n".toByteArray())
                output.write("$value\r\n".toByteArray())
            }
            writeField("model", "saaras:v3")
            writeField("mode", "translit")
            writeField("language_code", "ml-IN")
            output.write("--$boundary\r\n".toByteArray())
            output.write(
                "Content-Disposition: form-data; name=\"file\"; filename=\"${file.name}\"\r\n"
                    .toByteArray()
            )
            output.write("Content-Type: audio/wav\r\n\r\n".toByteArray())
            file.inputStream().use { it.copyTo(output) }
            output.write("\r\n--$boundary--\r\n".toByteArray())
        }

        val status = connection.responseCode
        val response = (if (status in 200..299) connection.inputStream else connection.errorStream)
            ?.bufferedReader()
            ?.use { it.readText() }
            .orEmpty()
        connection.disconnect()
        if (status !in 200..299) throw IOException("Sarvam returned HTTP $status")

        return JSONObject(response).optString("transcript").takeIf { it.isNotBlank() }
            ?: throw IOException("Sarvam returned no transcript")
    }

    private fun showResult(output: String, status: String) {
        resultText.text = output
        statusText.text = status
        copyButton.isEnabled = true
    }

    private fun copyResult() {
        val output = resultText.text.toString()
        if (output.isBlank() || output.startsWith("Your Roman Malayalam")) return
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Parayoo Manglish", output))
        Toast.makeText(this, "Copied. Paste it into WhatsApp.", Toast.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        mainHandler.removeCallbacks(autoStopRecording)
        recorder?.release()
        networkExecutor.shutdownNow()
        super.onDestroy()
    }

    private companion object {
        const val REQUEST_MIC_PERMISSION = 1
        const val SAMPLE_RATE = 16_000
        const val WAV_HEADER_SIZE = 44
        const val MAX_RECORDING_MS = 15_000L
        const val SARVAM_URL = "https://api.sarvam.ai/speech-to-text"
    }
}
