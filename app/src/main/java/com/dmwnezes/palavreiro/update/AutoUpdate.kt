package com.dmwnezes.palavreiro.update

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.dmwnezes.palavreiro.AppGraph
import com.dmwnezes.palavreiro.MainActivity
import com.dmwnezes.palavreiro.R
import com.dmwnezes.palavreiro.data.Store
import com.dmwnezes.palavreiro.system.Reminder
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Atualização automática: ao abrir o app e uma vez por dia (com internet), busca a versão nova no GitHub,
 * baixa o APK em segundo plano e instala por uma sessão do PackageInstaller.
 * Do Android 12 em diante, depois que o próprio Palavreiro instalou uma vez, as próximas atualizações
 * entram sozinhas (USER_ACTION_NOT_REQUIRED). Quando o Android ainda pede confirmação, aparece a
 * notificação "Atualização pronta — toque para instalar" e, no app, a pílula "Nova versão baixada · Instalar".
 */
object AutoUpdate {
    private const val TAG = "AutoUpdate"
    private const val WORK_DAILY = "atualizacao_diaria"
    private const val WORK_NOW = "atualizacao_agora"
    private const val CHANNEL = "atualizacao"
    private const val NOTIFICATION_ID = 5
    const val EXTRA_INSTALL = "install_update"

    /** O que fazer com a versão encontrada. */
    enum class Action { NONE, DOWNLOAD, INSTALL }

    /**
     * Decide o próximo passo: nada (sem versão nova ou desligado), baixar, ou instalar o APK
     * que já está baixado ([pendingTag] com o arquivo ainda no aparelho).
     */
    fun decide(current: Int, release: Release?, enabled: Boolean, pendingTag: String?, pendingFileExists: Boolean): Action = when {
        !enabled || release == null -> Action.NONE
        !Updater.isNewer(release.tag, current) -> Action.NONE
        pendingTag == release.tag && pendingFileExists -> Action.INSTALL
        else -> Action.DOWNLOAD
    }

    /** "tag|caminho" salvo no Store → par, ou null se inválido. */
    fun parsePending(v: String?): Pair<String, String>? {
        val s = v ?: return null
        val i = s.indexOf('|')
        if (i <= 0 || i == s.lastIndex) return null
        return s.substring(0, i) to s.substring(i + 1)
    }

    /** Atualização baixada esperando o toque em "Instalar" (pílula no app). */
    data class Ready(val tag: String, val apk: File)

    var ready by mutableStateOf<Ready?>(null)
        internal set

    private val tried = HashSet<String>()

    /** O app está aberto na tela (não instala sozinho no meio de uma partida). */
    @Volatile var foreground = false

    /** Liga/desliga a busca diária conforme o ajuste. */
    fun schedule(context: Context) {
        val wm = runCatching { WorkManager.getInstance(context) }.getOrNull() ?: return
        if (!Store(context).autoUpdate) {
            wm.cancelUniqueWork(WORK_DAILY)
            return
        }
        val req = PeriodicWorkRequestBuilder<UpdateWorker>(1, TimeUnit.DAYS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setInitialDelay(6, TimeUnit.HOURS)
            .build()
        wm.enqueueUniquePeriodicWork(WORK_DAILY, ExistingPeriodicWorkPolicy.KEEP, req)
    }

    /** Busca agora (ao abrir o app), em segundo plano. */
    fun checkNow(context: Context) {
        if (!Store(context).autoUpdate) return
        val wm = runCatching { WorkManager.getInstance(context) }.getOrNull() ?: return
        val req = OneTimeWorkRequestBuilder<UpdateWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        wm.enqueueUniqueWork(WORK_NOW, ExistingWorkPolicy.KEEP, req)
    }

    /** APK baixado de uma versão mais nova que a instalada, se houver. */
    fun pending(context: Context, store: Store = Store(context)): Ready? {
        val (tag, path) = parsePending(store.pendingUpdate) ?: return null
        val f = File(path)
        if (!Updater.isNewer(tag, Updater.currentNumber) || !f.exists()) {
            store.pendingUpdate = null
            return null
        }
        return Ready(tag, f)
    }

    /** Ao abrir o app: mostra a pílula se já houver uma versão baixada. */
    fun restore(context: Context) {
        ready = pending(context)
    }

    /** Trabalho da busca: baixa e, com o app fechado, instala. */
    suspend fun run(context: Context) {
        val store = Store(context)
        if (!store.autoUpdate) return
        val updater = Updater(AppGraph.http)
        val release = updater.latest()
        val pend = parsePending(store.pendingUpdate)
        val action = decide(Updater.currentNumber, release, store.autoUpdate, pend?.first, pend?.second?.let { File(it).exists() } == true)
        Log.i(TAG, "versão ${release.tag}: $action")
        val apk = when (action) {
            Action.NONE -> return
            Action.INSTALL -> File(pend!!.second)
            Action.DOWNLOAD -> updater.download(context, release) {}.also { store.pendingUpdate = "${release.tag}|${it.path}" }
        }
        ready = Ready(release.tag, apk)
        // Com o app aberto, só avisa (pílula); instala quando o app sair da tela.
        if (!foreground) install(context, apk, release.tag, silent = true)
    }

    /** O app saiu da tela: se houver versão baixada e o automático estiver ligado, instala agora. */
    fun installIfPending(context: Context) {
        val store = Store(context)
        if (!store.autoUpdate) return
        val p = pending(context, store) ?: return
        // Uma tentativa por versão a cada abertura do app (evita avisar de novo toda vez que sai da tela).
        if (!tried.add(p.tag)) return
        runCatching { install(context, p.apk, p.tag, silent = true) }.onFailure { Log.w(TAG, "instalação falhou", it) }
    }

    /**
     * Instala [apk] por uma sessão do PackageInstaller. [silent]: veio da busca automática (sem a pessoa
     * olhando); se o Android pedir confirmação, avisa por notificação em vez de abrir a tela.
     */
    fun install(context: Context, apk: File, tag: String, silent: Boolean) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            if (Build.VERSION.SDK_INT >= 31) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }
        val id = installer.createSession(params)
        try {
            installer.openSession(id).use { session ->
                apk.inputStream().use { input ->
                    session.openWrite("Palavreiro.apk", 0, apk.length()).use { out ->
                        input.copyTo(out)
                        session.fsync(out)
                    }
                }
                val result = Intent(context, InstallReceiver::class.java)
                    .putExtra("tag", tag).putExtra("path", apk.path).putExtra("silent", silent)
                val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
                session.commit(PendingIntent.getBroadcast(context, 4300 + (id and 0xFF), result, flags).intentSender)
            }
        } catch (e: Exception) {
            runCatching { installer.abandonSession(id) }
            throw e
        }
    }

    /** Toque em "Instalar" (pílula, notificação ou janela): pede a permissão se precisar e instala. */
    fun installFromUser(context: Context, r: Ready? = ready ?: pending(context)) {
        r ?: return
        val updater = Updater(AppGraph.http)
        if (!updater.canInstall(context)) { updater.openInstallPermission(context); return }
        runCatching { install(context, r.apk, r.tag, silent = false) }
            .onFailure { Log.w(TAG, "sessão falhou, abrindo o instalador", it); runCatching { updater.install(context, r.apk) } }
    }

    internal fun notifyReady(context: Context, tag: String) {
        if (!Reminder.canNotify(context)) return
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Atualizações", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Avisa quando uma versão nova do Palavreiro foi baixada."
        })
        val open = PendingIntent.getActivity(
            context, 4301,
            Intent(context, MainActivity::class.java).putExtra(EXTRA_INSTALL, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Atualização pronta — toque para instalar")
            .setContentText("Palavreiro ${tag.removePrefix("v")} já foi baixado.")
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, n) }
    }

    internal fun cancelNotification(context: Context) {
        runCatching { NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID) }
    }
}

/** Trabalho do WorkManager: busca, baixa e instala. */
class UpdateWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        AppGraph.init(applicationContext)
        return runCatching { AutoUpdate.run(applicationContext) }.fold(
            { Result.success() },
            { e -> Log.w("AutoUpdate", "busca falhou", e); if (runAttemptCount < 2) Result.retry() else Result.failure() },
        )
    }
}

/** Resultado da sessão de instalação. */
class InstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val tag = intent.getStringExtra("tag") ?: return
        val path = intent.getStringExtra("path")
        val silent = intent.getBooleanExtra("silent", true)
        when (val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                path?.let { AutoUpdate.ready = AutoUpdate.Ready(tag, File(it)) }
                @Suppress("DEPRECATION")
                val confirm = if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                else intent.getParcelableExtra(Intent.EXTRA_INTENT)
                // A pessoa tocou em "Instalar" com o app aberto: mostra a confirmação do Android na hora.
                if (!silent && AutoUpdate.foreground && confirm != null) {
                    runCatching { context.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                        .onFailure { AutoUpdate.notifyReady(context, tag) }
                } else {
                    AutoUpdate.notifyReady(context, tag)
                }
            }
            PackageInstaller.STATUS_SUCCESS -> {
                Store(context).pendingUpdate = null
                AutoUpdate.ready = null
                AutoUpdate.cancelNotification(context)
            }
            else -> Log.w("AutoUpdate", "instalação: status $status ${intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)}")
        }
    }
}
