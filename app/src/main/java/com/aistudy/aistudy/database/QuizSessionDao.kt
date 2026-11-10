package com.aistudy.aistudy.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update

@Dao
interface QuizSessionDao {

    @Insert
    fun insert(session: QuizSession): Long

    @Update
    fun update(session: QuizSession)

    @Query("SELECT * FROM quiz_sessions WHERE id = :id LIMIT 1")
    fun getById(id: Long): QuizSession?

    @Query("SELECT * FROM quiz_sessions WHERE userId = :userId ORDER BY createdAt DESC")
    fun getAllByUserId(userId: Long): List<QuizSession>
}
