package com.aistudy.aistudy.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.aistudy.aistudy.MainActivity
import com.aistudy.aistudy.R

object NotificationHelper {

    const val CHANNEL_LEARNING = "learning"

    const val ID_BOOKMARK = 13001
    const val ID_MISTAKES = 13002
    const val ID_MOTIVATIONAL = 13003
    const val ID_SUMMARY = 13004

    fun ensureChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_LEARNING,
            context.getString(R.string.notify_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT
        )
        channel.description = context.getString(R.string.notify_channel_desc)
        context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    fun show(
        context: Context,
        notificationId: Int,
        title: String?,
        body: String?,
        openTab: String?,
        qaId: Long
    ) {
        ensureChannel(context)

        val intent = Intent(context, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        if (openTab != null) {
            intent.putExtra(MainActivity.EXTRA_OPEN_TAB, openTab)
        }
        if (qaId > 0) {
            intent.putExtra(MainActivity.EXTRA_QA_ID, qaId)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_LEARNING)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)

        NotificationManagerCompat.from(context).notify(notificationId, builder.build())
    }
}
