package com.dmwnezes.palavreiro.system

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.provider.Settings
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.dmwnezes.palavreiro.MainActivity
import com.dmwnezes.palavreiro.R
import com.dmwnezes.palavreiro.data.Store
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * "Barra do dia": contagem até a próxima palavra como notificação ao vivo (Live Update do Android 16).
 * No Samsung com One UI 8 aparece na Now Bar. Visual limpo: título "Próxima palavra", o tempo que falta
 * e uma barra que vai enchendo até a meia-noite, pintada com as cores da marca, com uma estrela no fim.
 * Aparece depois do Termo do dia e some sozinha à meia-noite.
 */
object NextWordLive {
    private const val CHANNEL = "proxima_palavra"
    private const val ID = 3
    private const val TITLE = "Próxima palavra"
    /** Minutos do dia: a barra vai de 0 (meia-noite de ontem) a 1440 (meia-noite de hoje). */
    const val DAY_MINUTES = 24 * 60

    /** Cores da marca (verde, amarelo, azul, amarelo, verde), uma por quinto do dia. */
    val SEGMENT_COLORS = intArrayOf(0xFF5FB873.toInt(), 0xFFE6C14F.toInt(), 0xFF4A72CF.toInt(), 0xFFE6C14F.toInt(), 0xFF5FB873.toInt())

    private const val EXTRA_REQUEST_PROMOTED_ONGOING = "android.requestPromotedOngoing"
    private const val ACTION_PROMOTED_SETTINGS = "android.settings.MANAGE_APP_PROMOTED_NOTIFICATIONS"

    /** Minutos já passados hoje (posição da barra). */
    fun minutesElapsed(now: LocalDateTime = LocalDateTime.now()): Int =
        ChronoUnit.MINUTES.between(now.toLocalDate().atStartOfDay(), now).toInt().coerceIn(0, DAY_MINUTES)

    /** Mostra, atualiza ou tira a barra conforme o dia e o ajuste do perfil. */
    fun update(context: Context, store: Store = Store(context)) {
        val nm = NotificationManagerCompat.from(context)
        val status = DailyStatus.read(store)
        val zone = ZoneId.systemDefault()
        val midnight = LocalDate.now().plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val left = midnight - System.currentTimeMillis()
        if (!store.liveCountdown || !Reminder.canNotify(context) || !status.termo || left <= 0) {
            nm.cancel(ID)
            scheduleTicks(context, false)
            return
        }
        context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(
            NotificationChannel(CHANNEL, "Próxima palavra", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Barra do dia até a palavra nova (Now Bar no Samsung)."
                setSound(null, null)
                enableVibration(false)
                setShowBadge(false)
            }
        )
        val open = PendingIntent.getActivity(
            context, 30,
            Intent(context, MainActivity::class.java).putExtra("dest", "HOME").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val elapsed = minutesElapsed()
        val n = (if (Build.VERSION.SDK_INT >= 36) runCatching { live(context, midnight, left, elapsed, open) }.getOrNull() else null)
            ?: classic(context, midnight, left, elapsed, open)
        runCatching { nm.notify(ID, n) }
        // A barra anda sozinha: atualiza a cada ~15 minutos enquanto estiver na tela.
        scheduleTicks(context, true)
    }

    /** Android 16+: barra segmentada com as cores da marca (ProgressStyle) e pedido de Live Update. */
    @RequiresApi(36)
    private fun live(context: Context, midnight: Long, left: Long, elapsed: Int, open: PendingIntent): Notification {
        val part = DAY_MINUTES / SEGMENT_COLORS.size
        val style = Notification.ProgressStyle()
            .setStyledByProgress(true)
            .setProgressSegments(SEGMENT_COLORS.map { Notification.ProgressStyle.Segment(part).setColor(it) })
            .setProgress(elapsed)
            .setProgressTrackerIcon(Icon.createWithResource(context, R.drawable.ic_live_tracker))
            .setProgressEndIcon(Icon.createWithResource(context, R.drawable.ic_live_end))
        return Notification.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(TITLE)
            .setStyle(style)
            .setCategory(Notification.CATEGORY_STATUS)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setWhen(midnight)
            .setShowWhen(true)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setTimeoutAfter(left)
            .setContentIntent(open)
            // Pedido de Live Update (Notification.EXTRA_REQUEST_PROMOTED_ONGOING).
            .addExtras(android.os.Bundle().apply { putBoolean(EXTRA_REQUEST_PROMOTED_ONGOING, true) })
            .build()
    }

    /** Antes do Android 16: mesma ideia com a barra simples do sistema. */
    private fun classic(context: Context, midnight: Long, left: Long, elapsed: Int, open: PendingIntent): Notification =
        NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(TITLE)
            .setProgress(DAY_MINUTES, elapsed, false)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setWhen(midnight)
            .setShowWhen(true)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setTimeoutAfter(left)
            .setContentIntent(open)
            .addExtras(android.os.Bundle().apply { putBoolean(EXTRA_REQUEST_PROMOTED_ONGOING, true) })
            .build()

    /** Liga ou desliga a atualização da barra a cada ~15 minutos (sem acordar o celular). */
    private fun scheduleTicks(context: Context, on: Boolean) {
        val alarm = context.getSystemService(AlarmManager::class.java) ?: return
        val pi = PendingIntent.getBroadcast(
            context, 4203, Intent(context, NextWordReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        alarm.cancel(pi)
        if (on) alarm.setInexactRepeating(
            AlarmManager.RTC, System.currentTimeMillis() + AlarmManager.INTERVAL_FIFTEEN_MINUTES,
            AlarmManager.INTERVAL_FIFTEEN_MINUTES, pi,
        )
    }

    /** Android 16+: abre o ajuste "notificações ao vivo" do Palavreiro (ou as notificações do app). */
    fun openSettings(context: Context) {
        val intent = if (Build.VERSION.SDK_INT >= 36) Intent(ACTION_PROMOTED_SETTINGS)
        else Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        intent.putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }.onFailure {
            runCatching {
                context.startActivity(
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
        }
    }
}

/** Atualiza a posição da barra do dia. */
class NextWordReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = NextWordLive.update(context)
}
