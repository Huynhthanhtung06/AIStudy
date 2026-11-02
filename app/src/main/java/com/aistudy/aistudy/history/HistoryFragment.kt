package com.aistudy.aistudy.history

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.aistudy.aistudy.R
import com.aistudy.aistudy.answer.AnswerActivity
import com.aistudy.aistudy.auth.LoginActivity
import com.aistudy.aistudy.database.AppDatabase
import com.aistudy.aistudy.database.QuestionAnswer
import com.aistudy.aistudy.database.QuestionAnswerDao

class HistoryFragment : Fragment() {

    private lateinit var etSearch: EditText
    private lateinit var chipAll: TextView
    private lateinit var chipMath: TextView
    private lateinit var chipScience: TextView
    private lateinit var chipCode: TextView
    private lateinit var chipHistory: TextView
    private lateinit var chipStarred: TextView
    private lateinit var rvHistory: RecyclerView
    private lateinit var tvEmpty: TextView

    private lateinit var adapter: HistoryAdapter
    private var qaDao: QuestionAnswerDao? = null
    private val allItems = mutableListOf<QuestionAnswer>()

    private var subjectFilter = "all"
    private var starredOnly = false
    private var query = ""

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_history, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        etSearch = view.findViewById(R.id.et_search)
        chipAll = view.findViewById(R.id.chip_all)
        chipMath = view.findViewById(R.id.chip_math)
        chipScience = view.findViewById(R.id.chip_science)
        chipCode = view.findViewById(R.id.chip_code)
        chipHistory = view.findViewById(R.id.chip_history)
        chipStarred = view.findViewById(R.id.chip_starred)
        rvHistory = view.findViewById(R.id.rv_history)
        tvEmpty = view.findViewById(R.id.tv_empty)

        adapter = HistoryAdapter(
            object : HistoryAdapter.Listener {
                override fun onItemClick(qa: QuestionAnswer) {
                    val intent = Intent(requireContext(), AnswerActivity::class.java)
                    intent.putExtra(AnswerActivity.EXTRA_QA_ID, qa.id)
                    startActivity(intent)
                }

                override fun onStarClick(qa: QuestionAnswer) {
                    val dao = qaDao ?: return
                    dao.toggleStar(qa.id)
                    loadHistory()
                }
            },
            ::subjectLabel,
            ::relativeTime
        )
        rvHistory.layoutManager = LinearLayoutManager(requireContext())
        rvHistory.adapter = adapter

        chipAll.setOnClickListener { setFilter("all", false) }
        chipMath.setOnClickListener { setFilter("math", false) }
        chipScience.setOnClickListener { setFilter("science", false) }
        chipCode.setOnClickListener { setFilter("code", false) }
        chipHistory.setOnClickListener { setFilter("history", false) }
        chipStarred.setOnClickListener { setFilter("all", true) }

        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                query = s?.toString()?.trim() ?: ""
                applyFilter()
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    override fun onResume() {
        super.onResume()
        loadHistory()
    }

    private fun loadHistory() {
        if (context == null) return
        val userId = requireContext()
            .getSharedPreferences(LoginActivity.PREFS_AUTH, Context.MODE_PRIVATE)
            .getLong(LoginActivity.KEY_USER_ID, -1L)
        if (userId == -1L) return

        val db = AppDatabase.getInstance(requireContext())
        qaDao = db.questionAnswerDao()
        allItems.clear()
        allItems.addAll(qaDao!!.getAllByUserId(userId))
        applyFilter()
    }

    private fun setFilter(sub: String, starred: Boolean) {
        subjectFilter = sub
        starredOnly = starred
        refreshChipsUi()
        applyFilter()
    }

    private fun refreshChipsUi() {
        val activeBg = R.drawable.bg_history_chip_active
        val inactiveBg = R.drawable.bg_history_chip
        val activeText = ContextCompat.getColor(requireContext(), R.color.auth_primary_foreground)
        val inactiveText = ContextCompat.getColor(requireContext(), R.color.auth_foreground)

        val isAll = subjectFilter == "all" && !starredOnly
        val isMath = subjectFilter == "math" && !starredOnly
        val isScience = subjectFilter == "science" && !starredOnly
        val isCode = subjectFilter == "code" && !starredOnly
        val isHistory = subjectFilter == "history" && !starredOnly
        val isStarred = starredOnly

        setChipStyle(chipAll, isAll, activeBg, inactiveBg, activeText, inactiveText)
        setChipStyle(chipMath, isMath, activeBg, inactiveBg, activeText, inactiveText)
        setChipStyle(chipScience, isScience, activeBg, inactiveBg, activeText, inactiveText)
        setChipStyle(chipCode, isCode, activeBg, inactiveBg, activeText, inactiveText)
        setChipStyle(chipHistory, isHistory, activeBg, inactiveBg, activeText, inactiveText)
        setChipStyle(chipStarred, isStarred, activeBg, inactiveBg, activeText, inactiveText)
    }

    private fun setChipStyle(
        view: TextView,
        active: Boolean,
        activeBg: Int,
        inactiveBg: Int,
        activeText: Int,
        inactiveText: Int
    ) {
        view.setBackgroundResource(if (active) activeBg else inactiveBg)
        view.setTextColor(if (active) activeText else inactiveText)
    }

    private fun applyFilter() {
        val filtered = mutableListOf<QuestionAnswer>()
        for (qa in allItems) {
            if (subjectFilter != "all" && subjectFilter != qa.subject) {
                continue
            }
            if (starredOnly && !qa.starred) {
                continue
            }
            if (query.isNotEmpty() && qa.question?.lowercase()?.contains(query.lowercase()) != true) {
                continue
            }
            filtered.add(qa)
        }
        adapter.submitList(filtered)
        val empty = filtered.isEmpty()
        tvEmpty.visibility = if (empty) View.VISIBLE else View.GONE
        rvHistory.visibility = if (empty) View.GONE else View.VISIBLE
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

    private fun relativeTime(createdAt: Long): String {
        val diff = System.currentTimeMillis() - createdAt
        val hours = diff / 3_600_000L
        if (hours < 1) {
            return getString(R.string.history_just_now)
        }
        if (hours < 24) {
            return getString(R.string.history_hours_ago, hours.toInt())
        }
        return getString(R.string.history_days_ago, (hours / 24).toInt())
    }
}
