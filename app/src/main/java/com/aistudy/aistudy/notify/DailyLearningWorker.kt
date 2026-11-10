package com.aistudy.aistudy.notify

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.aistudy.aistudy.MainActivity
import com.aistudy.aistudy.R
import com.aistudy.aistudy.auth.LoginActivity
import com.aistudy.aistudy.database.AppDatabase
import com.aistudy.aistudy.database.Profile
import com.aistudy.aistudy.database.QuestionAnswerDao
import com.aistudy.aistudy.database.QuizItemDao
import com.aistudy.aistudy.xp.XpRules
import java.util.Calendar
import java.util.Locale
import kotlin.math.roundToInt

class DailyLearningWorker(context: Context, params: WorkerParameters) : Worker(context, params) {

    override fun doWork(): Result {
        val context = applicationContext
        val userId = context.getSharedPreferences(LoginActivity.PREFS_AUTH, Context.MODE_PRIVATE)
            .getLong(LoginActivity.KEY_USER_ID, -1)
        if (userId == -1L) {
            return Result.success()
        }

        val db = AppDatabase.getInstance(context)
        val profile = db.profileDao().getByUserId(userId) ?: return Result.success()

        val qaDao = db.questionAnswerDao()
        val quizItemDao = db.quizItemDao()

        maybeBookmark(context, qaDao, userId)
        maybeMistakes(context, quizItemDao, userId)
        maybeMotivational(context, profile, userId)
        maybeSummary(context, qaDao, quizItemDao, profile, userId)

        return Result.success()
    }

    private fun maybeBookmark(context: Context, qaDao: QuestionAnswerDao, userId: Long) {
        if (alreadySentToday(context, "bookmark")) {
            return
        }
        val beforeMs = System.currentTimeMillis() - HOURS_48_MS
        val qa = qaDao.getOldestUnreviewedStarred(userId, beforeMs) ?: return
        NotificationHelper.show(
            context,
            NotificationHelper.ID_BOOKMARK,
            context.getString(R.string.notify_bookmark_title),
            context.getString(R.string.notify_bookmark_body),
            MainActivity.TAB_HISTORY,
            qa.id
        )
        markSentToday(context, "bookmark")
    }

    private fun maybeMistakes(context: Context, quizItemDao: QuizItemDao, userId: Long) {
        if (alreadySentToday(context, "mistakes")) {
            return
        }
        val fromMs = System.currentTimeMillis() - DAYS_7_MS
        val subject = quizItemDao.findSubjectWithRepeatedMistakes(userId, fromMs)
        if (subject.isNullOrEmpty()) {
            return
        }
        NotificationHelper.show(
            context,
            NotificationHelper.ID_MISTAKES,
            context.getString(R.string.notify_mistakes_title),
            context.getString(R.string.notify_mistakes_body, capitalize(subject)),
            MainActivity.TAB_PROGRESS,
            -1
        )
        markSentToday(context, "mistakes")
    }

    private fun maybeMotivational(context: Context, profile: Profile, userId: Long) {
        if (alreadySentToday(context, "motivational")) {
            return
        }
        XpRules.ensureWeek(profile)
        val nearLevel = XpRules.isNearNextLevel(profile.xp)
        val streakRisk = XpRules.streakAtRisk(profile)
        if (!nearLevel && !streakRisk) {
            return
        }
        val body = if (streakRisk) {
            context.getString(R.string.notify_motivational_streak, profile.streak)
        } else {
            context.getString(
                R.string.notify_motivational_level,
                XpRules.xpToNextLevel(profile.xp),
                XpRules.levelFromXp(profile.xp) + 1
            )
        }
        NotificationHelper.show(
            context,
            NotificationHelper.ID_MOTIVATIONAL,
            context.getString(R.string.notify_motivational_title),
            body,
            MainActivity.TAB_HOME,
            -1
        )
        markSentToday(context, "motivational")
    }

    private fun maybeSummary(
        context: Context,
        qaDao: QuestionAnswerDao,
        quizItemDao: QuizItemDao,
        profile: Profile,
        userId: Long
    ) {
        val cal = Calendar.getInstance()
        val sunday = cal.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY
        val kind = if (sunday) "weekly" else "daily"
        if (alreadySentToday(context, "summary_$kind")) {
            return
        }

        val fromMs = if (sunday) {
            System.currentTimeMillis() - DAYS_7_MS
        } else {
            val start = Calendar.getInstance()
            start.set(Calendar.HOUR_OF_DAY, 0)
            start.set(Calendar.MINUTE, 0)
            start.set(Calendar.SECOND, 0)
            start.set(Calendar.MILLISECOND, 0)
            start.timeInMillis
        }

        val questions = qaDao.countCreatedSince(userId, fromMs)
        val attempted = quizItemDao.countAttemptedByUserId(userId)
        val correct = quizItemDao.countCorrectByUserId(userId)
        val accuracy = if (attempted > 0) ((correct * 100f) / attempted).roundToInt() else 0
        XpRules.ensureWeek(profile)

        val title = if (sunday) {
            context.getString(R.string.notify_summary_weekly_title)
        } else {
            context.getString(R.string.notify_summary_daily_title)
        }
        val body = context.getString(
            R.string.notify_summary_body,
            questions,
            accuracy,
            profile.weeklyXp
        )

        NotificationHelper.show(
            context,
            NotificationHelper.ID_SUMMARY,
            title,
            body,
            MainActivity.TAB_PROGRESS,
            -1
        )
        markSentToday(context, "summary_$kind")
    }

    private fun alreadySentToday(context: Context, type: String): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val today = XpRules.todayKey()
        return today == prefs.getString(KEY_DAY + type, "")
    }

    private fun markSentToday(context: Context, type: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_DAY + type, XpRules.todayKey())
            .apply()
    }

    companion object {
        private const val PREFS = "notify_state"
        private const val KEY_DAY = "last_day_"
        private const val HOURS_48_MS = 48L * 60L * 60L * 1000L
        private const val DAYS_7_MS = 7L * 24L * 60L * 60L * 1000L

        private fun capitalize(subject: String?): String {
            if (subject.isNullOrEmpty()) {
                return ""
            }
            return subject.substring(0, 1).uppercase(Locale.US) + subject.substring(1)
        }
    }
}
