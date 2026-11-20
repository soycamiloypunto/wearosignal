package com.wearosignal.app.poll

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.wearosignal.app.AppDeps
import org.signal.core.util.logging.Log
import java.util.concurrent.TimeUnit

/**
 * Schedules the next poll using Doze-friendly inexact alarms (Play Store compliant, no exact-alarm
 * permission required, compatible from Wear OS 3 / API 30 / Galaxy Watch 4 onwards) with a
 * 15-minute WorkManager periodic fallback.
 */
object PollScheduler {

  private val TAG = Log.tag(PollScheduler::class)
  private const val WORK_NAME = "poll-fallback"

  private fun pollPendingIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
    context,
    0,
    Intent(context, PollReceiver::class.java),
    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
  )

  fun scheduleNext(context: Context) {
    if (!AppDeps.account.isLinked) return
    if (!AppDeps.account.backgroundPollingEnabled) {
      cancel(context)
      return
    }

    val alarmManager = context.getSystemService(AlarmManager::class.java)
    val intervalMs = AppDeps.account.pollIntervalMinutes * 60_000L

    try {
      alarmManager.setAndAllowWhileIdle(
        AlarmManager.RTC_WAKEUP,
        System.currentTimeMillis() + intervalMs,
        pollPendingIntent(context)
      )
      Log.i(TAG, "Scheduled inexact poll in ~${AppDeps.account.pollIntervalMinutes} min")
    } catch (e: SecurityException) {
      Log.w(TAG, "Alarm scheduling restricted; using 15-min WorkManager fallback", e)
      val request = PeriodicWorkRequestBuilder<PollWorker>(15, TimeUnit.MINUTES).build()
      WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }
  }

  /** Cancels any pending recurring poll (alarm + WorkManager fallback). */
  fun cancel(context: Context) {
    context.getSystemService(AlarmManager::class.java).cancel(pollPendingIntent(context))
    WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    Log.i(TAG, "Background polling cancelled")
  }
}
