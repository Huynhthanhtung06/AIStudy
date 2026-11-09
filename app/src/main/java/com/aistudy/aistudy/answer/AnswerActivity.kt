package com.aistudy.aistudy.answer

import android.os.Bundle
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
import org.json.JSONArray

class AnswerActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_QA_ID = "qa_id"
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

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.layout_root)) { v, insets ->
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
            Toast.makeText(this, R.string.answer_not_found, Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val qaDao = AppDatabase.getInstance(this).questionAnswerDao()
        val qa = qaDao.getById(qaId)
        if (qa == null) {
            Toast.makeText(this, R.string.answer_not_found, Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        tvSubject.text = (qa.subject ?: "Other").uppercase()
        tvQuestion.text = qa.question
        tvFinalAnswer.text = qa.finalAnswer ?: ""

        val stepsJson = qa.steps
        if (!stepsJson.isNullOrEmpty()) {
            try {
                val jsonArray = JSONArray(stepsJson)
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    val title = obj.optString("title", "Step ${i + 1}")
                    val body = obj.optString("body", "")
                    val expr = obj.optString("expr", "")
                    addStepView(i + 1, title, body, expr)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun addStepView(stepNum: Int, title: String, body: String, expr: String) {
        val stepCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.bg_auth_input)
            setPadding(32, 24, 32, 24)
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.setMargins(0, 0, 0, 24)
            layoutParams = params
        }

        val tvTitle = TextView(this).apply {
            text = "$stepNum. $title"
            setTextColor(getColor(R.color.auth_foreground))
            textSize = 14f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        stepCard.addView(tvTitle)

        if (body.isNotEmpty()) {
            val tvBody = TextView(this).apply {
                text = body
                setTextColor(getColor(R.color.auth_muted_foreground))
                textSize = 13f
                val lp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                lp.setMargins(0, 8, 0, 0)
                layoutParams = lp
            }
            stepCard.addView(tvBody)
        }

        if (expr.isNotEmpty()) {
            val tvExpr = TextView(this).apply {
                text = expr
                setTextColor(getColor(R.color.auth_brand))
                textSize = 13f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setBackgroundResource(R.drawable.bg_auth_brand_icon)
                setPadding(16, 12, 16, 12)
                val lp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                lp.setMargins(0, 12, 0, 0)
                layoutParams = lp
            }
            stepCard.addView(tvExpr)
        }

        layoutSteps.addView(stepCard)
    }
}
