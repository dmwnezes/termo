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
 * Lembretes:
 * - diário, no horário escolhido, só se faltar algum desafio do dia (e diz quais faltam);
 * - alerta de sequência às 21h, só se o Termo do dia ainda não foi jogado e há sequência em jogo.
 */
object Reminder {
    private const val CHANNEL = "lembrete"
    private const val REQUEST = 4201
    private const val REQUEST_STREAK = 4202
    const val KIND_STREAK = "streak"
    const val STREAK_HOUR = 21

    fun schedule(context: Context) {
        val store = Store(context)
        val alarm = context.getSystemService(AlarmManager::class.java) ?: return
        val daily = pending(context, REQUEST, null)
        val streak = pending(context, REQUEST_STREAK, KIND_STREAK)
        alarm.cancel(daily); alarm.cancel(streak)
        if (!store.reminder) return
        // Inexato: não precisa de permissão especial e economiza bateria.
        alarm.setInexactRepeating(AlarmManager.RTC_WAKEUP, nextAt(store.reminderHour), AlarmManager.INTERVAL_DAY, daily)
        if (store.streakAlert) alarm.setInexactRepeating(AlarmManager.RTC_WAKEUP, nextAt(STREAK_HOUR), AlarmManager.INTERVAL_DAY, streak)
    }

    private fun nextAt(hour: Int): Long {
        var next = LocalDateTime.now().withHour(hour).withMinute(0).withSecond(0).withNano(0)
        if (!next.isAfter(LocalDateTime.now())) next = next.plusDays(1)
        return next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }

    private fun pending(context: Context, request: Int, kind: String?): PendingIntent =
        PendingIntent.getBroadcast(
            context, request, Intent(context, ReminderReceiver::class.java).apply { kind?.let { putExtra("kind", it) } },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    /** Nomes dos desafios do dia que ainda faltam. */
    fun missing(status: DailyStatus): List<String> = listOfNotNull(
        "Termo".takeIf { !status.termo }, "Dueto".takeIf { !status.dueto }, "Quarteto".takeIf { !status.quarteto },
        "Conexões".takeIf { !status.conexoes },
    )

    /** Texto do lembrete, ou null se não precisa avisar. */
    fun message(status: DailyStatus, kind: String?): Pair<String, String>? {
        if (kind == KIND_STREAK) {
            if (status.termo || status.streak <= 0) return null
            val d = if (status.streak == 1) "dia" else "dias"
            return "Sua sequência acaba hoje! 🔥" to "Jogue o Termo antes da meia-noite para manter seus ${status.streak} $d seguidos."
        }
        val left = missing(status)
        if (left.isEmpty()) return null
        val list = if (left.size == 1) left[0] else left.dropLast(1).joinToString(", ") + " e " + left.last()
        val title = if (left.size == status.total) "Seus desafios do dia chegaram" else "Falta${if (left.size > 1) "m" else ""} ${left.size} desafio${if (left.size > 1) "s" else ""} hoje"
        val text = if (!status.termo && status.streak > 0) "Não perca sua sequência de ${status.streak} ${if (status.streak == 1) "dia" else "dias"} 🔥 · Falta: $list"
        else "Falta: $list"
        return title to text
    }

    fun canNotify(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun notifyIfNeeded(context: Context, kind: String? = null) {
        val store = Store(context)
        if (!store.reminder || !canNotify(context)) return
        if (kind == KIND_STREAK && !store.streakAlert) return
        val status = DailyStatus.read(store)
        val (title, text) = message(status, kind) ?: return
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Lembrete diário", NotificationManager.IMPORTANCE_DEFAULT))
        val open = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).putExtra("dest", if (kind == KIND_STREAK || !status.termo) "TERMO" else "HOME").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(if (kind == KIND_STREAK) 2 else 1, n) }
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Reminder.notifyIfNeeded(context, intent.getStringExtra("kind"))
        Widget.refresh(context)
        NextWordLive.update(context)
    }
}

/** Depois de reiniciar o celular, o Android apaga os alarmes: agenda de novo. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            Reminder.schedule(context)
            Widget.refresh(context)
            NextWordLive.update(context)
        }
    }
}
