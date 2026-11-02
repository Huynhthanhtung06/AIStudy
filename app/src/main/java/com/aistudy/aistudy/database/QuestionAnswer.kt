package com.aistudy.aistudy.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "question_answers")
class QuestionAnswer {

    @PrimaryKey(autoGenerate = true)
    var id: Long = 0

    var userId: Long = 0
    var subject: String? = null
    var question: String? = null
    var steps: String? = null
    var finalAnswer: String? = null
    var starred: Boolean = false
    var createdAt: Long = 0
}
