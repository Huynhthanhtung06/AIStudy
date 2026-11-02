package com.aistudy.aistudy.auth

import java.security.MessageDigest

object PasswordHasher {

    fun hash(plainPassword: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest(plainPassword.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun matches(plainPassword: String, stored: String?): Boolean {
        if (stored == null) {
            return false
        }
        if (plainPassword == stored) {
            return true
        }
        return hash(plainPassword) == stored
    }
}
