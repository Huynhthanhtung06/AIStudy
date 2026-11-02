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
import androidx.fragment.app.Fragment
import com.aistudy.aistudy.R
import com.aistudy.aistudy.auth.LoginActivity
import com.aistudy.aistudy.database.AppDatabase
import kotlin.math.roundToInt

class ProgressFragment : Fragment() {

    companion object {
        private const val XP_MAX = 1200
    }

    private lateinit var tvUserEmail: TextView
    private lateinit var btnLogout: TextView

    private lateinit var tvLevelScholar: TextView
    private lateinit var tvXpTotal: TextView
    private lateinit var progressXp: ProgressBar
    private lateinit var tvXpPercent: TextView

    private lateinit var tvStatStreak: TextView
    private lateinit var tvStatQuestions: TextView
    private lateinit var tvStatStars: TextView

    private lateinit var tvSubjectsEmpty: TextView
    private lateinit var rowMath: LinearLayout
    private lateinit var tvCountMath: TextView
    private lateinit var barMath: ProgressBar
    private lateinit var rowScience: LinearLayout
    private lateinit var tvCountScience: TextView
    private lateinit var barScience: ProgressBar
    private lateinit var rowCode: LinearLayout
    private lateinit var tvCountCode: TextView
    private lateinit var barCode: ProgressBar
    private lateinit var rowHistory: LinearLayout
    private lateinit var tvCountHistory: TextView
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
        tvUserEmail = view.findViewById(R.id.tv_user_email)
        btnLogout = view.findViewById(R.id.btn_logout)

        tvLevelScholar = view.findViewById(R.id.tv_level_scholar)
        tvXpTotal = view.findViewById(R.id.tv_xp_total)
        progressXp = view.findViewById(R.id.progress_xp)
        tvXpPercent = view.findViewById(R.id.tv_xp_percent)

        tvStatStreak = view.findViewById(R.id.tv_stat_streak)
        tvStatQuestions = view.findViewById(R.id.tv_stat_questions)
        tvStatStars = view.findViewById(R.id.tv_stat_stars)

        tvSubjectsEmpty = view.findViewById(R.id.tv_subjects_empty)
        rowMath = view.findViewById(R.id.row_math)
        tvCountMath = view.findViewById(R.id.tv_count_math)
        barMath = view.findViewById(R.id.bar_math)

        rowScience = view.findViewById(R.id.row_science)
        tvCountScience = view.findViewById(R.id.tv_count_science)
        barScience = view.findViewById(R.id.bar_science)

        rowCode = view.findViewById(R.id.row_code)
        tvCountCode = view.findViewById(R.id.tv_count_code)
        barCode = view.findViewById(R.id.bar_code)

        rowHistory = view.findViewById(R.id.row_history)
        tvCountHistory = view.findViewById(R.id.tv_count_history)
        barHistory = view.findViewById(R.id.bar_history)

        badgeStarter = view.findViewById(R.id.badge_starter)
        badgeWeek = view.findViewById(R.id.badge_week)
        badgeMath = view.findViewById(R.id.badge_math)
        badgeScholar = view.findViewById(R.id.badge_scholar)

        btnLogout.setOnClickListener { logout() }
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
        if (context == null) return
        val userId = requireContext()
            .getSharedPreferences(LoginActivity.PREFS_AUTH, Context.MODE_PRIVATE)
            .getLong(LoginActivity.KEY_USER_ID, -1L)
        if (userId == -1L) return

        val db = AppDatabase.getInstance(requireContext())
        val user = db.userDao().getById(userId)
        val profile = db.profileDao().getByUserId(userId)
        val qaDao = db.questionAnswerDao()

        tvUserEmail.text = user?.email ?: ""

        val xp = profile?.xp ?: 0
        val streak = profile?.streak ?: 0
        val level = maxOf(1, xp / 100)
        val xpPct = minOf(100, ((xp * 100f) / XP_MAX).roundToInt())

        tvLevelScholar.text = getString(R.string.progress_level_scholar, level)
        tvXpTotal.text = xp.toString()
        progressXp.progress = xpPct
        tvXpPercent.text = getString(R.string.progress_xp_percent, xpPct)

        tvStatStreak.text = getString(R.string.progress_streak_value, streak)

        val allQa = qaDao.getAllByUserId(userId)
        val totalQuestions = allQa.size
        var starsCount = 0
        for (qa in allQa) {
            if (qa.starred) starsCount++
        }

        tvStatQuestions.text = totalQuestions.toString()
        tvStatStars.text = starsCount.toString()

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
            val denom = subjectTotal
            bindSubjectRow(rowMath, tvCountMath, barMath, countMath, denom)
            bindSubjectRow(rowScience, tvCountScience, barScience, countScience, denom)
            bindSubjectRow(rowCode, tvCountCode, barCode, countCode, denom)
            bindSubjectRow(rowHistory, tvCountHistory, barHistory, countHistory, denom)
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
