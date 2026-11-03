package com.jarvis.app.brain

import com.jarvis.app.core.Brain
import com.jarvis.app.core.BrainException
import com.jarvis.app.core.BrainReply
import com.jarvis.app.core.ChatMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Gemini over REST with server-sent-event streaming. The model answers as
 * "SPOKEN: …" then "DETAIL: …"; SPOKEN is handed over the moment DETAIL starts.
 */
class GeminiBrain(
    private val apiKey: () -> String,
    private val model: () -> String,
    private val replyLanguage: () -> String,
) : Brain {

    override suspend fun reply(history: List<ChatMessage>, userText: String, onSpoken: (String) -> Unit): BrainReply =
        withContext(Dispatchers.IO) {
            val key = apiKey().trim()
            if (key.isEmpty()) throw BrainException("Please add your Gemini key in Settings first.")
            val modelId = model().trim().ifEmpty { DEFAULT_MODEL }
            val url = URL(
                "https://generativelanguage.googleapis.com/v1beta/models/" +
                        URLEncoder.encode(modelId, "UTF-8") + ":streamGenerateContent?alt=sse"
            )
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 15_000
                readTimeout = 30_000
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("x-goog-api-key", key)
            }
            try {
                conn.outputStream.use { it.write(buildBody(history, userText).toString().toByteArray()) }
                val code = conn.responseCode
                if (code != 200) {
                    val err = runCatching { conn.errorStream?.bufferedReader()?.readText() }.getOrNull().orEmpty()
                    throw BrainException(explain(code, err))
                }
                val buf = StringBuilder()
                var spokenSent = false
                conn.inputStream.bufferedReader().useLines { lines ->
                    for (line in lines) {
                        if (!line.startsWith("data:")) continue
                        val json = runCatching { JSONObject(line.removePrefix("data:").trim()) }.getOrNull() ?: continue
                        buf.append(extractText(json))
                        if (!spokenSent) {
                            val idx = detailIndex(buf)
                            if (idx >= 0) {
                                spokenSent = true
                                val spoken = clean(buf.substring(0, idx).replaceFirst(SPOKEN_TAG, ""))
                                if (spoken.isNotEmpty()) withContextMain { onSpoken(spoken) }
                            }
                        }
                    }
                }
                val full = buf.toString()
                val idx = detailIndex(buf)
                val spoken = clean((if (idx >= 0) full.substring(0, idx) else full).replaceFirst(SPOKEN_TAG, ""))
                val detail = if (idx >= 0) clean(full.substring(idx).replaceFirst(DETAIL_TAG, "")) else ""
                if (spoken.isEmpty()) throw BrainException("Gemini sent an empty answer. Please try again.")
                if (!spokenSent) withContextMain { onSpoken(spoken) }
                BrainReply(spoken, if (detail.equals("none", true)) "" else detail)
            } catch (e: BrainException) {
                throw e
            } catch (e: IOException) {
                throw BrainException("I can't reach Gemini. Check your internet.")
            } finally {
                conn.disconnect()
            }
        }

    private suspend fun withContextMain(block: () -> Unit) = withContext(Dispatchers.Main) { block() }

    private fun buildBody(history: List<ChatMessage>, userText: String): JSONObject {
        val contents = JSONArray()
        history.takeLast(10).forEach { m ->
            contents.put(JSONObject().put("role", if (m.fromUser) "user" else "model")
                .put("parts", JSONArray().put(JSONObject().put("text", m.text))))
        }
        contents.put(JSONObject().put("role", "user")
            .put("parts", JSONArray().put(JSONObject().put("text", userText))))
        return JSONObject()
            .put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", systemPrompt()))))
            .put("contents", contents)
            .put("generationConfig", JSONObject().put("temperature", 0.7).put("maxOutputTokens", 800))
    }

    private fun systemPrompt(): String = """
        You are JARVIS, a calm, sharp voice assistant on the user's Android phone. The user is an AI video designer in India
        who runs a small business selling AI ads, websites and social media content to local businesses.
        ${replyLanguage()}
        Always answer in exactly this format, plain text, no markdown, no emojis:
        SPOKEN: <one or two short sentences, natural to say out loud, under 35 words>
        DETAIL: <a fuller answer with specifics, or the word none if nothing more is useful>
        Never say the word "Jarvis" in your answer. If you don't know something current, say so briefly.
    """.trimIndent()

    private fun extractText(json: JSONObject): String {
        val parts = json.optJSONArray("candidates")?.optJSONObject(0)
            ?.optJSONObject("content")?.optJSONArray("parts") ?: return ""
        val sb = StringBuilder()
        for (i in 0 until parts.length()) {
            val p = parts.optJSONObject(i) ?: continue
            if (p.optBoolean("thought", false)) continue
            sb.append(p.optString("text"))
        }
        return sb.toString()
    }

    private fun detailIndex(buf: CharSequence): Int = DETAIL_TAG.find(buf)?.range?.first ?: -1

    private fun clean(s: String) = s.replace("*", "").replace("#", "").trim()

    private fun explain(code: Int, body: String): String {
        val msg = runCatching { JSONObject(body).optJSONObject("error")?.optString("message") }.getOrNull().orEmpty()
        return when {
            code == 429 -> "Gemini's free limit is used up for now. It resets at midnight US Pacific time (1:30 PM India time in winter, 12:30 PM in summer)."
            code == 400 && msg.contains("API key", true) -> "Your Gemini key isn't valid. Please check it in Settings."
            code == 401 || code == 403 -> "Gemini refused the key. Please check it in Settings."
            code == 404 -> "That Gemini model name wasn't found. Pick another model in Settings."
            code >= 500 -> "Gemini is having trouble right now. Try again in a minute."
            else -> "Gemini error $code. ${msg.take(120)}"
        }
    }

    companion object {
        const val DEFAULT_MODEL = "gemini-3.5-flash-lite"
        val MODELS = listOf("gemini-3.5-flash-lite", "gemini-3.1-flash-lite", "gemini-3.8-flash")
        private val SPOKEN_TAG = Regex("^\\s*SPOKEN\\s*:\\s*", RegexOption.IGNORE_CASE)
        private val DETAIL_TAG = Regex("DETAIL\\s*:\\s*", RegexOption.IGNORE_CASE)
    }
}
