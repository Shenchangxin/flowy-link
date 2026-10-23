package com.flowy.link

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner

object LinkNotifier {
    private const val channelId = "flowy-link-wake"

    fun showIfBackground(context: Context, phase: String) {
        val state = ProcessLifecycleOwner.get().lifecycle.currentState
        if (state.isAtLeast(Lifecycle.State.STARTED)) return
        val text = when (phase) {
            "confirmation.pending" -> "有一项操作需要你确认"
            "turn.failed" -> "有一条回复没有完成"
            else -> "有一条回复已经完成"
        }
        val manager = context.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26 && manager.getNotificationChannel(channelId) == null) {
            manager.createNotificationChannel(NotificationChannel(channelId, "电脑状态", NotificationManager.IMPORTANCE_DEFAULT))
        }
        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentTitle("电脑上的 Flowy")
            .setContentText(text)
            .setAutoCancel(true)
            .build()
        manager.notify(phase.hashCode(), notification)
    }
}
