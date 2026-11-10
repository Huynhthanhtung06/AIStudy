package com.aistudy.aistudy.ask

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.aistudy.aistudy.BuildConfig
import com.aistudy.aistudy.R
import com.aistudy.aistudy.answer.AnswerActivity
import com.aistudy.aistudy.auth.LoginActivity
import com.aistudy.aistudy.database.AppDatabase
import com.aistudy.aistudy.database.QuestionAnswer
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class AskFragment : Fragment() {

    companion object {
        private const val TAG = "AskFragment"
        private const val XP_PER_QUESTION = 50
    }

    private lateinit var editQuestion: EditText
    private lateinit var btnSend: Button
    private lateinit var progressLoading: ProgressBar

    private var executor: ExecutorService? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_ask, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        executor = Executors.newSingleThreadExecutor()

        editQuestion = view.findViewById(R.id.edit_question)
        btnSend = view.findViewById(R.id.btn_send)
        progressLoading = view.findViewById(R.id.progress_loading)

        btnSend.setOnClickListener {
            val q = editQuestion.text.toString().trim()
            if (q.isNotEmpty()) {
                submitQuestion(q)
            }
        }

        view.findViewById<View>(R.id.btn_suggestion_1).setOnClickListener {
            editQuestion.setText(R.string.ask_suggestion_1)
        }
        view.findViewById<View>(R.id.btn_suggestion_2).setOnClickListener {
            editQuestion.setText(R.string.ask_suggestion_2)
        }
        view.findViewById<View>(R.id.btn_suggestion_3).setOnClickListener {
            editQuestion.setText(R.string.ask_suggestion_3)
        }
        view.findViewById<View>(R.id.btn_suggestion_4).setOnClickListener {
            editQuestion.setText(R.string.ask_suggestion_4)
        }
    }

    private fun submitQuestion(question: String) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey == "YOUR_REAL_GEMINI_API_KEY") {
            Log.w(TAG, "submit blocked: api key missing or placeholder")
            Toast.makeText(requireContext(), "GEMINI_API_KEY not set in local.properties", Toast.LENGTH_LONG).show()
            return
        }

        setLoading(true)
        Log.d(TAG, "submit start")

        val ctx = requireContext()
        val prefs = ctx.getSharedPreferences(LoginActivity.PREFS_AUTH, android.content.Context.MODE_PRIVATE)
        val userId = prefs.getLong(LoginActivity.KEY_USER_ID, -1)

        val db = AppDatabase.getInstance(ctx)
        val profile = db.profileDao().getByUserId(userId)
        val level = profile?.level ?: "high"
        val style = profile?.style ?: "stepwise"

        val prompt = buildPrompt(question, level, style)

        executor?.execute {
            try {
                val rawResponse = GeminiClient.ask(apiKey, prompt)
                val jsonText = extractJson(rawResponse)
                val json = JSONObject(jsonText)

                var subject = json.optString("subject", "other")
                if (!isValidSubject(subject)) {
                    Log.w(TAG, "invalid subject from AI: $subject, fallback to other")
                    subject = "other"
                }

                var difficulty = json.optString("difficulty", "medium")
                if (!isValidDifficulty(difficulty)) {
                    difficulty = "medium"
                }

                val stepsArray = json.getJSONArray("steps")
                val finalAnswer = json.getString("finalAnswer")

                Log.d(
                    TAG, "parsed subject=$subject difficulty=$difficulty" +
                        " steps=${stepsArray.length()}" +
                        " finalAnswerLength=${finalAnswer.length}"
                )

                val qa = QuestionAnswer().apply {
                    this.userId = userId
                    this.subject = subject
                    this.question = question
                    this.steps = stepsArray.toString()
                    this.finalAnswer = finalAnswer
                    this.starred = false
                    this.createdAt = System.currentTimeMillis()
                    this.difficulty = difficulty
                    this.extrasJson = null
                    this.reviewedAt = 0
                }

                val qaId = db.questionAnswerDao().insert(qa)

                if (profile != null) {
                    profile.xp += XP_PER_QUESTION
                    db.profileDao().update(profile)
                }

                Log.d(TAG, "saved qaId=$qaId")

                activity?.runOnUiThread {
                    setLoading(false)
                    editQuestion.setText("")
                    val intent = Intent(ctx, AnswerActivity::class.java).apply {
                        putExtra(AnswerActivity.EXTRA_QA_ID, qaId)
                    }
                    startActivity(intent)
                }
            } catch (e: Exception) {
                Log.e(TAG, "submit failed", e)
                activity?.runOnUiThread {
                    setLoading(false)
                    Toast.makeText(ctx, "Could not get AI response: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun isValidSubject(subject: String): Boolean {
        return subject == "math" ||
            subject == "science" ||
            subject == "code" ||
            subject == "history" ||
            subject == "language" ||
            subject == "other"
    }

    private fun isValidDifficulty(difficulty: String): Boolean {
        return difficulty == "easy" ||
            difficulty == "medium" ||
            difficulty == "hard"
    }

    private fun buildPrompt(question: String, level: String, style: String): String {
        val levelText = when (level) {
            "junior" -> "Middle school student"
            "uni" -> "University student"
            else -> "High school student"
        }
        val styleText = when (style) {
            "short" -> "Concise, quick answer with fewer words"
            "detailed" -> "In-depth with detailed examples"
            else -> "Clear steps, one idea each"
        }

        return "You are AI Study Mentor, a study tutor in English for a $levelText.\n" +
            styleText + "\n" +
            "Always answer in English. Put math formulas in the \"expr\" field of the matching step.\n" +
            "Classify the subject correctly (math/science/code/history/language/other).\n" +
            "Also rate difficulty as easy, medium, or hard for this student.\n\n" +
            "Return ONLY valid JSON (no markdown) with this structure:\n" +
            "{\n" +
            "  \"subject\": \"math|science|code|history|language|other\",\n" +
            "  \"difficulty\": \"easy|medium|hard\",\n" +
            "  \"steps\": [{\"title\": \"optional\", \"body\": \"required\", \"expr\": \"optional\"}],\n" +
            "  \"finalAnswer\": \"string\"\n" +
            "}\n\n" +
            "Question: " + question
    }

    private fun extractJson(raw: String): String {
        var text = raw.trim()
        if (text.startsWith("```")) {
            val firstNewline = text.indexOf('\n')
            if (firstNewline != -1) {
                text = text.substring(firstNewline + 1)
            }
            if (text.endsWith("```")) {
                text = text.substring(0, text.length - 3)
            }
            text = text.trim()
        }
        return text
    }

    private fun setLoading(loading: Boolean) {
        btnSend.isEnabled = !loading
        btnSend.text = if (loading) "" else getString(R.string.ask_send)
        progressLoading.visibility = if (loading) View.VISIBLE else View.GONE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        executor?.shutdown()
        executor = null
    }
}
