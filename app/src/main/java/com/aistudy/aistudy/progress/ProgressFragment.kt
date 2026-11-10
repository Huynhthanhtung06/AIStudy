package com.aistudy.aistudy.progress

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.aistudy.aistudy.R
import com.aistudy.aistudy.auth.LoginActivity
import com.aistudy.aistudy.database.AppDatabase
import com.aistudy.aistudy.database.QuestionAnswerDao
import com.aistudy.aistudy.database.QuizItemDao
import com.aistudy.aistudy.quiz.QuizActivity
import kotlin.math.roundToInt

class ProgressFragment : Fragment() {

    companion object {
        private const val XP_MAX = 1200
        private const val MIN_PRACTICE_QA = 2
        private const val DAY_MS = 24L * 60L * 60L * 1000L
        private const val WINDOW_MS = 30L * DAY_MS
        private val SUBJECTS = arrayOf(
            "math", "science", "code", "history", "language"
        )
    }

    private lateinit var tvUserEmail: TextView
    private lateinit var btnLogout: TextView
    private lateinit var tvXpTotal: TextView
    private lateinit var tvLevel: TextView
    private lateinit var tvXpPercent: TextView
    private lateinit var progressXp: ProgressBar
    private lateinit var tvStatStreak: TextView
    private lateinit var tvStatQuestions: TextView
    private lateinit var tvStatStars: TextView

    private lateinit var tvQuizAccuracy: TextView
    private lateinit var tvReviewTime: TextView
    private lateinit var tvInsight1: TextView
    private lateinit var tvInsight2: TextView
    private lateinit var tvInsightsEmpty: TextView
    private lateinit var cardReviewSuggest: LinearLayout
    private lateinit var tvReviewSubtitle: TextView

    private lateinit var rowMath: LinearLayout
    private lateinit var rowScience: LinearLayout
    private lateinit var rowCode: LinearLayout
    private lateinit var rowHistory: LinearLayout
    private lateinit var rowLanguage: LinearLayout
    private lateinit var tvCountMath: TextView
    private lateinit var tvCountScience: TextView
    private lateinit var tvCountCode: TextView
    private lateinit var tvCountHistory: TextView
    private lateinit var tvCountLanguage: TextView
    private lateinit var barMath: ProgressBar
    private lateinit var barScience: ProgressBar
    private lateinit var barCode: ProgressBar
    private lateinit var barHistory: ProgressBar
    private lateinit var barLanguage: ProgressBar
    private lateinit var tvSubjectsEmpty: TextView

    private lateinit var badgeStarter: LinearLayout
    private lateinit var badgeWeek: LinearLayout
    private lateinit var badgeMath: LinearLayout
    private lateinit var badgeScholar: LinearLayout

    private var qaDao: QuestionAnswerDao? = null
    private var currentUserId = -1L
    private var reviewSubject: String? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_progress, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        tvUserEmail = view.findViewById(R.id.tv_user_email)
        btnLogout = view.findViewById(R.id.btn_logout)
        tvXpTotal = view.findViewById(R.id.tv_xp_total)
        tvLevel = view.findViewById(R.id.tv_level)
        tvXpPercent = view.findViewById(R.id.tv_xp_percent)
        progressXp = view.findViewById(R.id.progress_xp)
        tvStatStreak = view.findViewById(R.id.tv_stat_streak)
        tvStatQuestions = view.findViewById(R.id.tv_stat_questions)
        tvStatStars = view.findViewById(R.id.tv_stat_stars)

        tvQuizAccuracy = view.findViewById(R.id.tv_quiz_accuracy)
        tvReviewTime = view.findViewById(R.id.tv_review_time)
        tvInsight1 = view.findViewById(R.id.tv_insight_1)
        tvInsight2 = view.findViewById(R.id.tv_insight_2)
        tvInsightsEmpty = view.findViewById(R.id.tv_insights_empty)
        cardReviewSuggest = view.findViewById(R.id.card_review_suggest)
        tvReviewSubtitle = view.findViewById(R.id.tv_review_subtitle)

        rowMath = view.findViewById(R.id.row_math)
        rowScience = view.findViewById(R.id.row_science)
        rowCode = view.findViewById(R.id.row_code)
        rowHistory = view.findViewById(R.id.row_history)
        rowLanguage = view.findViewById(R.id.row_language)
        tvCountMath = view.findViewById(R.id.tv_count_math)
        tvCountScience = view.findViewById(R.id.tv_count_science)
        tvCountCode = view.findViewById(R.id.tv_count_code)
        tvCountHistory = view.findViewById(R.id.tv_count_history)
        tvCountLanguage = view.findViewById(R.id.tv_count_language)
        barMath = view.findViewById(R.id.bar_math)
        barScience = view.findViewById(R.id.bar_science)
        barCode = view.findViewById(R.id.bar_code)
        barHistory = view.findViewById(R.id.bar_history)
        barLanguage = view.findViewById(R.id.bar_language)
        tvSubjectsEmpty = view.findViewById(R.id.tv_subjects_empty)

        badgeStarter = view.findViewById(R.id.badge_starter)
        badgeWeek = view.findViewById(R.id.badge_week)
        badgeMath = view.findViewById(R.id.badge_math)
        badgeScholar = view.findViewById(R.id.badge_scholar)

        btnLogout.setOnClickListener { logout() }
        cardReviewSuggest.setOnClickListener {
            reviewSubject?.let { openPractice(it) }
        }
    }

    override fun onResume() {
        super.onResume()
        loadProgress()
    }

    private fun logout() {
        if (context == null) {
            return
        }
        requireContext()
            .getSharedPreferences(LoginActivity.PREFS_AUTH, Context.MODE_PRIVATE)
            .edit()
            .remove(LoginActivity.KEY_USER_ID)
            .apply()

        val intent = Intent(requireContext(), LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
    }

    private fun loadProgress() {
        if (context == null) {
            return
        }

        val userId = requireContext()
            .getSharedPreferences(LoginActivity.PREFS_AUTH, Context.MODE_PRIVATE)
            .getLong(LoginActivity.KEY_USER_ID, -1L)
        if (userId == -1L) {
            return
        }

        val db = AppDatabase.getInstance(requireContext())
        val user = db.userDao().getById(userId)
        tvUserEmail.text = user?.email ?: ""

        val profile = db.profileDao().getByUserId(userId)
        val qaDao = db.questionAnswerDao()
        this.qaDao = qaDao
        val quizItemDao = db.quizItemDao()
        currentUserId = userId

        val all = qaDao.getAllByUserId(userId)

        val xp = profile?.xp ?: 0
        val streak = profile?.streak ?: 0
        val level = maxOf(1, xp / 100)
        val xpPct = minOf(100, ((xp * 100f) / XP_MAX).roundToInt())
        val totalQuestions = all.size
        val starredCount = all.count { it.starred }

        tvXpTotal.text = xp.toString()
        tvLevel.text = getString(R.string.progress_level_scholar, level)
        tvXpPercent.text = getString(R.string.progress_xp_percent, xpPct)
        progressXp.progress = xpPct
        tvStatStreak.text = getString(R.string.progress_streak_value, streak)
        tvStatQuestions.text = totalQuestions.toString()
        tvStatStars.text = starredCount.toString()

        val attempted = quizItemDao.countAttemptedByUserId(userId)
        val correct = quizItemDao.countCorrectByUserId(userId)
        if (attempted > 0) {
            val accuracyPct = ((correct * 100f) / attempted).roundToInt()
            tvQuizAccuracy.text = getString(
                R.string.progress_quiz_accuracy_value, accuracyPct, correct, attempted
            )
        } else {
            tvQuizAccuracy.setText(R.string.progress_quiz_accuracy_empty)
        }

        val reviewSeconds = qaDao.sumReviewedSecondsByUserId(userId)
        tvReviewTime.text = formatReviewTime(reviewSeconds)

        bindInsights(qaDao, quizItemDao, userId)
        bindReviewSuggest(qaDao, userId)

        val countMath = qaDao.countBySubject(userId, "math")
        val countScience = qaDao.countBySubject(userId, "science")
        val countCode = qaDao.countBySubject(userId, "code")
        val countHistory = qaDao.countBySubject(userId, "history")
        val countLanguage = qaDao.countBySubject(userId, "language")
        val subjectTotal = countMath + countScience + countCode + countHistory + countLanguage
        val barDenom = if (subjectTotal > 0) subjectTotal else 1

        bindSubjectRow(rowMath, tvCountMath, barMath, countMath, barDenom)
        bindSubjectRow(rowScience, tvCountScience, barScience, countScience, barDenom)
        bindSubjectRow(rowCode, tvCountCode, barCode, countCode, barDenom)
        bindSubjectRow(rowHistory, tvCountHistory, barHistory, countHistory, barDenom)
        bindSubjectRow(rowLanguage, tvCountLanguage, barLanguage, countLanguage, barDenom)
        tvSubjectsEmpty.visibility = if (subjectTotal == 0) View.VISIBLE else View.GONE

        setBadge(badgeStarter, totalQuestions >= 1)
        setBadge(badgeWeek, streak >= 7)
        setBadge(badgeMath, countMath >= 1)
        setBadge(badgeScholar, xp >= 1000)
    }

    private fun bindInsights(qaDao: QuestionAnswerDao, quizItemDao: QuizItemDao, userId: Long) {
        tvInsight1.visibility = View.GONE
        tvInsight2.visibility = View.GONE

        var shown = 0
        val topSubject = qaDao.getTopSubjectByUserId(userId)
        if (topSubject != null && qaDao.countBySubject(userId, topSubject) >= 1) {
            tvInsight1.text = getString(
                R.string.progress_insight_top_subject, subjectLabel(topSubject)
            )
            tvInsight1.visibility = View.VISIBLE
            shown++
        }

        val improvedSubject = findImprovedSubject(quizItemDao, userId)
        if (improvedSubject != null) {
            val target = if (shown == 0) tvInsight1 else tvInsight2
            target.text = getString(
                R.string.progress_insight_accuracy_improved, subjectLabel(improvedSubject)
            )
            target.visibility = View.VISIBLE
            shown++
        }

        tvInsightsEmpty.visibility = if (shown == 0) View.VISIBLE else View.GONE
    }

    private fun findImprovedSubject(quizItemDao: QuizItemDao, userId: Long): String? {
        val now = System.currentTimeMillis()
        val recentFrom = now - WINDOW_MS
        val olderFrom = now - (2L * WINDOW_MS)

        var bestSubject: String? = null
        var bestDelta = 0f

        for (subject in SUBJECTS) {
            val recentAttempted = quizItemDao.countAttemptedByUserIdSubjectAndRange(
                userId, subject, recentFrom, now
            )
            val olderAttempted = quizItemDao.countAttemptedByUserIdSubjectAndRange(
                userId, subject, olderFrom, recentFrom
            )
            if (recentAttempted < 2 || olderAttempted < 2) {
                continue
            }
            val recentCorrect = quizItemDao.countCorrectByUserIdSubjectAndRange(
                userId, subject, recentFrom, now
            )
            val olderCorrect = quizItemDao.countCorrectByUserIdSubjectAndRange(
                userId, subject, olderFrom, recentFrom
            )
            val recentAcc = recentCorrect * 1f / recentAttempted
            val olderAcc = olderCorrect * 1f / olderAttempted
            val delta = recentAcc - olderAcc
            if (delta > 0.05f && delta > bestDelta) {
                bestDelta = delta
                bestSubject = subject
            }
        }
        return bestSubject
    }

    private fun bindReviewSuggest(qaDao: QuestionAnswerDao, userId: Long) {
        val topSubject = qaDao.getTopSubjectByUserId(userId)
        if (topSubject == null || qaDao.countBySubject(userId, topSubject) < MIN_PRACTICE_QA) {
            reviewSubject = null
            cardReviewSuggest.visibility = View.GONE
            return
        }
        reviewSubject = topSubject
        tvReviewSubtitle.text = getString(R.string.progress_review_subtitle, subjectLabel(topSubject))
        cardReviewSuggest.visibility = View.VISIBLE
    }

    private fun openPractice(subject: String) {
        val dao = qaDao
        if (context == null || dao == null || currentUserId == -1L) {
            return
        }
        val count = dao.countBySubject(currentUserId, subject)
        if (count < MIN_PRACTICE_QA) {
            Toast.makeText(requireContext(), R.string.quiz_need_more, Toast.LENGTH_LONG).show()
            return
        }
        val intent = Intent(requireContext(), QuizActivity::class.java)
        intent.putExtra(QuizActivity.EXTRA_SUBJECT, subject)
        startActivity(intent)
    }

    private fun formatReviewTime(totalSeconds: Int): String {
        if (totalSeconds < 60) {
            return getString(R.string.progress_review_time_seconds, totalSeconds)
        }
        if (totalSeconds < 3600) {
            return getString(R.string.progress_review_time_minutes, totalSeconds / 60)
        }
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        return getString(R.string.progress_review_time_hours, hours, minutes)
    }

    private fun bindSubjectRow(
        row: LinearLayout,
        tvCount: TextView,
        bar: ProgressBar,
        count: Int,
        denom: Int
    ) {
        if (count <= 0) {
            row.visibility = View.GONE
            return
        }
        row.visibility = View.VISIBLE
        tvCount.text = count.toString()
        bar.progress = ((count * 100f) / denom).roundToInt()
    }

    private fun setBadge(badge: LinearLayout, earned: Boolean) {
        badge.setBackgroundResource(if (earned) R.drawable.bg_badge_earned else R.drawable.bg_badge_locked)
        badge.alpha = if (earned) 1f else 0.5f
    }

    private fun subjectLabel(subject: String?): String {
        return when (subject) {
            "math" -> getString(R.string.subject_math)
            "science" -> getString(R.string.subject_science)
            "code" -> getString(R.string.subject_code)
            "history" -> getString(R.string.subject_history)
            "language" -> getString(R.string.subject_language)
            else -> getString(R.string.subject_other)
        }
    }
}
