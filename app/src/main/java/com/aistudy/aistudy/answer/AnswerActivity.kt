package com.aistudy.aistudy.answer

import android.os.Bundle
import android.view.LayoutInflater
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
            Toast.makeText(this, "Question not found", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val qa = AppDatabase.getInstance(this).questionAnswerDao().getById(qaId)
        if (qa == null) {
            Toast.makeText(this, "Question not found", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        findViewById<ImageButton>(R.id.btn_back).setOnClickListener { finish() }
        findViewById<TextView>(R.id.tv_subject).text = qa.subject?.uppercase() ?: "STUDY"
        findViewById<TextView>(R.id.tv_question).text = qa.question
        findViewById<TextView>(R.id.tv_final_answer).text = qa.finalAnswer

        val layoutSteps = findViewById<LinearLayout>(R.id.layout_steps)
        populateSteps(layoutSteps, qa.steps)
    }

    private fun populateSteps(container: LinearLayout, stepsJson: String?) {
        if (stepsJson.isNullOrEmpty()) return
        try {
            val array = JSONArray(stepsJson)
            for (i in 0 until array.length()) {
                val stepObj = array.getJSONObject(i)
                val title = stepObj.optString("title", "Step ${i + 1}")
                val body = stepObj.optString("body", "")
                val expr = stepObj.optString("expr", "")

                val stepView = LayoutInflater.from(this).inflate(R.layout.item_answer_step, container, false)
                stepView.findViewById<TextView>(R.id.tv_step_number).text = "Step ${i + 1}"
                stepView.findViewById<TextView>(R.id.tv_step_title).text = title
                stepView.findViewById<TextView>(R.id.tv_step_body).text = body

                val tvExpr = stepView.findViewById<TextView>(R.id.tv_step_expr)
                if (expr.isNotEmpty()) {
                    tvExpr.text = expr
                    tvExpr.visibility = android.view.View.VISIBLE
                } else {
                    tvExpr.visibility = android.view.View.GONE
                }

                container.addView(stepView)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
