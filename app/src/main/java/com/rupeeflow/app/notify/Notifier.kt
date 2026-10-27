package com.rupeeflow.app.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.rupeeflow.app.MainActivity
import com.rupeeflow.app.R

class Notifier(private val context: Context) {

    companion object {
        const val CH_REMINDERS = "reminders"
        const val CH_BUDGETS = "budgets"
        const val CH_BILLS = "bills"
        const val CH_CYCLE = "cycle"

        const val ACTION_QUICK_ADD = "com.rupeeflow.app.QUICK_ADD"
        const val ACTION_VOICE_ADD = "com.rupeeflow.app.VOICE_ADD"
        const val ACTION_OPEN_BILLS = "com.rupeeflow.app.OPEN_BILLS"
        const val ACTION_OPEN_BUDGETS = "com.rupeeflow.app.OPEN_BUDGETS"
        const val ACTION_OPEN_INSIGHTS = "com.rupeeflow.app.OPEN_INSIGHTS"
        const val EXTRA_TX_TYPE = "tx_type"
    }

    fun createChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannels(
            listOf(
                NotificationChannel(CH_REMINDERS, "Daily reminders", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "A nudge to log the day's spending"
                },
                NotificationChannel(CH_BUDGETS, "Budget alerts", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "When a budget crosses 50/75/90/100%"
                },
                NotificationChannel(CH_BILLS, "Bills & subscriptions", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Upcoming and due recurring payments"
                },
                NotificationChannel(CH_CYCLE, "Salary cycle & savings", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Salary day, new cycle and savings plan reminders"
                },
            ),
        )
    }

    fun canNotify(): Boolean {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return false
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    fun openAppIntent(action: String? = null, requestCode: Int = 0, extraType: String? = null): PendingIntent {
        val i = Intent(context, MainActivity::class.java).apply {
            this.action = action ?: Intent.ACTION_MAIN
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            extraType?.let { putExtra(EXTRA_TX_TYPE, it) }
        }
        return PendingIntent.getActivity(context, requestCode, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    fun actionIntent(action: String, requestCode: Int, extras: Map<String, String> = emptyMap()): PendingIntent {
        val i = Intent(context, ActionReceiver::class.java).apply {
            this.action = action
            extras.forEach { (k, v) -> putExtra(k, v) }
        }
        return PendingIntent.getBroadcast(context, requestCode, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    data class Action(val label: String, val intent: PendingIntent)

    fun show(
        id: Int,
        channel: String,
        title: String,
        text: String,
        tap: PendingIntent = openAppIntent(),
        actions: List<Action> = emptyList(),
    ) {
        if (!canNotify()) return
        val b = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_stat_rupee)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(tap)
            .setAutoCancel(true)
            .setColor(0xFF5B4CF0.toInt())
            // Amounts are personal: hide details on the lock screen.
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(
                NotificationCompat.Builder(context, channel).setSmallIcon(R.drawable.ic_stat_rupee)
                    .setContentTitle("RupeeFlow").setContentText("You have a money update").build(),
            )
        actions.forEach { b.addAction(0, it.label, it.intent) }
        try {
            NotificationManagerCompat.from(context).notify(id, b.build())
        } catch (_: SecurityException) {
            // Permission revoked between the check and the call; nothing to do.
        }
    }

    fun cancel(id: Int) = NotificationManagerCompat.from(context).cancel(id)
}
