package com.aistudy.aistudy.answer

import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Typeface
import android.os.Bundle
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
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
import com.aistudy.aistudy.database.AppDatabase
import com.aistudy.aistudy.database.ProfileDao
import com.aistudy.aistudy.database.QuestionAnswer
import com.aistudy.aistudy.database.QuestionAnswerDao
import com.aistudy.aistudy.quiz.QuizActivity
import com.aistudy.aistudy.xp.XpRules
import com.google.android.material.button.MaterialButton
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Calendar
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class AnswerActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_QA_ID = "qa_id"

        private const val KEY_SIMPLIFY = "simplify"
        private const val KEY_ALTERNATIVE = "alternative"
        private const val KEY_CONCEPTS = "concepts"
        private const val KEY_MISTAKES = "mistakes"
        private const val KEY_FOLLOWUPS = "followUps"

        private const val MAX_SESSION_SECONDS = 1800

        private fun isSameDay(a: Long, b: Long): Boolean {
            val ca = Calendar.getInstance()
            ca.timeInMillis = a
            val cb = Calendar.getInstance()
            cb.timeInMillis = b
            return ca.get(Calendar.YEAR) == cb.get(Calendar.YEAR) &&
                ca.get(Calendar.DAY_OF_YEAR) == cb.get(Calendar.DAY_OF_YEAR)
        }
    }

    private var qaDao: QuestionAnswerDao? = null
    private lateinit var profileDao: ProfileDao
    private var qa: QuestionAnswer? = null
    private var executor: ExecutorService? = null

    private lateinit var tvDifficulty: TextView
    private lateinit var btnSimplify: TextView
    private lateinit var btnAlternative: TextView
    private lateinit var btnConcepts: TextView
    private lateinit var btnMistakes: TextView
    private lateinit var btnFollowups: TextView
    private lateinit var tvExtras: TextView
    private lateinit var progressExtras: ProgressBar
    private lateinit var btnMarkReviewed: MaterialButton

    private var activeExtraKey: String? = null
    private var extrasLoading = false
    private var reviewSessionStartMs: Long = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_answer)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val qaId = intent.getLongExtra(EXTRA_QA_ID, -1)
        if (qaId == -1L) {
            Toast.makeText(this, R.string.answer_not_found, Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val db = AppDatabase.getInstance(this)
        val dao = db.questionAnswerDao()
        qaDao = dao
        profileDao = db.profileDao()
        val item = dao.getById(qaId)
        qa = item
        if (item == null) {
            Toast.makeText(this, R.string.answer_not_found, Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        executor = Executors.newSingleThreadExecutor()

        val btnBack = findViewById<ImageButton>(R.id.btn_back)
        val tvSubject = findViewById<TextView>(R.id.tv_subject)
        val tvQuestion = findViewById<TextView>(R.id.tv_question)
        val layoutSteps = findViewById<LinearLayout>(R.id.layout_steps)
        val tvFinalAnswer = findViewById<TextView>(R.id.tv_final_answer)
        tvDifficulty = findViewById(R.id.tv_difficulty)
        btnSimplify = findViewById(R.id.btn_simplify)
        btnAlternative = findViewById(R.id.btn_alternative)
        btnConcepts = findViewById(R.id.btn_concepts)
        btnMistakes = findViewById(R.id.btn_mistakes)
        btnFollowups = findViewById(R.id.btn_followups)
        tvExtras = findViewById(R.id.tv_extras)
        progressExtras = findViewById(R.id.progress_extras)
        btnMarkReviewed = findViewById(R.id.btn_mark_reviewed)
        val btnPractice = findViewById<MaterialButton>(R.id.btn_practice)

        btnBack.setOnClickListener { finish() }
        tvSubject.text = subjectLabel(item.subject)
        tvQuestion.text = item.question
        tvFinalAnswer.text = item.finalAnswer
        tvDifficulty.text = difficultyLabel(item.difficulty)
        btnPractice.text = getString(R.string.quiz_practice_on, subjectLabel(item.subject))
        btnPractice.setOnClickListener {
            val intent = Intent(this, QuizActivity::class.java)
            intent.putExtra(QuizActivity.EXTRA_SUBJECT, item.subject)
            startActivity(intent)
        }

        val layoutAnswerImage = findViewById<LinearLayout>(R.id.layout_answer_image)
        val imgAnswer = findViewById<ImageView>(R.id.img_answer)
        val imagePath = item.imagePath
        if (!imagePath.isNullOrEmpty()) {
            val imageFile = File(imagePath)
            if (imageFile.exists()) {
                imgAnswer.setImageBitmap(BitmapFactory.decodeFile(imageFile.absolutePath))
                layoutAnswerImage.visibility = View.VISIBLE
            }
        }

        try {
            val steps = JSONArray(item.steps)
            for (i in 0 until steps.length()) {
                val step = steps.getJSONObject(i)
                addStepView(layoutSteps, i + 1, step)
            }
        } catch (e: Exception) {
            val error = TextView(this)
            error.text = item.steps
            error.setTextColor(getColor(R.color.auth_foreground))
            layoutSteps.addView(error)
        }

        btnSimplify.setOnClickListener { onExtraAction(KEY_SIMPLIFY) }
        btnAlternative.setOnClickListener { onExtraAction(KEY_ALTERNATIVE) }
        btnConcepts.setOnClickListener { onExtraAction(KEY_CONCEPTS) }
        btnMistakes.setOnClickListener { onExtraAction(KEY_MISTAKES) }
        btnFollowups.setOnClickListener { onExtraAction(KEY_FOLLOWUPS) }
        btnMarkReviewed.setOnClickListener { markReviewed() }

        refreshReviewButton()
        maybeAutoReview()
        reviewSessionStartMs = System.currentTimeMillis()
    }

    override fun onResume() {
        super.onResume()
        reviewSessionStartMs = System.currentTimeMillis()
    }

    override fun onPause() {
        flushReviewSeconds()
        super.onPause()
    }

    private fun flushReviewSeconds() {
        val item = qa
        val dao = qaDao
        if (item == null || dao == null || reviewSessionStartMs <= 0) {
            return
        }
        val elapsedMs = System.currentTimeMillis() - reviewSessionStartMs
        reviewSessionStartMs = 0
        var delta = (elapsedMs / 1000L).toInt()
        if (delta <= 0) {
            return
        }
        if (delta > MAX_SESSION_SECONDS) {
            delta = MAX_SESSION_SECONDS
        }
        item.reviewedSeconds += delta
        dao.addReviewedSeconds(item.id, delta)
    }

    private fun maybeAutoReview() {
        val item = qa ?: return
        val now = System.currentTimeMillis()
        if (item.reviewedAt != 0L && isSameDay(item.reviewedAt, now)) {
            return
        }
        markReviewed()
    }

    private fun markReviewed() {
        val item = qa ?: return
        val now = System.currentTimeMillis()
        if (item.reviewedAt != 0L && isSameDay(item.reviewedAt, now)) {
            refreshReviewButton()
            return
        }

        val firstReview = item.reviewedAt == 0L || !isSameDay(item.reviewedAt, now)
        item.reviewedAt = now
        qaDao?.updateReviewedAt(item.id, now)

        if (firstReview) {
            val profile = profileDao.getByUserId(item.userId)
            if (profile != null) {
                XpRules.addXp(profile, profileDao, XpRules.REVIEW)
                Toast.makeText(this, R.string.answer_review_xp_toast, Toast.LENGTH_SHORT).show()
            }
        }
        refreshReviewButton()
    }

    private fun refreshReviewButton() {
        val item = qa ?: return
        val now = System.currentTimeMillis()
        val doneToday = item.reviewedAt != 0L && isSameDay(item.reviewedAt, now)
        btnMarkReviewed.isEnabled = !doneToday
        btnMarkReviewed.setText(if (doneToday) R.string.answer_reviewed else R.string.answer_mark_reviewed)
    }

    private fun onExtraAction(key: String) {
        if (extrasLoading) {
            return
        }
        activeExtraKey = key
        styleExtraButtons()
        val cached = readExtraField(key)
        if (!cached.isNullOrEmpty()) {
            tvExtras.text = cached
            return
        }
        loadExtras(key)
    }

    private fun loadExtras(focusKey: String) {
        if (TextUtils.isEmpty(BuildConfig.GEMINI_API_KEY) ||
            "YOUR_KEY_HERE" == BuildConfig.GEMINI_API_KEY
        ) {
            Toast.makeText(this, R.string.ask_no_api_key, Toast.LENGTH_LONG).show()
            return
        }
        val item = qa ?: return

        extrasLoading = true
        progressExtras.visibility = View.VISIBLE
        tvExtras.setText(R.string.answer_extras_loading)

        val prompt = buildExtrasPrompt(item)
        executor?.execute {
            try {
                val raw = GeminiClient.ask(BuildConfig.GEMINI_API_KEY, prompt)
                val jsonText = extractJson(raw)
                val extras = JSONObject(jsonText)
                val payload = extras.toString()
                item.extrasJson = payload
                qaDao?.updateExtras(item.id, payload)

                runOnUiThread {
                    extrasLoading = false
                    progressExtras.visibility = View.GONE
                    val text = formatExtra(extras, focusKey)
                    if (text.isNullOrEmpty()) {
                        tvExtras.setText(R.string.answer_extras_error)
                    } else {
                        tvExtras.text = text
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    extrasLoading = false
                    progressExtras.visibility = View.GONE
                    tvExtras.setText(R.string.answer_extras_error)
                    Toast.makeText(this, R.string.answer_extras_error, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun readExtraField(key: String): String? {
        val extrasJson = qa?.extrasJson
        if (extrasJson.isNullOrEmpty()) {
            return null
        }
        return try {
            formatExtra(JSONObject(extrasJson), key)
        } catch (e: Exception) {
            null
        }
    }

    private fun formatExtra(extras: JSONObject, key: String): String? {
        return try {
            val value = extras.opt(key)
            if (value == null || JSONObject.NULL == value) {
                return null
            }
            if (value is JSONArray) {
                val sb = StringBuilder()
                for (i in 0 until value.length()) {
                    if (i > 0) {
                        sb.append("\n\n")
                    }
                    sb.append("• ").append(value.getString(i))
                }
                return sb.toString()
            }
            value.toString()
        } catch (e: Exception) {
            null
        }
    }

    private fun buildExtrasPrompt(item: QuestionAnswer): String {
        return "You are AI Study Mentor. Given this solved question, return ONLY valid JSON " +
            "(no markdown) with learning support fields:\n" +
            "{\n" +
            "  \"simplify\": \"simpler explanation in English\",\n" +
            "  \"alternative\": \"another way to solve in English\",\n" +
            "  \"concepts\": [\"key concept 1\", \"key concept 2\"],\n" +
            "  \"mistakes\": [\"common mistake 1\", \"common mistake 2\"],\n" +
            "  \"followUps\": [\"follow-up question 1\", \"follow-up question 2\"]\n" +
            "}\n\n" +
            "Subject: ${item.subject}\n" +
            "Difficulty: ${item.difficulty ?: "medium"}\n" +
            "Question: ${item.question}\n" +
            "Final answer: ${item.finalAnswer}\n" +
            "Steps JSON: ${item.steps}"
    }

    private fun styleExtraButtons() {
        styleAction(btnSimplify, KEY_SIMPLIFY == activeExtraKey)
        styleAction(btnAlternative, KEY_ALTERNATIVE == activeExtraKey)
        styleAction(btnConcepts, KEY_CONCEPTS == activeExtraKey)
        styleAction(btnMistakes, KEY_MISTAKES == activeExtraKey)
        styleAction(btnFollowups, KEY_FOLLOWUPS == activeExtraKey)
    }

    private fun styleAction(btn: TextView, active: Boolean) {
        if (active) {
            btn.setBackgroundResource(R.drawable.bg_answer_action_active)
            btn.setTextColor(ContextCompat.getColor(this, R.color.auth_primary_foreground))
        } else {
            btn.setBackgroundResource(R.drawable.bg_answer_action)
            btn.setTextColor(ContextCompat.getColor(this, R.color.auth_foreground))
        }
    }

    private fun addStepView(parent: LinearLayout, number: Int, step: JSONObject) {
        val density = resources.displayMetrics.density
        val stepNumber = TextView(this)
        stepNumber.text = number.toString()
        stepNumber.gravity = Gravity.CENTER
        stepNumber.setTextColor(getColor(R.color.auth_primary_foreground))
        stepNumber.setBackgroundColor(getColor(R.color.auth_brand))
        val size = (24 * density).toInt()
        val numberParams = LinearLayout.LayoutParams(size, size)
        numberParams.setMargins(0, 0, (12 * density).toInt(), 0)
        stepNumber.layoutParams = numberParams

        val content = LinearLayout(this)
        content.orientation = LinearLayout.VERTICAL
        val contentParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        contentParams.bottomMargin = (16 * density).toInt()
        content.layoutParams = contentParams

        val title = step.optString("title", "")
        if (title.isNotEmpty()) {
            val tvTitle = TextView(this)
            tvTitle.text = title
            tvTitle.setTextColor(getColor(R.color.auth_foreground))
            tvTitle.textSize = 14f
            tvTitle.setTypeface(null, Typeface.BOLD)
            content.addView(tvTitle)
        }

        val body = step.optString("body", "")
        val tvBody = TextView(this)
        tvBody.text = body
        tvBody.setTextColor(getColor(R.color.auth_foreground))
        tvBody.textSize = 14f
        content.addView(tvBody)

        val expr = step.optString("expr", "")
        if (expr.isNotEmpty()) {
            val tvExpr = TextView(this)
            tvExpr.text = expr
            tvExpr.setTextColor(getColor(R.color.auth_foreground))
            tvExpr.textSize = 14f
            tvExpr.setTypeface(Typeface.MONOSPACE, Typeface.NORMAL)
            tvExpr.setPadding(12, 8, 12, 8)
            tvExpr.setBackgroundColor(getColor(R.color.auth_surface))
            val exprParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            exprParams.topMargin = (8 * density).toInt()
            tvExpr.layoutParams = exprParams
            content.addView(tvExpr)
        }

        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.addView(stepNumber)
        row.addView(content)
        parent.addView(row)
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

    private fun difficultyLabel(difficulty: String?): String {
        return when (difficulty) {
            "easy" -> getString(R.string.answer_difficulty_easy)
            "hard" -> getString(R.string.answer_difficulty_hard)
            else -> getString(R.string.answer_difficulty_medium)
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
        flushReviewSeconds()
        executor?.shutdown()
        super.onDestroy()
    }
}
