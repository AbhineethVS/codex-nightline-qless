package ai.parayoo.demo

import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

object OpenAiPolisher {
    fun polish(manglish: String): String {
        if (BuildConfig.OPENAI_API_KEY.isBlank()) {
            throw IOException("Missing OPENAI_API_KEY")
        }
        val body = JSONObject().apply {
            put("model", "gpt-4o")
            put("temperature", 0.15)
            put(
                "messages",
                JSONArray()
                    .put(
                        JSONObject()
                            .put("role", "system")
                            .put(
                                "content",
                                """
                                Polish Malayalam Manglish for a chat message. Return only the polished text.
                                Preserve the user's meaning, Malayalam/English language mix, and informal tone.
                                Add natural punctuation, capitalization, and sentence endings.
                                Correct only high-confidence obvious spelling or transcription mistakes.
                                Never invent missing ideas, words, facts, or a different intent.
                                """.trimIndent()
                            )
                    )
                    .put(JSONObject().put("role", "user").put("content", manglish))
            )
        }
        val connection = (URL("https://api.openai.com/v1/chat/completions").openConnection()
            as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 20_000
            readTimeout = 45_000
            setRequestProperty("Authorization", "Bearer ${BuildConfig.OPENAI_API_KEY}")
            setRequestProperty("Content-Type", "application/json")
        }
        connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
        val status = connection.responseCode
        val response = (if (status in 200..299) connection.inputStream else connection.errorStream)
            ?.bufferedReader()
            ?.use { it.readText() }
            .orEmpty()
        connection.disconnect()
        if (status !in 200..299) throw IOException("OpenAI returned HTTP $status")
        return JSONObject(response)
            .optJSONArray("choices")
            ?.optJSONObject(0)
            ?.optJSONObject("message")
            ?.optString("content")
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: throw IOException("OpenAI returned no text")
    }
}
