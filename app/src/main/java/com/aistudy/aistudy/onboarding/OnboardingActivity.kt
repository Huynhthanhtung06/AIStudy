package com.aistudy.aistudy.onboarding

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.aistudy.aistudy.MainActivity
import com.aistudy.aistudy.R
import com.aistudy.aistudy.auth.LoginActivity
import com.aistudy.aistudy.database.AppDatabase
import com.aistudy.aistudy.database.Profile
import com.aistudy.aistudy.database.ProfileDao
import com.aistudy.aistudy.database.ProfileDefaults
import com.google.android.material.button.MaterialButton

class OnboardingActivity : AppCompatActivity() {

    companion object {
        private const val STEP_LEVEL = 0
        private const val STEP_SUBJECTS = 1
        private const val STEP_STYLE = 2

        private fun joinSubjects(subjects: Set<String>): String {
            return subjects.joinToString(",")
        }
    }

    private lateinit var profileDao: ProfileDao
    private var userId: Long = 0
    private lateinit var profile: Profile
    private var currentStep = STEP_LEVEL

    private var selectedLevel: String? = ProfileDefaults.LEVEL_HIGH
    private var selectedStyle: String? = ProfileDefaults.STYLE_STEPWISE
    private val selectedSubjects = hashSetOf(
        ProfileDefaults.SUBJECT_MATH,
        ProfileDefaults.SUBJECT_SCIENCE
    )

    private lateinit var progress0: View
    private lateinit var progress1: View
    private lateinit var progress2: View
    private lateinit var tvStepTitle: TextView
    private lateinit var tvStepSubtitle: TextView
    private lateinit var stepLevel: LinearLayout
    private lateinit var stepSubjects: LinearLayout
    private lateinit var stepStyle: LinearLayout
    private lateinit var btnBack: MaterialButton
    private lateinit var btnNext: MaterialButton

    private lateinit var choiceLevelJunior: LinearLayout
    private lateinit var choiceLevelHigh: LinearLayout
    private lateinit var choiceLevelUni: LinearLayout

    private lateinit var choiceSubjectMath: TextView
    private lateinit var choiceSubjectScience: TextView
    private lateinit var choiceSubjectCode: TextView
    private lateinit var choiceSubjectHistory: TextView
    private lateinit var choiceSubjectLanguage: TextView
    private lateinit var choiceSubjectOther: TextView

    private lateinit var choiceStyleShort: LinearLayout
    private lateinit var choiceStyleStepwise: LinearLayout
    private lateinit var choiceStyleDetailed: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_onboarding)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        userId = getSharedPreferences(LoginActivity.PREFS_AUTH, MODE_PRIVATE)
            .getLong(LoginActivity.KEY_USER_ID, -1)
        if (userId == -1L) {
            finish()
            return
        }

        profileDao = AppDatabase.getInstance(this).profileDao()
        ProfileDefaults.ensureExists(profileDao, userId)
        profile = profileDao.getByUserId(userId)!!

        if (profile.onboarded) {
            goToMain()
            return
        }

        selectedLevel = profile.level
        selectedStyle = profile.style
        selectedSubjects.clear()
        for (subject in profile.subjects.orEmpty().split(",")) {
            if (subject.isNotEmpty()) {
                selectedSubjects.add(subject)
            }
        }

        bindViews()
        setupLevelChoices()
        setupSubjectChoices()
        setupStyleChoices()
        showStep(STEP_LEVEL)

        btnBack.setOnClickListener {
            if (currentStep > STEP_LEVEL) {
                showStep(currentStep - 1)
            }
        }

        btnNext.setOnClickListener { onNext() }
    }

    private fun bindViews() {
        progress0 = findViewById(R.id.progress_0)
        progress1 = findViewById(R.id.progress_1)
        progress2 = findViewById(R.id.progress_2)
        tvStepTitle = findViewById(R.id.tv_step_title)
        tvStepSubtitle = findViewById(R.id.tv_step_subtitle)
        stepLevel = findViewById(R.id.step_level)
        stepSubjects = findViewById(R.id.step_subjects)
        stepStyle = findViewById(R.id.step_style)
        btnBack = findViewById(R.id.btn_back)
        btnNext = findViewById(R.id.btn_next)

        choiceLevelJunior = findViewById(R.id.choice_level_junior)
        choiceLevelHigh = findViewById(R.id.choice_level_high)
        choiceLevelUni = findViewById(R.id.choice_level_uni)

        choiceSubjectMath = findViewById(R.id.choice_subject_math)
        choiceSubjectScience = findViewById(R.id.choice_subject_science)
        choiceSubjectCode = findViewById(R.id.choice_subject_code)
        choiceSubjectHistory = findViewById(R.id.choice_subject_history)
        choiceSubjectLanguage = findViewById(R.id.choice_subject_language)
        choiceSubjectOther = findViewById(R.id.choice_subject_other)

        choiceStyleShort = findViewById(R.id.choice_style_short)
        choiceStyleStepwise = findViewById(R.id.choice_style_stepwise)
        choiceStyleDetailed = findViewById(R.id.choice_style_detailed)
    }

    private fun setupLevelChoices() {
        choiceLevelJunior.setOnClickListener { selectLevel(ProfileDefaults.LEVEL_JUNIOR) }
        choiceLevelHigh.setOnClickListener { selectLevel(ProfileDefaults.LEVEL_HIGH) }
        choiceLevelUni.setOnClickListener { selectLevel(ProfileDefaults.LEVEL_UNI) }
        refreshLevelUi()
    }

    private fun selectLevel(level: String) {
        selectedLevel = level
        refreshLevelUi()
    }

    private fun refreshLevelUi() {
        setChoiceSelected(choiceLevelJunior, selectedLevel == ProfileDefaults.LEVEL_JUNIOR)
        setChoiceSelected(choiceLevelHigh, selectedLevel == ProfileDefaults.LEVEL_HIGH)
        setChoiceSelected(choiceLevelUni, selectedLevel == ProfileDefaults.LEVEL_UNI)
    }

    private fun setupSubjectChoices() {
        choiceSubjectMath.setOnClickListener { toggleSubject(ProfileDefaults.SUBJECT_MATH, choiceSubjectMath) }
        choiceSubjectScience.setOnClickListener { toggleSubject(ProfileDefaults.SUBJECT_SCIENCE, choiceSubjectScience) }
        choiceSubjectCode.setOnClickListener { toggleSubject(ProfileDefaults.SUBJECT_CODE, choiceSubjectCode) }
        choiceSubjectHistory.setOnClickListener { toggleSubject(ProfileDefaults.SUBJECT_HISTORY, choiceSubjectHistory) }
        choiceSubjectLanguage.setOnClickListener { toggleSubject(ProfileDefaults.SUBJECT_LANGUAGE, choiceSubjectLanguage) }
        choiceSubjectOther.setOnClickListener { toggleSubject(ProfileDefaults.SUBJECT_OTHER, choiceSubjectOther) }
        refreshSubjectUi()
    }

    private fun toggleSubject(subject: String, view: TextView) {
        if (subject in selectedSubjects) {
            selectedSubjects.remove(subject)
        } else {
            selectedSubjects.add(subject)
        }
        refreshSubjectUi()
    }

    private fun refreshSubjectUi() {
        setSubjectSelected(choiceSubjectMath, ProfileDefaults.SUBJECT_MATH in selectedSubjects)
        setSubjectSelected(choiceSubjectScience, ProfileDefaults.SUBJECT_SCIENCE in selectedSubjects)
        setSubjectSelected(choiceSubjectCode, ProfileDefaults.SUBJECT_CODE in selectedSubjects)
        setSubjectSelected(choiceSubjectHistory, ProfileDefaults.SUBJECT_HISTORY in selectedSubjects)
        setSubjectSelected(choiceSubjectLanguage, ProfileDefaults.SUBJECT_LANGUAGE in selectedSubjects)
        setSubjectSelected(choiceSubjectOther, ProfileDefaults.SUBJECT_OTHER in selectedSubjects)
    }

    private fun setupStyleChoices() {
        choiceStyleShort.setOnClickListener { selectStyle(ProfileDefaults.STYLE_SHORT) }
        choiceStyleStepwise.setOnClickListener { selectStyle(ProfileDefaults.STYLE_STEPWISE) }
        choiceStyleDetailed.setOnClickListener { selectStyle(ProfileDefaults.STYLE_DETAILED) }
        refreshStyleUi()
    }

    private fun selectStyle(style: String) {
        selectedStyle = style
        refreshStyleUi()
    }

    private fun refreshStyleUi() {
        setChoiceSelected(choiceStyleShort, selectedStyle == ProfileDefaults.STYLE_SHORT)
        setChoiceSelected(choiceStyleStepwise, selectedStyle == ProfileDefaults.STYLE_STEPWISE)
        setChoiceSelected(choiceStyleDetailed, selectedStyle == ProfileDefaults.STYLE_DETAILED)
    }

    private fun setChoiceSelected(view: LinearLayout, selected: Boolean) {
        view.setBackgroundResource(
            if (selected) R.drawable.bg_onboarding_choice_selected
            else R.drawable.bg_onboarding_choice
        )
    }

    private fun setSubjectSelected(view: TextView, selected: Boolean) {
        view.setBackgroundResource(
            if (selected) R.drawable.bg_onboarding_subject_selected
            else R.drawable.bg_onboarding_subject
        )
    }

    private fun showStep(step: Int) {
        currentStep = step

        stepLevel.visibility = if (step == STEP_LEVEL) View.VISIBLE else View.GONE
        stepSubjects.visibility = if (step == STEP_SUBJECTS) View.VISIBLE else View.GONE
        stepStyle.visibility = if (step == STEP_STYLE) View.VISIBLE else View.GONE

        val brand = ContextCompat.getColor(this, R.color.auth_brand)
        val muted = ContextCompat.getColor(this, R.color.auth_card)

        progress0.setBackgroundColor(if (step >= STEP_LEVEL) brand else muted)
        progress1.setBackgroundColor(if (step >= STEP_SUBJECTS) brand else muted)
        progress2.setBackgroundColor(if (step >= STEP_STYLE) brand else muted)

        btnBack.visibility = if (step > STEP_LEVEL) View.VISIBLE else View.GONE

        when (step) {
            STEP_LEVEL -> {
                tvStepTitle.setText(R.string.onboarding_step_level_title)
                tvStepSubtitle.setText(R.string.onboarding_step_level_subtitle)
                btnNext.setText(R.string.onboarding_continue)
            }
            STEP_SUBJECTS -> {
                tvStepTitle.setText(R.string.onboarding_step_subjects_title)
                tvStepSubtitle.setText(R.string.onboarding_step_subjects_subtitle)
                btnNext.setText(R.string.onboarding_continue)
            }
            else -> {
                tvStepTitle.setText(R.string.onboarding_step_style_title)
                tvStepSubtitle.setText(R.string.onboarding_step_style_subtitle)
                btnNext.setText(R.string.onboarding_finish)
            }
        }
    }

    private fun onNext() {
        if (currentStep == STEP_SUBJECTS && selectedSubjects.isEmpty()) {
            Toast.makeText(this, R.string.onboarding_pick_subject, Toast.LENGTH_SHORT).show()
            return
        }

        if (currentStep < STEP_STYLE) {
            showStep(currentStep + 1)
            return
        }

        profile.level = selectedLevel
        profile.style = selectedStyle
        profile.subjects = joinSubjects(selectedSubjects)
        profile.onboarded = true
        profileDao.update(profile)

        goToMain()
    }

    private fun goToMain() {
        val intent = Intent(this, MainActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        startActivity(intent)
        finish()
    }
}
