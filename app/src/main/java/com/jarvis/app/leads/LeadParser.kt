package com.jarvis.app.leads

import org.json.JSONArray
import org.json.JSONObject

data class ScoredLead(val name: String, val platform: String, val profileUrl: String, val score: Int, val reason: String)

/** Reads the Brain's JSON safely. Anything malformed is skipped, never crashes. */
object LeadParser {
    fun parse(raw: String): List<ScoredLead> {
        val arr = extractArray(raw) ?: return emptyList()
        val out = mutableListOf<ScoredLead>()
        for (i in 0 until arr.length()) {
            val o = arr.opt(i) as? JSONObject ?: continue
            runCatching {
                val url = normaliseUrl(o.optString("profileUrl").ifBlank { o.optString("url") }) ?: return@runCatching
                val name = o.optString("name").trim().take(80).ifBlank { return@runCatching }
                val score = when (val s = o.opt("score")) {
                    is Number -> s.toInt()
                    is String -> s.filter { it.isDigit() }.take(3).toIntOrNull() ?: return@runCatching
                    else -> return@runCatching
                }.coerceIn(0, 100)
                out += ScoredLead(name, platformOf(url, o.optString("platform")), url, score,
                    o.optString("reason").trim().take(160))
            }
        }
        return out.distinctBy { it.profileUrl }
    }

    private fun extractArray(raw: String): JSONArray? {
        val t = raw.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        runCatching { return JSONArray(t) }
        runCatching { JSONObject(t).optJSONArray("leads")?.let { return it } }
        val a = t.indexOf('['); val b = t.lastIndexOf(']')
        if (a >= 0 && b > a) runCatching { return JSONArray(t.substring(a, b + 1)) }
        return null
    }

    fun normaliseUrl(u: String): String? {
        var s = u.trim().trimEnd('/', '.', ',')
        if (s.isEmpty() || s.contains(' ')) return null
        if (!s.startsWith("http")) s = "https://$s"
        s = s.replace("http://", "https://").replace("://m.", "://www.").substringBefore("?").trimEnd('/')
        return if (Regex("^https://[\\w.-]+\\.[a-z]{2,}(/.*)?$", RegexOption.IGNORE_CASE).matches(s)) s else null
    }

    fun platformOf(url: String, hint: String = ""): String = when {
        url.contains("instagram.com", true) -> "Instagram"
        url.contains("linkedin.com", true) -> "LinkedIn"
        hint.equals("instagram", true) -> "Instagram"
        hint.equals("linkedin", true) -> "LinkedIn"
        else -> "Web"
    }

    /** Pulls profile-looking links out of anything the user pastes. */
    fun linksIn(text: String): List<String> =
        Regex("(https?://)?(www\\.)?(instagram\\.com|linkedin\\.com)/[\\w./%-]+|https?://[^\\s,]+", RegexOption.IGNORE_CASE)
            .findAll(text).mapNotNull { normaliseUrl(it.value) }.distinct().take(25).toList()
}
