package com.aistudy.aistudy.notify

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import java.util.Calendar
import java.util.concurrent.TimeUnit

object NotificationScheduler {

    const val UNIQUE_DAILY = "aistudy_daily_learning"
    const val UNIQUE_FORCE = "aistudy_force_learning"

    fun schedule(context: Context) {
        NotificationHelper.ensureChannel(context)

        val delayMs = millisUntilHour(20)
        val daily = PeriodicWorkRequest.Builder(
            DailyLearningWorker::class.java,
            1,
            TimeUnit.DAYS
        )
            .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            UNIQUE_DAILY,
            ExistingPeriodicWorkPolicy.KEEP,
            daily
        )
    }

    fun enqueueNow(context: Context) {
        NotificationHelper.ensureChannel(context)
        context.getSharedPreferences("notify_state", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .apply()
        val once = OneTimeWorkRequest.Builder(DailyLearningWorker::class.java).build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            UNIQUE_FORCE,
            ExistingWorkPolicy.REPLACE,
            once
        )
    }

    private fun millisUntilHour(hourOfDay: Int): Long {
        val now = Calendar.getInstance()
        val target = Calendar.getInstance()
        target.set(Calendar.HOUR_OF_DAY, hourOfDay)
        target.set(Calendar.MINUTE, 0)
        target.set(Calendar.SECOND, 0)
        target.set(Calendar.MILLISECOND, 0)
        if (!target.after(now)) {
            target.add(Calendar.DAY_OF_MONTH, 1)
        }
        return target.timeInMillis - now.timeInMillis
    }
}
