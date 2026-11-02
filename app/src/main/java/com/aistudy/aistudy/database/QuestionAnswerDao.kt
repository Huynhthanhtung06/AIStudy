package com.aistudy.aistudy.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface QuestionAnswerDao {

    @Insert
    fun insert(qa: QuestionAnswer): Long

    @Query("SELECT * FROM question_answers WHERE userId = :userId ORDER BY createdAt DESC")
    fun getAllByUserId(userId: Long): List<QuestionAnswer>

    @Query("SELECT * FROM question_answers WHERE userId = :userId ORDER BY createdAt DESC LIMIT 1")
    fun getLatestByUserId(userId: Long): QuestionAnswer?

    @Query("SELECT * FROM question_answers WHERE id = :id LIMIT 1")
    fun getById(id: Long): QuestionAnswer?

    @Query("UPDATE question_answers SET starred = NOT starred WHERE id = :id")
    fun toggleStar(id: Long)

    @Query("SELECT COUNT(*) FROM question_answers WHERE userId = :userId AND subject = :subject")
    fun countBySubject(userId: Long, subject: String): Int
}
