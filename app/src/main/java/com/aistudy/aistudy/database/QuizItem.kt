package com.aistudy.aistudy.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "quiz_items")
class QuizItem {

    @PrimaryKey(autoGenerate = true)
    var id: Long = 0

    var sessionId: Long = 0
    var type: String? = null
    var prompt: String? = null
    var optionsJson: String? = null
    var correctAnswer: String? = null
    var explanation: String? = null
    var userAnswer: String? = null

    @ColumnInfo(name = "isCorrect")
    var correct: Boolean? = null
}
