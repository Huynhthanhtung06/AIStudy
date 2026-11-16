package com.aistudy.aistudy.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "quiz_sessions")
class QuizSession {

    @PrimaryKey(autoGenerate = true)
    var id: Long = 0

    var userId: Long = 0
    var subject: String? = null
    var sourceQaIds: String? = null
    var createdAt: Long = 0
    var score: Int = 0
    var total: Int = 0
    var completed: Boolean = false
}
