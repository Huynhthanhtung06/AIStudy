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
            if (key.length <= 8) return "****"
            return key.take(4) + "..." + key.takeLast(4)
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

        btnSuggestion1.setOnClickListener { editQuestion.setText(btnSuggestion1.text) }
        btnSuggestion2.setOnClickListener { editQuestion.setText(btnSuggestion2.text) }
        btnSuggestion3.setOnClickListener { editQuestion.setText(btnSuggestion3.text) }
        btnSuggestion4.setOnClickListener { editQuestion.setText(btnSuggestion4.text) }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        executor?.shutdown()
        executor = null
    }

    private fun sendQuestion() {
        val questionText = editQuestion.text.toString().trim()
        if (questionText.isEmpty()) {
            Toast.makeText(requireContext(), R.string.ask_error_empty, Toast.LENGTH_SHORT).show()
            return
        }

        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isBlank() || apiKey == "YOUR_REAL_GEMINI_API_KEY") {
            Log.e(TAG, "submit blocked: api key missing or placeholder")
            Toast.makeText(requireContext(), R.string.ask_error_api_key, Toast.LENGTH_LONG).show()
            return
        }

        Log.d(TAG, "submit start with key=${maskKey(apiKey)}")
        setLoading(true)

        val context = requireContext()
        val prefs = context.getSharedPreferences(LoginActivity.PREFS_AUTH, Context.MODE_PRIVATE)
        val userId = prefs.getLong(LoginActivity.KEY_USER_ID, -1)

        val profileDao = AppDatabase.getInstance(context).profileDao()
        val profile = profileDao.getByUserId(userId)
        val level = profile?.level ?: "high"
        val style = profile?.style ?: "stepwise"

        val prompt = buildPrompt(questionText, level, style)

        executor?.execute {
            try {
                val rawResponse = GeminiClient.ask(apiKey, prompt)
                val jsonStr = extractJson(rawResponse)
                val jsonObj = JSONObject(jsonStr)

                val subject = jsonObj.optString("subject", "other")
                val stepsArray = jsonObj.optJSONArray("steps")
                val finalAnswer = jsonObj.optString("finalAnswer", "")

                val qa = QuestionAnswer().apply {
                    this.userId = userId
                    this.subject = subject
                    this.question = questionText
                    this.steps = stepsArray?.toString() ?: "[]"
                    this.finalAnswer = finalAnswer
                    this.starred = false
                    this.createdAt = System.currentTimeMillis()
                }

                val qaDao = AppDatabase.getInstance(context).questionAnswerDao()
                val qaId = qaDao.insert(qa)
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
                activity?.runOnUiThread {
                    if (isAdded) {
                        setLoading(false)
                        Toast.makeText(context, R.string.ask_error_failed, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun setLoading(loading: Boolean) {
        btnSend.visibility = if (loading) View.INVISIBLE else View.VISIBLE
        progressLoading.visibility = if (loading) View.VISIBLE else View.GONE
        editQuestion.isEnabled = !loading
    }

    private fun buildPrompt(question: String, level: String, style: String): String {
        val levelDesc = when (level) {
            "junior" -> "middle school level"
            "uni" -> "university level"
            else -> "high school level"
        }
        val styleDesc = when (style) {
            "short" -> "concise and quick answer"
            "detailed" -> "in-depth with detailed explanation"
            else -> "clear step by step solution"
        }

        return """
            You are a helpful AI study mentor.
            Student level: $levelDesc.
            Explanation style: $styleDesc.
            
            Answer the following question in strict JSON format.
            Do not include any markdown formatting outside the JSON unless wrapping JSON in ```json ``` codeblock.
            
            JSON schema required:
            {
              "subject": "one of: math, science, code, history, language, other",
              "steps": [
                {
                  "title": "Step title",
                  "body": "Step description",
                  "expr": "Optional math expression or code snippet, or empty string"
                }
              ],
              "finalAnswer": "Final summary answer"
            }
            
            Question:
            $question
        """.trimIndent()
    }

    private fun extractJson(raw: String): String {
        var text = raw.trim()
        if (text.startsWith("```json")) {
            text = text.removePrefix("```json")
        } else if (text.startsWith("```")) {
            text = text.removePrefix("```")
        }
        if (text.endsWith("```")) {
            text = text.removeSuffix("```")
        }
        return text.trim()
    }
}
