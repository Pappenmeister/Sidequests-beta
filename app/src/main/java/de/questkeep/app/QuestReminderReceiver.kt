package de.questkeep.app

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import java.util.concurrent.TimeUnit

/**
 * Local reminder support. Call schedule(context, questId, title, dueAtMillis)
 * when a quest is saved with a due date. Call cancel(context, questId) when removed
 * or when its due date is cleared/completed.
 */
object QuestReminderScheduler {
    private const val CHANNEL_ID = "sidequests_due_dates"

    fun schedule(context: Context, questId: String, title: String, dueAtMillis: Long) {
        if (dueAtMillis <= System.currentTimeMillis()) return
        ensureChannel(context)
        val intent = Intent(context, QuestReminderReceiver::class.java).apply {
            putExtra("quest_id", questId)
            putExtra("quest_title", title)
        }
        val pending = PendingIntent.getBroadcast(
            context, questId.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val trigger = (dueAtMillis - TimeUnit.MINUTES.toMillis(15)).coerceAtLeast(System.currentTimeMillis() + 1000)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pending)
        } else {
            alarm.set(AlarmManager.RTC_WAKEUP, trigger, pending)
        }
    }

    fun cancel(context: Context, questId: String) {
        val intent = Intent(context, QuestReminderReceiver::class.java)
        val pending = PendingIntent.getBroadcast(
            context, questId.hashCode(), intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pending != null) {
            (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(pending)
            pending.cancel()
        }
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (manager.getNotificationChannel(CHANNEL_ID) == null) {
                manager.createNotificationChannel(
                    NotificationChannel(CHANNEL_ID, "Quest-Termine", NotificationManager.IMPORTANCE_DEFAULT).apply {
                        description = "Erinnerungen kurz vor dem Termin einer Quest"
                    }
                )
            }
        }
    }

    internal fun channelId() = CHANNEL_ID
}

class QuestReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val title = intent.getStringExtra("quest_title")?.takeIf { it.isNotBlank() } ?: "Quest-Termin"
        val questId = intent.getStringExtra("quest_id").orEmpty()
        val notification = NotificationCompat.Builder(context, QuestReminderScheduler.channelId())
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Quest steht bald an")
            .setContentText(title)
            .setStyle(NotificationCompat.BigTextStyle().bigText("Dein Termin für „$title“ steht an."))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(questId.hashCode(), notification)
    }
}
