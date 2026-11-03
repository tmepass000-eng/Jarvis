package com.jarvis.app.brain

import com.jarvis.app.core.BrainException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.URLEncoder

/** One non-streaming Gemini call, used for lead search and scoring. */
object GeminiCall {
    /** Free tier includes Google Search grounding (about 500 a day) on the 2.5 Flash models. */
    const val GROUNDED_MODEL = "gemini-2.5-flash-lite"

    suspend fun generate(
        key: String,
        model: String,
        system: String,
        prompt: String,
        grounded: Boolean = false,
        json: Boolean = false,
        timeoutMs: Int = 30_000,
    ): String = withContext(Dispatchers.IO) {
        if (key.isBlank()) throw BrainException("Please add your Gemini key in Settings first.")
        val url = URL("https://generativelanguage.googleapis.com/v1beta/models/" +
                URLEncoder.encode(model, "UTF-8") + ":generateContent")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"; connectTimeout = 10_000; readTimeout = timeoutMs; doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("x-goog-api-key", key.trim())
        }
        val gen = JSONObject().put("temperature", 0.3).put("maxOutputTokens", 2048)
        if (json && !grounded) gen.put("responseMimeType", "application/json")
        val body = JSONObject()
            .put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", system))))
            .put("contents", JSONArray().put(JSONObject().put("role", "user")
                .put("parts", JSONArray().put(JSONObject().put("text", prompt)))))
            .put("generationConfig", gen)
        if (grounded) body.put("tools", JSONArray().put(JSONObject().put("google_search", JSONObject())))
        try {
            conn.outputStream.use { it.write(body.toString().toByteArray()) }
            val code = conn.responseCode
            if (code != 200) {
                val err = runCatching { conn.errorStream?.bufferedReader()?.readText() }.getOrNull().orEmpty()
                val msg = runCatching { JSONObject(err).optJSONObject("error")?.optString("message") }.getOrNull().orEmpty()
                throw BrainException(when {
                    code == 429 -> "Gemini's free limit is used up for now. It resets at midnight US Pacific time (1:30 PM India time in winter)."
                    code == 400 && msg.contains("API key", true) -> "Your Gemini key isn't valid. Please check it in Settings."
                    code == 401 || code == 403 -> "Gemini refused the key. Please check it in Settings."
                    code == 404 -> "Gemini model $model wasn't found."
                    code >= 500 -> "Gemini is having trouble right now. Try again in a minute."
                    else -> "Gemini error $code. ${msg.take(120)}"
                })
            }
            val res = JSONObject(conn.inputStream.bufferedReader().readText())
            val parts = res.optJSONArray("candidates")?.optJSONObject(0)
                ?.optJSONObject("content")?.optJSONArray("parts") ?: JSONArray()
            buildString {
                for (i in 0 until parts.length()) {
                    val p = parts.optJSONObject(i) ?: continue
                    if (!p.optBoolean("thought", false)) append(p.optString("text"))
                }
            }
        } catch (e: BrainException) { throw e
        } catch (e: SocketTimeoutException) { throw BrainException("Gemini took too long. Try again.")
        } catch (e: IOException) { throw BrainException("I can't reach Gemini. Check your internet.")
        } finally { conn.disconnect() }
    }
}
