package com.jarvis.app.outreach

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.jarvis.app.JarvisApp
import com.jarvis.app.MainActivity
import com.jarvis.app.R
import com.jarvis.app.data.JarvisDatabase
import com.jarvis.app.data.LeadStatus
import java.util.concurrent.TimeUnit

/** Schedules and shows follow-up reminders with WorkManager. */
object FollowUps {
    fun schedule(context: Context, leadId: Long, at: Long) {
        val delay = (at - System.currentTimeMillis()).coerceAtLeast(0)
        val req = OneTimeWorkRequestBuilder<FollowUpWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setInputData(workDataOf("leadId" to leadId))
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork("followup-$leadId", ExistingWorkPolicy.REPLACE, req)
    }

    fun cancel(context: Context, leadId: Long) =
        WorkManager.getInstance(context).cancelUniqueWork("followup-$leadId")
}

class FollowUpWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val id = inputData.getLong("leadId", -1)
        val lead = JarvisDatabase.get(applicationContext).leadDao().byId(id) ?: return Result.success()
        if (lead.status in listOf(LeadStatus.WON, LeadStatus.LOST, LeadStatus.SKIPPED)) return Result.success()
        if (ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED && android.os.Build.VERSION.SDK_INT >= 33) return Result.success()
        val open = PendingIntent.getActivity(
            applicationContext, id.toInt(),
            Intent(applicationContext, MainActivity::class.java).putExtra(MainActivity.EXTRA_OPEN, "pipeline")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n = NotificationCompat.Builder(applicationContext, JarvisApp.CHANNEL_FOLLOWUP)
            .setSmallIcon(R.drawable.ic_stat_jarvis)
            .setContentTitle("Follow up with ${lead.name}")
            .setContentText("${lead.platform} · ${lead.status}. A short, friendly nudge works best.")
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(applicationContext).notify(10_000 + id.toInt(), n) }
        return Result.success()
    }
}
