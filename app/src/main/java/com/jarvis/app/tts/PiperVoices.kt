package com.jarvis.app.tts

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** An offline Piper voice that can be downloaded once and used without internet. */
data class PiperVoice(
    val id: String,          // e.g. en_US-norman-medium
    val title: String,
    val description: String,
    val sizeMb: Int,
    val license: String,
) {
    val archive get() = "vits-piper-$id.tar.bz2"
    val url get() = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/$archive"
}

object PiperVoices {
    // Licenses checked on each voice's MODEL_CARD (huggingface.co/rhasspy/piper-voices), Nov 2026.
    val ALL = listOf(
        PiperVoice("en_US-norman-medium", "Deep male", "US English, deep and steady", 67, "Public domain"),
        PiperVoice("en_US-john-medium", "Calm male", "US English, calm narrator", 67, "Public domain"),
        PiperVoice("en_GB-northern_english_male-medium", "British male", "UK English, JARVIS-style accent", 67, "CC BY-SA 4.0"),
        PiperVoice("en_GB-southern_english_female-medium", "Female", "UK English, clear female voice", 80, "CC BY-SA 4.0"),
        PiperVoice("hi_IN-rohan-medium", "Hindi male", "Hindi (Devanagari text)", 67, "IndicTTS: personal / non-commercial use"),
    )

    fun byId(id: String?) = ALL.firstOrNull { it.id == id }

    fun dir(context: Context, v: PiperVoice) = File(context.filesDir, "voices/${v.id}")

    fun isInstalled(context: Context, v: PiperVoice) = File(dir(context, v), ".ready").exists()

    fun delete(context: Context, v: PiperVoice) { dir(context, v).deleteRecursively() }

    /** Downloads and unpacks a voice. [onProgress] gets 0..1. Runs on IO. */
    suspend fun download(context: Context, v: PiperVoice, onProgress: (Float) -> Unit) = withContext(Dispatchers.IO) {
        val target = dir(context, v)
        target.deleteRecursively()
        target.mkdirs()
        val tmp = File(context.cacheDir, v.archive)
        try {
            var conn = URL(v.url).openConnection() as HttpURLConnection
            conn.instanceFollowRedirects = true
            conn.connectTimeout = 20_000
            conn.readTimeout = 60_000
            // GitHub redirects to a different host; follow manually if needed.
            var hops = 0
            while (conn.responseCode in 301..308 && hops++ < 5) {
                val next = conn.getHeaderField("Location")
                conn.disconnect()
                conn = URL(next).openConnection() as HttpURLConnection
            }
            if (conn.responseCode != 200) throw IllegalStateException("Download failed (${conn.responseCode})")
            val total = conn.contentLengthLong.takeIf { it > 0 } ?: (v.sizeMb * 1_000_000L)
            conn.inputStream.use { input ->
                tmp.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    var done = 0L
                    var lastReport = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        done += n
                        if (done - lastReport > 500_000) {
                            lastReport = done
                            withContext(Dispatchers.Main) { onProgress((done.toFloat() / total * 0.9f).coerceAtMost(0.9f)) }
                        }
                    }
                }
            }
            conn.disconnect()
            // Unpack, dropping the top-level folder.
            TarArchiveInputStream(BZip2CompressorInputStream(tmp.inputStream().buffered())).use { tar ->
                while (true) {
                    val e = tar.nextEntry ?: break
                    val rel = e.name.substringAfter('/', "")
                    if (rel.isEmpty()) continue
                    val f = File(target, rel)
                    if (!f.canonicalPath.startsWith(target.canonicalPath)) continue
                    if (e.isDirectory) f.mkdirs() else {
                        f.parentFile?.mkdirs()
                        f.outputStream().use { tar.copyTo(it) }
                    }
                }
            }
            File(target, ".ready").writeText("ok")
            withContext(Dispatchers.Main) { onProgress(1f) }
        } catch (e: Exception) {
            target.deleteRecursively()
            throw e
        } finally {
            tmp.delete()
        }
    }
}
