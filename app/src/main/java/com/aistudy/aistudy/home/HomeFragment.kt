package com.aistudy.aistudy.home

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
import com.aistudy.aistudy.MainActivity
import com.aistudy.aistudy.R
import com.aistudy.aistudy.answer.AnswerActivity
import com.aistudy.aistudy.auth.LoginActivity
import com.aistudy.aistudy.database.AppDatabase
import com.aistudy.aistudy.database.QuestionAnswerDao
import com.aistudy.aistudy.quiz.QuizActivity
import com.aistudy.aistudy.xp.UnlockCatalog
import com.aistudy.aistudy.xp.XpRules
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.button.MaterialButton

class HomeFragment : Fragment() {

    companion object {
        private const val MIN_PRACTICE_QA = 2
    }

    private lateinit var tvLevelBadge: TextView
    private lateinit var tvStreak: TextView
    private lateinit var tvXpLabel: TextView
    private lateinit var tvXpPercent: TextView
    private lateinit var progressXp: ProgressBar
    private lateinit var tvGreeting: TextView
    private lateinit var btnAsk: MaterialButton
    private lateinit var btnPracticeRecent: MaterialButton

    private lateinit var cardReviewSuggest: LinearLayout
    private lateinit var tvReviewSubtitle: TextView

    private lateinit var sectionRecent: LinearLayout
    private lateinit var tvViewAll: TextView
    private lateinit var cardRecent: LinearLayout
    private lateinit var tvRecentQuestion: TextView
    private lateinit var tvRecentSubject: TextView

    private lateinit var tvCountMath: TextView
    private lateinit var tvCountScience: TextView
    private lateinit var tvCountCode: TextView
    private lateinit var tvCountHistory: TextView
    private lateinit var tvCountLanguage: TextView

    private var latestQaId = -1L
    private var latestSubject: String? = null
    private var reviewSubject: String? = null
    private var currentUserId = -1L
    private var qaDao: QuestionAnswerDao? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_home, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        tvLevelBadge = view.findViewById(R.id.tv_level_badge)
        tvStreak = view.findViewById(R.id.tv_streak)
        tvXpLabel = view.findViewById(R.id.tv_xp_label)
        tvXpPercent = view.findViewById(R.id.tv_xp_percent)
        progressXp = view.findViewById(R.id.progress_xp)
        tvGreeting = view.findViewById(R.id.tv_greeting)
        btnAsk = view.findViewById(R.id.btn_ask)
        btnPracticeRecent = view.findViewById(R.id.btn_practice_recent)

        cardReviewSuggest = view.findViewById(R.id.card_review_suggest)
        tvReviewSubtitle = view.findViewById(R.id.tv_review_subtitle)

        sectionRecent = view.findViewById(R.id.section_recent)
        tvViewAll = view.findViewById(R.id.tv_view_all)
        cardRecent = view.findViewById(R.id.card_recent)
        tvRecentQuestion = view.findViewById(R.id.tv_recent_question)
        tvRecentSubject = view.findViewById(R.id.tv_recent_subject)

        tvCountMath = view.findViewById(R.id.tv_count_math)
        tvCountScience = view.findViewById(R.id.tv_count_science)
        tvCountCode = view.findViewById(R.id.tv_count_code)
        tvCountHistory = view.findViewById(R.id.tv_count_history)
        tvCountLanguage = view.findViewById(R.id.tv_count_language)

        btnAsk.setOnClickListener { selectTab(R.id.nav_ask) }
        btnPracticeRecent.setOnClickListener {
            latestSubject?.let { openPractice(it) }
        }
        cardReviewSuggest.setOnClickListener {
            reviewSubject?.let { openPractice(it) }
        }
        tvViewAll.setOnClickListener { selectTab(R.id.nav_history) }
        cardRecent.setOnClickListener {
            if (latestQaId != -1L) {
                val intent = Intent(requireContext(), AnswerActivity::class.java)
                intent.putExtra(AnswerActivity.EXTRA_QA_ID, latestQaId)
                startActivity(intent)
            }
        }

        view.findViewById<View>(R.id.card_subject_math).setOnClickListener { openPractice("math") }
        view.findViewById<View>(R.id.card_subject_science).setOnClickListener { openPractice("science") }
        view.findViewById<View>(R.id.card_subject_code).setOnClickListener { openPractice("code") }
        view.findViewById<View>(R.id.card_subject_history).setOnClickListener { openPractice("history") }
        view.findViewById<View>(R.id.card_subject_language).setOnClickListener { openPractice("language") }
    }

    override fun onResume() {
        super.onResume()
        loadDashboard()
    }

    private fun loadDashboard() {
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
        val profile = db.profileDao().getByUserId(userId)
        val qaDao = db.questionAnswerDao()
        this.qaDao = qaDao
        currentUserId = userId

        val fullName = user?.fullName ?: ""
        val xp = profile?.xp ?: 0
        val streak = profile?.streak ?: 0
        val level = XpRules.levelFromXp(xp)
        val xpPct = XpRules.progressPercentToNext(xp)
        val nextXp = XpRules.nextLevelThreshold(xp)

        tvLevelBadge.text = level.toString()
        tvStreak.text = getString(R.string.home_streak_days, streak)
        tvXpLabel.text = getString(R.string.home_xp_progress, xp, nextXp)
        tvXpPercent.text = getString(R.string.home_xp_percent, xpPct)
        progressXp.progress = xpPct
        tvGreeting.text = getString(R.string.home_greeting, fullName)

        if (profile != null) {
            activity?.findViewById<View>(R.id.main)?.setBackgroundResource(
                UnlockCatalog.backgroundColorRes(UnlockCatalog.normalizeThemeId(profile.themeId))
            )
        }

        val latest = qaDao.getLatestByUserId(userId)
        if (latest != null) {
            latestQaId = latest.id
            latestSubject = latest.subject
            sectionRecent.visibility = View.VISIBLE
            tvRecentQuestion.text = latest.question
            tvRecentSubject.text = subjectLabel(latest.subject)
            val subjectCount = latest.subject?.let { qaDao.countBySubject(userId, it) } ?: 0
            if (subjectCount >= MIN_PRACTICE_QA) {
                btnPracticeRecent.visibility = View.VISIBLE
                btnPracticeRecent.text = getString(R.string.quiz_practice_on, subjectLabel(latest.subject))
            } else {
                btnPracticeRecent.visibility = View.GONE
            }
        } else {
            latestQaId = -1L
            latestSubject = null
            sectionRecent.visibility = View.GONE
            btnPracticeRecent.visibility = View.GONE
        }

        val topSubject = qaDao.getTopSubjectByUserId(userId)
        if (topSubject != null && qaDao.countBySubject(userId, topSubject) >= MIN_PRACTICE_QA) {
            reviewSubject = topSubject
            tvReviewSubtitle.text = getString(R.string.home_review_subtitle, subjectLabel(topSubject))
            cardReviewSuggest.visibility = View.VISIBLE
        } else {
            reviewSubject = null
            cardReviewSuggest.visibility = View.GONE
        }

        tvCountMath.text = getString(R.string.home_subject_count, qaDao.countBySubject(userId, "math"))
        tvCountScience.text = getString(R.string.home_subject_count, qaDao.countBySubject(userId, "science"))
        tvCountCode.text = getString(R.string.home_subject_count, qaDao.countBySubject(userId, "code"))
        tvCountHistory.text = getString(R.string.home_subject_count, qaDao.countBySubject(userId, "history"))
        tvCountLanguage.text = getString(R.string.home_subject_count, qaDao.countBySubject(userId, "language"))
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

    private fun selectTab(menuItemId: Int) {
        val activity = activity
        if (activity is MainActivity) {
            activity.findViewById<BottomNavigationView>(R.id.bottom_nav)?.selectedItemId = menuItemId
        }
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
