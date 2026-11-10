package com.aistudy.aistudy.database

object ProfileDefaults {

    const val LEVEL_JUNIOR = "junior"
    const val LEVEL_HIGH = "high"
    const val LEVEL_UNI = "uni"

    const val STYLE_SHORT = "short"
    const val STYLE_STEPWISE = "stepwise"
    const val STYLE_DETAILED = "detailed"

    const val SUBJECT_MATH = "math"
    const val SUBJECT_SCIENCE = "science"
    const val SUBJECT_CODE = "code"
    const val SUBJECT_HISTORY = "history"
    const val SUBJECT_LANGUAGE = "language"
    const val SUBJECT_OTHER = "other"

    fun createDefault(userId: Long): Profile {
        val profile = Profile()
        profile.userId = userId
        profile.onboarded = false
        profile.level = LEVEL_HIGH
        profile.subjects = "$SUBJECT_MATH,$SUBJECT_SCIENCE"
        profile.style = STYLE_STEPWISE
        profile.xp = 0
        profile.streak = 0
        return profile
    }

    fun ensureExists(profileDao: ProfileDao, userId: Long) {
        if (profileDao.getByUserId(userId) == null) {
            profileDao.insert(createDefault(userId))
        }
    }
}
