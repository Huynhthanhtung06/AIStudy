package com.aistudy.aistudy.xp

import com.aistudy.aistudy.R

object UnlockCatalog {

    const val AVATAR_FOX = "fox"
    const val AVATAR_OWL = "owl"
    const val AVATAR_CAT = "cat"
    const val AVATAR_ROCKET = "rocket"

    val AVATARS = arrayOf(
        AVATAR_FOX, AVATAR_OWL, AVATAR_CAT, AVATAR_ROCKET
    )

    const val THEME_DEFAULT = "default"
    const val THEME_NIGHT = "night"
    const val THEME_PASTEL = "pastel"

    val THEMES = arrayOf(
        THEME_DEFAULT, THEME_NIGHT, THEME_PASTEL
    )

    fun isAvatarUnlocked(avatarId: String?, level: Int): Boolean {
        val required = requiredLevelForAvatar(avatarId)
        return required > 0 && level >= required
    }

    fun isThemeUnlocked(themeId: String?, level: Int): Boolean {
        val required = requiredLevelForTheme(themeId)
        return required > 0 && level >= required
    }

    fun requiredLevelForAvatar(avatarId: String?): Int {
        val index = AVATARS.indexOf(avatarId)
        return if (index >= 0) index + 1 else -1
    }

    fun requiredLevelForTheme(themeId: String?): Int {
        val index = THEMES.indexOf(themeId)
        return if (index >= 0) index + 1 else -1
    }

    fun emojiForAvatar(avatarId: String?): String {
        return when (avatarId) {
            AVATAR_OWL -> "🦉"
            AVATAR_CAT -> "🐱"
            AVATAR_ROCKET -> "🚀"
            else -> "🦊"
        }
    }

    fun labelForTheme(themeId: String?): String {
        return when (themeId) {
            THEME_NIGHT -> "Night"
            THEME_PASTEL -> "Pastel"
            else -> "Default"
        }
    }

    fun backgroundColorRes(themeId: String?): Int {
        return when (themeId) {
            THEME_NIGHT -> R.color.theme_night_bg
            THEME_PASTEL -> R.color.theme_pastel_bg
            else -> R.color.theme_default_bg
        }
    }

    fun normalizeAvatarId(avatarId: String?): String {
        if (avatarId.isNullOrEmpty()) {
            return AVATAR_FOX
        }
        return AVATARS.firstOrNull { it == avatarId } ?: AVATAR_FOX
    }

    fun normalizeThemeId(themeId: String?): String {
        if (themeId.isNullOrEmpty()) {
            return THEME_DEFAULT
        }
        return THEMES.firstOrNull { it == themeId } ?: THEME_DEFAULT
    }
}
