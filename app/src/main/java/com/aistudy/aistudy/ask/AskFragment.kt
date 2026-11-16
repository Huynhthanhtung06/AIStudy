package com.aistudy.aistudy.ask

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
import com.google.android.material.button.MaterialButton
import org.json.JSONObject
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class AskFragment : Fragment() {

    companion object {
        private const val TAG = "AskFragment"
        private const val XP_PER_QUESTION = 50

        private fun maskKey(key: String): String {
            return if (key.length > 8) "${key.take(4)}...${key.takeLast(4)}" else "***"
        }
    }

    private lateinit var editQuestion: EditText
    private lateinit var btnSend: MaterialButton
    private lateinit var progressLoading: ProgressBar
    private lateinit var btnSuggestion1: MaterialButton
    private lateinit var btnSuggestion2: MaterialButton
    private lateinit var btnSuggestion3: MaterialButton
    private lateinit var btnSuggestion4: MaterialButton

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
        btnSuggestion1 = view.findViewById(R.id.btn_suggestion_1)
        btnSuggestion2 = view.findViewById(R.id.btn_suggestion_2)
        btnSuggestion3 = view.findViewById(R.id.btn_suggestion_3)
        btnSuggestion4 = view.findViewById(R.id.btn_suggestion_4)

        btnSend.setOnClickListener { sendQuestion() }

        btnSuggestion1.setOnClickListener { setQuestionAndSend(btnSuggestion1.text.toString()) }
        btnSuggestion2.setOnClickListener { setQuestionAndSend(btnSuggestion2.text.toString()) }
        btnSuggestion3.setOnClickListener { setQuestionAndSend(btnSuggestion3.text.toString()) }
        btnSuggestion4.setOnClickListener { setQuestionAndSend(btnSuggestion4.text.toString()) }
    }

    private fun setQuestionAndSend(questionText: String) {
        editQuestion.setText(questionText)
        sendQuestion()
    }

    private fun sendQuestion() {
        val question = editQuestion.text.toString().trim()
        if (question.isEmpty()) {
            Toast.makeText(requireContext(), R.string.ask_error_empty, Toast.LENGTH_SHORT).show()
            return
        }

        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isBlank() || apiKey == "YOUR_REAL_GEMINI_API_KEY") {
            Log.w(TAG, "submit blocked: api key missing or placeholder")
            Toast.makeText(requireContext(), R.string.ask_error_api_key, Toast.LENGTH_LONG).show()
            return
        }

        Log.d(TAG, "submit start with key=${maskKey(apiKey)}")
        setLoading(true)

        val context = requireContext()
        val prefs = context.getSharedPreferences(LoginActivity.PREFS_AUTH, Context.MODE_PRIVATE)
        val userId = prefs.getLong(LoginActivity.KEY_USER_ID, -1L)

        val profileDao = AppDatabase.getInstance(context).profileDao()
        val profile = if (userId != -1L) profileDao.getByUserId(userId) else null

        val level = profile?.level ?: "high"
        val style = profile?.style ?: "stepwise"

        val prompt = buildPrompt(question, level, style)

        executor?.execute {
            try {
                val rawResponse = GeminiClient.ask(apiKey, prompt)
                if (rawResponse.isBlank()) {
                    showError(getString(R.string.ask_error_failed))
                    return@execute
                }

                val jsonText = extractJson(rawResponse)
                val jsonObject = JSONObject(jsonText)

                var subject = jsonObject.optString("subject", "other")
                if (!isValidSubject(subject)) {
                    Log.w(TAG, "invalid subject from AI: $subject, fallback to other")
                    subject = "other"
                }

                var difficulty = jsonObject.optString("difficulty", "medium")
                if (!isValidDifficulty(difficulty)) {
                    difficulty = "medium"
                }

                val stepsArray = jsonObject.optJSONArray("steps")
                val stepsJson = stepsArray?.toString() ?: "[]"
                val finalAnswer = jsonObject.optString("finalAnswer", "")

                Log.d(
                    TAG, "parsed subject=$subject difficulty=$difficulty" +
                        " steps=${stepsArray?.length() ?: 0}" +
                        " finalAnswerLength=${finalAnswer.length}"
                )

                val db = AppDatabase.getInstance(context)
                val qa = QuestionAnswer().apply {
                    this.userId = userId
                    this.subject = subject
                    this.question = question
                    this.steps = stepsJson
                    this.finalAnswer = finalAnswer
                    this.createdAt = System.currentTimeMillis()
                    this.difficulty = difficulty
                    this.extrasJson = null
                    this.reviewedAt = 0
                }

                val qaId = db.questionAnswerDao().insert(qa)
                Log.d(TAG, "saved qaId=$qaId")

                if (profile != null) {
                    profile.xp += XP_PER_QUESTION
                    profileDao.update(profile)
                }

                activity?.runOnUiThread {
                    if (isAdded) {
                        setLoading(false)
                        editQuestion.setText("")
                        val intent = Intent(context, AnswerActivity::class.java).apply {
                            putExtra(AnswerActivity.EXTRA_QA_ID, qaId)
                        }
                        startActivity(intent)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "submit failed", e)
                showError(getString(R.string.ask_error_failed))
            }
        }
    }

    private fun buildPrompt(question: String, level: String, style: String): String {
        val levelText = when (level) {
            "junior" -> "Middle school level (simple concepts, gentle language)"
            "uni" -> "University level (rigorous proofs, advanced academic terminology)"
            else -> "High school level (standard high school curriculum)"
        }

        val styleText = when (style) {
            "short" -> "Concise and brief, get straight to the point"
            "detailed" -> "Detailed with complete explanations, context, and edge cases"
            else -> "Step by step, breakdown into clear numbered logical steps"
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

    private fun extractJson(raw: String): String {
        var trimmed = raw.trim()
        if (trimmed.startsWith("```json")) {
            trimmed = trimmed.substring(7)
        } else if (trimmed.startsWith("```")) {
            trimmed = trimmed.substring(3)
        }
        if (trimmed.endsWith("```")) {
            trimmed = trimmed.substring(0, trimmed.length - 3)
        }
        return trimmed.trim()
    }

    private fun setLoading(loading: Boolean) {
        btnSend.isEnabled = !loading
        btnSend.text = if (loading) getString(R.string.ask_sending) else getString(R.string.ask_send)
        progressLoading.visibility = if (loading) View.VISIBLE else View.GONE
    }

    private fun showError(message: String) {
        activity?.runOnUiThread {
            if (isAdded) {
                setLoading(false)
                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        executor?.shutdown()
        executor = null
    }
}
