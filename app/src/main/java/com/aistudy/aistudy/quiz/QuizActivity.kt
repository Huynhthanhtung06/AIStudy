package com.aistudy.aistudy.quiz

import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.aistudy.aistudy.BuildConfig
import com.aistudy.aistudy.R
import com.aistudy.aistudy.ask.GeminiClient
import com.aistudy.aistudy.auth.LoginActivity
import com.aistudy.aistudy.database.AppDatabase
import com.aistudy.aistudy.database.ProfileDao
import com.aistudy.aistudy.database.QuestionAnswer
import com.aistudy.aistudy.database.QuestionAnswerDao
import com.aistudy.aistudy.database.QuizItem
import com.aistudy.aistudy.database.QuizItemDao
import com.aistudy.aistudy.database.QuizSession
import com.aistudy.aistudy.database.QuizSessionDao
import com.google.android.material.button.MaterialButton
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class QuizActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_SUBJECT = "subject"

        private const val MIN_SOURCE_QA = 2
        private const val RECENT_LIMIT = 5
        private const val TARGET_ITEMS = 4
        private const val XP_PER_CORRECT = 10
    }

    private lateinit var qaDao: QuestionAnswerDao
    private lateinit var sessionDao: QuizSessionDao
    private lateinit var itemDao: QuizItemDao
    private lateinit var profileDao: ProfileDao
    private lateinit var executor: ExecutorService

    private var userId: Long = -1
    private lateinit var subject: String
    private lateinit var session: QuizSession
    private val items = mutableListOf<QuizItem>()
    private var currentIndex = 0
    private var answered = false

    private lateinit var layoutLoading: LinearLayout
    private lateinit var layoutQuiz: LinearLayout
    private lateinit var layoutResult: LinearLayout
    private lateinit var tvQuizTitle: TextView
    private lateinit var tvProgress: TextView
    private lateinit var tvItemType: TextView
    private lateinit var tvPrompt: TextView
    private lateinit var groupOptions: RadioGroup
    private lateinit var etAnswer: EditText
    private lateinit var layoutFeedback: LinearLayout
    private lateinit var tvFeedbackResult: TextView
    private lateinit var tvFeedbackCorrect: TextView
    private lateinit var tvExplanation: TextView
    private lateinit var btnPrimary: MaterialButton
    private lateinit var tvResultScore: TextView
    private lateinit var tvResultXp: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_quiz)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val subjectExtra = intent.getStringExtra(EXTRA_SUBJECT)
        if (subjectExtra.isNullOrEmpty()) {
            Toast.makeText(this, R.string.quiz_error, Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        subject = subjectExtra

        userId = getSharedPreferences(LoginActivity.PREFS_AUTH, MODE_PRIVATE)
            .getLong(LoginActivity.KEY_USER_ID, -1)
        if (userId == -1L) {
            finish()
            return
        }

        val db = AppDatabase.getInstance(this)
        qaDao = db.questionAnswerDao()
        sessionDao = db.quizSessionDao()
        itemDao = db.quizItemDao()
        profileDao = db.profileDao()
        executor = Executors.newSingleThreadExecutor()

        layoutLoading = findViewById(R.id.layout_loading)
        layoutQuiz = findViewById(R.id.layout_quiz)
        layoutResult = findViewById(R.id.layout_result)
        tvQuizTitle = findViewById(R.id.tv_quiz_title)
        tvProgress = findViewById(R.id.tv_progress)
        tvItemType = findViewById(R.id.tv_item_type)
        tvPrompt = findViewById(R.id.tv_prompt)
        groupOptions = findViewById(R.id.group_options)
        etAnswer = findViewById(R.id.et_answer)
        layoutFeedback = findViewById(R.id.layout_feedback)
        tvFeedbackResult = findViewById(R.id.tv_feedback_result)
        tvFeedbackCorrect = findViewById(R.id.tv_feedback_correct)
        tvExplanation = findViewById(R.id.tv_explanation)
        btnPrimary = findViewById(R.id.btn_primary)
        tvResultScore = findViewById(R.id.tv_result_score)
        tvResultXp = findViewById(R.id.tv_result_xp)
        val btnBack = findViewById<ImageButton>(R.id.btn_back)
        val btnDone = findViewById<MaterialButton>(R.id.btn_done)

        tvQuizTitle.text = getString(R.string.quiz_practice_on, subjectLabel(subject))
        btnBack.setOnClickListener { finish() }
        btnDone.setOnClickListener { finish() }
        btnPrimary.setOnClickListener { onPrimaryClick() }

        startGenerate()
    }

    private fun startGenerate() {
        val sources = qaDao.getRecentByUserIdAndSubject(userId, subject, RECENT_LIMIT)
        if (sources.size < MIN_SOURCE_QA) {
            Toast.makeText(this, R.string.quiz_need_more, Toast.LENGTH_LONG).show()
            finish()
            return
        }

        if (BuildConfig.GEMINI_API_KEY.isEmpty() || BuildConfig.GEMINI_API_KEY == "YOUR_KEY_HERE") {
            Toast.makeText(this, R.string.quiz_no_api_key, Toast.LENGTH_LONG).show()
            finish()
            return
        }

        showLoading()
        val prompt = buildQuizPrompt(sources)
        val sourceIds = JSONArray()
        for (qa in sources) {
            sourceIds.put(qa.id)
        }

        executor.execute {
            try {
                val raw = GeminiClient.ask(BuildConfig.GEMINI_API_KEY, prompt)
                val jsonText = extractJson(raw)
                val root = JSONObject(jsonText)
                val arr = root.getJSONArray("items")
                if (arr.length() == 0) {
                    throw Exception("empty items")
                }

                val newSession = QuizSession()
                newSession.userId = userId
                newSession.subject = subject
                newSession.sourceQaIds = sourceIds.toString()
                newSession.createdAt = System.currentTimeMillis()
                newSession.score = 0
                newSession.total = 0
                newSession.completed = false
                val sessionId = sessionDao.insert(newSession)
                newSession.id = sessionId

                val generated = mutableListOf<QuizItem>()
                val limit = minOf(arr.length(), TARGET_ITEMS)
                for (i in 0 until limit) {
                    val obj = arr.getJSONObject(i)
                    val item = QuizItem()
                    item.sessionId = sessionId
                    item.type = normalizeType(obj.optString("type", "mcq"))
                    item.prompt = obj.optString("prompt", "").trim()
                    item.correctAnswer = obj.optString("correctAnswer", "").trim()
                    item.explanation = obj.optString("explanation", "").trim()
                    val options = obj.optJSONArray("options")
                    item.optionsJson = options?.toString() ?: "[]"
                    item.userAnswer = null
                    item.correct = null
                    if (item.prompt.isNullOrEmpty() || item.correctAnswer.isNullOrEmpty()) {
                        continue
                    }
                    item.id = itemDao.insert(item)
                    generated.add(item)
                }

                if (generated.isEmpty()) {
                    throw Exception("no valid items")
                }

                newSession.total = generated.size
                sessionDao.update(newSession)

                runOnUiThread {
                    session = newSession
                    items.clear()
                    items.addAll(generated)
                    currentIndex = 0
                    answered = false
                    showQuiz()
                    bindCurrentItem()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this, R.string.quiz_error, Toast.LENGTH_LONG).show()
                    finish()
                }
            }
        }
    }

    private fun onPrimaryClick() {
        if (!answered) {
            checkAnswer()
        } else if (currentIndex < items.size - 1) {
            currentIndex++
            answered = false
            bindCurrentItem()
        } else {
            finishQuiz()
        }
    }

    private fun checkAnswer() {
        if (items.isEmpty() || currentIndex >= items.size) {
            return
        }
        val item = items[currentIndex]
        val userAnswer: String
        if (item.type == "mcq") {
            val checkedId = groupOptions.checkedRadioButtonId
            if (checkedId == -1) {
                Toast.makeText(this, R.string.quiz_select_option, Toast.LENGTH_SHORT).show()
                return
            }
            val selected = findViewById<RadioButton>(checkedId)
            userAnswer = selected.text.toString().trim()
        } else {
            userAnswer = etAnswer.text?.toString()?.trim() ?: ""
            if (userAnswer.isEmpty()) {
                Toast.makeText(this, R.string.quiz_enter_answer, Toast.LENGTH_SHORT).show()
                return
            }
        }

        val correct = normalizeAnswer(userAnswer) == normalizeAnswer(item.correctAnswer)
        item.userAnswer = userAnswer
        item.correct = correct
        itemDao.update(item)

        answered = true
        layoutFeedback.visibility = View.VISIBLE
        if (correct) {
            tvFeedbackResult.setText(R.string.quiz_correct)
            tvFeedbackResult.setTextColor(ContextCompat.getColor(this, R.color.auth_brand))
            tvFeedbackCorrect.visibility = View.GONE
        } else {
            tvFeedbackResult.setText(R.string.quiz_incorrect)
            tvFeedbackResult.setTextColor(ContextCompat.getColor(this, R.color.streak))
            tvFeedbackCorrect.visibility = View.VISIBLE
            tvFeedbackCorrect.text = getString(R.string.quiz_correct_answer_label, item.correctAnswer)
        }
        tvExplanation.text = if (item.explanation.isNullOrEmpty()) "—" else item.explanation

        setInputsEnabled(false)
        if (currentIndex < items.size - 1) {
            btnPrimary.setText(R.string.quiz_next)
        } else {
            btnPrimary.setText(R.string.quiz_finish)
        }
    }

    private fun finishQuiz() {
        val score = items.count { it.correct == true }
        val xpGain = score * XP_PER_CORRECT
        session.score = score
        session.total = items.size
        session.completed = true
        sessionDao.update(session)

        val profile = profileDao.getByUserId(userId)
        if (profile != null && xpGain > 0) {
            profile.xp += xpGain
            profileDao.update(profile)
        }

        layoutQuiz.visibility = View.GONE
        layoutLoading.visibility = View.GONE
        layoutResult.visibility = View.VISIBLE
        tvResultScore.text = getString(R.string.quiz_result_score, score, items.size)
        tvResultXp.text = getString(R.string.quiz_result_xp, xpGain)
    }

    private fun bindCurrentItem() {
        val item = items[currentIndex]
        tvProgress.text = getString(R.string.quiz_progress, currentIndex + 1, items.size)
        tvItemType.text = typeLabel(item.type)
        tvPrompt.text = item.prompt
        layoutFeedback.visibility = View.GONE
        btnPrimary.setText(R.string.quiz_check)
        setInputsEnabled(true)

        groupOptions.removeAllViews()
        groupOptions.clearCheck()
        etAnswer.setText("")

        if (item.type == "mcq") {
            groupOptions.visibility = View.VISIBLE
            etAnswer.visibility = View.GONE
            try {
                val options = JSONArray(item.optionsJson ?: "[]")
                for (i in 0 until options.length()) {
                    val option = options.getString(i)
                    val rb = RadioButton(this)
                    rb.id = View.generateViewId()
                    rb.text = option
                    rb.setTextColor(ContextCompat.getColor(this, R.color.auth_foreground))
                    rb.setPadding(8, 16, 8, 16)
                    groupOptions.addView(rb)
                }
            } catch (ignored: Exception) {
            }
        } else {
            groupOptions.visibility = View.GONE
            etAnswer.visibility = View.VISIBLE
        }
    }

    private fun setInputsEnabled(enabled: Boolean) {
        for (i in 0 until groupOptions.childCount) {
            groupOptions.getChildAt(i).isEnabled = enabled
        }
        etAnswer.isEnabled = enabled
    }

    private fun showLoading() {
        layoutLoading.visibility = View.VISIBLE
        layoutQuiz.visibility = View.GONE
        layoutResult.visibility = View.GONE
    }

    private fun showQuiz() {
        layoutLoading.visibility = View.GONE
        layoutQuiz.visibility = View.VISIBLE
        layoutResult.visibility = View.GONE
    }

    private fun buildQuizPrompt(sources: List<QuestionAnswer>): String {
        val sb = StringBuilder()
        sb.append("You are AI Study Mentor. Create a practice quiz from the student's recent questions.\n")
            .append("Return ONLY valid JSON (no markdown):\n")
            .append("{\n")
            .append("  \"items\": [\n")
            .append("    {\n")
            .append("      \"type\": \"mcq\",\n")
            .append("      \"prompt\": \"question text\",\n")
            .append("      \"options\": [\"A\", \"B\", \"C\", \"D\"],\n")
            .append("      \"correctAnswer\": \"exact matching option text\",\n")
            .append("      \"explanation\": \"why this is correct\"\n")
            .append("    },\n")
            .append("    {\n")
            .append("      \"type\": \"short\",\n")
            .append("      \"prompt\": \"question text\",\n")
            .append("      \"options\": [],\n")
            .append("      \"correctAnswer\": \"expected short answer\",\n")
            .append("      \"explanation\": \"brief explanation\"\n")
            .append("    },\n")
            .append("    {\n")
            .append("      \"type\": \"blank\",\n")
            .append("      \"prompt\": \"Fill in the blank: ...\",\n")
            .append("      \"options\": [],\n")
            .append("      \"correctAnswer\": \"word or phrase\",\n")
            .append("      \"explanation\": \"brief explanation\"\n")
            .append("    }\n")
            .append("  ]\n")
            .append("}\n\n")
            .append("Rules:\n")
            .append("- Create exactly ").append(TARGET_ITEMS).append(" items\n")
            .append("- Mix types: include at least one mcq, one short, one blank if possible\n")
            .append("- For mcq: options must have exactly 4 distinct choices; correctAnswer must equal one option exactly\n")
            .append("- Subject: ").append(subject).append("\n")
            .append("- Questions must be based on the source material below (similar concepts, not identical copies)\n")
            .append("- English only\n\n")
            .append("Source questions:\n")
        for ((i, qa) in sources.withIndex()) {
            sb.append(i + 1).append(". Q: ").append(qa.question)
                .append("\n   Final: ").append(qa.finalAnswer)
                .append("\n   Difficulty: ").append(qa.difficulty ?: "medium")
                .append("\n")
        }
        return sb.toString()
    }

    private fun normalizeType(type: String): String {
        return when (type) {
            "short", "blank", "mcq" -> type
            else -> "mcq"
        }
    }

    private fun normalizeAnswer(value: String?): String {
        if (value == null) {
            return ""
        }
        return value.trim().replace(Regex("\\s+"), " ").lowercase()
    }

    private fun typeLabel(type: String?): String {
        return when (type) {
            "short" -> getString(R.string.quiz_type_short)
            "blank" -> getString(R.string.quiz_type_blank)
            else -> getString(R.string.quiz_type_mcq)
        }
    }

    private fun subjectLabel(value: String?): String {
        return when (value) {
            "math" -> getString(R.string.subject_math)
            "science" -> getString(R.string.subject_science)
            "code" -> getString(R.string.subject_code)
            "history" -> getString(R.string.subject_history)
            "language" -> getString(R.string.subject_language)
            else -> getString(R.string.subject_other)
        }
    }

    private fun extractJson(raw: String): String {
        val text = raw.trim()
        if (text.startsWith("```")) {
            val start = text.indexOf('{')
            val end = text.lastIndexOf('}')
            if (start >= 0 && end > start) {
                return text.substring(start, end + 1)
            }
        }
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        if (start >= 0 && end > start) {
            return text.substring(start, end + 1)
        }
        return text
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::executor.isInitialized) {
            executor.shutdown()
        }
    }
}
