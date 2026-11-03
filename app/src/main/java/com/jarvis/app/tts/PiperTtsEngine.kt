package com.jarvis.app.tts

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import java.io.File
import java.util.concurrent.Executors

/** Offline Piper/VITS voice through sherpa-onnx, streamed to an AudioTrack as it is generated. */
class PiperTtsEngine(private val context: Context) {

    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private var tts: OfflineTts? = null
    private var loadedId: String? = null
    @Volatile private var generation = 0
    @Volatile private var track: AudioTrack? = null

    /** Speaks [text] with [voice]. Calls [onDone] (main thread) when finished; [onFail] if the voice can't run. */
    fun speak(voice: PiperVoice, text: String, speed: Float, onDone: () -> Unit, onFail: () -> Unit) {
        val my = ++generation
        worker.execute {
            try {
                val engine = load(voice)
                if (my != generation) return@execute
                val rate = engine.sampleRate()
                val t = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ASSISTANT)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                            .setSampleRate(rate).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build()
                    )
                    .setBufferSizeInBytes(
                        AudioTrack.getMinBufferSize(rate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_FLOAT) * 2
                    )
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()
                track = t
                t.play()
                var written = 0L
                engine.generateWithCallback(text, sid = 0, speed = speed) { samples ->
                    if (my != generation) return@generateWithCallback 0
                    t.write(samples, 0, samples.size, AudioTrack.WRITE_BLOCKING)
                    written += samples.size
                    1
                }
                if (my == generation) {
                    // Wait for the buffer to drain before saying we're done.
                    val endFrames = written.toInt()
                    var guard = 0
                    while (my == generation && t.playbackHeadPosition < endFrames && guard++ < 400) Thread.sleep(25)
                }
                runCatching { t.stop(); t.release() }
                if (track === t) track = null
                if (my == generation) main.post(onDone)
            } catch (e: Throwable) {
                Log.e("PiperTts", "speak failed", e)
                if (my == generation) main.post(onFail)
            }
        }
    }

    private fun load(voice: PiperVoice): OfflineTts {
        val current = tts
        if (current != null && loadedId == voice.id) return current
        current?.release()
        tts = null
        val dir = PiperVoices.dir(context, voice)
        val model = File(dir, "${voice.id}.onnx")
        val config = OfflineTtsConfig(
            model = OfflineTtsModelConfig(
                vits = OfflineTtsVitsModelConfig(
                    model = model.absolutePath,
                    tokens = File(dir, "tokens.txt").absolutePath,
                    dataDir = File(dir, "espeak-ng-data").absolutePath,
                ),
                numThreads = 2,
                debug = false,
                provider = "cpu",
            ),
            maxNumSentences = 1,
        )
        return OfflineTts(assetManager = null, config = config).also { tts = it; loadedId = voice.id }
    }

    fun stop() {
        generation++
        track?.let { runCatching { it.pause(); it.flush() } }
    }

    fun release() {
        stop()
        worker.execute { tts?.release(); tts = null; loadedId = null }
        worker.shutdown()
    }
}
