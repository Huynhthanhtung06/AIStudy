package com.aistudy.aistudy.answer

import android.os.Bundle
import android.view.LayoutInflater
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
        const val EXTRA_QA_ID = "extra_qa_id"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_answer)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val qaId = intent.getLongExtra(EXTRA_QA_ID, -1L)
        if (qaId == -1L) {
            Toast.makeText(this, "Invalid question ID", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val qa = AppDatabase.getInstance(this).questionAnswerDao().getById(qaId)
        if (qa == null) {
            Toast.makeText(this, "Question not found", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        findViewById<android.view.View>(R.id.btn_back).setOnClickListener { finish() }

        val tvSubject = findViewById<TextView>(R.id.tv_subject)
        val tvQuestion = findViewById<TextView>(R.id.tv_question)
        val layoutSteps = findViewById<LinearLayout>(R.id.layout_steps)
        val tvFinalAnswer = findViewById<TextView>(R.id.tv_final_answer)

        tvSubject.text = qa.subject?.uppercase() ?: "OTHER"
        tvQuestion.text = qa.question
        tvFinalAnswer.text = qa.finalAnswer

        try {
            val stepsArray = JSONArray(qa.steps ?: "[]")
            for (i in 0 until stepsArray.length()) {
                val stepObj = stepsArray.getJSONObject(i)
                val title = stepObj.optString("title", "Step ${i + 1}")
                val body = stepObj.optString("body", "")
                val expr = stepObj.optString("expr", "")

                val stepView = LayoutInflater.from(this).inflate(R.layout.item_answer_step, layoutSteps, false)
                val tvStepTitle = stepView.findViewById<TextView>(R.id.tv_step_title)
                val tvStepBody = stepView.findViewById<TextView>(R.id.tv_step_body)
                val tvStepExpr = stepView.findViewById<TextView>(R.id.tv_step_expr)

                tvStepTitle.text = title
                tvStepBody.text = body
                if (expr.isNotEmpty()) {
                    tvStepExpr.text = expr
                    tvStepExpr.visibility = TextView.VISIBLE
                } else {
                    tvStepExpr.visibility = TextView.GONE
                }

                layoutSteps.addView(stepView)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
