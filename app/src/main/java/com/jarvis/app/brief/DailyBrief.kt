package com.jarvis.app.brief

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.jarvis.app.JarvisApp
import com.jarvis.app.MainActivity
import com.jarvis.app.R
import com.jarvis.app.brain.GeminiCall
import com.jarvis.app.data.JarvisDatabase
import com.jarvis.app.settings.SettingsRepository
import java.util.Calendar
import java.util.concurrent.TimeUnit

object DailyBrief {
    private const val WORK = "daily-brief"

    /** Schedules the next brief at the time set in Settings (re-armed after each one fires). */
    fun schedule(context: Context, policy: ExistingWorkPolicy = ExistingWorkPolicy.REPLACE) {
        val s = SettingsRepository.get(context)
        val wm = WorkManager.getInstance(context)
        if (!s.briefOn.value) { wm.cancelUniqueWork(WORK); return }
        val delay = nextFire(s.briefTime.value) - System.currentTimeMillis()
        wm.enqueueUniqueWork(WORK, policy,
            OneTimeWorkRequestBuilder<BriefWorker>().setInitialDelay(delay, TimeUnit.MILLISECONDS).build())
    }

    fun nextFire(minutes: Int): Long {
        val c = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, minutes / 60); set(Calendar.MINUTE, minutes % 60)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        if (c.timeInMillis <= System.currentTimeMillis() + 30_000) c.add(Calendar.DAY_OF_YEAR, 1)
        return c.timeInMillis
    }

    fun endOfToday(): Long = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59); set(Calendar.SECOND, 59)
    }.timeInMillis

    private val FALLBACK_IDEAS = listOf(
        "Before/after Reel: a plain product photo turning into a 10-second AI ad.",
        "Show 3 AI ad styles for the same product and ask viewers to vote in comments.",
        "Speed-run Reel: brief to finished AI ad in 60 seconds, with a timer on screen.",
        "\"Brands I'd love to make ads for\": a spec ad for a famous Indian brand.",
        "Myth vs fact Reel: \"AI ads look fake\", then show your best sample.",
        "Behind the scenes: the prompts and edits behind one of your ads.",
        "A local shop's product turned into a cinematic ad, tagged to the shop.",
    )

    /** One short content idea for the user's own Instagram. Falls back offline. */
    suspend fun newIdea(context: Context): String {
        val s = SettingsRepository.get(context)
        val idea = runCatching {
            GeminiCall.generate(
                key = s.geminiKey.value,
                model = s.model.value.ifBlank { GeminiCall.GROUNDED_MODEL },
                system = "You suggest Instagram Reel ideas for an Indian freelancer who makes AI video ads for brands. One idea, one or two sentences, practical, no hashtags.",
                prompt = "Give today's idea. Avoid repeating: ${s.briefIdea.take(200)}",
                timeoutMs = 15_000
            ).also { s.countRequest() }.trim().replace("*", "")
        }.getOrNull()?.takeIf { it.isNotBlank() }
            ?: FALLBACK_IDEAS[(System.currentTimeMillis() / 86_400_000L % FALLBACK_IDEAS.size).toInt()]
        s.briefIdea = idea
        return idea
    }
}

class BriefWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val ctx = applicationContext
        try {
            val dao = JarvisDatabase.get(ctx).leadDao()
            val due = dao.followUpsDue(DailyBrief.endOfToday()).size
            val fresh = dao.newSince(System.currentTimeMillis() - 24 * 3_600_000L).size
            DailyBrief.newIdea(ctx)
            val allowed = Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(ctx,
                Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            if (allowed) {
                val open = PendingIntent.getActivity(ctx, 7001,
                    Intent(ctx, MainActivity::class.java).putExtra(MainActivity.EXTRA_OPEN, "brief")
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
                val n = NotificationCompat.Builder(ctx, JarvisApp.CHANNEL_BRIEF)
                    .setSmallIcon(R.drawable.ic_stat_jarvis)
                    .setContentTitle("Good morning. Your Daily Brief")
                    .setContentText("$due follow-up${if (due == 1) "" else "s"} due · $fresh new lead${if (fresh == 1) "" else "s"} · 1 content idea")
                    .setContentIntent(open).setAutoCancel(true).build()
                runCatching { NotificationManagerCompat.from(ctx).notify(7001, n) }
            }
        } finally {
            DailyBrief.schedule(ctx, ExistingWorkPolicy.APPEND_OR_REPLACE)
        }
        return Result.success()
    }
}
