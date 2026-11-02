package com.aistudy.aistudy.ask

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
import com.aistudy.aistudy.database.Profile
import com.aistudy.aistudy.database.QuestionAnswer
import com.google.android.material.button.MaterialButton
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class AskFragment : Fragment() {

    companion object {
        private const val TAG = "AskFragment"
        private const val XP_PER_QUESTION = 50

        private fun maskKey(key: String): String {
            return if (key.length > 8) "${key.substring(0, 4)}...${key.substring(key.length - 4)}" else "***"
        }

        private fun truncate(text: String, max: Int = 100): String {
            return if (text.length > max) "${text.substring(0, max)}..." else text
        }

        private fun buildPrompt(question: String, level: String?, style: String?): String {
            val levelDesc = when (level) {
                "junior" -> "Middle school (Grades 6-9)"
                "uni" -> "University / College"
                else -> "High school (Grades 10-12)"
            }
            val styleDesc = when (style) {
                "short" -> "Concise, quick answer with fewer words"
                "detailed" -> "In-depth with examples and thorough explanations"
                else -> "Step by step, clear steps with one main idea each"
            }
            return """
                You are an expert AI study mentor. Answer the student's question adhering strictly to:
                - Student Level: $levelDesc
                - Explanation Style: $styleDesc

                Question: "$question"

                You MUST return ONLY a valid JSON object with the following structure, and NO markdown code block formatting (do not wrap in ```json ... ```, just raw JSON string):
                {
                  "subject": "math|science|code|history|language|other",
                  "steps": [
                    {
                      "title": "Step title",
                      "body": "Detailed explanation body",
                      "expr": "Optional formula or short expression"
                    }
                  ],
                  "finalAnswer": "Summary of final answer"
                }
            """.trimIndent()
        }

        private fun extractJson(raw: String): String {
            var text = raw.trim()
            if (text.startsWith("```json")) {
                text = text.removePrefix("```json").trim()
            } else if (text.startsWith("```")) {
                text = text.removePrefix("```").trim()
            }
            if (text.endsWith("```")) {
                text = text.removeSuffix("```").trim()
            }
            return text
        }
    }

    private lateinit var editQuestion: EditText
    private lateinit var btnSend: MaterialButton
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
        editQuestion = view.findViewById(R.id.edit_question)
        btnSend = view.findViewById(R.id.btn_send)
        progressLoading = view.findViewById(R.id.progress_loading)

        executor = Executors.newSingleThreadExecutor()

        btnSend.setOnClickListener { submitQuestion(editQuestion.text.toString().trim()) }

        view.findViewById<MaterialButton>(R.id.btn_suggestion_1).setOnClickListener {
            editQuestion.setText(R.string.ask_suggestion_1)
        }
        view.findViewById<MaterialButton>(R.id.btn_suggestion_2).setOnClickListener {
            editQuestion.setText(R.string.ask_suggestion_2)
        }
        view.findViewById<MaterialButton>(R.id.btn_suggestion_3).setOnClickListener {
            editQuestion.setText(R.string.ask_suggestion_3)
        }
        view.findViewById<MaterialButton>(R.id.btn_suggestion_4).setOnClickListener {
            editQuestion.setText(R.string.ask_suggestion_4)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        executor?.shutdown()
        executor = null
    }

    private fun setLoading(loading: Boolean) {
        btnSend.isEnabled = !loading
        btnSend.text = if (loading) getString(R.string.ask_sending) else getString(R.string.ask_send)
        progressLoading.visibility = if (loading) View.VISIBLE else View.GONE
    }

    private fun submitQuestion(question: String) {
        if (question.isEmpty()) {
            Toast.makeText(requireContext(), R.string.ask_error_empty, Toast.LENGTH_SHORT).show()
            return
        }
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey.startsWith("YOUR_")) {
            Log.w(TAG, "submit blocked: api key missing or placeholder")
            Toast.makeText(requireContext(), R.string.ask_error_key, Toast.LENGTH_LONG).show()
            return
        }

        val activity = requireActivity()
        val userId = activity.getSharedPreferences(LoginActivity.PREFS_AUTH, android.content.Context.MODE_PRIVATE)
            .getLong(LoginActivity.KEY_USER_ID, -1)
        if (userId == -1L) return

        val db = AppDatabase.getInstance(activity)
        val profile = db.profileDao().getByUserId(userId) ?: Profile().apply { this.userId = userId }
        val prompt = buildPrompt(question, profile.level, profile.style)

        setLoading(true)
        Log.d(TAG, "submit start: question='${truncate(question)}'")

        executor?.execute {
            try {
                val rawResponse = GeminiClient.ask(apiKey, prompt)
                Log.d(TAG, "raw response preview: ${truncate(rawResponse, 200)}")

                val jsonText = extractJson(rawResponse)
                val jsonObj = JSONObject(jsonText)

                val subject = jsonObj.optString("subject", "other")
                val stepsArray = jsonObj.optJSONArray("steps") ?: JSONArray()
                val finalAnswer = jsonObj.optString("finalAnswer", "")

                val qa = QuestionAnswer().apply {
                    this.userId = userId
                    this.subject = subject
                    this.question = question
                    this.steps = stepsArray.toString()
                    this.finalAnswer = finalAnswer
                    this.createdAt = System.currentTimeMillis()
                }

                val qaId = db.questionAnswerDao().insert(qa)
                profile.xp += XP_PER_QUESTION
                db.profileDao().update(profile)

                Log.d(TAG, "saved qaId=$qaId, subject=$subject, stepsCount=${stepsArray.length()}")

                activity.runOnUiThread {
                    setLoading(false)
                    editQuestion.setText("")
                    val intent = Intent(activity, AnswerActivity::class.java).apply {
                        putExtra(AnswerActivity.EXTRA_QA_ID, qaId)
                    }
                    startActivity(intent)
                }
            } catch (e: Exception) {
                Log.e(TAG, "submit failed", e)
                activity.runOnUiThread {
                    setLoading(false)
                    Toast.makeText(activity, R.string.ask_error_network, Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}
