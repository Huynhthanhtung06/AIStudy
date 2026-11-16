package com.aistudy.aistudy.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "friends")
class Friend {

    @PrimaryKey(autoGenerate = true)
    var id: Long = 0

    var ownerUserId: Long = 0
    var displayName: String? = null
    var weeklyXp: Int = 0
}
