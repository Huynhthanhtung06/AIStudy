package com.aistudy.aistudy.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface FriendDao {

    @Insert
    fun insert(friend: Friend): Long

    @Query("SELECT COUNT(*) FROM friends WHERE ownerUserId = :ownerUserId")
    fun countByOwner(ownerUserId: Long): Int

    @Query("SELECT * FROM friends WHERE ownerUserId = :ownerUserId ORDER BY weeklyXp DESC")
    fun getByOwner(ownerUserId: Long): List<Friend>
}
