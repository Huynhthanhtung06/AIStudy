package com.aistudy.aistudy.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "profiles")
class Profile {

    @PrimaryKey
    var userId: Long = 0

    var onboarded: Boolean = false
    var level: String? = null
    var subjects: String? = null
    var style: String? = null
    var xp: Int = 0
    var streak: Int = 0
    var avatarId: String? = null
    var themeId: String? = null
    var weeklyXp: Int = 0
    var weekStartMs: Long = 0
    var lastActiveDay: String? = null
}
