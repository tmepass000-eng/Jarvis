package com.jarvis.app.leads

import com.jarvis.app.brain.GeminiCall
import com.jarvis.app.core.SearchResult
import com.jarvis.app.core.WebSearch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Tavily search API: 1,000 free searches a month (checked Nov 2026). Basic search = 1 credit. */
class TavilySearch(private val key: () -> String) : WebSearch {
    override val name = "Tavily"
    override suspend fun search(query: String, max: Int): List<SearchResult> = withContext(Dispatchers.IO) {
        withTimeoutOrNull(8_000) {
            runCatching {
                val conn = (URL("https://api.tavily.com/search").openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"; connectTimeout = 8_000; readTimeout = 8_000; doOutput = true
                    setRequestProperty("Content-Type", "application/json")
                    setRequestProperty("Authorization", "Bearer ${key().trim()}")
                }
                val body = JSONObject().put("query", query).put("max_results", max.coerceAtMost(5))
                    .put("search_depth", "basic")
                conn.outputStream.use { it.write(body.toString().toByteArray()) }
                if (conn.responseCode != 200) return@runCatching emptyList<SearchResult>()
                val arr = JSONObject(conn.inputStream.bufferedReader().readText()).optJSONArray("results")
                    ?: return@runCatching emptyList<SearchResult>()
                (0 until arr.length()).mapNotNull { i ->
                    val o = arr.optJSONObject(i) ?: return@mapNotNull null
                    SearchResult(o.optString("title"), o.optString("url"), o.optString("content").take(300))
                }.filter { it.url.startsWith("http") }.take(max)
            }.getOrDefault(emptyList())
        } ?: emptyList()
    }
}

/** Gemini with Google Search grounding (free tier on 2.5 Flash-Lite). No extra key needed. */
class GeminiGroundedSearch(private val key: () -> String) : WebSearch {
    override val name = "Google (via Gemini)"
    override suspend fun search(query: String, max: Int): List<SearchResult> = withTimeoutOrNull(20_000) {
        runCatching {
            val text = GeminiCall.generate(
                key = key(), model = GeminiCall.GROUNDED_MODEL, grounded = true, timeoutMs = 20_000,
                system = "You are a web search tool. Use Google Search. Never invent URLs; only list URLs you found.",
                prompt = "Search: $query\nList up to $max results, one per line, exactly as:\nTitle | URL | one-line description\nNo other text."
            )
            text.lines().mapNotNull { line ->
                val p = line.split("|").map { it.trim().trim('-', '*', ' ') }
                if (p.size < 2) return@mapNotNull null
                val url = URL_RX.find(p[1])?.value ?: URL_RX.find(line)?.value ?: return@mapNotNull null
                SearchResult(p[0], url.trimEnd('.', ')', ','), p.getOrElse(2) { "" })
            }.take(max)
        }.getOrDefault(emptyList())
    } ?: emptyList()

    companion object { private val URL_RX = Regex("https?://[^\\s|)\\]]+") }
}
