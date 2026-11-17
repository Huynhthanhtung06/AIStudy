package com.aistudy.aistudy.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update

@Dao
interface QuestionAnswerDao {

    @Insert
    fun insert(qa: QuestionAnswer): Long

    @Update
    fun update(qa: QuestionAnswer)

    @Query("SELECT * FROM question_answers WHERE userId = :userId ORDER BY createdAt DESC")
    fun getAllByUserId(userId: Long): List<QuestionAnswer>

    @Query("SELECT * FROM question_answers WHERE userId = :userId ORDER BY createdAt DESC LIMIT 1")
    fun getLatestByUserId(userId: Long): QuestionAnswer?

    @Query("SELECT * FROM question_answers WHERE id = :id LIMIT 1")
    fun getById(id: Long): QuestionAnswer?

    @Query("UPDATE question_answers SET starred = NOT starred WHERE id = :id")
    fun toggleStar(id: Long)

    @Query("UPDATE question_answers SET extrasJson = :extrasJson WHERE id = :id")
    fun updateExtras(id: Long, extrasJson: String?)

    @Query("UPDATE question_answers SET reviewedAt = :reviewedAt WHERE id = :id")
    fun updateReviewedAt(id: Long, reviewedAt: Long)

    @Query("UPDATE question_answers SET reviewedSeconds = reviewedSeconds + :delta WHERE id = :id")
    fun addReviewedSeconds(id: Long, delta: Int)

    @Query("SELECT COALESCE(SUM(reviewedSeconds), 0) FROM question_answers WHERE userId = :userId")
    fun sumReviewedSecondsByUserId(userId: Long): Int

    @Query("SELECT subject FROM question_answers WHERE userId = :userId GROUP BY subject ORDER BY COUNT(*) DESC LIMIT 1")
    fun getTopSubjectByUserId(userId: Long): String?

    @Query("SELECT COUNT(*) FROM question_answers WHERE userId = :userId AND subject = :subject")
    fun countBySubject(userId: Long, subject: String): Int

    @Query("SELECT * FROM question_answers WHERE userId = :userId AND subject = :subject ORDER BY createdAt DESC LIMIT :limit")
    fun getRecentByUserIdAndSubject(userId: Long, subject: String, limit: Int): List<QuestionAnswer>
}
