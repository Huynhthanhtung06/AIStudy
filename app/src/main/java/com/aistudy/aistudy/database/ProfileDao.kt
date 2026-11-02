package com.aistudy.aistudy.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface ProfileDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insert(profile: Profile): Long

    @Update
    fun update(profile: Profile)

    @Query("SELECT * FROM profiles WHERE userId = :userId LIMIT 1")
    fun getByUserId(userId: Long): Profile?
}
