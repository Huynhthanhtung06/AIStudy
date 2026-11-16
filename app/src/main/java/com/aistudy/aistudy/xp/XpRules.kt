package com.aistudy.aistudy.xp

import com.aistudy.aistudy.database.Profile
import com.aistudy.aistudy.database.ProfileDao
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.roundToInt

object XpRules {

    const val ASK = 50
    const val REVIEW = 10
    const val QUIZ_CORRECT = 10
    const val QUIZ_COMPLETE_BONUS = 20

    val LEVEL_XP = intArrayOf(0, 100, 250, 500, 1000, 2000)

    fun levelFromXp(xp: Int): Int {
        var level = 1
        for (i in LEVEL_XP.indices) {
            if (xp >= LEVEL_XP[i]) {
                level = i + 1
            }
        }
        return level
    }

    fun nextLevelThreshold(xp: Int): Int {
        val level = levelFromXp(xp)
        if (level >= LEVEL_XP.size) {
            return LEVEL_XP[LEVEL_XP.size - 1]
        }
        return LEVEL_XP[level]
    }

    fun progressPercentToNext(xp: Int): Int {
        val level = levelFromXp(xp)
        if (level >= LEVEL_XP.size) {
            return 100
        }
        val start = LEVEL_XP[level - 1]
        val end = LEVEL_XP[level]
        val span = end - start
        if (span <= 0) {
            return 100
        }
        return minOf(100, ((xp - start) * 100f / span).roundToInt())
    }

    fun xpToNextLevel(xp: Int): Int {
        val next = nextLevelThreshold(xp)
        val remain = next - xp
        return maxOf(0, remain)
    }

    fun currentWeekStartMs(): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val dow = cal.get(Calendar.DAY_OF_WEEK)
        val daysFromMonday = if (dow == Calendar.SUNDAY) 6 else dow - Calendar.MONDAY
        cal.add(Calendar.DAY_OF_MONTH, -daysFromMonday)
        return cal.timeInMillis
    }

    fun ensureWeek(profile: Profile?) {
        if (profile == null) {
            return
        }
        val weekStart = currentWeekStartMs()
        if (profile.weekStartMs != weekStart) {
            profile.weekStartMs = weekStart
            profile.weeklyXp = 0
        }
    }

    fun addXp(profile: Profile?, profileDao: ProfileDao?, amount: Int) {
        if (profile == null || profileDao == null || amount <= 0) {
            return
        }
        ensureWeek(profile)
        profile.xp += amount
        profile.weeklyXp += amount
        profileDao.update(profile)
    }

    fun recordDailyActivity(profile: Profile?, profileDao: ProfileDao?) {
        if (profile == null || profileDao == null) {
            return
        }
        val today = dayKey(0)
        if (today == profile.lastActiveDay) {
            return
        }
        val yesterday = dayKey(-1)
        if (yesterday == profile.lastActiveDay) {
            profile.streak = profile.streak + 1
        } else {
            profile.streak = 1
        }
        profile.lastActiveDay = today
        profileDao.update(profile)
    }

    fun isNearNextLevel(xp: Int): Boolean {
        val remain = xpToNextLevel(xp)
        return remain in 1..50
    }

    fun streakAtRisk(profile: Profile?): Boolean {
        if (profile == null || profile.streak <= 0) {
            return false
        }
        val today = dayKey(0)
        return profile.lastActiveDay != null &&
            today != profile.lastActiveDay &&
            dayKey(-1) == profile.lastActiveDay
    }

    private fun dayKey(dayOffset: Int): String {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_MONTH, dayOffset)
        return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.time)
    }

    fun todayKey(): String {
        return dayKey(0)
    }
}
