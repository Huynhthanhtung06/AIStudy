package com.aistudy.aistudy.answer

import android.graphics.Typeface
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.aistudy.aistudy.R
import com.aistudy.aistudy.database.AppDatabase
import com.aistudy.aistudy.database.QuestionAnswer
import org.json.JSONArray

class AnswerActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_QA_ID = "extra_qa_id"
    }

    private lateinit var btnBack: ImageButton
    private lateinit var tvSubject: TextView
    private lateinit var tvQuestion: TextView
    private lateinit var layoutSteps: LinearLayout
    private lateinit var tvFinalAnswer: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_answer)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        btnBack = findViewById(R.id.btn_back)
        tvSubject = findViewById(R.id.tv_subject)
        tvQuestion = findViewById(R.id.tv_question)
        layoutSteps = findViewById(R.id.layout_steps)
        tvFinalAnswer = findViewById(R.id.tv_final_answer)

        btnBack.setOnClickListener { finish() }

        val qaId = intent.getLongExtra(EXTRA_QA_ID, -1L)
        if (qaId == -1L) {
            Toast.makeText(this, R.string.answer_error_not_found, Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val qa = AppDatabase.getInstance(this).questionAnswerDao().getById(qaId)
        if (qa == null) {
            Toast.makeText(this, R.string.answer_error_not_found, Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        displayQA(qa)
    }

    private fun displayQA(qa: QuestionAnswer) {
        tvSubject.text = (qa.subject ?: "other").uppercase()
        tvQuestion.text = qa.question ?: ""
        tvFinalAnswer.text = qa.finalAnswer ?: ""

        layoutSteps.removeAllViews()

        val stepsJson = qa.steps ?: "[]"
        try {
            val stepsArray = JSONArray(stepsJson)
            for (i in 0 until stepsArray.length()) {
                val stepObj = stepsArray.getJSONObject(i)
                val title = stepObj.optString("title", "Step ${i + 1}")
                val body = stepObj.optString("body", "")
                val expr = stepObj.optString("expr", "")

                addStepCard(i + 1, title, body, expr)
            }
        } catch (e: Exception) {
            val tvError = TextView(this).apply {
                text = stepsJson
                setTextColor(getColor(R.color.auth_foreground))
            }
            layoutSteps.addView(tvError)
        }
    }

    private fun addStepCard(stepNumber: Int, title: String, body: String, expr: String) {
        val stepView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.bg_auth_input)
            setPadding(32, 32, 32, 32)
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 24
            }
            layoutParams = params
        }

        val tvTitle = TextView(this).apply {
            text = "$stepNumber. $title"
            setTextColor(getColor(R.color.auth_foreground))
            textSize = 14f
            setTypeface(typeface, Typeface.BOLD)
        }
        stepView.addView(tvTitle)

        if (body.isNotEmpty()) {
            val tvBody = TextView(this).apply {
                text = body
                setTextColor(getColor(R.color.auth_muted_foreground))
                textSize = 13f
                setPadding(0, 8, 0, 0)
            }
            stepView.addView(tvBody)
        }

        if (expr.isNotEmpty()) {
            val tvExpr = TextView(this).apply {
                text = expr
                setTextColor(getColor(R.color.auth_brand))
                textSize = 13f
                setTypeface(typeface, Typeface.BOLD_ITALIC)
                setPadding(0, 12, 0, 0)
            }
            stepView.addView(tvExpr)
        }

        layoutSteps.addView(stepView)
    }
}
