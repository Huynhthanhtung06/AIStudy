package com.aistudy.aistudy.xp

import com.aistudy.aistudy.database.Friend
import com.aistudy.aistudy.database.FriendDao

object FriendSeeder {

    fun ensureSeeded(friendDao: FriendDao?, ownerUserId: Long) {
        if (friendDao == null || ownerUserId < 0) {
            return
        }
        if (friendDao.countByOwner(ownerUserId) > 0) {
            return
        }
        insert(friendDao, ownerUserId, "Alex", 80)
        insert(friendDao, ownerUserId, "Sam", 120)
        insert(friendDao, ownerUserId, "Jordan", 45)
        insert(friendDao, ownerUserId, "Riley", 200)
    }

    private fun insert(dao: FriendDao, ownerUserId: Long, name: String, weeklyXp: Int) {
        val friend = Friend()
        friend.ownerUserId = ownerUserId
        friend.displayName = name
        friend.weeklyXp = weeklyXp
        dao.insert(friend)
    }
}
