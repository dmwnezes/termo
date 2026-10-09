package com.dmwnezes.palavreiro.system

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.dmwnezes.palavreiro.MainActivity
import com.dmwnezes.palavreiro.R
import com.dmwnezes.palavreiro.data.Store
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Lembrete diário: uma notificação no horário escolhido,
 * só se a palavra do dia ainda não foi jogada.
 */
object Reminder {
    private const val CHANNEL = "lembrete"
    private const val REQUEST = 4201

    fun schedule(context: Context) {
        val store = Store(context)
        val alarm = context.getSystemService(AlarmManager::class.java) ?: return
        val pi = pending(context)
        alarm.cancel(pi)
        if (!store.reminder) return
        var next = LocalDateTime.now().withHour(store.reminderHour).withMinute(0).withSecond(0).withNano(0)
        if (!next.isAfter(LocalDateTime.now())) next = next.plusDays(1)
        val at = next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        // Inexato: não precisa de permissão especial e economiza bateria.
        alarm.setInexactRepeating(AlarmManager.RTC_WAKEUP, at, AlarmManager.INTERVAL_DAY, pi)
    }

    private fun pending(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context, REQUEST, Intent(context, ReminderReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    fun canNotify(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun notifyIfNeeded(context: Context) {
        val store = Store(context)
        if (!store.reminder || !canNotify(context)) return
        val status = DailyStatus.read(store)
        if (status.termo) return
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Lembrete diário", NotificationManager.IMPORTANCE_DEFAULT))
        val open = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).putExtra("dest", "TERMO").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val text = if (status.streak > 0) "Não perca sua sequência de ${status.streak} ${if (status.streak == 1) "dia" else "dias"} 🔥"
        else "Uma palavra nova está esperando por você."
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Sua palavra do dia chegou")
            .setContentText(text)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(1, n) }
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Reminder.notifyIfNeeded(context)
        Widget.refresh(context)
    }
}

/** Depois de reiniciar o celular, o Android apaga os alarmes: agenda de novo. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            Reminder.schedule(context)
            Widget.refresh(context)
        }
    }
}
