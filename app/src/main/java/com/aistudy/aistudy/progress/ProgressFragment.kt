package com.aistudy.aistudy.progress

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.aistudy.aistudy.R
import com.aistudy.aistudy.auth.LoginActivity
import com.aistudy.aistudy.database.AppDatabase
import kotlin.math.roundToInt

class ProgressFragment : Fragment() {

    companion object {
        private const val XP_MAX = 1200
    }

    private lateinit var tvXpTotal: TextView
    private lateinit var tvLevel: TextView
    private lateinit var tvXpPercent: TextView
    private lateinit var progressXp: ProgressBar

    private lateinit var tvStatStreak: TextView
    private lateinit var tvStatQuestions: TextView
    private lateinit var tvStatStars: TextView

    private lateinit var tvSubjectsEmpty: TextView
    private lateinit var rowMath: LinearLayout
    private lateinit var rowScience: LinearLayout
    private lateinit var rowCode: LinearLayout
    private lateinit var rowHistory: LinearLayout

    private lateinit var tvBarCountMath: TextView
    private lateinit var tvBarCountScience: TextView
    private lateinit var tvBarCountCode: TextView
    private lateinit var tvBarCountHistory: TextView

    private lateinit var barMath: ProgressBar
    private lateinit var barScience: ProgressBar
    private lateinit var barCode: ProgressBar
    private lateinit var barHistory: ProgressBar

    private lateinit var badgeStarter: LinearLayout
    private lateinit var badgeWeek: LinearLayout
    private lateinit var badgeMath: LinearLayout
    private lateinit var badgeScholar: LinearLayout

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_progress, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        tvXpTotal = view.findViewById(R.id.tv_xp_total)
        tvLevel = view.findViewById(R.id.tv_level)
        tvXpPercent = view.findViewById(R.id.tv_xp_percent)
        progressXp = view.findViewById(R.id.progress_xp)

        tvStatStreak = view.findViewById(R.id.tv_stat_streak)
        tvStatQuestions = view.findViewById(R.id.tv_stat_questions)
        tvStatStars = view.findViewById(R.id.tv_stat_stars)

        tvSubjectsEmpty = view.findViewById(R.id.tv_subjects_empty)
        rowMath = view.findViewById(R.id.row_math)
        rowScience = view.findViewById(R.id.row_science)
        rowCode = view.findViewById(R.id.row_code)
        rowHistory = view.findViewById(R.id.row_history)

        tvBarCountMath = view.findViewById(R.id.tv_bar_count_math)
        tvBarCountScience = view.findViewById(R.id.tv_bar_count_science)
        tvBarCountCode = view.findViewById(R.id.tv_bar_count_code)
        tvBarCountHistory = view.findViewById(R.id.tv_bar_count_history)

        barMath = view.findViewById(R.id.bar_math)
        barScience = view.findViewById(R.id.bar_science)
        barCode = view.findViewById(R.id.bar_code)
        barHistory = view.findViewById(R.id.bar_history)

        badgeStarter = view.findViewById(R.id.badge_starter)
        badgeWeek = view.findViewById(R.id.badge_week)
        badgeMath = view.findViewById(R.id.badge_math)
        badgeScholar = view.findViewById(R.id.badge_scholar)
    }

    override fun onResume() {
        super.onResume()
        loadProgress()
    }

    private fun loadProgress() {
        if (context == null) return
        val userId = requireContext()
            .getSharedPreferences(LoginActivity.PREFS_AUTH, Context.MODE_PRIVATE)
            .getLong(LoginActivity.KEY_USER_ID, -1L)
        if (userId == -1L) return

        val db = AppDatabase.getInstance(requireContext())
        val profile = db.profileDao().getByUserId(userId)
        val qaDao = db.questionAnswerDao()

        val xp = profile?.xp ?: 0
        val streak = profile?.streak ?: 0
        val level = maxOf(1, xp / 100)
        val xpPct = minOf(100, ((xp * 100f) / XP_MAX).roundToInt())

        tvXpTotal.text = xp.toString()
        tvLevel.text = getString(R.string.progress_level_scholar, level)
        tvXpPercent.text = getString(R.string.progress_xp_percent, xpPct)
        progressXp.progress = xpPct

        val allQa = qaDao.getAllByUserId(userId)
        val totalQuestions = allQa.size
        val starredCount = allQa.count { it.starred }

        tvStatStreak.text = getString(R.string.progress_streak_value, streak)
        tvStatQuestions.text = totalQuestions.toString()
        tvStatStars.text = starredCount.toString()

        val countMath = qaDao.countBySubject(userId, "math")
        val countScience = qaDao.countBySubject(userId, "science")
        val countCode = qaDao.countBySubject(userId, "code")
        val countHistory = qaDao.countBySubject(userId, "history")

        val subjectTotal = countMath + countScience + countCode + countHistory
        if (subjectTotal == 0) {
            tvSubjectsEmpty.visibility = View.VISIBLE
            rowMath.visibility = View.GONE
            rowScience.visibility = View.GONE
            rowCode.visibility = View.GONE
            rowHistory.visibility = View.GONE
        } else {
            tvSubjectsEmpty.visibility = View.GONE
            val denom = if (subjectTotal > 0) subjectTotal else 1
            bindSubjectRow(rowMath, tvBarCountMath, barMath, countMath, denom)
            bindSubjectRow(rowScience, tvBarCountScience, barScience, countScience, denom)
            bindSubjectRow(rowCode, tvBarCountCode, barCode, countCode, denom)
            bindSubjectRow(rowHistory, tvBarCountHistory, barHistory, countHistory, denom)
        }

        setBadge(badgeStarter, totalQuestions >= 1)
        setBadge(badgeWeek, streak >= 7)
        setBadge(badgeMath, countMath >= 1)
        setBadge(badgeScholar, xp >= 1000)
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
}
