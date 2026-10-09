package com.dmwnezes.palavreiro.system

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.dmwnezes.palavreiro.MainActivity
import com.dmwnezes.palavreiro.R
import com.dmwnezes.palavreiro.data.Store
import java.time.LocalDate
import java.time.ZoneId

/**
 * Contagem até a próxima palavra, como notificação "ao vivo" (Live Update do Android 16).
 * No Samsung com One UI 8 ela aparece na Now Bar e na tela de bloqueio; nos outros celulares,
 * como chip com a contagem na barra de status. Aparece depois do Termo do dia e some sozinha à meia-noite.
 */
object NextWordLive {
    private const val CHANNEL = "proxima_palavra"
    private const val ID = 3

    /** Pedido de promoção a Live Update (Notification.EXTRA_REQUEST_PROMOTED_ONGOING do Android 16). */
    private const val EXTRA_REQUEST_PROMOTED_ONGOING = "android.requestPromotedOngoing"

    /** Ação da tela de ajustes "notificações ao vivo" deste app (Android 16). */
    private const val ACTION_PROMOTED_SETTINGS = "android.settings.MANAGE_APP_PROMOTED_NOTIFICATIONS"

    /** Mostra, atualiza ou tira a contagem conforme o dia e o ajuste do perfil. */
    fun update(context: Context, store: Store = Store(context)) {
        val nm = NotificationManagerCompat.from(context)
        val status = DailyStatus.read(store)
        val zone = ZoneId.systemDefault()
        val midnight = LocalDate.now().plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val left = midnight - System.currentTimeMillis()
        if (!store.liveCountdown || !Reminder.canNotify(context) || !status.termo || left <= 0) {
            nm.cancel(ID)
            return
        }
        val sys = context.getSystemService(NotificationManager::class.java) ?: return
        sys.createNotificationChannel(
            NotificationChannel(CHANNEL, "Contagem da próxima palavra", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Mostra quanto falta para a palavra nova (Now Bar no Samsung)."
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
        val text = message(status)
        val extras = Bundle().apply {
            putBoolean(EXTRA_REQUEST_PROMOTED_ONGOING, true)
            // Campos da Samsung (One UI 7) para Live Notifications/Now Bar; ignorados nos outros celulares.
            putInt("android.ongoingActivityNoti.style", 1)
            putString("android.ongoingActivityNoti.primaryInfo", "Nova palavra à meia-noite")
            putString("android.ongoingActivityNoti.secondaryInfo", text)
            putString("android.ongoingActivityNoti.nowbarPrimaryInfo", "Nova palavra")
            putString("android.ongoingActivityNoti.nowbarSecondaryInfo", text)
        }
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Nova palavra à meia-noite")
            .setContentText(text)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            // A contagem corre sozinha: o relógio conta até a meia-noite sem o app precisar atualizar.
            .setWhen(midnight)
            .setShowWhen(true)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setTimeoutAfter(left)
            .setContentIntent(open)
            .addExtras(extras)
            .build()
        runCatching { nm.notify(ID, n) }
    }

    fun message(status: DailyStatus): String {
        val done = "${status.doneCount} de ${status.total} desafios feitos"
        return if (status.streak > 0) "$done · 🔥 ${status.streak} ${if (status.streak == 1) "dia" else "dias"}" else done
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
