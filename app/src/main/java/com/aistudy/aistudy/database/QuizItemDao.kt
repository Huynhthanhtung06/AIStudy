package com.aistudy.aistudy.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update

@Dao
interface QuizItemDao {

    @Insert
    fun insert(item: QuizItem): Long

    @Update
    fun update(item: QuizItem)

    @Query("SELECT * FROM quiz_items WHERE sessionId = :sessionId ORDER BY id ASC")
    fun getBySessionId(sessionId: Long): List<QuizItem>

    @Query("SELECT COUNT(*) FROM quiz_items WHERE sessionId IN (SELECT id FROM quiz_sessions WHERE userId = :userId) AND isCorrect IS NOT NULL")
    fun countAttemptedByUserId(userId: Long): Int

    @Query("SELECT COUNT(*) FROM quiz_items WHERE sessionId IN (SELECT id FROM quiz_sessions WHERE userId = :userId) AND isCorrect = 1")
    fun countCorrectByUserId(userId: Long): Int
}
