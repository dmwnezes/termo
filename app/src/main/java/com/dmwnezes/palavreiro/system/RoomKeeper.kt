package com.dmwnezes.palavreiro.system

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.dmwnezes.palavreiro.MainActivity
import com.dmwnezes.palavreiro.R
import com.dmwnezes.palavreiro.data.Store
import com.dmwnezes.palavreiro.game.Multi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient

/**
 * Segura a sala da partida com amigo por até 10 minutos enquanto você sai do app para mandar o link
 * (mesmo com o app fechado). Fica ouvindo a sala e avisa quando o amigo entra (ou, para o convidado,
 * quando o anfitrião começa). Ao tocar no aviso, o app volta direto para a sala.
 */
class RoomKeeper : Service() {
    companion object {
        private const val CH_ONGOING = "sala"
        private const val CH_ALERT = "sala_alerta"
        private const val ONGOING_ID = 4210
        private const val ALERT_ID = 4211
        private const val KEY = "mp_session"

        /** true enquanto o app está na tela (aí o aviso de "entrou" não precisa aparecer). */
        @Volatile var appVisible = false

        fun session(store: Store): Multi.Session? = Multi.Session.decode(store.text(KEY))?.takeIf { it.valid() }

        /** Guarda a sala e liga o serviço. */
        fun save(context: Context, store: Store, s: Multi.Session) {
            store.setText(KEY, s.encode())
            runCatching { ContextCompat.startForegroundService(context, Intent(context, RoomKeeper::class.java)) }
        }

        /** Esquece a sala (partida começou, saiu de propósito, sala cheia ou expirou). */
        fun clear(context: Context, store: Store) {
            store.setText(KEY, null)
            runCatching { context.stopService(Intent(context, RoomKeeper::class.java)) }
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    private var watching: String? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val store = Store(applicationContext)
        val s = session(store)
        if (s == null) { stopSelf(); return START_NOT_STICKY }
        channels()
        runCatching {
            ServiceCompat.startForeground(
                this, ONGOING_ID, ongoing(s),
                if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0,
            )
        }.onFailure { stopSelf(); return START_NOT_STICKY }
        if (watching == s.code + s.id && job?.isActive == true) return START_NOT_STICKY
        job?.cancel()
        watching = s.code + s.id
        job = scope.launch {
            launch {
                delay((s.expiresAt - System.currentTimeMillis()).coerceAtLeast(0))
                clear(applicationContext, store)
            }
            val room = s.room ?: return@launch
            var hostName: String? = null
            var alerted = false
            Ntfy(OkHttpClient()).events(room.topic).collect { e ->
                if (e !is Ntfy.Event.Message) return@collect
                when (val m = Multi.decode(e.text)) {
                    is Multi.Msg.Hello -> when {
                        m.host && m.id != s.id -> hostName = m.name
                        s.host && !m.host && m.id != s.id && !alerted -> {
                            alerted = true
                            alert("${m.name} entrou na sala!", "Toque para começar a partida.")
                        }
                    }
                    is Multi.Msg.Start -> if (!s.host && m.guest == s.id && !alerted) {
                        alerted = true
                        alert("${hostName ?: "Seu amigo"} começou a partida!", "Toque para jogar.")
                    }
                    else -> {}
                }
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun openApp(): PendingIntent = PendingIntent.getActivity(
        this, 42,
        Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP).putExtra("mp_resume", true),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun ongoing(s: Multi.Session) = NotificationCompat.Builder(this, CH_ONGOING)
        .setSmallIcon(R.drawable.ic_notification)
        .setContentTitle(if (s.host) "Sala aberta · esperando seu amigo" else "Na sala · esperando o anfitrião")
        .setContentText("A sala fica aberta por 10 minutos. Toque para voltar.")
        .setWhen(s.expiresAt).setShowWhen(true).setUsesChronometer(true).setChronometerCountDown(true)
        .setOngoing(true).setOnlyAlertOnce(true).setSilent(true)
        .setCategory(NotificationCompat.CATEGORY_PROGRESS)
        .setContentIntent(openApp())
        .build()

    private fun alert(title: String, text: String) {
        if (appVisible) return
        val n = NotificationCompat.Builder(this, CH_ALERT)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title).setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_HIGH).setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setAutoCancel(true).setContentIntent(openApp())
            .build()
        runCatching { getSystemService(NotificationManager::class.java)?.notify(ALERT_ID, n) }
    }

    private fun channels() {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(NotificationChannel(CH_ONGOING, "Sala aberta", NotificationManager.IMPORTANCE_LOW).apply {
            description = "Mantém a sala da partida com amigo aberta enquanto você manda o link"
        })
        nm.createNotificationChannel(NotificationChannel(CH_ALERT, "Amigo entrou", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Avisa quando seu amigo entra na sala"
        })
    }
}
