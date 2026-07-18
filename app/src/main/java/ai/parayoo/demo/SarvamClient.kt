package ai.parayoo.demo

import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

object SarvamClient {
    fun transliterate(audioFile: File): String {
        val boundary = "Parayoo${UUID.randomUUID()}"
        val connection = (URL("https://api.sarvam.ai/speech-to-text").openConnection()
            as HttpURLConnection).apply {
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
                "Content-Disposition: form-data; name=\"file\"; filename=\"${audioFile.name}\"\r\n"
                    .toByteArray()
            )
            output.write("Content-Type: audio/wav\r\n\r\n".toByteArray())
            audioFile.inputStream().use { it.copyTo(output) }
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
}
